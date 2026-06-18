package io.github.androidpoet.supabase.database

import io.github.androidpoet.supabase.client.SupabaseClient

/**
 * Creates a [DatabaseClient] (PostgREST) bound to [supabaseClient].
 *
 * The result is a thin, stateless wrapper that forwards every call to the given
 * client's transport, so it is cheap to create and holds no state of its own —
 * the [SupabaseClient.database] property simply calls this. Use it directly when
 * you want to keep a named reference.
 */
public fun createDatabaseClient(supabaseClient: SupabaseClient): DatabaseClient =
    DatabaseClientImpl(client = supabaseClient)
