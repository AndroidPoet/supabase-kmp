package io.github.androidpoet.supabase.client

import io.github.androidpoet.supabase.client.transport.HttpTransport
import io.github.androidpoet.supabase.client.transport.platformEngine

/**
 * Entry point for creating a [SupabaseClient].
 *
 * ```kotlin
 * val client = Supabase.create(
 *     projectUrl = "https://xyzcompany.supabase.co",
 *     apiKey = "eyJ...",
 * ) {
 *     logging = true
 *     logLevel = LogLevel.BODY
 * }
 * ```
 */
public object Supabase {

    /**
     * Creates a new [SupabaseClient] for the given project.
     *
     * @param projectUrl Base URL of the Supabase project (e.g. `https://xyz.supabase.co`).
     * @param apiKey     The project's anon or service-role API key.
     * @param configure  Optional DSL block to customize logging, headers, etc.
     */
    public fun create(
        projectUrl: String,
        apiKey: String,
        configure: SupabaseConfigBuilder.() -> Unit = {},
    ): SupabaseClient {
        val config = SupabaseConfigBuilder().apply(configure).build()
        val transport = HttpTransport(
            config = config,
            engineFactory = platformEngine(),
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
