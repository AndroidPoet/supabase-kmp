package io.github.androidpoet.supabase.realtime

import io.github.androidpoet.supabase.client.SupabaseClient
import io.ktor.client.engine.HttpClientEngineFactory

/**
 * Creates a [RealtimeClient]. By default it uses the platform's WebSocket-capable Ktor engine
 * (the same selection [io.github.androidpoet.supabase.client] uses); pass [engineFactory] to
 * supply your own — e.g. a mock engine in tests.
 */
public fun createRealtimeClient(
    supabaseClient: SupabaseClient,
    config: RealtimeConfig = RealtimeConfig(),
    engineFactory: HttpClientEngineFactory<*> = platformEngine(),
): RealtimeClient =
    RealtimeClientImpl(supabaseClient = supabaseClient, config = config, engineFactory = engineFactory)
