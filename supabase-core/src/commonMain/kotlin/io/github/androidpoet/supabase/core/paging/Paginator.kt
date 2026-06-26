package io.github.androidpoet.supabase.core.paging

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A small, dependency-free, **demand-driven** paginator for infinite-scroll UIs.
 *
 * Unlike a flow that walks every page on its own, this loads the next page only
 * when the consumer asks — call [loadNext] when the list nears its end. It holds
 * the accumulated [items], plus [isLoading], [isEndReached] and [error] as
 * [StateFlow]s a UI can observe directly (or fold into a screen state). It depends
 * on nothing but coroutines, so it works on every target and with or without
 * Compose.
 *
 * Provide a [fetch] that returns one page given an `offset` and `limit`; the
 * paginator advances the offset and stops ([isEndReached]) once a page comes back
 * shorter than [pageSize]. [fetch] is expected to **throw** on failure (e.g. via
 * `getOrThrow()`); the throwable is captured into [error] rather than propagated,
 * except [CancellationException], which is always rethrown so coroutine
 * cancellation is honoured.
 *
 * Wire it to the database with `DatabaseClient.paginator(...)`, or drive it
 * manually:
 *
 * ```
 * val pager = Paginator(pageSize = 20) { offset, limit ->
 *     api.load(offset, limit)            // returns List<T>, throws on error
 * }
 * // in the UI: observe pager.items; when the last visible row appears:
 * LaunchedEffect(lastVisibleIndex) { pager.loadNext() }
 * ```
 *
 * For keyset/seek paging over large or live tables, capture your own cursor in the
 * [fetch] closure and ignore `offset` — the offset is just a "how many loaded so
 * far" hint.
 *
 * @param pageSize rows requested per page; must be greater than 0.
 */
public class Paginator<T>(
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val fetch: suspend (offset: Int, limit: Int) -> List<T>,
) {
    init {
        require(pageSize > 0) { "pageSize must be greater than 0, was $pageSize" }
    }

    private val _items = MutableStateFlow<List<T>>(emptyList())

    /** The rows loaded so far, growing with each successful [loadNext]. */
    public val items: StateFlow<List<T>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)

    /** `true` while a page is being fetched. */
    public val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isEndReached = MutableStateFlow(false)

    /** `true` once a short page signalled there are no more rows. */
    public val isEndReached: StateFlow<Boolean> = _isEndReached.asStateFlow()

    private val _error = MutableStateFlow<Throwable?>(null)

    /** The last [fetch] failure, or `null`. Cleared when a new load starts. */
    public val error: StateFlow<Throwable?> = _error.asStateFlow()

    // Guards the compound check-then-set on the loading/end flags and the offset
    // (visibility alone is not enough for check-then-act — see the concurrency rules).
    private val mutex = Mutex()
    private var offset = 0

    // Bumped by refresh() so a page still in flight from before the refresh is
    // discarded instead of being appended on top of the reset list.
    private var epoch = 0

    /**
     * Loads and appends the next page. No-op if a load is already running or
     * [isEndReached] is set. Failures land in [error]; cancellation is rethrown.
     */
    public suspend fun loadNext() {
        val startEpoch =
            mutex.withLock {
                if (_isLoading.value || _isEndReached.value) return
                _isLoading.value = true
                _error.value = null
                epoch
            }
        runFetch(startEpoch)
    }

    /**
     * Resets to the first page and loads it again — for pull-to-refresh. Any page
     * still in flight is discarded so it cannot append onto the cleared list.
     */
    public suspend fun refresh() {
        val startEpoch =
            mutex.withLock {
                epoch++
                offset = 0
                _isEndReached.value = false
                _error.value = null
                _items.value = emptyList()
                // Take ownership of the loading flag in the SAME lock as the reset and
                // epoch bump: a page still in flight from the previous epoch would
                // otherwise keep _isLoading=true, making this reload a no-op and leaving
                // the list empty. The stale load's result is dropped by the epoch check,
                // and its finally won't clear our flag (the epoch no longer matches).
                _isLoading.value = true
                epoch
            }
        runFetch(startEpoch)
    }

    // Runs one fetch for [startEpoch] and folds the result in only while that epoch is
    // still current. The loading flag is cleared under the mutex and only by the load
    // that owns the current epoch, so a stale in-flight load can never clear a newer
    // load's flag.
    private suspend fun runFetch(startEpoch: Int) {
        try {
            val page = fetch(offset, pageSize)
            mutex.withLock {
                if (epoch == startEpoch) {
                    _items.update { it + page }
                    offset += page.size
                    if (page.size < pageSize) _isEndReached.value = true
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            mutex.withLock { if (epoch == startEpoch) _error.value = e }
        } finally {
            // Cleared under NonCancellable so a cancelled load still releases the flag
            // (the mutex is a suspension point that a cancelled coroutine would skip).
            withContext(NonCancellable) {
                mutex.withLock { if (epoch == startEpoch) _isLoading.value = false }
            }
        }
    }
}

private const val DEFAULT_PAGE_SIZE = 20
