package io.github.androidpoet.supabase.client
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
)
