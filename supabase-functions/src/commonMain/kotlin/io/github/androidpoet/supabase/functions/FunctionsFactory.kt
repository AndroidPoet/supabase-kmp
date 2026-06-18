package io.github.androidpoet.supabase.functions

import io.github.androidpoet.supabase.client.SupabaseClient

/**
 * Creates a [FunctionsClient] backed by [supabaseClient], reusing its base URL,
 * HTTP engine and session token. The standard entry point to the Functions API;
 * the returned client holds no state of its own beyond an optionally pinned
 * [FunctionsClient.setAuth] token, so one per [SupabaseClient] is enough.
 */
public fun createFunctionsClient(supabaseClient: SupabaseClient): FunctionsClient =
    FunctionsClientImpl(client = supabaseClient)
