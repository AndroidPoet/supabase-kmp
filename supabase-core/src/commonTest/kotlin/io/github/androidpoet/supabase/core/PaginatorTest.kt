package io.github.androidpoet.supabase.core

import io.github.androidpoet.supabase.core.paging.Paginator
import kotlinx.coroutines.CancellationException
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
    fun test_constructor_rejectsNonPositivePageSize() {
        assertFailsWith<IllegalArgumentException> {
            Paginator<Int>(pageSize = 0) { _, _ -> emptyList() }
        }
    }
}
