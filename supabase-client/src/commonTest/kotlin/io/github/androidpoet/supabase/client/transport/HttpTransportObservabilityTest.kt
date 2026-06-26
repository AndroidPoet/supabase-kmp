package io.github.androidpoet.supabase.client.transport

import io.github.androidpoet.supabase.client.HttpLogLevel
import io.github.androidpoet.supabase.client.RetryConfig
import io.github.androidpoet.supabase.client.SupabaseConfig
import io.github.androidpoet.supabase.client.SupabaseInterceptor
import io.github.androidpoet.supabase.client.SupabaseLogLevel
import io.github.androidpoet.supabase.client.SupabaseLogger
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val RETRY_HEADER = "X-Supabase-Kmp-Retry"

private class RecordingInterceptor : SupabaseInterceptor {
    val requests = mutableListOf<Pair<String, String>>()
    val responses = mutableListOf<Triple<String, String, Int>>()
    val errors = mutableListOf<Triple<String, String, SupabaseError>>()

    override suspend fun onRequest(method: String, url: String) {
        requests += method to url
    }

    override suspend fun onResponse(method: String, url: String, statusCode: Int, durationMillis: Long) {
        assertTrue(durationMillis >= 0)
        responses += Triple(method, url, statusCode)
    }

    override suspend fun onError(method: String, url: String, error: SupabaseError, durationMillis: Long) {
        assertTrue(durationMillis >= 0)
        errors += Triple(method, url, error)
    }
}

private fun config(
    retry: RetryConfig = RetryConfig.Default,
    interceptor: SupabaseInterceptor? = null,
    logger: SupabaseLogger? = null,
    logging: Boolean = false,
    logLevel: HttpLogLevel = HttpLogLevel.NONE,
) = SupabaseConfig(
    logging = logging,
    logLevel = logLevel,
    headers = emptyMap(),
    retry = retry,
    logger = logger,
    interceptor = interceptor,
)

private fun transport(
    config: SupabaseConfig,
    handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(
        io.ktor.client.request.HttpRequestData,
    ) -> io.ktor.client.request.HttpResponseData,
) = HttpTransport(
    config = config,
    engineFactory = TestMockEngineFactory(handler),
    projectUrl = "https://example.supabase.co",
    apiKey = "anon",
)

class HttpTransportObservabilityTest {
    @Test
    fun test_interceptor_firesRequestAndResponseOnSuccess() =
        runTest {
            val spy = RecordingInterceptor()
            val transport =
                transport(config(interceptor = spy)) {
                    respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }

            transport.get(url = "https://example.supabase.co/rest/v1/messages")

            assertEquals(listOf("GET" to "https://example.supabase.co/rest/v1/messages"), spy.requests)
            assertEquals(1, spy.responses.size)
            assertEquals(200, spy.responses.single().third)
            assertTrue(spy.errors.isEmpty())
        }

    @Test
    fun test_interceptor_firesResponseOnHttpError() =
        runTest {
            val spy = RecordingInterceptor()
            val transport =
                transport(config(interceptor = spy)) {
                    respondError(HttpStatusCode.NotFound)
                }

            transport.get(url = "https://example.supabase.co/rest/v1/missing")

            assertEquals(404, spy.responses.single().third)
            assertTrue(spy.errors.isEmpty(), "HTTP error is a response, not a transport error")
        }

    @Test
    fun test_interceptor_firesErrorOnNetworkFailure() =
        runTest {
            val spy = RecordingInterceptor()
            val transport =
                transport(config(interceptor = spy)) {
                    throw IllegalStateException("connection refused")
                }

            val result = transport.get(url = "https://example.supabase.co/rest/v1/messages")

            assertTrue(result.isFailure)
            assertEquals(1, spy.errors.size)
            assertTrue(spy.responses.isEmpty())
        }

    @Test
    fun test_retry_usesConfiguredRetryableStatusesAndSucceeds() =
        runTest {
            var calls = 0
            val transport =
                transport(config(retry = RetryConfig(maxRetries = 3, baseDelayMillis = 1, retryableStatuses = setOf(503)))) {
                    calls++
                    if (calls < 3) {
                        respondError(HttpStatusCode.ServiceUnavailable)
                    } else {
                        respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }

            val result = transport.get(url = "https://example.supabase.co/rest/v1/x", headers = mapOf(RETRY_HEADER to "true"))

            assertTrue(result.isSuccess)
            assertEquals(3, calls, "expected 2 retries before success")
        }

    @Test
    fun test_retry_doesNotRetryStatusOutsideConfiguredSet() =
        runTest {
            var calls = 0
            val transport =
                transport(config(retry = RetryConfig(maxRetries = 3, baseDelayMillis = 1, retryableStatuses = setOf(503)))) {
                    calls++
                    respondError(HttpStatusCode.InternalServerError)
                }

            val result =
                transport.get(
                    url = "https://example.supabase.co/rest/v1/x",
                    headers = mapOf(RETRY_HEADER to "true"),
                )

            assertTrue(result.isFailure)
            assertEquals(1, calls, "500 is not in the configured retryable set, so no retry")
            assertEquals(500, result.errorOrNull()?.httpStatus)
        }

    @Test
    fun test_logger_receivesWireLogs() =
        runTest {
            val lines = mutableListOf<String>()
            val sink =
                object : SupabaseLogger {
                    override fun log(level: SupabaseLogLevel, message: String, throwable: Throwable?) {
                        lines += message
                    }
                }
            val transport =
                transport(config(logger = sink, logging = true, logLevel = HttpLogLevel.ALL)) {
                    respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }

            transport.get(url = "https://example.supabase.co/rest/v1/messages")

            assertTrue(lines.isNotEmpty(), "expected wire logs routed to the custom sink")
        }
}
