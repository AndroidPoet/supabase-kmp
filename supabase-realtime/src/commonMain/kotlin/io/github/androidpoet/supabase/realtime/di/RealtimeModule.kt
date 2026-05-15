package io.github.androidpoet.supabase.realtime.di
import io.github.androidpoet.supabase.realtime.RealtimeClient
import io.github.androidpoet.supabase.realtime.RealtimeClientImpl
import io.github.androidpoet.supabase.realtime.RealtimeConfig
import org.koin.core.module.Module
import org.koin.dsl.module
public fun realtimeModule(config: RealtimeConfig = RealtimeConfig()): Module = module {
    single<RealtimeClient> { RealtimeClientImpl(get(), config) }
}
public val realtimeModule: Module = realtimeModule()
