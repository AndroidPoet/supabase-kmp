package io.github.androidpoet.supabase.client
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.logging.LogLevel

/** [DslMarker] scoping the [SupabaseConfigBuilder] receiver so nested DSLs can't leak into it. */
@DslMarker
public annotation class SupabaseDsl

/**
 * Mutable builder for [SupabaseConfig], populated inside the client-creation DSL.
 *
 * Every property has a sensible default, so an empty block yields a working
 * client; set only what you need. The typed fields (timeouts, [retry], [logger],
 * [interceptor], [accessTokenProvider]) cover the common cases — reach for
 * [httpClientConfig] only for raw transport tweaks the library doesn't expose.
 */
@SupabaseDsl
public class SupabaseConfigBuilder {
    /** Enable HTTP wire logging at [logLevel]. Off by default. */
    public var logging: Boolean = false

    /** Verbosity of the wire log when [logging] is on. */
    public var logLevel: LogLevel = LogLevel.NONE

    /** Extra headers attached to every request, merged under any per-call headers. */
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

/**
 * Resolved, immutable configuration for a [SupabaseClient], produced by
 * [SupabaseConfigBuilder.build].
 *
 * Mirrors the builder field-for-field; see [SupabaseConfigBuilder] for the
 * meaning and defaults of each option. Construct it via the DSL rather than
 * directly so the documented defaults and validation apply.
 */
public data class SupabaseConfig(
    /** Whether HTTP wire logging is enabled. See [SupabaseConfigBuilder.logging]. */
    val logging: Boolean,
    /** Verbosity of the wire log. See [SupabaseConfigBuilder.logLevel]. */
    val logLevel: LogLevel,
    /** Extra headers attached to every request. See [SupabaseConfigBuilder.headers]. */
    val headers: Map<String, String>,
    /** Retry policy for transient failures. See [RetryConfig]. */
    val retry: RetryConfig = RetryConfig.Default,
    /** Sink for HTTP wire logs, or `null` for the default. See [SupabaseConfigBuilder.logger]. */
    val logger: SupabaseLogger? = null,
    /** Observation hooks fired around every request. See [SupabaseInterceptor]. */
    val interceptor: SupabaseInterceptor? = null,
    /** Connection-establishment timeout in ms, or `null` for unbounded. See [SupabaseConfigBuilder.connectTimeoutMillis]. */
    val connectTimeoutMillis: Long? = null,
    /** Max inactivity between packets in ms, or `null` for unbounded. See [SupabaseConfigBuilder.socketTimeoutMillis]. */
    val socketTimeoutMillis: Long? = null,
    /** Whole-request timeout in ms, or `null` for unbounded. See [SupabaseConfigBuilder.requestTimeoutMillis]. */
    val requestTimeoutMillis: Long? = null,
    /** Raw Ktor escape hatch applied after the library's own installs. See [SupabaseConfigBuilder.httpClientConfig]. */
    val httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
    /** Per-request Bearer token provider for third-party auth. See [SupabaseConfigBuilder.accessTokenProvider]. */
    val accessTokenProvider: (suspend () -> String?)? = null,
)
