package io.github.androidpoet.supabase.functions

import io.github.androidpoet.supabase.client.SupabaseClient

public fun createFunctionsClient(supabaseClient: SupabaseClient): FunctionsClient =
    FunctionsClientImpl(client = supabaseClient)
