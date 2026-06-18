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

/**
 * The SDK's HTTP transport: every feature module (Auth, PostgREST, Storage,
 * Functions, …) talks to Supabase through this one seam rather than touching the
 * network directly, so authentication, base-URL resolution, retries, logging and
 * interceptors are applied uniformly in one place.
 *
 * The typed verbs ([get]/[post]/[put]/[patch]/[delete]) cover the common
 * JSON-over-REST case and return the response body as a [String];
 * [postRaw]/[putRaw] carry binary bodies, [rawRequest] exposes status + headers +
 * bytes for the cases the typed helpers can't express, and [streamLines] reads a
 * long-lived response incrementally. Every call returns a [SupabaseResult] —
 * transport and non-2xx errors come back as [SupabaseResult.Failure] rather than
 * being thrown (the one exception is [streamLines], which surfaces errors as a
 * terminal exception in its [Flow]).
 *
 * Implementations resolve relative `endpoint` paths against [projectUrl], attach
 * the [apiKey] and the current bearer token, and are safe to share across
 * coroutines. Being [AutoCloseable], a client owns its underlying engine and must
 * be [close]d when no longer needed.
 */
public interface SupabaseClient : AutoCloseable {
    /** Base URL of the Supabase project; relative `endpoint` paths are resolved against it. */
    public val projectUrl: String

    /** The project's `apikey`, sent on every request and used as the fallback bearer credential. */
    public val apiKey: String

    /** The bearer token currently applied to requests, or `null` when no session/override token is set. */
    public val accessTokenOrNull: String?

    /**
     * Issues a GET to [endpoint] and returns the response body. [queryParams] are
     * URL-encoded and appended; [headers] are merged over the client's defaults.
     * A non-2xx status comes back as [SupabaseResult.Failure], not an exception.
     */
    public suspend fun get(
        endpoint: String,
        queryParams: List<Pair<String, String>> = emptyList(),
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>

    /**
     * Issues a POST to [endpoint] with an optional JSON [body] and returns the
     * response body. [headers] are merged over the client's defaults. A non-2xx
     * status comes back as [SupabaseResult.Failure], not an exception.
     */
    public suspend fun post(
        endpoint: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>

    /**
     * Issues a PUT to [endpoint] with an optional JSON [body] and returns the
     * response body. Behaves like [post] otherwise.
     */
    public suspend fun put(
        endpoint: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>

    /**
     * Issues a PATCH to [endpoint] with an optional JSON [body] and returns the
     * response body — the verb used for partial updates. Behaves like [post]
     * otherwise.
     */
    public suspend fun patch(
        endpoint: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>

    /**
     * Issues a DELETE to [endpoint] with an optional [body] and returns the
     * response body. Behaves like [post] otherwise.
     */
    public suspend fun delete(
        endpoint: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>

    /**
     * POSTs a binary [body] to an absolute [url] with the given [contentType] —
     * the raw-bytes counterpart to [post], used for uploads (e.g. Storage object
     * writes) where the payload is not JSON.
     */
    public suspend fun postRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>

    /** PUTs a binary [body] to an absolute [url] with the given [contentType]. See [postRaw]. */
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

    /**
     * Sets the bearer [token] applied to subsequent requests (typically the
     * current session's access token), overriding the API-key fallback until
     * [clearAccessToken] is called.
     */
    public fun setAccessToken(token: String)

    /** Clears any token set via [setAccessToken], reverting to the API-key credential. */
    public fun clearAccessToken()

    /** Releases the underlying HTTP engine. After closing, the client must not be used again. */
    override fun close()
}
