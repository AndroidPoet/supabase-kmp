package io.github.androidpoet.supabase.core.result

/**
 * A discriminated result type for Supabase operations.
 *
 * Every SDK call that can fail returns [SupabaseResult] instead of throwing,
 * giving callers full control over error handling via [map], [flatMap],
 * [recover], and friends.
 */
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
        /**
         * Executes [block] and wraps the outcome in a [SupabaseResult].
         *
         * Any [SupabaseException] is unwrapped back to its [SupabaseError].
         * All other exceptions are wrapped with their message.
         */
        public inline fun <T> catching(block: () -> T): SupabaseResult<T> =
            try {
                Success(block())
            } catch (e: SupabaseException) {
                Failure(e.error)
            } catch (e: Exception) {
                Failure(SupabaseError(message = e.message ?: "Unknown error"))
            }
    }
}

// ── Extension functions ─────────────────────────────────────────────────

/**
 * Transforms the success value, leaving failures untouched.
 */
public inline fun <T, R> SupabaseResult<T>.map(
    transform: (T) -> R,
): SupabaseResult<R> = when (this) {
    is SupabaseResult.Success -> SupabaseResult.Success(transform(value))
    is SupabaseResult.Failure -> this
}

/**
 * Transforms the success value into another [SupabaseResult], flattening
 * the nesting.
 */
public inline fun <T, R> SupabaseResult<T>.flatMap(
    transform: (T) -> SupabaseResult<R>,
): SupabaseResult<R> = when (this) {
    is SupabaseResult.Success -> transform(value)
    is SupabaseResult.Failure -> this
}

/**
 * Invokes [action] when the result is a success, returning `this` for chaining.
 */
public inline fun <T> SupabaseResult<T>.onSuccess(
    action: (T) -> Unit,
): SupabaseResult<T> = apply {
    if (this is SupabaseResult.Success) action(value)
}

/**
 * Invokes [action] when the result is a failure, returning `this` for chaining.
 */
public inline fun <T> SupabaseResult<T>.onFailure(
    action: (SupabaseError) -> Unit,
): SupabaseResult<T> = apply {
    if (this is SupabaseResult.Failure) action(error)
}

/**
 * Executes the given [action] if this result is an [SupabaseResult.Failure] of a specific [SupabaseErrorCategory].
 */
public inline fun <T> SupabaseResult<T>.onFailureCategory(
    category: SupabaseErrorCategory,
    action: (SupabaseError) -> Unit,
): SupabaseResult<T> = apply {
    if (this is SupabaseResult.Failure && error.category == category) {
        action(error)
    }
}

/** Helper for Conflict errors (e.g., UserAlreadyExists, UniquenessViolation) */
public inline fun <T> SupabaseResult<T>.onConflict(
    action: (SupabaseError) -> Unit,
): SupabaseResult<T> = onFailureCategory(SupabaseErrorCategory.Conflict, action)

/** Helper for NotFound errors (e.g., TableNotFound, UserNotFound) */
public inline fun <T> SupabaseResult<T>.onNotFound(
    action: (SupabaseError) -> Unit,
): SupabaseResult<T> = onFailureCategory(SupabaseErrorCategory.NotFound, action)

/** Helper for Unauthorized errors (e.g., InvalidCredentials, AccessDenied) */
public inline fun <T> SupabaseResult<T>.onUnauthorized(
    action: (SupabaseError) -> Unit,
): SupabaseResult<T> = onFailureCategory(SupabaseErrorCategory.Unauthorized, action)

/** Helper for RateLimited errors */
public inline fun <T> SupabaseResult<T>.onRateLimited(
    action: (SupabaseError) -> Unit,
): SupabaseResult<T> = onFailureCategory(SupabaseErrorCategory.RateLimited, action)

/**
 * Attempts to recover from a failure by producing a new success value.
 */
public inline fun <T> SupabaseResult<T>.recover(
    transform: (SupabaseError) -> T,
): SupabaseResult<T> = when (this) {
    is SupabaseResult.Success -> this
    is SupabaseResult.Failure -> SupabaseResult.Success(transform(error))
}

/**
 * Returns the success value or the result of [defaultValue] on failure.
 */
public inline fun <T> SupabaseResult<T>.getOrElse(
    defaultValue: (SupabaseError) -> T,
): T = when (this) {
    is SupabaseResult.Success -> value
    is SupabaseResult.Failure -> defaultValue(error)
}
