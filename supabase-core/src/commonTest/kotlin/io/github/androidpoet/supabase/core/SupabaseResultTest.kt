package io.github.androidpoet.supabase.core
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseException
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.core.result.flatMap
import io.github.androidpoet.supabase.core.result.getOrElse
import io.github.androidpoet.supabase.core.result.map
import io.github.androidpoet.supabase.core.result.onFailure
import io.github.androidpoet.supabase.core.result.onSuccess
import io.github.androidpoet.supabase.core.result.recover
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
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
        val result = SupabaseResult.Success(5)
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
        val result: SupabaseResult<String> = SupabaseResult.Failure(error)
            .recover { "default" }
        assertEquals("default", result.getOrNull())
    }
    @Test
    fun test_recover_leavesSuccessUntouched() {
        val result = SupabaseResult.Success("original")
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
        val result = SupabaseResult.catching {
            throw SupabaseException(error)
        }
        assertTrue(result.isFailure)
        assertEquals(error, result.errorOrNull())
    }
    @Test
    fun test_catching_wrapsGenericException() {
        val result = SupabaseResult.catching<Int> {
            throw IllegalStateException("boom")
        }
        assertTrue(result.isFailure)
        assertEquals("boom", result.errorOrNull()?.message)
    }
}
