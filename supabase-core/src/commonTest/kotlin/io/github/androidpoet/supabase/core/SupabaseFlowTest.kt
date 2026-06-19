package io.github.androidpoet.supabase.core

import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseException
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.core.result.asFlow
import io.github.androidpoet.supabase.core.result.dataFlow
import io.github.androidpoet.supabase.core.result.supabaseFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SupabaseFlowTest {
    @Test
    fun test_asFlow_emitsTheResultOnce() =
        runTest {
            val result: SupabaseResult<Int> = SupabaseResult.Success(7)
            assertEquals(listOf(result), result.asFlow().toList())
        }

    @Test
    fun test_supabaseFlow_isColdAndKeepsWrapper() =
        runTest {
            var ran = 0
            val flow = supabaseFlow { SupabaseResult.Success(++ran) }
            assertEquals(0, ran) // not run until collected
            assertEquals(listOf(SupabaseResult.Success(1)), flow.toList())
            assertEquals(1, ran)
        }

    @Test
    fun test_dataFlow_emitsPlainValueOnSuccess() =
        runTest {
            val values = dataFlow { SupabaseResult.Success(listOf("a", "b")) }.toList()
            assertEquals(listOf(listOf("a", "b")), values)
        }

    @Test
    fun test_dataFlow_throwsOnFailure() =
        runTest {
            val ex =
                assertFailsWith<SupabaseException> {
                    dataFlow { SupabaseResult.Failure(SupabaseError(message = "boom", code = "x")) }.toList()
                }
            assertTrue(ex.error.message == "boom")
        }
}
