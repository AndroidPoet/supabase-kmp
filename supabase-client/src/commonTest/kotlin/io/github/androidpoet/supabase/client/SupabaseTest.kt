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
}
