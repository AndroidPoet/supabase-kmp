package io.github.androidpoet.supabase.core.result

import kotlinx.coroutines.CancellationException

public sealed interface SupabaseResult<out T> {
    public data class Success<out T>(public val value: T) : SupabaseResult<T>
    public data class Failure(public val error: SupabaseError) : SupabaseResult<Nothing>
    public val isSuccess: Boolean get() = this is Success
    public val isFailure: Boolean get() = this is Failure
    public fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }
    public fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw error.toException()
    }
    public fun errorOrNull(): SupabaseError? = when (this) {
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
    }
}
public inline fun <T, R> SupabaseResult<T>.map(
    transform: (T) -> R,
): SupabaseResult<R> = when (this) {
    is SupabaseResult.Success -> SupabaseResult.Success(transform(value))
    is SupabaseResult.Failure -> this
}
public inline fun <T, R> SupabaseResult<T>.flatMap(
    transform: (T) -> SupabaseResult<R>,
): SupabaseResult<R> = when (this) {
    is SupabaseResult.Success -> transform(value)
    is SupabaseResult.Failure -> this
}
public inline fun <T> SupabaseResult<T>.onSuccess(
    action: (T) -> Unit,
): SupabaseResult<T> = apply {
    if (this is SupabaseResult.Success) action(value)
}
public inline fun <T> SupabaseResult<T>.onFailure(
    action: (SupabaseError) -> Unit,
): SupabaseResult<T> = apply {
    if (this is SupabaseResult.Failure) action(error)
}
public inline fun <T> SupabaseResult<T>.onFailureCategory(
    category: SupabaseErrorCategory,
    action: (SupabaseError) -> Unit,
): SupabaseResult<T> = apply {
    if (this is SupabaseResult.Failure && error.category == category) {
        action(error)
    }
}
public inline fun <T, C> SupabaseResult<T>.onFailureCategory(
    category: C,
    classifier: (SupabaseError) -> C,
    action: (SupabaseError) -> Unit,
): SupabaseResult<T> = apply {
    if (this is SupabaseResult.Failure && classifier(error) == category) {
        action(error)
    }
}
public inline fun <T> SupabaseResult<T>.onConflict(
    action: (SupabaseError) -> Unit,
): SupabaseResult<T> = onFailureCategory(SupabaseErrorCategory.Conflict, action)
public inline fun <T> SupabaseResult<T>.onNotFound(
    action: (SupabaseError) -> Unit,
): SupabaseResult<T> = onFailureCategory(SupabaseErrorCategory.NotFound, action)
public inline fun <T> SupabaseResult<T>.onUnauthorized(
    action: (SupabaseError) -> Unit,
): SupabaseResult<T> = onFailureCategory(SupabaseErrorCategory.Unauthorized, action)
public inline fun <T> SupabaseResult<T>.onRateLimited(
    action: (SupabaseError) -> Unit,
): SupabaseResult<T> = onFailureCategory(SupabaseErrorCategory.RateLimited, action)
public inline fun <T> SupabaseResult<T>.mapError(
    transform: (SupabaseError) -> SupabaseError,
): SupabaseResult<T> = when (this) {
    is SupabaseResult.Success -> this
    is SupabaseResult.Failure -> SupabaseResult.Failure(transform(error))
}
public inline fun <T> SupabaseResult<T>.recover(
    transform: (SupabaseError) -> T,
): SupabaseResult<T> = when (this) {
    is SupabaseResult.Success -> this
    is SupabaseResult.Failure -> SupabaseResult.Success(transform(error))
}
public inline fun <T> SupabaseResult<T>.getOrElse(
    defaultValue: (SupabaseError) -> T,
): T = when (this) {
    is SupabaseResult.Success -> value
    is SupabaseResult.Failure -> defaultValue(error)
}

public fun <T> SupabaseResult<T>.toKotlinResult(): Result<T> = when (this) {
    is SupabaseResult.Success -> Result.success(value)
    is SupabaseResult.Failure -> Result.failure(error.toException())
}

public inline fun <T> Result<T>.toSupabaseResult(
    mapThrowable: (Throwable) -> SupabaseError = { throwable ->
        val supabaseException = throwable as? SupabaseException
        supabaseException?.error ?: SupabaseError(message = throwable.message ?: "Unknown error")
    },
): SupabaseResult<T> = fold(
    onSuccess = { SupabaseResult.Success(it) },
    onFailure = { throwable ->
        if (throwable is CancellationException) throw throwable
        SupabaseResult.Failure(mapThrowable(throwable))
    },
)
