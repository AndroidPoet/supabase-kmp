package io.github.androidpoet.supabase.client

/** Severity for [SupabaseLogger] messages. */
public enum class SupabaseLogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * Pluggable sink for the SDK's diagnostic output.
 *
 * Provide one via `SupabaseConfigBuilder.logger` to route HTTP wire logs into
 * your app's logging framework (Timber, OSLog, SLF4J, …) instead of relying on
 * Ktor's default `println`-style logger. Verbosity is still governed by
 * `logLevel`; this only changes where the lines go.
 */
public interface SupabaseLogger {
    public fun log(
        level: SupabaseLogLevel,
        message: String,
        throwable: Throwable? = null,
    )
}
