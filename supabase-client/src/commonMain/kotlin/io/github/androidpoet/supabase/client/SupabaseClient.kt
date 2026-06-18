package io.github.androidpoet.supabase.client
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.flow.Flow

/** HTTP method for [SupabaseClient.rawRequest]. */
public enum class SupabaseHttpMethod {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
    HEAD,
}

/**
 * A low-level HTTP response exposing the status code, response headers and raw
 * body bytes — used by features that need to read response headers (e.g. the
 * resumable-upload `Location` / `Upload-Offset`) rather than just the body.
 */
public class SupabaseHttpResponse(
    public val status: Int,
    public val headers: Map<String, String>,
    public val body: ByteArray,
) {
    /** Case-insensitive header lookup. */
    public fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

public interface SupabaseClient : AutoCloseable {
    public val projectUrl: String
    public val apiKey: String
    public val accessTokenOrNull: String?

    public suspend fun get(
        endpoint: String,
        queryParams: List<Pair<String, String>> = emptyList(),
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>

    public suspend fun post(
        endpoint: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>

    public suspend fun put(
        endpoint: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>

    public suspend fun patch(
        endpoint: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>

    public suspend fun delete(
        endpoint: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>

    public suspend fun postRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>

    public suspend fun putRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>

    /**
     * Performs an arbitrary HTTP request and returns the status, headers and raw
     * body bytes. [url] may be an absolute URL (`http(s)://…`) or an endpoint
     * path (prefixed with the project URL). Supports method/header/body needs
     * that the typed helpers above don't expose — notably `HEAD` and reading
     * response headers, as required by resumable (TUS) uploads.
     */
    public suspend fun rawRequest(
        method: SupabaseHttpMethod,
        url: String,
        body: ByteArray? = null,
        contentType: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<SupabaseHttpResponse>

    /**
     * Streams the body of a POST to [endpoint] as a cold [Flow] of decoded UTF-8
     * lines, read incrementally rather than buffered — for Server-Sent Events and
     * other long-lived responses. The request is issued on collection; a non-2xx
     * status surfaces as a terminal exception in the flow. [endpoint] may be a
     * path (prefixed with the project URL) or an absolute URL.
     *
     * Has a default that throws so existing non-streaming [SupabaseClient]
     * implementations (notably test fakes) keep compiling; the real client
     * overrides it.
     */
    public fun streamLines(
        endpoint: String,
        body: String? = null,
        contentType: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): Flow<String> = throw UnsupportedOperationException("streamLines is not supported by this SupabaseClient")

    public fun setAccessToken(token: String)

    public fun clearAccessToken()

    override fun close()
}
