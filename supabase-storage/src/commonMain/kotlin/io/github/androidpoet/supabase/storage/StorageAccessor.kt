package io.github.androidpoet.supabase.storage

import io.github.androidpoet.supabase.client.SupabaseClient

/**
 * The [StorageClient] for this Supabase project — a discoverable shortcut for
 * [createStorageClient] so you can reach storage as `client.storage` instead of
 * holding a separate factory reference.
 *
 * The returned client is a thin, stateless wrapper around [this] client, so
 * reading this property repeatedly is cheap and always talks to the same
 * underlying transport.
 */
public val SupabaseClient.storage: StorageClient
    get() = createStorageClient(this)
