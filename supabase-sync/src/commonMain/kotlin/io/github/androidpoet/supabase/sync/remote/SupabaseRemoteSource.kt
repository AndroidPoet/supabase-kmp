package io.github.androidpoet.supabase.sync.remote

import io.github.androidpoet.supabase.database.DatabaseClient
import io.github.androidpoet.supabase.database.ReturnOption
import io.github.androidpoet.supabase.realtime.RealtimeClient
import io.github.androidpoet.supabase.realtime.models.PostgresChangeEvent
import io.github.androidpoet.supabase.sync.Cursor
import io.github.androidpoet.supabase.sync.PendingChange
import io.github.androidpoet.supabase.sync.PullResult
import io.github.androidpoet.supabase.sync.PushResult
import io.github.androidpoet.supabase.sync.RemoteChange
import io.github.androidpoet.supabase.sync.RemoteSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * The [RemoteSource] backed by Supabase: incremental pulls and bulk upserts over PostgREST
 * ([database]) and a live delta stream over Realtime ([realtime]). Drop it into a
 * [io.github.androidpoet.supabase.sync.SyncEngine] alongside a local store and it provides the
 * "remote" half of offline-first sync against any table that follows the [SyncColumns] convention.
 *
 * Build [database] and [realtime] from your `supabase-kmp` client and share the same auth token,
 * so row-level security applies to both the pulled rows and the realtime subscription.
 *
 * @param columns names of the id / updated-at / soft-delete columns on the synced tables.
 * @param schema the Postgres schema the tables live in (`public` by default).
 * @param pageSize max rows fetched per [pull]; if more changed, the advanced cursor lets the next
 *   [io.github.androidpoet.supabase.sync.SyncEngine.sync] pick up the remainder.
 * @param scope used only to best-effort tear down a realtime channel when a [changes] flow is
 *   cancelled.
 */
public class SupabaseRemoteSource(
    private val database: DatabaseClient,
    private val realtime: RealtimeClient,
    private val columns: SyncColumns = SyncColumns(),
    private val schema: String = "public",
    private val pageSize: Int = 1000,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : RemoteSource {
    /**
     * Fetches rows whose `(updatedAt, id)` is greater than [since] (all rows when `null`), ordered
     * by that same keyset and capped at [pageSize]. The keyset — rather than a bare `updated_at >`
     * — keeps the pull stable when several rows share an `updatedAt`. Returns the new high-water
     * cursor, or `null` (don't advance) when nothing changed.
     */
    override suspend fun pull(table: String, since: Cursor?): PullResult {
        val body =
            database
                .select(
                    table = table,
                    schema = schema,
                    columns = "*",
                ) {
                    if (since != null) {
                        or {
                            gt(columns.updatedAt, since.updatedAt)
                            and {
                                eq(columns.updatedAt, since.updatedAt)
                                gt(columns.id, since.id)
                            }
                        }
                    }
                    order(columns.updatedAt, ascending = true)
                    order(columns.id, ascending = true)
                    limit(pageSize)
                }.getOrThrow()

        val records = parseRows(body, columns)
        return PullResult(changed = records, nextCursor = pullCursor(records))
    }

    /**
     * Bulk-upserts the outbox records (`on_conflict` on the id column). A push is all-or-nothing
     * per request: on success every id is accepted; a transport/server error throws, leaving the
     * outbox intact for the next sync. Deletes are soft — the record carries `deleted = true`, so
     * the same upsert path tombstones the row.
     */
    override suspend fun push(table: String, changes: List<PendingChange>): PushResult {
        if (changes.isEmpty()) return PushResult(emptyList())
        database
            .insert(
                table = table,
                schema = schema,
                body = encodeRows(changes.map { it.record }, columns),
                upsert = true,
                onConflict = columns.id,
                returning = ReturnOption.MINIMAL,
            ).getOrThrow()
        return PushResult(accepted = changes.map { it.record.id })
    }

    /**
     * A cold [Flow] of [table]'s realtime `postgres_changes`, each mapped to a [RemoteChange].
     * INSERT/UPDATE carry the new row; a DELETE is surfaced as a tombstone. Connects the client if
     * needed and tears the channel down when collection stops. Pair with periodic [pull] to
     * backfill anything missed while disconnected.
     */
    override fun changes(table: String): Flow<RemoteChange> =
        callbackFlow {
            if (!realtime.isConnected) realtime.connect()
            val subscription =
                realtime
                    .channel("offline-sync:$table")
                    .onPostgresChange(schema = schema, table = table) { event, row ->
                        trySend(RemoteChange(rowToRecord(row, columns, forceDeleted = event == PostgresChangeEvent.DELETE)))
                    }.subscribe()
            awaitClose {
                scope.launch { runCatching { realtime.removeSubscription(subscription) } }
            }
        }
}
