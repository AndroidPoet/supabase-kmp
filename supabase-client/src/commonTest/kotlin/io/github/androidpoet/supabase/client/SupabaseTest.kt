package io.github.androidpoet.supabase.client

import io.github.androidpoet.supabase.client.transport.TestMockEngineFactory
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupabaseTest {
    @Test
    fun test_create_usesCustomEngineFactory() =
        runTest {
            var requestedUrl: String? = null
            val engineFactory =
                TestMockEngineFactory { request ->
                    requestedUrl = request.url.toString()
                    respond(
                        content = """{"ok":true}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client =
                Supabase.create(
                    projectUrl = "https://example.supabase.co",
                    apiKey = "anon",
                    engineFactory = engineFactory,
                )

            val result = client.get("/rest/v1/messages")

            assertTrue(result is SupabaseResult.Success)
            assertEquals("https://example.supabase.co/rest/v1/messages", requestedUrl)
        }

    @Test
    fun test_postRaw_prefixesAnEndpointPath() =
        runTest {
            var requestedUrl: String? = null
            val client = mockClient { requestedUrl = it }

            client.postRaw(url = "/storage/v1/object/bucket/key", body = byteArrayOf(1, 2, 3), contentType = "image/png")

            assertEquals("https://example.supabase.co/storage/v1/object/bucket/key", requestedUrl)
        }

    @Test
    fun test_postRaw_usesAnAbsoluteUrlVerbatim() =
        runTest {
            // A caller passing a full URL must NOT get it double-prefixed into
            // `https://example.supabase.cohttps://…` (the CHANGELOG-recorded footgun).
            var requestedUrl: String? = null
            val client = mockClient { requestedUrl = it }

            val absolute = "https://other.example.com/upload/signed?token=abc"
            client.postRaw(url = absolute, body = byteArrayOf(1, 2, 3), contentType = "image/png")

            assertEquals(absolute, requestedUrl)
        }

    private fun mockClient(record: (String) -> Unit) =
        Supabase.create(
            projectUrl = "https://example.supabase.co",
            apiKey = "anon",
            engineFactory =
                TestMockEngineFactory { request ->
                    record(request.url.toString())
                    respond(
                        content = "{}",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
        )
}
