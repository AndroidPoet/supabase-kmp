package io.github.androidpoet.supabase.client

import io.ktor.client.plugins.logging.LogLevel

/**
 * DSL builder for [SupabaseConfig].
 *
 * ```kotlin
 * Supabase.create(url, key) {
 *     logging = true
 *     logLevel = LogLevel.HEADERS
 *     headers["X-Custom"] = "value"
 * }
 * ```
 */
@DslMarker
public annotation class SupabaseDsl

@SupabaseDsl
public class SupabaseConfigBuilder {
    /** Enable Ktor HTTP logging. Defaults to `false`. */
    public var logging: Boolean = false

    /** Ktor log level. Only effective when [logging] is `true`. */
    public var logLevel: LogLevel = LogLevel.NONE

    /** Additional headers sent with every request. */
    public val headers: MutableMap<String, String> = mutableMapOf()

    internal fun build(): SupabaseConfig = SupabaseConfig(
        logging = logging,
        logLevel = logLevel,
        headers = headers.toMap(),
    )
}

/**
 * Immutable configuration snapshot for the Supabase HTTP client.
 */
public data class SupabaseConfig(
    val logging: Boolean,
    val logLevel: LogLevel,
    val headers: Map<String, String>,
)
