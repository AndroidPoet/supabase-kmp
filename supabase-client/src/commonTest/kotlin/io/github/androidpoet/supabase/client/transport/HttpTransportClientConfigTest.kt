package io.github.androidpoet.supabase.client.transport

import io.github.androidpoet.supabase.client.SupabaseConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val ANY_KEY = "sb_publishable_abc123"

private fun transport(
    captured: MutableList<HttpRequestData>,
    httpClientConfig: (io.ktor.client.HttpClientConfig<*>.() -> Unit)? = null,
) = HttpTransport(
    config =
        SupabaseConfig(
            logging = false,
            httpLogLevel = io.github.androidpoet.supabase.client.HttpLogLevel.NONE,
            headers = emptyMap(),
            httpClientConfig = httpClientConfig,
        ),
    engineFactory =
        TestMockEngineFactory { request ->
            captured += request
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    projectUrl = "https://example.supabase.co",
    apiKey = ANY_KEY,
)

class HttpTransportClientConfigTest {
    @Test
    fun test_httpClientConfig_isApplied() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            // The raw Ktor escape hatch can install plugins / tweak the client. Here
            // we use it to add a default header and assert it reaches the wire.
            val transport =
                transport(seen) {
                    defaultRequest {
                        header("X-Custom-From-Hook", "yes")
                    }
                }
            transport.get(url = "https://example.supabase.co/rest/v1/x")
            assertEquals("yes", seen.single().headers["X-Custom-From-Hook"])
        }

    @Test
    fun test_noHttpClientConfig_stillWorks() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            transport(seen).get(url = "https://example.supabase.co/rest/v1/x")
            // Library defaults remain intact when the hook is absent.
            assertEquals(ANY_KEY, seen.single().headers["apikey"])
        }
}
