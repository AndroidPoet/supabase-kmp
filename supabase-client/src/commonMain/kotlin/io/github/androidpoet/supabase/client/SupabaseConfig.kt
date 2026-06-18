package io.github.androidpoet.supabase.client
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.logging.LogLevel

@DslMarker
public annotation class SupabaseDsl

@SupabaseDsl
public class SupabaseConfigBuilder {
    public var logging: Boolean = false
    public var logLevel: LogLevel = LogLevel.NONE
    public val headers: MutableMap<String, String> = mutableMapOf()

    /** Retry policy for transient failures. See [RetryConfig]. */
    public var retry: RetryConfig = RetryConfig.Default

    /** Optional sink for HTTP wire logs (routes Ktor logging into your framework). */
    public var logger: SupabaseLogger? = null

    /** Optional observation hooks fired around every request. */
    public var interceptor: SupabaseInterceptor? = null

    /**
     * Maximum time to establish a connection, in milliseconds. `null` (default)
     * leaves it unbounded. Safe to set without affecting streaming responses.
     */
    public var connectTimeoutMillis: Long? = null

    /**
     * Maximum inactivity between data packets, in milliseconds. `null` (default)
     * leaves it unbounded. Avoid combining with long-idle streams (SSE).
     */
    public var socketTimeoutMillis: Long? = null

    /**
     * Maximum total time for a whole request including the response body, in
     * milliseconds. `null` (default) leaves it unbounded. Do NOT set this when
     * streaming (`streamLines`/`invokeSSE`) or transferring large
     * uploads/downloads — it caps the entire transfer, not just connection setup.
     */
    public var requestTimeoutMillis: Long? = null

    /**
     * Raw Ktor escape hatch applied to the underlying [HttpClientConfig] AFTER the
     * library's own installs (content negotiation, optional timeouts/logging, the
     * default `apikey`/`X-Client-Info` headers). Use it to install arbitrary Ktor
     * plugins or tweak engine behaviour the library does not expose — e.g.
     * `HttpCache`, a proxy, custom TLS/cookies, or `HttpRequestRetry`.
     *
     * This is NOT a library plugin-registration DSL: it is the unfiltered Ktor
     * builder, so anything you do here can override or conflict with the library's
     * own configuration. Prefer the typed config fields where they exist.
     */
    public var httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null

    /**
     * Supplies the `Authorization: Bearer` token per request, resolved fresh on
     * every call. Intended for third-party-auth setups (e.g. Firebase/Auth0) where
     * a JWT comes from an external identity provider rather than the library's
     * session manager.
     *
     * When set and it returns a non-null value, that token wins over both the
     * session token and the API key. Returning `null` falls back to the normal
     * rules (session token, then a legacy JWT API key). A caller-supplied
     * `Authorization` header on an individual request still takes precedence and is
     * never overwritten.
     */
    public var accessTokenProvider: (suspend () -> String?)? = null

    internal fun build(): SupabaseConfig =
        SupabaseConfig(
            logging = logging,
            logLevel = logLevel,
            headers = headers.toMap(),
            retry = retry,
            logger = logger,
            interceptor = interceptor,
            connectTimeoutMillis = connectTimeoutMillis,
            socketTimeoutMillis = socketTimeoutMillis,
            requestTimeoutMillis = requestTimeoutMillis,
            httpClientConfig = httpClientConfig,
            accessTokenProvider = accessTokenProvider,
        )
}

public data class SupabaseConfig(
    val logging: Boolean,
    val logLevel: LogLevel,
    val headers: Map<String, String>,
    val retry: RetryConfig = RetryConfig.Default,
    val logger: SupabaseLogger? = null,
    val interceptor: SupabaseInterceptor? = null,
    val connectTimeoutMillis: Long? = null,
    val socketTimeoutMillis: Long? = null,
    val requestTimeoutMillis: Long? = null,
    /** Raw Ktor escape hatch applied after the library's own installs. See [SupabaseConfigBuilder.httpClientConfig]. */
    val httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
    /** Per-request Bearer token provider for third-party auth. See [SupabaseConfigBuilder.accessTokenProvider]. */
    val accessTokenProvider: (suspend () -> String?)? = null,
)
