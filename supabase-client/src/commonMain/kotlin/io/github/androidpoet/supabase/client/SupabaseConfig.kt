package io.github.androidpoet.supabase.client
import io.ktor.client.HttpClientConfig

/** [DslMarker] scoping the [SupabaseConfigBuilder] receiver so nested DSLs can't leak into it. */
@DslMarker
public annotation class SupabaseDsl

/**
 * How verbose the HTTP wire log is when [SupabaseConfigBuilder.logging] is on — an SDK-owned
 * enum so the transport's logging library isn't part of the public surface.
 *
 * [NONE] logs nothing; [INFO] the request line and status; [HEADERS] adds request/response
 * headers; [BODY] adds bodies; [ALL] is everything (the most verbose).
 */
public enum class HttpLogLevel {
    NONE,
    INFO,
    HEADERS,
    BODY,
    ALL,
}

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
    public var logLevel: HttpLogLevel = HttpLogLevel.NONE

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
 *
 * Not a `data class`: it carries function-typed fields ([httpClientConfig],
 * [accessTokenProvider]) whose reference-based `equals`/`hashCode` would make a generated
 * `copy`/`equals` meaningless, and per Kotlin/AndroidX guidance a stable public config should
 * not expose `copy`/`componentN` (adding a field later would break their ABI).
 */
public class SupabaseConfig(
    /** Whether HTTP wire logging is enabled. See [SupabaseConfigBuilder.logging]. */
    public val logging: Boolean,
    /** Verbosity of the wire log. See [SupabaseConfigBuilder.logLevel]. */
    public val logLevel: HttpLogLevel,
    /** Extra headers attached to every request. See [SupabaseConfigBuilder.headers]. */
    public val headers: Map<String, String>,
    /** Retry policy for transient failures. See [RetryConfig]. */
    public val retry: RetryConfig = RetryConfig.Default,
    /** Sink for HTTP wire logs, or `null` for the default. See [SupabaseConfigBuilder.logger]. */
    public val logger: SupabaseLogger? = null,
    /** Observation hooks fired around every request. See [SupabaseInterceptor]. */
    public val interceptor: SupabaseInterceptor? = null,
    /** Connection-establishment timeout in ms, or `null` for unbounded. See [SupabaseConfigBuilder.connectTimeoutMillis]. */
    public val connectTimeoutMillis: Long? = null,
    /** Max inactivity between packets in ms, or `null` for unbounded. See [SupabaseConfigBuilder.socketTimeoutMillis]. */
    public val socketTimeoutMillis: Long? = null,
    /** Whole-request timeout in ms, or `null` for unbounded. See [SupabaseConfigBuilder.requestTimeoutMillis]. */
    public val requestTimeoutMillis: Long? = null,
    /** Raw Ktor escape hatch applied after the library's own installs. See [SupabaseConfigBuilder.httpClientConfig]. */
    public val httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
    /** Per-request Bearer token provider for third-party auth. See [SupabaseConfigBuilder.accessTokenProvider]. */
    public val accessTokenProvider: (suspend () -> String?)? = null,
)
