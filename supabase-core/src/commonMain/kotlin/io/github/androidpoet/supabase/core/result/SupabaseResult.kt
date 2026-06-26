package io.github.androidpoet.supabase.core.result

import kotlinx.coroutines.CancellationException

public sealed interface SupabaseResult<out T> {
    public data class Success<out T>(
        public val value: T,
    ) : SupabaseResult<T>

    public data class Failure(
        public val error: SupabaseError,
    ) : SupabaseResult<Nothing>

    public val isSuccess: Boolean get() = this is Success
    public val isFailure: Boolean get() = this is Failure

    public fun getOrNull(): T? =
        when (this) {
            is Success -> value
            is Failure -> null
        }

    public fun getOrThrow(): T =
        when (this) {
            is Success -> value
            is Failure -> throw error.toException()
        }

    public fun errorOrNull(): SupabaseError? =
        when (this) {
            is Success -> null
            is Failure -> error
        }

    public companion object {
        public inline fun <T> catching(block: () -> T): SupabaseResult<T> =
            try {
                Success(block())
            } catch (e: SupabaseException) {
                Failure(e.error)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Failure(SupabaseError(message = e.message ?: "Unknown error"))
            }

        public suspend inline fun <T> suspendCatching(
            crossinline block: suspend () -> T,
        ): SupabaseResult<T> =
            try {
                Success(block())
            } catch (e: SupabaseException) {
                Failure(e.error)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Failure(SupabaseError(message = e.message ?: "Unknown error"))
            }
    }
}

public inline fun <T, R> SupabaseResult<T>.map(
    transform: (T) -> R,
): SupabaseResult<R> =
    when (this) {
        is SupabaseResult.Success -> SupabaseResult.Success(transform(value))
        is SupabaseResult.Failure -> this
    }

public inline fun <T, R> SupabaseResult<T>.flatMap(
    transform: (T) -> SupabaseResult<R>,
): SupabaseResult<R> =
    when (this) {
        is SupabaseResult.Success -> transform(value)
        is SupabaseResult.Failure -> this
    }

/**
 * Recovers from a [SupabaseResult.Failure] by mapping its error to another
 * result; a [SupabaseResult.Success] passes through unchanged. The failure-side
 * mirror of [flatMap], for fallback chains such as retrying after a refresh.
 */
public inline fun <T> SupabaseResult<T>.flatMapError(
    transform: (SupabaseError) -> SupabaseResult<T>,
): SupabaseResult<T> =
    when (this) {
        is SupabaseResult.Success -> this
        is SupabaseResult.Failure -> transform(error)
    }

/**
 * Turns a [SupabaseResult.Success] whose value fails [predicate] into a
 * [SupabaseResult.Failure] built by [lazyError]; a passing value and any
 * existing [SupabaseResult.Failure] pass through unchanged. Asserts an
 * invariant on a payload without an explicit `flatMap`/`if` dance.
 */
public inline fun <T> SupabaseResult<T>.validate(
    predicate: (T) -> Boolean,
    lazyError: (T) -> SupabaseError,
): SupabaseResult<T> =
    when (this) {
        is SupabaseResult.Success ->
            if (predicate(value)) this else SupabaseResult.Failure(lazyError(value))
        is SupabaseResult.Failure -> this
    }

/**
 * Combines two results: a [SupabaseResult.Success] of [transform] applied to
 * both values when both succeed, otherwise the first [SupabaseResult.Failure]
 * encountered (receiver before [other]). Lets independent calls compose without
 * nested `flatMap`s.
 */
public inline fun <A, B, R> SupabaseResult<A>.zip(
    other: SupabaseResult<B>,
    transform: (A, B) -> R,
): SupabaseResult<R> =
    when (this) {
        is SupabaseResult.Failure -> this
        is SupabaseResult.Success ->
            when (other) {
                is SupabaseResult.Failure -> other
                is SupabaseResult.Success -> SupabaseResult.Success(transform(value, other.value))
            }
    }

/**
 * Combines three results: a [SupabaseResult.Success] of [transform] applied to
 * all three values when every result succeeds, otherwise the first
 * [SupabaseResult.Failure] in receiver → [second] → [third] order.
 */
public inline fun <A, B, C, R> SupabaseResult<A>.zip(
    second: SupabaseResult<B>,
    third: SupabaseResult<C>,
    transform: (A, B, C) -> R,
): SupabaseResult<R> =
    when (this) {
        is SupabaseResult.Failure -> this
        is SupabaseResult.Success ->
            when (second) {
                is SupabaseResult.Failure -> second
                is SupabaseResult.Success ->
                    when (third) {
                        is SupabaseResult.Failure -> third
                        is SupabaseResult.Success ->
                            SupabaseResult.Success(transform(value, second.value, third.value))
                    }
            }
    }

/**
 * Collapses many homogeneous results into a single [SupabaseResult] of the list
 * of values, preserving order. Short-circuits and returns the first
 * [SupabaseResult.Failure] encountered, so the success path guarantees every
 * input succeeded.
 */
public fun <T> mergeAll(results: List<SupabaseResult<T>>): SupabaseResult<List<T>> {
    val values = ArrayList<T>(results.size)
    for (result in results) {
        when (result) {
            is SupabaseResult.Success -> values += result.value
            is SupabaseResult.Failure -> return result
        }
    }
    return SupabaseResult.Success(values)
}

/** [mergeAll] over a vararg of results. */
public fun <T> mergeAll(vararg results: SupabaseResult<T>): SupabaseResult<List<T>> =
    mergeAll(results.asList())

/**
 * Keeps a [SupabaseResult.Success] only while its value satisfies [predicate];
 * otherwise it becomes a [SupabaseResult.Failure] built by [lazyError]. A lighter
 * [validate] for quick guards where a generic error message is acceptable.
 */
public inline fun <T> SupabaseResult<T>.filter(
    lazyError: (T) -> SupabaseError = { SupabaseError(message = "Value did not match the predicate") },
    predicate: (T) -> Boolean,
): SupabaseResult<T> =
    when (this) {
        is SupabaseResult.Success ->
            if (predicate(value)) this else SupabaseResult.Failure(lazyError(value))
        is SupabaseResult.Failure -> this
    }

/** The negation of [filter]: a [SupabaseResult.Success] survives only when [predicate] is false. */
public inline fun <T> SupabaseResult<T>.filterNot(
    lazyError: (T) -> SupabaseError = { SupabaseError(message = "Value matched the excluded predicate") },
    predicate: (T) -> Boolean,
): SupabaseResult<T> =
    when (this) {
        is SupabaseResult.Success ->
            if (!predicate(value)) this else SupabaseResult.Failure(lazyError(value))
        is SupabaseResult.Failure -> this
    }

/** Returns the success value, or [defaultValue] on failure. The eager sibling of [getOrElse]. */
public fun <T> SupabaseResult<T>.getOrDefault(defaultValue: T): T =
    when (this) {
        is SupabaseResult.Success -> value
        is SupabaseResult.Failure -> defaultValue
    }

public inline fun <T> SupabaseResult<T>.onSuccess(
    action: (T) -> Unit,
): SupabaseResult<T> =
    apply {
        if (this is SupabaseResult.Success) action(value)
    }

public inline fun <T> SupabaseResult<T>.onFailure(
    action: (SupabaseError) -> Unit,
): SupabaseResult<T> =
    apply {
        if (this is SupabaseResult.Failure) action(error)
    }

public inline fun <T> SupabaseResult<T>.onFailureCategory(
    category: SupabaseErrorCategory,
    action: (SupabaseError) -> Unit,
): SupabaseResult<T> =
    apply {
        if (this is SupabaseResult.Failure && error.category == category) {
            action(error)
        }
    }

public inline fun <T, C> SupabaseResult<T>.onFailureCategory(
    category: C,
    classifier: (SupabaseError) -> C,
    action: (SupabaseError) -> Unit,
): SupabaseResult<T> =
    apply {
        if (this is SupabaseResult.Failure && classifier(error) == category) {
            action(error)
        }
    }

public inline fun <T> SupabaseResult<T>.onConflict(
    action: (SupabaseError) -> Unit,
): SupabaseResult<T> = onFailureCategory(SupabaseErrorCategory.CONFLICT, action)

public inline fun <T> SupabaseResult<T>.onNotFound(
    action: (SupabaseError) -> Unit,
): SupabaseResult<T> = onFailureCategory(SupabaseErrorCategory.NOT_FOUND, action)

public inline fun <T> SupabaseResult<T>.onUnauthorized(
    action: (SupabaseError) -> Unit,
): SupabaseResult<T> = onFailureCategory(SupabaseErrorCategory.UNAUTHORIZED, action)

public inline fun <T> SupabaseResult<T>.onRateLimited(
    action: (SupabaseError) -> Unit,
): SupabaseResult<T> = onFailureCategory(SupabaseErrorCategory.RATE_LIMITED, action)

public inline fun <T> SupabaseResult<T>.onNetworkError(
    action: (SupabaseError) -> Unit,
): SupabaseResult<T> = onFailureCategory(SupabaseErrorCategory.NETWORK, action)

public inline fun <T> SupabaseResult<T>.mapError(
    transform: (SupabaseError) -> SupabaseError,
): SupabaseResult<T> =
    when (this) {
        is SupabaseResult.Success -> this
        is SupabaseResult.Failure -> SupabaseResult.Failure(transform(error))
    }

public inline fun <T> SupabaseResult<T>.recover(
    transform: (SupabaseError) -> T,
): SupabaseResult<T> =
    when (this) {
        is SupabaseResult.Success -> this
        is SupabaseResult.Failure -> SupabaseResult.Success(transform(error))
    }

public inline fun <T> SupabaseResult<T>.getOrElse(
    defaultValue: (SupabaseError) -> T,
): T =
    when (this) {
        is SupabaseResult.Success -> value
        is SupabaseResult.Failure -> defaultValue(error)
    }

public inline fun <T, R> SupabaseResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (SupabaseError) -> R,
): R =
    when (this) {
        is SupabaseResult.Success -> onSuccess(value)
        is SupabaseResult.Failure -> onFailure(error)
    }

public suspend inline fun <T> SupabaseResult<T>.onSuccessSuspend(
    crossinline action: suspend (T) -> Unit,
): SupabaseResult<T> =
    apply {
        if (this is SupabaseResult.Success) action(value)
    }

public suspend inline fun <T> SupabaseResult<T>.onFailureSuspend(
    crossinline action: suspend (SupabaseError) -> Unit,
): SupabaseResult<T> =
    apply {
        if (this is SupabaseResult.Failure) action(error)
    }

public suspend inline fun <T> SupabaseResult<T>.onFailureCategorySuspend(
    category: SupabaseErrorCategory,
    crossinline action: suspend (SupabaseError) -> Unit,
): SupabaseResult<T> =
    apply {
        if (this is SupabaseResult.Failure && error.category == category) {
            action(error)
        }
    }

public suspend inline fun <T, C> SupabaseResult<T>.onFailureCategorySuspend(
    category: C,
    crossinline classifier: (SupabaseError) -> C,
    crossinline action: suspend (SupabaseError) -> Unit,
): SupabaseResult<T> =
    apply {
        if (this is SupabaseResult.Failure && classifier(error) == category) {
            action(error)
        }
    }

public suspend inline fun <T, R> SupabaseResult<T>.foldSuspend(
    crossinline onSuccess: suspend (T) -> R,
    crossinline onFailure: suspend (SupabaseError) -> R,
): R =
    when (this) {
        is SupabaseResult.Success -> onSuccess(value)
        is SupabaseResult.Failure -> onFailure(error)
    }

public suspend inline fun <T, R> SupabaseResult<T>.mapSuspend(
    crossinline transform: suspend (T) -> R,
): SupabaseResult<R> =
    when (this) {
        is SupabaseResult.Success -> SupabaseResult.Success(transform(value))
        is SupabaseResult.Failure -> this
    }

public suspend inline fun <T> SupabaseResult<T>.recoverSuspend(
    crossinline transform: suspend (SupabaseError) -> T,
): SupabaseResult<T> =
    when (this) {
        is SupabaseResult.Success -> this
        is SupabaseResult.Failure -> SupabaseResult.Success(transform(error))
    }

public suspend inline fun <T> SupabaseResult<T>.flatMapErrorSuspend(
    crossinline transform: suspend (SupabaseError) -> SupabaseResult<T>,
): SupabaseResult<T> =
    when (this) {
        is SupabaseResult.Success -> this
        is SupabaseResult.Failure -> transform(error)
    }

public fun <T> SupabaseResult<T>.toKotlinResult(): Result<T> =
    when (this) {
        is SupabaseResult.Success -> Result.success(value)
        is SupabaseResult.Failure -> Result.failure(error.toException())
    }

public inline fun <T> Result<T>.toSupabaseResult(
    mapThrowable: (Throwable) -> SupabaseError = { throwable ->
        val supabaseException = throwable as? SupabaseException
        supabaseException?.error ?: SupabaseError(message = throwable.message ?: "Unknown error")
    },
): SupabaseResult<T> =
    fold(
        onSuccess = { SupabaseResult.Success(it) },
        onFailure = { throwable ->
            if (throwable is CancellationException) throw throwable
            SupabaseResult.Failure(mapThrowable(throwable))
        },
    )
