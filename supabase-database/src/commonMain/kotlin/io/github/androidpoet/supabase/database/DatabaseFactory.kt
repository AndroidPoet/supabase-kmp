package io.github.androidpoet.supabase.database

import io.github.androidpoet.supabase.client.SupabaseClient

public fun createDatabaseClient(supabaseClient: SupabaseClient): DatabaseClient =
    DatabaseClientImpl(client = supabaseClient)
