package io.github.androidpoet.supabase.core
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseException
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.core.result.flatMap
import io.github.androidpoet.supabase.core.result.flatMapError
import io.github.androidpoet.supabase.core.result.flatMapErrorSuspend
import io.github.androidpoet.supabase.core.result.fold
import io.github.androidpoet.supabase.core.result.foldSuspend
import io.github.androidpoet.supabase.core.result.getOrElse
import io.github.androidpoet.supabase.core.result.map
import io.github.androidpoet.supabase.core.result.mapError
import io.github.androidpoet.supabase.core.result.mapSuspend
import io.github.androidpoet.supabase.core.result.onFailure
import io.github.androidpoet.supabase.core.result.onFailureCategory
import io.github.androidpoet.supabase.core.result.onFailureCategorySuspend
import io.github.androidpoet.supabase.core.result.onFailureSuspend
import io.github.androidpoet.supabase.core.result.onSuccess
import io.github.androidpoet.supabase.core.result.onSuccessSuspend
import io.github.androidpoet.supabase.core.result.recover
import io.github.androidpoet.supabase.core.result.recoverSuspend
import io.github.androidpoet.supabase.core.result.toKotlinResult
import io.github.androidpoet.supabase.core.result.toSupabaseResult
import io.github.androidpoet.supabase.core.result.validate
import io.github.androidpoet.supabase.core.result.zip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SupabaseResultTest {
    private val error = SupabaseError(message = "not found", code = "PGRST116")

    @Test
    fun test_success_holdsValue() {
        val result: SupabaseResult<Int> = SupabaseResult.Success(42)
        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
        assertEquals(42, result.getOrNull())
        assertEquals(42, result.getOrThrow())
        assertNull(result.errorOrNull())
    }

    @Test
    fun test_failure_holdsError() {
        val result: SupabaseResult<Int> = SupabaseResult.Failure(error)
        assertFalse(result.isSuccess)
        assertTrue(result.isFailure)
        assertNull(result.getOrNull())
        assertEquals(error, result.errorOrNull())
    }

    @Test
    fun test_failure_getOrThrowThrowsSupabaseException() {
        val result: SupabaseResult<Int> = SupabaseResult.Failure(error)
        val exception = assertFailsWith<SupabaseException> { result.getOrThrow() }
        assertEquals(error, exception.error)
    }

    @Test
    fun test_map_transformsSuccess() {
        val result = SupabaseResult.Success(10).map { it * 2 }
        assertEquals(20, result.getOrNull())
    }

    @Test
    fun test_map_preservesFailure() {
        val result: SupabaseResult<Int> = SupabaseResult.Failure(error)
        val mapped = result.map { it * 2 }
        assertTrue(mapped.isFailure)
        assertEquals(error, mapped.errorOrNull())
    }

    @Test
    fun test_flatMap_chainsSuccesses() {
        val result =
            SupabaseResult
                .Success(5)
                .flatMap { SupabaseResult.Success(it.toString()) }
        assertEquals("5", result.getOrNull())
    }

    @Test
    fun test_flatMap_shortCircuitsOnFailure() {
        val result: SupabaseResult<Int> = SupabaseResult.Failure(error)
        val chained = result.flatMap { SupabaseResult.Success(it.toString()) }
        assertTrue(chained.isFailure)
    }

    @Test
    fun test_onSuccess_invokesForSuccess() {
        var captured = 0
        SupabaseResult.Success(7).onSuccess { captured = it }
        assertEquals(7, captured)
    }

    @Test
    fun test_onFailure_invokesForFailure() {
        var captured: SupabaseError? = null
        SupabaseResult.Failure(error).onFailure { captured = it }
        assertEquals(error, captured)
    }

    @Test
    fun test_recover_convertsFailureToSuccess() {
        val result: SupabaseResult<String> =
            SupabaseResult
                .Failure(error)
                .recover { "default" }
        assertEquals("default", result.getOrNull())
    }

    @Test
    fun test_recover_leavesSuccessUntouched() {
        val result =
            SupabaseResult
                .Success("original")
                .recover { "default" }
        assertEquals("original", result.getOrNull())
    }

    @Test
    fun test_getOrElse_returnsValueOnSuccess() {
        val value = SupabaseResult.Success(99).getOrElse { -1 }
        assertEquals(99, value)
    }

    @Test
    fun test_getOrElse_returnsDefaultOnFailure() {
        val value: Int = SupabaseResult.Failure(error).getOrElse { -1 }
        assertEquals(-1, value)
    }

    @Test
    fun test_catching_wrapsSuccessfulBlock() {
        val result = SupabaseResult.catching { 42 }
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun test_catching_wrapsSupabaseException() {
        val result =
            SupabaseResult.catching {
                throw SupabaseException(error)
            }
        assertTrue(result.isFailure)
        assertEquals(error, result.errorOrNull())
    }

    @Test
    fun test_catching_wrapsGenericException() {
        val result =
            SupabaseResult.catching<Int> {
                throw IllegalStateException("boom")
            }
        assertTrue(result.isFailure)
        assertEquals("boom", result.errorOrNull()?.message)
    }

    @Test
    fun test_catching_rethrowsCancellationException() {
        assertFailsWith<CancellationException> {
            SupabaseResult.catching<Int> {
                throw CancellationException("cancel")
            }
        }
    }

    @Test
    fun test_onFailureCategory_supportsCustomClassifier() {
        var called = false
        val result: SupabaseResult<Int> = SupabaseResult.Failure(error)

        result.onFailureCategory(
            category = "pgrst",
            classifier = { if (it.code?.startsWith("PGRST") == true) "pgrst" else "other" },
        ) {
            called = true
        }

        assertTrue(called)
    }

    @Test
    fun test_mapError_transformsFailureOnly() {
        val transformed =
            SupabaseResult.Failure(error).mapError {
                it.copy(message = "mapped")
            }
        val successUnchanged =
            SupabaseResult.Success(1).mapError {
                it.copy(message = "ignored")
            }

        assertEquals("mapped", transformed.errorOrNull()?.message)
        assertEquals(1, successUnchanged.getOrNull())
    }

    @Test
    fun test_toKotlinResult_mapsSuccess() {
        val result = SupabaseResult.Success(5).toKotlinResult()
        assertTrue(result.isSuccess)
        assertEquals(5, result.getOrNull())
    }

    @Test
    fun test_toKotlinResult_mapsFailureToSupabaseException() {
        val result = SupabaseResult.Failure(error).toKotlinResult()
        val exception = result.exceptionOrNull()
        assertIs<SupabaseException>(exception)
        assertEquals(error, exception.error)
    }

    @Test
    fun test_toSupabaseResult_mapsKotlinSuccess() {
        val result = Result.success("ok").toSupabaseResult()
        assertTrue(result.isSuccess)
        assertEquals("ok", result.getOrNull())
    }

    @Test
    fun test_toSupabaseResult_mapsSupabaseExceptionFailure() {
        val result = Result.failure<String>(SupabaseException(error)).toSupabaseResult()
        assertTrue(result.isFailure)
        assertEquals(error, result.errorOrNull())
    }

    @Test
    fun test_toSupabaseResult_mapsGenericFailure() {
        val result = Result.failure<String>(IllegalArgumentException("bad")).toSupabaseResult()
        assertTrue(result.isFailure)
        assertEquals("bad", result.errorOrNull()?.message)
    }

    @Test
    fun test_toSupabaseResult_rethrowsCancellationException() {
        assertFailsWith<CancellationException> {
            Result.failure<String>(CancellationException("stop")).toSupabaseResult()
        }
    }

    @Test
    fun test_suspendCatching_wrapsSuccessfulSuspend() =
        runTest {
            val result = SupabaseResult.suspendCatching { 42 }
            assertEquals(42, result.getOrNull())
        }

    @Test
    fun test_suspendCatching_wrapsSupabaseException() =
        runTest {
            val result =
                SupabaseResult.suspendCatching<Int> {
                    throw SupabaseException(error)
                }
            assertTrue(result.isFailure)
            assertEquals(error, result.errorOrNull())
        }

    @Test
    fun test_suspendCatching_wrapsGenericException() =
        runTest {
            val result =
                SupabaseResult.suspendCatching<Int> {
                    throw IllegalStateException("boom")
                }
            assertTrue(result.isFailure)
            assertEquals("boom", result.errorOrNull()?.message)
        }

    @Test
    fun test_suspendCatching_rethrowsCancellationException() =
        runTest {
            assertFailsWith<CancellationException> {
                SupabaseResult.suspendCatching<Int> {
                    throw CancellationException("cancel")
                }
            }
        }

    @Test
    fun test_fold_invokesOnSuccess() {
        val value =
            SupabaseResult.Success(10).fold(
                onSuccess = { it * 2 },
                onFailure = { -1 },
            )
        assertEquals(20, value)
    }

    @Test
    fun test_fold_invokesOnFailure() {
        val value: Int =
            SupabaseResult.Failure(error).fold(
                onSuccess = { 0 },
                onFailure = { -1 },
            )
        assertEquals(-1, value)
    }

    @Test
    fun test_onSuccessSuspend_invokesForSuccess() =
        runTest {
            var captured = 0
            SupabaseResult.Success(7).onSuccessSuspend { captured = it * 2 }
            assertEquals(14, captured)
        }

    @Test
    fun test_onFailureSuspend_invokesForFailure() =
        runTest {
            var captured: SupabaseError? = null
            SupabaseResult.Failure(error).onFailureSuspend { captured = it }
            assertEquals(error, captured)
        }

    @Test
    fun test_foldSuspend_invokesOnSuccess() =
        runTest {
            val value =
                SupabaseResult.Success(10).foldSuspend(
                    onSuccess = { it * 2 },
                    onFailure = { -1 },
                )
            assertEquals(20, value)
        }

    @Test
    fun test_foldSuspend_invokesOnFailure() =
        runTest {
            val value: Int =
                SupabaseResult.Failure(error).foldSuspend(
                    onSuccess = { 0 },
                    onFailure = { -1 },
                )
            assertEquals(-1, value)
        }

    @Test
    fun test_mapSuspend_transformsSuccess() =
        runTest {
            val result = SupabaseResult.Success(10).mapSuspend { it * 2 }
            assertEquals(20, result.getOrNull())
        }

    @Test
    fun test_mapSuspend_preservesFailure() =
        runTest {
            val mapped: SupabaseResult<Int> =
                SupabaseResult
                    .Failure(error)
                    .mapSuspend { 0 }
            assertTrue(mapped.isFailure)
            assertEquals(error, mapped.errorOrNull())
        }

    @Test
    fun test_recoverSuspend_convertsFailureToSuccess() =
        runTest {
            val result: SupabaseResult<String> =
                SupabaseResult
                    .Failure(error)
                    .recoverSuspend { "default" }
            assertEquals("default", result.getOrNull())
        }

    @Test
    fun test_recoverSuspend_leavesSuccessUntouched() =
        runTest {
            val result =
                SupabaseResult
                    .Success("original")
                    .recoverSuspend { "default" }
            assertEquals("original", result.getOrNull())
        }

    @Test
    fun test_onFailureCategorySuspend_invokesForMatchingCategory() =
        runTest {
            val notFoundError = SupabaseError(message = "table not found", code = "PGRST205")
            var called = false
            SupabaseResult.Failure(notFoundError).onFailureCategorySuspend(
                category = io.github.androidpoet.supabase.core.result.SupabaseErrorCategory.NotFound,
            ) { called = true }
            assertTrue(called)
        }

    @Test
    fun test_onFailureCategorySuspend_withClassifier_invokesOnMatch() =
        runTest {
            var called = false
            SupabaseResult.Failure(error).onFailureCategorySuspend(
                category = "pgrst",
                classifier = { if (it.code?.startsWith("PGRST") == true) "pgrst" else "other" },
            ) { called = true }
            assertTrue(called)
        }

    @Test
    fun test_onFailureCategorySuspend_skipsNonMatchingCategory() =
        runTest {
            var called = false
            SupabaseResult.Failure(error).onFailureCategorySuspend(
                category = io.github.androidpoet.supabase.core.result.SupabaseErrorCategory.Conflict,
            ) { called = true }
            assertFalse(called)
        }

    @Test
    fun test_flatMapError_recoversFailure() {
        val recovered: SupabaseResult<Int> =
            SupabaseResult.Failure(error).flatMapError { SupabaseResult.Success(7) }
        assertEquals(7, recovered.getOrNull())
    }

    @Test
    fun test_flatMapError_passesThroughSuccess() {
        val result: SupabaseResult<Int> = SupabaseResult.Success(1)
        val out = result.flatMapError { SupabaseResult.Success(99) }
        assertEquals(1, out.getOrNull())
    }

    @Test
    fun test_flatMapError_canStayFailure() {
        val out: SupabaseResult<Int> =
            SupabaseResult.Failure(error).flatMapError { SupabaseResult.Failure(it) }
        assertEquals("not found", out.errorOrNull()?.message)
    }

    @Test
    fun test_flatMapErrorSuspend_recoversFailure() =
        runTest {
            val recovered: SupabaseResult<Int> =
                SupabaseResult.Failure(error).flatMapErrorSuspend { SupabaseResult.Success(5) }
            assertEquals(5, recovered.getOrNull())
        }

    @Test
    fun test_validate_passesWhenPredicateHolds() {
        val result: SupabaseResult<Int> = SupabaseResult.Success(10)
        val out = result.validate({ it > 0 }) { SupabaseError("non-positive") }
        assertEquals(10, out.getOrNull())
    }

    @Test
    fun test_validate_failsWhenPredicateRejects() {
        val result: SupabaseResult<Int> = SupabaseResult.Success(-1)
        val out = result.validate({ it > 0 }) { SupabaseError("non-positive: $it") }
        assertTrue(out.isFailure)
        assertEquals("non-positive: -1", out.errorOrNull()?.message)
    }

    @Test
    fun test_validate_passesThroughExistingFailure() {
        val out: SupabaseResult<Int> =
            SupabaseResult.Failure(error).validate({ true }) { SupabaseError("never") }
        assertEquals("not found", out.errorOrNull()?.message)
    }

    @Test
    fun test_zip_combinesTwoSuccesses() {
        val a: SupabaseResult<Int> = SupabaseResult.Success(2)
        val b: SupabaseResult<Int> = SupabaseResult.Success(3)
        assertEquals(6, a.zip(b) { x, y -> x * y }.getOrNull())
    }

    @Test
    fun test_zip_returnsFirstFailure() {
        val a: SupabaseResult<Int> = SupabaseResult.Failure(error)
        val b: SupabaseResult<Int> = SupabaseResult.Success(3)
        val out = a.zip(b) { x, y -> x + y }
        assertEquals("not found", out.errorOrNull()?.message)
    }

    @Test
    fun test_zip_returnsOtherFailureWhenReceiverSucceeds() {
        val other = SupabaseError(message = "other failed")
        val a: SupabaseResult<Int> = SupabaseResult.Success(1)
        val b: SupabaseResult<Int> = SupabaseResult.Failure(other)
        val out = a.zip(b) { x, y -> x + y }
        assertEquals("other failed", out.errorOrNull()?.message)
    }
}
