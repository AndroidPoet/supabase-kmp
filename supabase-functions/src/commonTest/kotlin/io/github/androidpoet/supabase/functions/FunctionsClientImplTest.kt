package io.github.androidpoet.supabase.functions

import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FunctionsClientImplTest {
    @Test
    fun test_invoke_withRegion_setsRegionHeaderAndQueryParam() =
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
            // A pinned region is sent both as the x-region header AND the
            // forceFunctionRegion query param (the authoritative routing override),
            // matching functions-js.
            assertEquals("/functions/v1/hello?forceFunctionRegion=ap-southeast-1", fake.lastPostEndpoint)
            assertEquals("ap-southeast-1", fake.lastPostHeaders["x-region"])
        }

    @Test
    fun test_invoke_withoutRegion_omitsForceFunctionRegionParam() =
        runTest {
            val fake = FakeSupabaseClient()
            val sut = FunctionsClientImpl(fake)

            sut.invoke(functionName = "hello", body = """{"a":1}""")
            assertEquals("/functions/v1/hello", fake.lastPostEndpoint)

            // ANY means "no pin": no header and no query param.
            sut.invoke(functionName = "hello", body = """{"a":1}""", region = FunctionRegion.ANY)
            assertEquals("/functions/v1/hello", fake.lastPostEndpoint)
            assertTrue(fake.lastPostHeaders["x-region"] == null)
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
    fun test_invoke_getMethod_issuesGetWithNoBody() =
        runTest {
            val fake = FakeSupabaseClient()
            val sut = FunctionsClientImpl(fake)

            // A body supplied alongside GET is ignored: GET requests carry no body.
            val result =
                sut.invoke(
                    functionName = "hello",
                    body = """{"a":1}""",
                    method = FunctionMethod.GET,
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals(1, fake.getCalls)
            assertEquals(0, fake.postCalls)
            assertEquals("/functions/v1/hello", fake.lastGetEndpoint)
            // GET goes through client.get, which has no body parameter, so the
            // JSON Content-Type default is never applied either.
            assertEquals(null, fake.lastGetHeaders["Content-Type"])
        }

    @Test
    fun test_invoke_defaultMethod_issuesPostWithBody() =
        runTest {
            val fake = FakeSupabaseClient()
            val sut = FunctionsClientImpl(fake)

            val result = sut.invoke(functionName = "hello", body = """{"a":1}""")

            assertTrue(result is SupabaseResult.Success)
            assertEquals(1, fake.postCalls)
            assertEquals(0, fake.getCalls)
            assertEquals("/functions/v1/hello", fake.lastPostEndpoint)
            assertEquals("""{"a":1}""", fake.lastPostBody)
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
    var lastPostBody: String? = null
    var lastPostRawUrl: String? = null

    var lastGetEndpoint: String? = null
    var lastGetHeaders: Map<String, String> = emptyMap()
    var getCalls: Int = 0
    var postCalls: Int = 0

    override suspend fun get(endpoint: String, queryParams: List<Pair<String, String>>, headers: Map<String, String>): SupabaseResult<String> {
        getCalls++
        lastGetEndpoint = endpoint
        lastGetHeaders = headers
        return SupabaseResult.Success("{}")
    }

    override suspend fun post(endpoint: String, body: String?, headers: Map<String, String>): SupabaseResult<String> {
        postCalls++
        lastPostEndpoint = endpoint
        lastPostHeaders = headers
        lastPostBody = body
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
