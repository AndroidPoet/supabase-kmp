package io.github.androidpoet.supabase.sync.remote

import io.github.androidpoet.supabase.database.DatabaseClient
import io.github.androidpoet.supabase.realtime.RealtimeClient

/**
 * Creates a [SupabaseRemoteSource] from a [database] and [realtime] client — the
 * convention-matching entry point that mirrors the SDK's other `create*` factories. Equivalent to
 * calling the [SupabaseRemoteSource] constructor directly; prefer this for consistency with the
 * rest of `supabase-kmp`. Build [database] and [realtime] from the same `supabase-kmp` client so
 * row-level security applies to both the pulled rows and the realtime subscription.
 *
 * @param columns names of the id / updated-at / soft-delete columns on the synced tables.
 * @param schema the Postgres schema the tables live in (`public` by default).
 * @param pageSize max rows fetched per pull.
 */
public fun createSupabaseRemoteSource(
    database: DatabaseClient,
    realtime: RealtimeClient,
    columns: SyncColumns = SyncColumns(),
    schema: String = "public",
    pageSize: Int = 1000,
): SupabaseRemoteSource =
    SupabaseRemoteSource(
        database = database,
        realtime = realtime,
        columns = columns,
        schema = schema,
        pageSize = pageSize,
    )
