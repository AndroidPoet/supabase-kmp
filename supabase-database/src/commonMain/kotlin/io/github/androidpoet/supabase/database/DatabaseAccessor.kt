package io.github.androidpoet.supabase.database

import io.github.androidpoet.supabase.client.SupabaseClient

/**
 * The [DatabaseClient] (PostgREST) for this Supabase project — a discoverable
 * shortcut for [createDatabaseClient] so you can write
 * `client.database.selectTyped<Todo>("todos")` instead of holding a separate
 * factory reference.
 *
 * The returned client is a thin, stateless wrapper around [this] client, so
 * reading this property repeatedly is cheap and always talks to the same
 * underlying transport.
 */
public val SupabaseClient.database: DatabaseClient
    get() = createDatabaseClient(this)
