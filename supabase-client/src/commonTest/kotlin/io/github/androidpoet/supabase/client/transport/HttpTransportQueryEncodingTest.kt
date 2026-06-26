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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val KEY = "sb_publishable_abc123"

private fun transport(captured: MutableList<HttpRequestData>) =
    HttpTransport(
        config =
            SupabaseConfig(
                logging = false,
                logLevel = io.github.androidpoet.supabase.client.HttpLogLevel.NONE,
                headers = emptyMap(),
                accessTokenProvider = null,
            ),
        engineFactory =
            TestMockEngineFactory { request ->
                captured += request
                respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        projectUrl = "https://example.supabase.co",
        apiKey = KEY,
    )

/**
 * End-to-end query-encoding tests that drive the REAL Ktor encoder (`url.parameters.append`,
 * the path `select(...)` filters take) over MockEngine and inspect the wire URL. The unit-level
 * conformance tests assert the PostgREST grammar but use a fake client that never percent-encodes,
 * so without these a regression that mis-encoded (or double-encoded) filter values — `,`, `()`, `+`,
 * `"`, spaces — would pass unnoticed.
 */
class HttpTransportQueryEncodingTest {
    private suspend fun encodedQueryFor(vararg params: Pair<String, String>): Pair<String, HttpRequestData> {
        val seen = mutableListOf<HttpRequestData>()
        transport(seen).get(
            url = "https://example.supabase.co/rest/v1/messages",
            queryParams = params.toList(),
        )
        val request = seen.single()
        return request.url.encodedQuery to request
    }

    @Test
    fun test_commaValue_isPercentEncodedAndNotDoubleEncoded() =
        runTest {
            val (query, request) = encodedQueryFor("tag" to "eq.\"a,b\"")
            // Comma and double-quote must be percent-encoded on the wire...
            assertTrue("%2C" in query, "comma must encode to %2C, was: $query")
            assertTrue("%22" in query, "double-quote must encode to %22, was: $query")
            // ...but NOT double-encoded (a literal %25 would mean the % itself got re-encoded).
            assertFalse("%25" in query, "value must not be double-encoded, was: $query")
            // And it must round-trip back to the exact original value once decoded.
            assertEquals("eq.\"a,b\"", request.url.parameters["tag"])
        }

    @Test
    fun test_inListValue_roundTripsThroughEncoder() =
        runTest {
            val (query, request) = encodedQueryFor("priority" to "in.(1,2,3)")
            assertFalse("%25" in query, "value must not be double-encoded, was: $query")
            assertEquals("in.(1,2,3)", request.url.parameters["priority"])
        }

    @Test
    fun test_plusInTimestamp_isEncodedNotTreatedAsSpace() =
        runTest {
            // A literal '+' in a tz offset must encode to %2B; a bare '+' would decode to a space
            // server-side and corrupt the timestamp filter.
            val (query, request) = encodedQueryFor("created_at" to "gte.2021-01-01T00:00:00+00:00")
            assertTrue("%2B" in query, "'+' must encode to %2B, was: $query")
            assertEquals("gte.2021-01-01T00:00:00+00:00", request.url.parameters["created_at"])
        }

    @Test
    fun test_spaceInFullTextSearch_roundTrips() =
        runTest {
            val (query, request) = encodedQueryFor("body" to "fts.the cat")
            assertFalse("%25" in query, "value must not be double-encoded, was: $query")
            // Whichever way a space is encoded (+ or %20) it must decode back to a space.
            assertEquals("fts.the cat", request.url.parameters["body"])
        }
}
