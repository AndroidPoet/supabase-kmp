package io.github.androidpoet.supabase.functions

import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FunctionsClientExtTest {
    @Test
    fun test_invokeTyped_requestEncodesAndDecodes() =
        runTest {
            val client = FakeFunctionsClient()

            val result =
                client.invokeTyped<Req, Res>(
                    functionName = "echo",
                    request = Req("hello"),
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("hello", result.value.value)
            assertEquals("""{"value":"hello"}""", client.lastInvokeBody)
        }

    @Test
    fun test_invokeWithBodyTyped_decodesResponse() =
        runTest {
            val client = FakeFunctionsClient()

            val result =
                client.invokeWithBodyTyped<Res>(
                    functionName = "bin",
                    body = byteArrayOf(1),
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("binary-ok", result.value.value)
        }

    @Test
    fun test_invokeUnit_mapsSuccessToUnit() =
        runTest {
            val client = FakeFunctionsClient()
            val result = client.invokeUnit(functionName = "ping")
            assertTrue(result is SupabaseResult.Success)
        }
}

@Serializable
private data class Req(
    val value: String,
)

@Serializable
private data class Res(
    val value: String,
)

private class FakeFunctionsClient : FunctionsClient {
    var lastInvokeBody: String? = null

    override fun setAuth(token: String) = Unit

    override suspend fun invoke(
        functionName: String,
        body: String?,
        method: FunctionMethod,
        headers: Map<String, String>,
        region: FunctionRegion?,
    ): SupabaseResult<String> {
        lastInvokeBody = body
        return if (functionName == "echo") {
            SupabaseResult.Success("""{"value":"hello"}""")
        } else {
            SupabaseResult.Success("""{}""")
        }
    }

    override suspend fun invokeWithBody(
        functionName: String,
        body: ByteArray,
        contentType: String,
        method: FunctionMethod,
        headers: Map<String, String>,
        region: FunctionRegion?,
    ): SupabaseResult<String> =
        SupabaseResult.Success("""{"value":"binary-ok"}""")
}
