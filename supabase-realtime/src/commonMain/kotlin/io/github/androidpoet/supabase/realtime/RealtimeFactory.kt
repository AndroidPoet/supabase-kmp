package io.github.androidpoet.supabase.realtime

import io.github.androidpoet.supabase.client.SupabaseClient

public fun createRealtimeClient(
    supabaseClient: SupabaseClient,
    config: RealtimeConfig = RealtimeConfig(),
): RealtimeClient =
    RealtimeClientImpl(supabaseClient = supabaseClient, config = config)
