package io.github.androidpoet.supabase.core

import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.core.result.filter
import io.github.androidpoet.supabase.core.result.filterNot
import io.github.androidpoet.supabase.core.result.getOrDefault
import io.github.androidpoet.supabase.core.result.mergeAll
import io.github.androidpoet.supabase.core.result.toFlow
import io.github.androidpoet.supabase.core.result.toResultFlow
import io.github.androidpoet.supabase.core.result.toSuspendFlow
import io.github.androidpoet.supabase.core.result.zip
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupabaseResultParityTest {
    private val error = SupabaseError(message = "not found", code = "PGRST116")

    @Test
    fun test_zip3_combinesThreeSuccesses() {
        val a: SupabaseResult<Int> = SupabaseResult.Success(2)
        val b: SupabaseResult<Int> = SupabaseResult.Success(3)
        val c: SupabaseResult<Int> = SupabaseResult.Success(4)
        assertEquals(9, a.zip(b, c) { x, y, z -> x + y + z }.getOrNull())
    }

    @Test
    fun test_zip3_returnsFirstFailureInOrder() {
        val a: SupabaseResult<Int> = SupabaseResult.Success(2)
        val b: SupabaseResult<Int> = SupabaseResult.Failure(error)
        val c: SupabaseResult<Int> = SupabaseResult.Failure(SupabaseError("third"))
        val out = a.zip(b, c) { x, y, z -> x + y + z }
        assertEquals("not found", out.errorOrNull()?.message)
    }

    @Test
    fun test_mergeAll_collectsValuesInOrder() {
        val out =
            mergeAll(
                listOf(
                    SupabaseResult.Success(1),
                    SupabaseResult.Success(2),
                    SupabaseResult.Success(3),
                ),
            )
        assertEquals(listOf(1, 2, 3), out.getOrNull())
    }

    @Test
    fun test_mergeAll_shortCircuitsOnFirstFailure() {
        val out =
            mergeAll(
                SupabaseResult.Success(1),
                SupabaseResult.Failure(error),
                SupabaseResult.Success(3),
            )
        assertTrue(out.isFailure)
        assertEquals("not found", out.errorOrNull()?.message)
    }

    @Test
    fun test_filter_passesWhenPredicateHolds() {
        val out = SupabaseResult.Success(10).filter { it > 0 }
        assertEquals(10, out.getOrNull())
    }

    @Test
    fun test_filter_failsWithCustomErrorWhenRejected() {
        val out =
            SupabaseResult.Success(-1).filter(lazyError = { SupabaseError("non-positive: $it") }) { it > 0 }
        assertTrue(out.isFailure)
        assertEquals("non-positive: -1", out.errorOrNull()?.message)
    }

    @Test
    fun test_filter_usesDefaultErrorWhenRejected() {
        val out = SupabaseResult.Success(-1).filter { it > 0 }
        assertTrue(out.isFailure)
        assertEquals("Value did not match the predicate", out.errorOrNull()?.message)
    }

    @Test
    fun test_filterNot_keepsValueWhenPredicateFalse() {
        val out = SupabaseResult.Success(5).filterNot { it < 0 }
        assertEquals(5, out.getOrNull())
    }

    @Test
    fun test_filterNot_failsWhenPredicateTrue() {
        val out = SupabaseResult.Success(5).filterNot { it > 0 }
        assertTrue(out.isFailure)
    }

    @Test
    fun test_getOrDefault_returnsValueOnSuccess() {
        assertEquals(7, SupabaseResult.Success(7).getOrDefault(-1))
    }

    @Test
    fun test_getOrDefault_returnsDefaultOnFailure() {
        val value: Int = SupabaseResult.Failure(error).getOrDefault(-1)
        assertEquals(-1, value)
    }

    @Test
    fun test_toFlow_emitsValueOnSuccess() =
        runTest {
            val emitted = SupabaseResult.Success(42).toFlow().toList()
            assertEquals(listOf(42), emitted)
        }

    @Test
    fun test_toFlow_isEmptyOnFailure() =
        runTest {
            val emitted = SupabaseResult.Failure(error).toFlow().toList()
            assertTrue(emitted.isEmpty())
        }

    @Test
    fun test_toFlow_appliesTransform() =
        runTest {
            val emitted = SupabaseResult.Success(21).toFlow { it * 2 }.toList()
            assertEquals(listOf(42), emitted)
        }

    @Test
    fun test_toSuspendFlow_emitsTransformedValue() =
        runTest {
            val emitted = SupabaseResult.Success(21).toSuspendFlow { it * 2 }.toList()
            assertEquals(listOf(42), emitted)
        }

    @Test
    fun test_toResultFlow_preservesFailure() =
        runTest {
            val emitted = SupabaseResult.Failure(error).toResultFlow().toList()
            assertEquals(1, emitted.size)
            assertEquals(error, emitted.single().errorOrNull())
        }
}
