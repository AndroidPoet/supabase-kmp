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

private fun config(accessTokenProvider: (suspend () -> String?)? = null) =
    SupabaseConfig(
        logging = false,
        logLevel = io.github.androidpoet.supabase.client.HttpLogLevel.NONE,
        headers = emptyMap(),
        accessTokenProvider = accessTokenProvider,
    )

private fun transport(
    apiKey: String,
    captured: MutableList<HttpRequestData>,
    accessTokenProvider: (suspend () -> String?)? = null,
) = HttpTransport(
    config = config(accessTokenProvider),
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

    @Test
    fun test_accessTokenProvider_suppliesBearer_withoutSession() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            // Third-party-auth: no session set, key is a modern publishable key that
            // would otherwise omit Authorization entirely.
            val transport = transport(MODERN_PUBLISHABLE, seen, accessTokenProvider = { "external-jwt" })
            transport.get(url = "https://example.supabase.co/rest/v1/x")
            assertEquals("Bearer external-jwt", seen.single().headers[HttpHeaders.Authorization])
            // The apikey header is still always sent.
            assertEquals(MODERN_PUBLISHABLE, seen.single().headers["apikey"])
        }

    @Test
    fun test_accessTokenProvider_winsOverSessionToken() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val transport = transport(MODERN_PUBLISHABLE, seen, accessTokenProvider = { "external-jwt" })
            transport.setAccessToken("user-session-jwt")
            transport.get(url = "https://example.supabase.co/rest/v1/x")
            assertEquals("Bearer external-jwt", seen.single().headers[HttpHeaders.Authorization])
        }

    @Test
    fun test_accessTokenProvider_returningNull_fallsBackToSession() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val transport = transport(MODERN_PUBLISHABLE, seen, accessTokenProvider = { null })
            transport.setAccessToken("user-session-jwt")
            transport.get(url = "https://example.supabase.co/rest/v1/x")
            assertEquals("Bearer user-session-jwt", seen.single().headers[HttpHeaders.Authorization])
        }

    @Test
    fun test_accessTokenProvider_returningNull_fallsBackToKeyRules() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            // Provider returns null and key is modern publishable => no Authorization.
            val transport = transport(MODERN_PUBLISHABLE, seen, accessTokenProvider = { null })
            transport.get(url = "https://example.supabase.co/rest/v1/x")
            assertNull(seen.single().headers[HttpHeaders.Authorization])
        }

    @Test
    fun test_callerAuthorizationHeader_overridesProvider() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val transport = transport(MODERN_PUBLISHABLE, seen, accessTokenProvider = { "external-jwt" })
            transport.get(
                url = "https://example.supabase.co/rest/v1/x",
                headers = mapOf("Authorization" to "Bearer caller-supplied"),
            )
            // A caller-provided Authorization header must never be overwritten.
            assertEquals("Bearer caller-supplied", seen.single().headers[HttpHeaders.Authorization])
        }
}
