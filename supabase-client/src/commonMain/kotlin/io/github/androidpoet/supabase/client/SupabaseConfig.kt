package io.github.androidpoet.supabase.client
import io.ktor.client.plugins.logging.LogLevel
@DslMarker
public annotation class SupabaseDsl
@SupabaseDsl
public class SupabaseConfigBuilder {
    public var logging: Boolean = false
    public var logLevel: LogLevel = LogLevel.NONE
    public val headers: MutableMap<String, String> = mutableMapOf()
    internal fun build(): SupabaseConfig = SupabaseConfig(
        logging = logging,
        logLevel = logLevel,
        headers = headers.toMap(),
    )
}
public data class SupabaseConfig(
    val logging: Boolean,
    val logLevel: LogLevel,
    val headers: Map<String, String>,
)
