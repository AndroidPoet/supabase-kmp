package io.github.androidpoet.supabase.core.result

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * Emits the success value as a single-element [Flow], or an **empty** flow on
 * failure. Convenient for `stateIn`/`collectAsState` in MVVM/Compose layers.
 *
 * Failures are dropped silently — handle them with [onFailure]/[fold] before
 * converting, or use [asFlow] to keep the error in-stream.
 */
public fun <T> SupabaseResult<T>.toFlow(): Flow<T> =
    when (this) {
        is SupabaseResult.Success -> flowOf(value)
        is SupabaseResult.Failure -> emptyFlow()
    }

/** [toFlow] that maps the success value through [transform] before emitting. */
public inline fun <T, R> SupabaseResult<T>.toFlow(
    transform: (T) -> R,
): Flow<R> =
    when (this) {
        is SupabaseResult.Success -> flowOf(transform(value))
        is SupabaseResult.Failure -> emptyFlow()
    }

/**
 * [toFlow] with a `suspend` transform: emits the transformed success value, or
 * nothing on failure. The transform runs inside the flow's collection context.
 */
public inline fun <T, R> SupabaseResult<T>.toSuspendFlow(
    crossinline transform: suspend (T) -> R,
): Flow<R> {
    val self = this
    return flow {
        if (self is SupabaseResult.Success) emit(transform(self.value))
    }
}
