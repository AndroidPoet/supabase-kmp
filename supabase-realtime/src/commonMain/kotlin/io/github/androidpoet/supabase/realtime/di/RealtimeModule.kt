package io.github.androidpoet.supabase.realtime.di

import io.github.androidpoet.supabase.realtime.RealtimeClient
import io.github.androidpoet.supabase.realtime.RealtimeClientImpl
import io.github.androidpoet.supabase.realtime.RealtimeConfig
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Creates a Koin module that provides [RealtimeClient] backed by [RealtimeClientImpl]
 * with the given [config].
 *
 * Requires a [SupabaseClient] to already be available in the Koin graph
 * (provided by the `:supabase-client` module).
 *
 * ```kotlin
 * startKoin {
 *     modules(supabaseModule(url, key, config), realtimeModule(RealtimeConfig(autoReconnect = false)))
 * }
 * ```
 */
public fun realtimeModule(config: RealtimeConfig = RealtimeConfig()): Module = module {
    single<RealtimeClient> { RealtimeClientImpl(get(), config) }
}

/**
 * Koin module that provides [RealtimeClient] with default [RealtimeConfig].
 *
 * Kept for backward compatibility. Prefer [realtimeModule] function for
 * custom configuration.
 *
 * ```kotlin
 * startKoin {
 *     modules(supabaseModule(url, key, config), realtimeModule)
 * }
 * ```
 */
public val realtimeModule: Module = realtimeModule()
