package io.github.androidpoet.supabase.auth

import io.github.androidpoet.supabase.client.SupabaseClient

public fun createAuthClient(supabaseClient: SupabaseClient): AuthClient =
    AuthClientImpl(client = supabaseClient)
