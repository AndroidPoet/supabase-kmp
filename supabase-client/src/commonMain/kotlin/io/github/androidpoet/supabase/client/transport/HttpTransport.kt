package io.github.androidpoet.supabase.client.transport
import io.github.androidpoet.supabase.client.SupabaseConfig
import io.github.androidpoet.supabase.client.SupabaseHttpResponse
import io.github.androidpoet.supabase.client.SupabaseLogLevel
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseErrorCodes
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource
import io.ktor.client.plugins.logging.Logger as KtorLogger

private const val CLIENT_VERSION = "supabase-kmp/0.1.0"
private const val INTERNAL_RETRY_HEADER = "X-Supabase-Kmp-Retry"
private const val RETRY_COUNT_HEADER = "X-Retry-Count"

internal class HttpTransport(
    private val config: SupabaseConfig,
    engineFactory: HttpClientEngineFactory<*>,
    private val projectUrl: String,
    private val apiKey: String,
) {
    // Written by the session layer on a different coroutine/thread than the
    // request builders that read it; @Volatile guarantees cross-thread visibility
    // (a single reference write/read is already atomic on every target).
    @Volatile
    private var accessToken: String? = null
    internal val accessTokenOrNull: String? get() = accessToken
    private val errorJson = Json { ignoreUnknownKeys = true }
    private val retry = config.retry
    internal val httpClient: HttpClient =
        HttpClient(engineFactory) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        explicitNulls = false
                    },
                )
            }
            if (config.logging) {
                install(Logging) {
                    level = config.logLevel
                    // Route wire logs into the caller's framework when a logger is supplied;
                    // otherwise fall back to Ktor's default sink.
                    config.logger?.let { sink ->
                        logger =
                            object : KtorLogger {
                                override fun log(message: String) {
                                    sink.log(SupabaseLogLevel.DEBUG, message)
                                }
                            }
                    }
                    // Never let credentials reach the log sink. This covers the anon/service
                    // apikey header and every Bearer token (including the auth-admin
                    // service-role key, which flows through this same client).
                    sanitizeHeader { name ->
                        name.equals("Authorization", ignoreCase = true) ||
                            name.equals("apikey", ignoreCase = true)
                    }
                }
            }
            defaultRequest {
                header("apikey", apiKey)
                header("X-Client-Info", CLIENT_VERSION)
                config.headers.forEach { (key, value) -> header(key, value) }
            }
        }

    suspend fun get(
        url: String,
        queryParams: List<Pair<String, String>> = emptyList(),
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String> {
        val retryEnabled = headers[INTERNAL_RETRY_HEADER]?.toBooleanStrictOrNull() ?: false
        val outgoingHeaders = headers - INTERNAL_RETRY_HEADER
        return execute(method = "GET", url = url, retryEnabled = retryEnabled, retryableMethod = true) { attempt ->
            httpClient.get(url) {
                queryParams.forEach { (k, v) -> this.url.parameters.append(k, v) }
                outgoingHeaders.forEach { (k, v) -> header(k, v) }
                if (attempt > 0) header(RETRY_COUNT_HEADER, attempt.toString())
                if ("Authorization" !in outgoingHeaders) {
                    header("Authorization", "Bearer ${accessToken ?: apiKey}")
                }
            }
        }
    }

    suspend fun post(
        url: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String> {
        val outgoingHeaders = headers - INTERNAL_RETRY_HEADER
        return execute(method = "POST", url = url) {
            httpClient.post(url) {
                contentType(ContentType.Application.Json)
                body?.let { setBody(it) }
                outgoingHeaders.forEach { (k, v) -> header(k, v) }
                if ("Authorization" !in outgoingHeaders) {
                    header("Authorization", "Bearer ${accessToken ?: apiKey}")
                }
            }
        }
    }

    suspend fun put(
        url: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String> {
        val outgoingHeaders = headers - INTERNAL_RETRY_HEADER
        return execute(method = "PUT", url = url) {
            httpClient.put(url) {
                contentType(ContentType.Application.Json)
                body?.let { setBody(it) }
                outgoingHeaders.forEach { (k, v) -> header(k, v) }
                if ("Authorization" !in outgoingHeaders) {
                    header("Authorization", "Bearer ${accessToken ?: apiKey}")
                }
            }
        }
    }

    suspend fun patch(
        url: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String> {
        val outgoingHeaders = headers - INTERNAL_RETRY_HEADER
        return execute(method = "PATCH", url = url) {
            httpClient.patch(url) {
                contentType(ContentType.Application.Json)
                body?.let { setBody(it) }
                outgoingHeaders.forEach { (k, v) -> header(k, v) }
                if ("Authorization" !in outgoingHeaders) {
                    header("Authorization", "Bearer ${accessToken ?: apiKey}")
                }
            }
        }
    }

    suspend fun delete(
        url: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String> {
        val outgoingHeaders = headers - INTERNAL_RETRY_HEADER
        return execute(method = "DELETE", url = url) {
            httpClient.delete(url) {
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
                outgoingHeaders.forEach { (k, v) -> header(k, v) }
                if ("Authorization" !in outgoingHeaders) {
                    header("Authorization", "Bearer ${accessToken ?: apiKey}")
                }
            }
        }
    }

    suspend fun postRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String> {
        val outgoingHeaders = headers - INTERNAL_RETRY_HEADER
        return execute(method = "POST", url = url) {
            httpClient.post(url) {
                contentType(ContentType.parse(contentType))
                setBody(body)
                outgoingHeaders.forEach { (k, v) -> header(k, v) }
                if ("Authorization" !in outgoingHeaders) {
                    header("Authorization", "Bearer ${accessToken ?: apiKey}")
                }
            }
        }
    }

    suspend fun putRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String> {
        val outgoingHeaders = headers - INTERNAL_RETRY_HEADER
        return execute(method = "PUT", url = url) {
            httpClient.put(url) {
                contentType(ContentType.parse(contentType))
                setBody(body)
                outgoingHeaders.forEach { (k, v) -> header(k, v) }
                if ("Authorization" !in outgoingHeaders) {
                    header("Authorization", "Bearer ${accessToken ?: apiKey}")
                }
            }
        }
    }

    suspend fun rawRequest(
        method: String,
        url: String,
        body: ByteArray?,
        contentType: String?,
        requestHeaders: Map<String, String>,
    ): SupabaseResult<SupabaseHttpResponse> {
        val mark = TimeSource.Monotonic.markNow()
        config.interceptor?.onRequest(method, url)
        return try {
            val response =
                httpClient.request(url) {
                    this.method = HttpMethod.parse(method)
                    requestHeaders.forEach { (k, v) -> header(k, v) }
                    if ("Authorization" !in requestHeaders) {
                        header("Authorization", "Bearer ${accessToken ?: apiKey}")
                    }
                    contentType?.let { contentType(ContentType.parse(it)) }
                    body?.let { setBody(it) }
                }
            val bytes = response.readRawBytes()
            config.interceptor?.onResponse(method, url, response.status.value, mark.elapsedNow().inWholeMilliseconds)
            if (response.status.isSuccess()) {
                val flatHeaders = response.headers.entries().associate { (k, v) -> k to v.joinToString(",") }
                SupabaseResult.Success(SupabaseHttpResponse(response.status.value, flatHeaders, bytes))
            } else {
                val retryAfter = response.headers["Retry-After"]?.toLongOrNull()
                SupabaseResult.Failure(parseError(bytes.decodeToString(), response.status.value, retryAfter))
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            val error = networkError(throwable = e)
            config.interceptor?.onError(method, url, error, mark.elapsedNow().inWholeMilliseconds)
            SupabaseResult.Failure(error)
        }
    }

    fun setAccessToken(token: String) {
        accessToken = token
    }

    fun clearAccessToken() {
        accessToken = null
    }

    fun close() {
        httpClient.close()
    }

    private suspend inline fun execute(
        method: String,
        url: String,
        retryEnabled: Boolean = false,
        retryableMethod: Boolean = false,
        crossinline request: suspend (attempt: Int) -> io.ktor.client.statement.HttpResponse,
    ): SupabaseResult<String> {
        val mark = TimeSource.Monotonic.markNow()
        config.interceptor?.onRequest(method, url)
        return try {
            var attempt = 0
            while (attempt <= retry.maxRetries) {
                val response =
                    try {
                        request(attempt)
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        if (retryEnabled && retryableMethod && attempt < retry.maxRetries) {
                            delay(retry.backoffMillis(attempt))
                            attempt++
                            continue
                        }
                        throw e
                    }
                val text = response.bodyAsText()
                val statusCode = response.status.value
                if (response.status.isSuccess()) {
                    config.interceptor?.onResponse(method, url, statusCode, mark.elapsedNow().inWholeMilliseconds)
                    return SupabaseResult.Success(text)
                }
                if (retryEnabled &&
                    retryableMethod &&
                    statusCode in retry.retryableStatuses &&
                    attempt < retry.maxRetries
                ) {
                    delay(response.retryDelayMillis(attempt))
                    attempt++
                    continue
                }
                val retryAfter = response.headers["Retry-After"]?.toLongOrNull()
                val error = parseError(text, statusCode, retryAfter)
                config.interceptor?.onResponse(method, url, statusCode, mark.elapsedNow().inWholeMilliseconds)
                return SupabaseResult.Failure(error)
            }
            val exhausted = networkError(message = "Retry attempts exhausted")
            config.interceptor?.onError(method, url, exhausted, mark.elapsedNow().inWholeMilliseconds)
            SupabaseResult.Failure(exhausted)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            val error = networkError(throwable = e)
            config.interceptor?.onError(method, url, error, mark.elapsedNow().inWholeMilliseconds)
            SupabaseResult.Failure(error)
        }
    }

    // A request that throws (rather than returning a response) never reached a
    // usable server response: offline, DNS/TLS failure, connection refused, or a
    // timeout. Tag it with a synthetic [SupabaseErrorCodes.Client] code so
    // callers see [SupabaseErrorCategory.Network] instead of Unknown.
    private fun networkError(
        throwable: Throwable? = null,
        message: String? = null,
    ): SupabaseError {
        val name = throwable?.let { it::class.simpleName.orEmpty() } ?: ""
        val code =
            when {
                name.contains("Timeout", ignoreCase = true) -> SupabaseErrorCodes.Client.TIMEOUT
                name.contains("Connect", ignoreCase = true) -> SupabaseErrorCodes.Client.CONNECTION_FAILED
                else -> SupabaseErrorCodes.Client.NETWORK_ERROR
            }
        return SupabaseError(
            message = message ?: throwable?.message ?: "Network request failed",
            code = code,
        )
    }

    // A Retry-After header (seconds) wins over the configured exponential backoff.
    private fun io.ktor.client.statement.HttpResponse.retryDelayMillis(attempt: Int): Long {
        val retryAfterSeconds = headers["Retry-After"]?.toLongOrNull()
        return if (retryAfterSeconds != null) {
            retryAfterSeconds.coerceAtLeast(0) * 1_000
        } else {
            retry.backoffMillis(attempt)
        }
    }

    // Supabase services return heterogeneous error bodies: PostgREST uses
    // {code,message,details,hint}; GoTrue uses {error_code,msg} or
    // {error,error_description}; Storage uses {statusCode,error,message}. Parse
    // tolerantly across all of them and ALWAYS record the HTTP status so
    // categorization works even when the body carries no machine-readable code.
    private fun parseError(body: String, statusCode: Int, retryAfterSeconds: Long?): SupabaseError {
        val obj =
            try {
                (errorJson.parseToJsonElement(body) as? JsonObject)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }

        fun str(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { key -> (obj?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } }

        val message =
            str("message", "msg", "error_description", "error_message", "error")
                ?: body.ifBlank { "HTTP $statusCode" }
        val code = str("code", "error_code", "statusCode")

        return SupabaseError(
            message = message,
            code = code,
            details = obj?.get("details"),
            hint = str("hint"),
            httpStatus = statusCode,
            retryAfterSeconds = retryAfterSeconds,
        )
    }
}
