package io.github.androidpoet.supabase.auth.admin

import io.github.androidpoet.supabase.client.SupabaseClient

public fun SupabaseClient.authAdmin(serviceRoleKey: String = apiKey): AuthAdminClient =
    AuthAdminClientImpl(client = this, serviceRoleKey = serviceRoleKey)
