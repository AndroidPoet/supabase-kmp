package io.github.androidpoet.supabase.client.di

import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.client.SupabaseClientImpl
import io.github.androidpoet.supabase.client.SupabaseConfig
import io.github.androidpoet.supabase.client.transport.HttpTransport
import io.github.androidpoet.supabase.client.transport.platformEngine
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Creates a Koin [Module] that provides [HttpTransport] and [SupabaseClient]
 * as singletons.
 *
 * ```kotlin
 * startKoin {
 *     modules(supabaseModule(projectUrl, apiKey, config))
 * }
 * ```
 */
public fun supabaseModule(
    projectUrl: String,
    apiKey: String,
    config: SupabaseConfig,
): Module = module {
    single {
        HttpTransport(
            config = config,
            engineFactory = platformEngine(),
            projectUrl = projectUrl,
            apiKey = apiKey,
        )
    }
    single<SupabaseClient> {
        SupabaseClientImpl(
            projectUrl = projectUrl,
            apiKey = apiKey,
            transport = get(),
        )
    }
}
