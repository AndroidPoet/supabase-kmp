package io.github.androidpoet.supabase.client.transport
import io.github.androidpoet.supabase.client.SupabaseConfig
import io.github.androidpoet.supabase.core.result.SupabaseError
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
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
private const val CLIENT_VERSION = "supabase-kmp/0.1.0"
private const val INTERNAL_RETRY_HEADER = "X-Supabase-Kmp-Retry"
private const val RETRY_COUNT_HEADER = "X-Retry-Count"
private const val DEFAULT_MAX_RETRIES = 3
internal class HttpTransport(
    private val config: SupabaseConfig,
    engineFactory: HttpClientEngineFactory<*>,
    private val projectUrl: String,
    private val apiKey: String,
) {
    private var accessToken: String? = null
    internal val accessTokenOrNull: String? get() = accessToken
    private val errorJson = Json { ignoreUnknownKeys = true }
    internal val httpClient: HttpClient = HttpClient(engineFactory) {
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
        return execute(retryEnabled = retryEnabled, retryableMethod = true) { attempt ->
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
        return execute {
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
        return execute {
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
        return execute {
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
        return execute {
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
        return execute {
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
        return execute {
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
        retryEnabled: Boolean = false,
        retryableMethod: Boolean = false,
        crossinline request: suspend (attempt: Int) -> io.ktor.client.statement.HttpResponse,
    ): SupabaseResult<String> {
        return try {
            var attempt = 0
            while (attempt <= DEFAULT_MAX_RETRIES) {
                val response = try {
                    request(attempt)
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    if (retryEnabled && retryableMethod && attempt < DEFAULT_MAX_RETRIES) {
                        delay(retryDelayMillis(attempt))
                        attempt++
                        continue
                    }
                    throw e
                }
                val text = response.bodyAsText()
                val statusCode = response.status.value
                if (response.status.isSuccess()) {
                    return SupabaseResult.Success(text)
                }
                if (retryEnabled && retryableMethod && statusCode.isRetryableStatus() && attempt < DEFAULT_MAX_RETRIES) {
                    delay(response.retryDelayMillis(attempt))
                    attempt++
                    continue
                }
                val error = parseError(text, statusCode)
                return SupabaseResult.Failure(error)
            }
            SupabaseResult.Failure(SupabaseError(message = "Retry attempts exhausted"))
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            SupabaseResult.Failure(
                SupabaseError(message = e.message ?: "Unknown network error"),
            )
        }
    }
    private fun io.ktor.client.statement.HttpResponse.retryDelayMillis(attempt: Int): Long {
        val retryAfterSeconds = headers["Retry-After"]?.toLongOrNull()
        return if (retryAfterSeconds != null) retryAfterSeconds.coerceAtLeast(0) * 1_000 else retryDelayMillis(attempt)
    }
    private fun retryDelayMillis(attempt: Int): Long =
        1_000L shl attempt
    private fun Int.isRetryableStatus(): Boolean = this == 503 || this == 520
    private fun parseError(body: String, statusCode: Int): SupabaseError =
        try {
            errorJson.decodeFromString<SupabaseError>(body)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            SupabaseError(
                message = body.ifBlank { "HTTP $statusCode" },
                code = statusCode.toString(),
            )
        }
}
