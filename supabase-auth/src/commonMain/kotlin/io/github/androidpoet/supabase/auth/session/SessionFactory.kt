package io.github.androidpoet.supabase.auth.session

import io.github.androidpoet.supabase.auth.AuthClient
import io.github.androidpoet.supabase.client.SupabaseClient

public fun createSessionManager(
    authClient: AuthClient,
    supabaseClient: SupabaseClient,
    config: SessionConfig = SessionConfig(),
): SessionManager =
    SessionManagerImpl(
        authClient = authClient,
        supabaseClient = supabaseClient,
        storage = config.storage,
        autoRefresh = config.autoRefresh,
        refreshBufferSeconds = config.refreshBufferSeconds,
    )
