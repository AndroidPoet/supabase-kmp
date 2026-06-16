package io.github.androidpoet.supabase.auth

import io.github.androidpoet.supabase.client.SupabaseClient

/**
 * The [AuthClient] for this Supabase project — a discoverable shortcut for
 * [createAuthClient] so you can write `client.auth.signInWithEmail(...)` instead
 * of holding a separate factory reference.
 *
 * The returned client is a thin, stateless wrapper around [this] client (it owns
 * no connection or token state of its own), so reading this property repeatedly
 * is cheap and always talks to the same underlying transport.
 */
public val SupabaseClient.auth: AuthClient
    get() = createAuthClient(this)
