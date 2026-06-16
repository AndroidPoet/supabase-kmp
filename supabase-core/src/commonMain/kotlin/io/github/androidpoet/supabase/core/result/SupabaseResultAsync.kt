package io.github.androidpoet.supabase.core.result

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

/**
 * Launches [block] on this scope and returns a [Deferred] handle to its
 * [SupabaseResult], so independent calls run concurrently and are awaited later.
 *
 * The SDK's own calls are plain `suspend` functions; this is the idiomatic way to
 * fire several at once. Any throwable [block] raises is captured as a
 * [SupabaseResult.Failure] (so `await()` never throws and a failure can't cancel
 * sibling calls); cooperative cancellation still propagates.
 *
 * ```
 * val todos = scope.deferredResult { database.selectTyped<Todo>("todos") }
 * val tags  = scope.deferredResult { database.selectTyped<Tag>("tags") }
 * val (t, g) = todos.await() to tags.await()   // both ran concurrently
 * ```
 */
public fun <T> CoroutineScope.deferredResult(
    block: suspend () -> SupabaseResult<T>,
): Deferred<SupabaseResult<T>> =
    async { SupabaseResult.suspendCatching { block() }.flatMap { it } }

/**
 * Awaits every deferred call and [mergeAll]s them: a success list (in the given
 * order) when all succeed, or the first [SupabaseResult.Failure]. The collect step
 * for a concurrent fan-out started with [deferredResult].
 *
 * ```
 * val pages = ids.map { id -> scope.deferredResult { api.fetchPage(id) } }
 * val all: SupabaseResult<List<Page>> = awaitMerged(pages)
 * ```
 */
public suspend fun <T> awaitMerged(
    deferreds: List<Deferred<SupabaseResult<T>>>,
): SupabaseResult<List<T>> = mergeAll(deferreds.awaitAll())

/** [awaitMerged] over a vararg of deferred results. */
public suspend fun <T> awaitMerged(
    vararg deferreds: Deferred<SupabaseResult<T>>,
): SupabaseResult<List<T>> = mergeAll(deferreds.toList().awaitAll())
