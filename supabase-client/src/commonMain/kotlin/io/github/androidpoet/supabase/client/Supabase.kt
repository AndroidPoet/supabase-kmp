package io.github.androidpoet.supabase.client
import io.github.androidpoet.supabase.client.transport.HttpTransport
import io.github.androidpoet.supabase.client.transport.platformEngine
import io.ktor.client.engine.HttpClientEngineFactory

public object Supabase {
    public fun create(
        projectUrl: String,
        apiKey: String,
        engineFactory: HttpClientEngineFactory<*> = platformEngine(),
        configure: SupabaseConfigBuilder.() -> Unit = {},
    ): SupabaseClient {
        require(projectUrl.isNotBlank()) { "projectUrl must not be blank" }
        require(projectUrl.startsWith("http://") || projectUrl.startsWith("https://")) {
            "projectUrl must start with http:// or https:// (got: $projectUrl)"
        }
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }
        val config = SupabaseConfigBuilder().apply(configure).build()
        val transport =
            HttpTransport(
                config = config,
                engineFactory = engineFactory,
                projectUrl = projectUrl,
                apiKey = apiKey,
            )
        return SupabaseClientImpl(
            projectUrl = projectUrl,
            apiKey = apiKey,
            transport = transport,
        )
    }
}
