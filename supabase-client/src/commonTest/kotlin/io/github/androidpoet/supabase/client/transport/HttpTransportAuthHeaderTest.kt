package io.github.androidpoet.supabase.client.transport

import io.github.androidpoet.supabase.client.SupabaseConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val LEGACY_ANON_JWT = "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiYW5vbiJ9.sig"
private const val MODERN_PUBLISHABLE = "sb_publishable_abc123"

private fun config() =
    SupabaseConfig(
        logging = false,
        logLevel = io.ktor.client.plugins.logging.LogLevel.NONE,
        headers = emptyMap(),
    )

private fun transport(
    apiKey: String,
    captured: MutableList<HttpRequestData>,
) = HttpTransport(
    config = config(),
    engineFactory =
        TestMockEngineFactory { request ->
            captured += request
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    projectUrl = "https://example.supabase.co",
    apiKey = apiKey,
)

class HttpTransportAuthHeaderTest {
    @Test
    fun test_apikeyHeader_isAlwaysSent() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            transport(MODERN_PUBLISHABLE, seen).get(url = "https://example.supabase.co/rest/v1/x")
            assertEquals(MODERN_PUBLISHABLE, seen.single().headers["apikey"])
        }

    @Test
    fun test_legacyJwtKey_anonymous_goesIntoBearer() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            transport(LEGACY_ANON_JWT, seen).get(url = "https://example.supabase.co/rest/v1/x")
            assertEquals("Bearer $LEGACY_ANON_JWT", seen.single().headers[HttpHeaders.Authorization])
        }

    @Test
    fun test_modernKey_anonymous_omitsAuthorization() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            transport(MODERN_PUBLISHABLE, seen).get(url = "https://example.supabase.co/rest/v1/x")
            // Modern publishable keys must NOT be sent as a Bearer token; the apikey
            // header authorizes the request instead.
            assertNull(seen.single().headers[HttpHeaders.Authorization])
            assertEquals(MODERN_PUBLISHABLE, seen.single().headers["apikey"])
        }

    @Test
    fun test_sessionToken_overridesKey_evenForModernKey() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val transport = transport(MODERN_PUBLISHABLE, seen)
            transport.setAccessToken("user-session-jwt")
            transport.get(url = "https://example.supabase.co/rest/v1/x")
            assertEquals("Bearer user-session-jwt", seen.single().headers[HttpHeaders.Authorization])
        }

    @Test
    fun test_clearAccessToken_revertsToKeyRules() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val transport = transport(MODERN_PUBLISHABLE, seen)
            transport.setAccessToken("user-session-jwt")
            transport.clearAccessToken()
            transport.get(url = "https://example.supabase.co/rest/v1/x")
            assertNull(seen.single().headers[HttpHeaders.Authorization])
        }
}
