package io.github.androidpoet.supabase.core

import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseException
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.core.result.awaitMerged
import io.github.androidpoet.supabase.core.result.deferredResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupabaseResultAsyncTest {
    @Test
    fun test_deferredResult_awaitsSuccess() =
        runTest {
            val deferred = deferredResult { SupabaseResult.Success(42) }
            assertEquals(42, deferred.await().getOrNull())
        }

    @Test
    fun test_deferredResult_runsConcurrently() =
        runTest {
            // Two calls each gated on the other's signal; they can only both
            // complete if they were started before either awaited.
            val gateA = CompletableDeferred<Unit>()
            val gateB = CompletableDeferred<Unit>()
            val a =
                deferredResult {
                    gateB.complete(Unit)
                    gateA.await()
                    SupabaseResult.Success(1)
                }
            val b =
                deferredResult {
                    gateA.complete(Unit)
                    gateB.await()
                    SupabaseResult.Success(2)
                }
            assertEquals(listOf(1, 2), awaitMerged(a, b).getOrNull())
        }

    @Test
    fun test_awaitMerged_combinesSuccessesInOrder() =
        runTest {
            val a = deferredResult { SupabaseResult.Success(1) }
            val b = deferredResult { SupabaseResult.Success(2) }
            val c = deferredResult { SupabaseResult.Success(3) }
            assertEquals(listOf(1, 2, 3), awaitMerged(a, b, c).getOrNull())
        }

    @Test
    fun test_awaitMerged_returnsFirstFailure() =
        runTest {
            val error = SupabaseError(message = "boom")
            val a = deferredResult { SupabaseResult.Success(1) }
            val b = deferredResult<Int> { SupabaseResult.Failure(error) }
            val out = awaitMerged(a, b)
            assertTrue(out.isFailure)
            assertEquals("boom", out.errorOrNull()?.message)
        }

    @Test
    fun test_deferredResult_capturesThrownExceptionAsFailure() =
        runTest {
            val deferred = deferredResult<Int> { throw IllegalStateException("boom") }
            val result = deferred.await() // must not throw
            assertTrue(result.isFailure)
            assertEquals("boom", result.errorOrNull()?.message)
        }

    @Test
    fun test_deferredResult_capturesSupabaseExceptionError() =
        runTest {
            val error = SupabaseError(message = "denied", code = "42501")
            val deferred = deferredResult<Int> { throw SupabaseException(error) }
            assertEquals(error, deferred.await().errorOrNull())
        }

    @Test
    fun test_awaitMerged_emptyListIsEmptySuccess() =
        runTest {
            val out = awaitMerged(emptyList<Deferred<SupabaseResult<Int>>>())
            assertEquals(emptyList(), out.getOrNull())
        }
}
