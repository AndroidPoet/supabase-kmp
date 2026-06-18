package io.github.androidpoet.supabase.functions

import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FunctionsClientImplTest {
    @Test
    fun test_invoke_withRegion_setsRegionHeader() =
        runTest {
            val fake = FakeSupabaseClient()
            val sut = FunctionsClientImpl(fake)

            val result =
                sut.invoke(
                    functionName = "hello",
                    body = """{"a":1}""",
                    region = FunctionRegion.AP_SOUTHEAST_1,
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("/functions/v1/hello", fake.lastPostEndpoint)
            assertEquals("ap-southeast-1", fake.lastPostHeaders["x-region"])
        }

    @Test
    fun test_invokeWithBody_usesRelativeFunctionsPath() =
        runTest {
            val fake = FakeSupabaseClient()
            val sut = FunctionsClientImpl(fake)

            val result =
                sut.invokeWithBody(
                    functionName = "upload",
                    body = byteArrayOf(1, 2, 3),
                    contentType = "application/octet-stream",
                )

            assertTrue(result is SupabaseResult.Success)
            // postRaw receives a relative path; SupabaseClientImpl.postRaw prepends
            // projectUrl exactly once. Passing an absolute URL here would duplicate it.
            assertEquals("/functions/v1/upload", fake.lastPostRawUrl)
        }

    @Test
    fun test_setAuth_setsAuthorizationHeaderForInvoke() =
        runTest {
            val fake = FakeSupabaseClient()
            val sut = FunctionsClientImpl(fake)

            sut.setAuth("jwt-1")
            val result = sut.invoke(functionName = "hello")

            assertTrue(result is SupabaseResult.Success)
            assertEquals("Bearer jwt-1", fake.lastPostHeaders["Authorization"])
        }

    @Test
    fun test_invoke_withBody_defaultsJsonContentType() =
        runTest {
            val fake = FakeSupabaseClient()
            val sut = FunctionsClientImpl(fake)

            val result = sut.invoke(functionName = "hello", body = """{"a":1}""")

            assertTrue(result is SupabaseResult.Success)
            assertEquals("application/json", fake.lastPostHeaders["Content-Type"])
        }

    @Test
    fun test_invoke_callerContentTypeIsNotOverridden() =
        runTest {
            val fake = FakeSupabaseClient()
            val sut = FunctionsClientImpl(fake)

            val result =
                sut.invoke(
                    functionName = "hello",
                    body = "plain",
                    headers = mapOf("Content-Type" to "text/plain"),
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("text/plain", fake.lastPostHeaders["Content-Type"])
        }

    @Test
    fun test_invoke_nullBody_doesNotSetContentType() =
        runTest {
            val fake = FakeSupabaseClient()
            val sut = FunctionsClientImpl(fake)

            val result = sut.invoke(functionName = "hello")

            assertTrue(result is SupabaseResult.Success)
            assertEquals(null, fake.lastPostHeaders["Content-Type"])
        }

    @Test
    fun test_invokeHeaderOverridesSetAuthAuthorization() =
        runTest {
            val fake = FakeSupabaseClient()
            val sut = FunctionsClientImpl(fake)

            sut.setAuth("jwt-1")
            val result =
                sut.invoke(
                    functionName = "hello",
                    headers = mapOf("Authorization" to "Bearer invoke-jwt"),
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("Bearer invoke-jwt", fake.lastPostHeaders["Authorization"])
        }
}

private class FakeSupabaseClient : SupabaseClient {
    override val projectUrl: String = "https://example.supabase.co"
    override val apiKey: String = "anon"
    override val accessTokenOrNull: String? = null

    var lastPostEndpoint: String? = null
    var lastPostHeaders: Map<String, String> = emptyMap()
    var lastPostRawUrl: String? = null

    override suspend fun get(endpoint: String, queryParams: List<Pair<String, String>>, headers: Map<String, String>): SupabaseResult<String> =
        SupabaseResult.Success("{}")

    override suspend fun post(endpoint: String, body: String?, headers: Map<String, String>): SupabaseResult<String> {
        lastPostEndpoint = endpoint
        lastPostHeaders = headers
        return SupabaseResult.Success("""{"ok":true}""")
    }

    override suspend fun put(endpoint: String, body: String?, headers: Map<String, String>): SupabaseResult<String> =
        SupabaseResult.Success("{}")

    override suspend fun patch(endpoint: String, body: String?, headers: Map<String, String>): SupabaseResult<String> =
        SupabaseResult.Success("{}")

    override suspend fun delete(endpoint: String, body: String?, headers: Map<String, String>): SupabaseResult<String> =
        SupabaseResult.Success("{}")

    override suspend fun postRaw(url: String, body: ByteArray, contentType: String, headers: Map<String, String>): SupabaseResult<String> {
        lastPostRawUrl = url
        return SupabaseResult.Success("""{"ok":true}""")
    }

    override suspend fun putRaw(url: String, body: ByteArray, contentType: String, headers: Map<String, String>): SupabaseResult<String> =
        SupabaseResult.Success("{}")

    override fun setAccessToken(token: String) = Unit

    override fun clearAccessToken() = Unit

    override suspend fun rawRequest(
        method: io.github.androidpoet.supabase.client.SupabaseHttpMethod,
        url: String,
        body: ByteArray?,
        contentType: String?,
        headers: Map<String, String>,
    ): io.github.androidpoet.supabase.core.result.SupabaseResult<io.github.androidpoet.supabase.client.SupabaseHttpResponse> =
        io.github.androidpoet.supabase.core.result.SupabaseResult.Failure(
            io.github.androidpoet.supabase.core.result
                .SupabaseError("rawRequest not supported in test fake"),
        )

    override fun close() = Unit
}
