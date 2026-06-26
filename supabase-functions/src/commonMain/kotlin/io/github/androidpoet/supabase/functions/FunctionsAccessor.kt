package io.github.androidpoet.supabase.functions

import io.github.androidpoet.supabase.client.SupabaseClient

/**
 * The [FunctionsClient] for this Supabase project — a discoverable shortcut for
 * [createFunctionsClient] so you can reach Edge Functions as `client.functions`
 * instead of holding a separate factory reference.
 *
 * The returned client is a thin, stateless wrapper around [this] client, so
 * reading this property repeatedly is cheap and always talks to the same
 * underlying transport.
 */
public val SupabaseClient.functions: FunctionsClient
    get() = createFunctionsClient(this)
