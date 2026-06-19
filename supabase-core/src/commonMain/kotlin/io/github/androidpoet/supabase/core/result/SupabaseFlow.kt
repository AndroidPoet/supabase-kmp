package io.github.androidpoet.supabase.core.result

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Emits this already-computed [SupabaseResult] as a single-value cold [Flow],
 * keeping the [SupabaseResult] wrapper intact.
 *
 * Use it when you *have* a result in hand and want to drop it into the flow world
 * to compose with `combine`, `flatMapLatest`, `onStart`, etc. — for example to
 * seed a stream with a cached result before live values arrive:
 *
 * ```
 * merge(cached.asFlow(), liveResults).collect { result -> render(result) }
 * ```
 *
 * Prefer [supabaseFlow] when the result is produced by a suspending call you have
 * not run yet — that keeps the work cold (it runs on collection, not eagerly).
 */
public fun <T> SupabaseResult<T>.asFlow(): Flow<SupabaseResult<T>> =
    flow { emit(this@asFlow) }

/**
 * Wraps a one-shot suspending call that returns a [SupabaseResult] in a cold
 * [Flow] that runs the call on collection and emits its result exactly once,
 * **keeping the [SupabaseResult] wrapper**. The Result-first bridge from the SDK's
 * `suspend` functions into the flow world:
 *
 * ```
 * supabaseFlow { database.selectTyped<Todo>("todos") }
 *     .collect { result -> result.onSuccess { … }.onFailure { … } }
 * ```
 *
 * Reach for [dataFlow] instead when you do not want [SupabaseResult] in your
 * stream at all.
 */
public fun <T> supabaseFlow(block: suspend () -> SupabaseResult<T>): Flow<SupabaseResult<T>> =
    flow { emit(block()) }

/**
 * The **no-Result** access layer: wraps a one-shot suspending call in a cold
 * [Flow] that emits the plain value [T] on success and **throws** on failure
 * (via [SupabaseResult.getOrThrow], i.e. a [SupabaseException]) — so the wrapper
 * never appears in your data stream.
 *
 * Where this comes in:
 * - You prefer Kotlin's exception channel (`try`/`catch`) over branching on
 *   [SupabaseResult] — the happy path stays terse and untyped-by-wrapper.
 * - You want to plug into an existing exception-based stream pipeline (e.g. a
 *   presentation-layer `Flow<T>.asResult()` that adds Loading/Error by `catch`ing,
 *   the common ViewModel pattern). Such helpers expect a plain `Flow<T>` that
 *   throws; `dataFlow` produces exactly that:
 *
 * ```
 * dataFlow { database.selectTyped<Todo>("todos") }   // Flow<List<Todo>>, throws on error
 *     .map { it.toUiModels() }
 *     .asResult()                                     // your own Loading/Error wrapper
 *     .stateIn(scope, SharingStarted.WhileSubscribed(5_000), Loading)
 * ```
 *
 * The default library style stays Result-first ([supabaseFlow]); this is the
 * opt-in escape hatch for callers who want raw values and exceptions instead.
 */
public fun <T> dataFlow(block: suspend () -> SupabaseResult<T>): Flow<T> =
    flow { emit(block().getOrThrow()) }
