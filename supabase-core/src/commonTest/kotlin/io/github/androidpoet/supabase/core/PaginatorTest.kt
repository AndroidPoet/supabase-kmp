package io.github.androidpoet.supabase.core

import io.github.androidpoet.supabase.core.paging.Paginator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaginatorTest {
    @Test
    fun test_loadNext_accumulatesPagesAndStopsOnShortPage() =
        runTest {
            // 5 items, pageSize 2 → pages [0,1], [2,3], [4] (short → end).
            val all = (0 until 5).toList()
            val pager =
                Paginator(pageSize = 2) { offset, limit ->
                    all.drop(offset).take(limit)
                }

            pager.loadNext()
            assertEquals(listOf(0, 1), pager.items.value)
            assertTrue(!pager.endReached.value)

            pager.loadNext()
            assertEquals(listOf(0, 1, 2, 3), pager.items.value)

            pager.loadNext()
            assertEquals(listOf(0, 1, 2, 3, 4), pager.items.value)
            assertTrue(pager.endReached.value)
        }

    @Test
    fun test_loadNext_isNoOpAfterEndReached() =
        runTest {
            var calls = 0
            val pager =
                Paginator(pageSize = 10) { _, _ ->
                    calls++
                    listOf(1) // short immediately → endReached
                }
            pager.loadNext()
            pager.loadNext()
            pager.loadNext()
            assertEquals(1, calls)
            assertTrue(pager.endReached.value)
        }

    @Test
    fun test_loadNext_capturesErrorWithoutThrowing() =
        runTest {
            val pager =
                Paginator<Int>(pageSize = 2) { _, _ ->
                    throw IllegalStateException("boom")
                }
            pager.loadNext() // must not throw
            assertTrue(pager.error.value is IllegalStateException)
            assertTrue(pager.items.value.isEmpty())
            assertTrue(!pager.endReached.value) // a failure is retryable
        }

    @Test
    fun test_loadNext_rethrowsCancellation() =
        runTest {
            val pager =
                Paginator<Int>(pageSize = 2) { _, _ ->
                    throw CancellationException("cancelled")
                }
            assertFailsWith<CancellationException> { pager.loadNext() }
        }

    @Test
    fun test_refresh_resetsAndReloads() =
        runTest {
            val all = (0 until 4).toList()
            val pager = Paginator(pageSize = 2) { offset, limit -> all.drop(offset).take(limit) }

            pager.loadNext()
            pager.loadNext()
            assertEquals(listOf(0, 1, 2, 3), pager.items.value)

            pager.refresh()
            assertEquals(listOf(0, 1), pager.items.value) // back to first page
            assertNull(pager.error.value)
            assertTrue(!pager.endReached.value)
        }

    @Test
    fun test_refresh_whileLoadInFlight_stillLoadsFreshPage() =
        runTest {
            // The first load blocks inside fetch (so _isLoading stays true); a refresh that
            // arrives during it must still load the fresh first page, not no-op into an empty
            // list because the stale load is holding the loading flag.
            val firstLoadGate = CompletableDeferred<Unit>()
            var calls = 0
            val pager =
                Paginator(pageSize = 2) { _, _ ->
                    calls++
                    if (calls == 1) {
                        firstLoadGate.await()
                        listOf("stale-a", "stale-b")
                    } else {
                        listOf("fresh-a", "fresh-b")
                    }
                }

            val first = launch { pager.loadNext() }
            runCurrent() // first load is now suspended inside fetch with _isLoading = true

            val refreshed = launch { pager.refresh() }
            runCurrent() // refresh must load the fresh page despite the in-flight stale load

            firstLoadGate.complete(Unit) // release the stale load; its result must be discarded
            first.join()
            refreshed.join()

            assertEquals(listOf("fresh-a", "fresh-b"), pager.items.value)
            assertEquals(false, pager.isLoading.value)
        }

    @Test
    fun test_constructor_rejectsNonPositivePageSize() {
        assertFailsWith<IllegalArgumentException> {
            Paginator<Int>(pageSize = 0) { _, _ -> emptyList() }
        }
    }
}
