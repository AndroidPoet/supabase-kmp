package io.github.androidpoet.supabase.client.transport

import io.github.androidpoet.supabase.client.SupabaseConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class HttpTransportTest {
    @Test
    fun test_get_appliesGlobalHeadersAndAllowsPerCallOverride() =
        runTest {
            var traceHeader: String? = null
            var sourceHeader: String? = null
            val engineFactory =
                object : HttpClientEngineFactory<MockEngineConfig> {
                    override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine {
                        val config =
                            MockEngineConfig().apply {
                                addHandler { request ->
                                    traceHeader = request.headers["X-Trace-Id"]
                                    sourceHeader = request.headers["X-Source"]
                                    respond(
                                        content = "{}",
                                        status = HttpStatusCode.OK,
                                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                    )
                                }
                                block()
                            }
                        return MockEngine(config)
                    }
                }
            val transport =
                HttpTransport(
                    config =
                        SupabaseConfig(
                            logging = false,
                            logLevel = io.ktor.client.plugins.logging.LogLevel.NONE,
                            headers =
                                mapOf(
                                    "X-Trace-Id" to "global-trace",
                                    "X-Source" to "global-source",
                                ),
                        ),
                    engineFactory = engineFactory,
                    projectUrl = "https://example.supabase.co",
                    apiKey = "anon",
                )

            transport.get(
                url = "https://example.supabase.co/rest/v1/messages",
                headers = mapOf("X-Trace-Id" to "request-trace"),
            )

            assertEquals("request-trace", traceHeader)
            assertEquals("global-source", sourceHeader)
        }

    @Test
    fun test_get_typedIOExceptionMapsToConnectionFailed() =
        runTest {
            val transport =
                HttpTransport(
                    config =
                        SupabaseConfig(
                            logging = false,
                            logLevel = io.ktor.client.plugins.logging.LogLevel.NONE,
                            headers = emptyMap(),
                        ),
                    engineFactory =
                        TestMockEngineFactory {
                            throw kotlinx.io.IOException("connection refused")
                        },
                    projectUrl = "https://example.supabase.co",
                    apiKey = "anon",
                )

            val result = transport.get(url = "https://example.supabase.co/rest/v1/messages")

            // An IOException carries neither "Timeout" nor "Connect" in its class name,
            // so the old string-matching path mislabeled it NETWORK_ERROR. Typed
            // classification recognizes it as a connection failure.
            assertEquals(
                io.github.androidpoet.supabase.core.result.SupabaseErrorCodes.Client.CONNECTION_FAILED,
                result.errorOrNull()?.code,
            )
        }

    @Test
    fun test_get_retryAfterZeroFallsBackToBackoff() =
        runTest {
            var calls = 0
            val transport =
                HttpTransport(
                    config =
                        SupabaseConfig(
                            logging = false,
                            logLevel = io.ktor.client.plugins.logging.LogLevel.NONE,
                            headers = emptyMap(),
                            retry =
                                io.github.androidpoet.supabase.client
                                    .RetryConfig(jitter = false),
                        ),
                    engineFactory =
                        TestMockEngineFactory {
                            calls++
                            if (calls == 1) {
                                respond(
                                    content = "{}",
                                    status = HttpStatusCode.TooManyRequests,
                                    headers = headersOf("Retry-After", "0"),
                                )
                            } else {
                                respond(
                                    content = "{}",
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                )
                            }
                        },
                    projectUrl = "https://example.supabase.co",
                    apiKey = "anon",
                )

            val start = testScheduler.currentTime
            val result =
                transport.get(
                    url = "https://example.supabase.co/rest/v1/messages",
                    headers = mapOf("X-Supabase-Kmp-Retry" to "true"),
                )

            assertEquals(2, calls)
            assertEquals(null, result.errorOrNull())
            // A Retry-After of 0 must NOT retry instantly; it falls back to the
            // exponential backoff (1s base, jitter off) instead of hammering the server.
            assertEquals(1_000, testScheduler.currentTime - start)
        }

    @Test
    fun test_get_positiveRetryAfterIsHonoredOverBackoff() =
        runTest {
            var calls = 0
            val transport =
                HttpTransport(
                    config =
                        SupabaseConfig(
                            logging = false,
                            logLevel = io.ktor.client.plugins.logging.LogLevel.NONE,
                            headers = emptyMap(),
                            retry =
                                io.github.androidpoet.supabase.client
                                    .RetryConfig(jitter = false),
                        ),
                    engineFactory =
                        TestMockEngineFactory {
                            calls++
                            if (calls == 1) {
                                respond(
                                    content = "{}",
                                    status = HttpStatusCode.TooManyRequests,
                                    headers = headersOf("Retry-After", "2"),
                                )
                            } else {
                                respond(
                                    content = "{}",
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                )
                            }
                        },
                    projectUrl = "https://example.supabase.co",
                    apiKey = "anon",
                )

            val start = testScheduler.currentTime
            transport.get(
                url = "https://example.supabase.co/rest/v1/messages",
                headers = mapOf("X-Supabase-Kmp-Retry" to "true"),
            )

            // Retry-After: 2s wins over the 1s backoff base, proving the header path.
            assertEquals(2_000, testScheduler.currentTime - start)
        }

    @Test
    fun test_get_propagatesCoroutineCancellation() =
        runTest {
            val transport =
                HttpTransport(
                    config =
                        SupabaseConfig(
                            logging = false,
                            logLevel = io.ktor.client.plugins.logging.LogLevel.NONE,
                            headers = emptyMap(),
                        ),
                    engineFactory =
                        TestMockEngineFactory {
                            delay(60_000)
                            respond(
                                content = "{}",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        },
                    projectUrl = "https://example.supabase.co",
                    apiKey = "anon",
                )

            val request =
                async {
                    transport.get(url = "https://example.supabase.co/rest/v1/messages")
                }
            runCurrent()
            request.cancel()

            assertFailsWith<CancellationException> {
                request.await()
            }
        }
}
