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
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private const val CLIENT_VERSION = "supabase-kmp/0.1.0"

/**
 * Low-level HTTP transport layer for Supabase API communication.
 *
 * Wraps a Ktor [HttpClient] configured with content negotiation, optional
 * logging, and the project's default authentication headers. Higher-level
 * modules (database, auth, storage) delegate all network I/O here.
 */
internal class HttpTransport(
    private val config: SupabaseConfig,
    engineFactory: HttpClientEngineFactory<*>,
    private val projectUrl: String,
    private val apiKey: String,
) {
    /** Mutable access token — when set, overrides the anon key in Authorization. */
    private var accessToken: String? = null

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
            header("Authorization", "Bearer ${accessToken ?: apiKey}")
            header("X-Client-Info", CLIENT_VERSION)
            config.headers.forEach { (key, value) -> header(key, value) }
        }
    }

    // ── HTTP verbs ──────────────────────────────────────────────────────

    suspend fun get(
        url: String,
        queryParams: List<Pair<String, String>> = emptyList(),
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String> = execute {
        httpClient.get(url) {
            queryParams.forEach { (k, v) -> this.url.parameters.append(k, v) }
            headers.forEach { (k, v) -> header(k, v) }
            // Re-apply Authorization in case accessToken changed after client init
            header("Authorization", "Bearer ${accessToken ?: apiKey}")
        }
    }

    suspend fun post(
        url: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String> = execute {
        httpClient.post(url) {
            contentType(ContentType.Application.Json)
            body?.let { setBody(it) }
            headers.forEach { (k, v) -> header(k, v) }
            header("Authorization", "Bearer ${accessToken ?: apiKey}")
        }
    }

    suspend fun patch(
        url: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String> = execute {
        httpClient.patch(url) {
            contentType(ContentType.Application.Json)
            body?.let { setBody(it) }
            headers.forEach { (k, v) -> header(k, v) }
            header("Authorization", "Bearer ${accessToken ?: apiKey}")
        }
    }

    suspend fun delete(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String> = execute {
        httpClient.delete(url) {
            headers.forEach { (k, v) -> header(k, v) }
            header("Authorization", "Bearer ${accessToken ?: apiKey}")
        }
    }

    suspend fun postRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String> = execute {
        httpClient.post(url) {
            contentType(ContentType.parse(contentType))
            setBody(body)
            headers.forEach { (k, v) -> header(k, v) }
            header("Authorization", "Bearer ${accessToken ?: apiKey}")
        }
    }

    // ── Token management ────────────────────────────────────────────────

    fun setAccessToken(token: String) {
        accessToken = token
    }

    fun clearAccessToken() {
        accessToken = null
    }

    // ── Lifecycle ───────────────────────────────────────────────────────

    fun close() {
        httpClient.close()
    }

    // ── Internals ───────────────────────────────────────────────────────

    private suspend inline fun execute(
        crossinline request: suspend () -> io.ktor.client.statement.HttpResponse,
    ): SupabaseResult<String> =
        try {
            val response = request()
            val text = response.bodyAsText()
            if (response.status.isSuccess()) {
                SupabaseResult.Success(text)
            } else {
                val error = parseError(text, response.status.value)
                SupabaseResult.Failure(error)
            }
        } catch (e: Exception) {
            SupabaseResult.Failure(
                SupabaseError(message = e.message ?: "Unknown network error"),
            )
        }

    private fun parseError(body: String, statusCode: Int): SupabaseError =
        try {
            errorJson.decodeFromString<SupabaseError>(body)
        } catch (_: Exception) {
            SupabaseError(
                message = body.ifBlank { "HTTP $statusCode" },
                code = statusCode.toString(),
            )
        }
}
