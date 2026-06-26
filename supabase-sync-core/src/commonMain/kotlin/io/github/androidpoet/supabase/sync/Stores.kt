package io.github.androidpoet.supabase.sync

import kotlinx.coroutines.flow.Flow

/**
 * The local database side of sync (backed by SQLDelight in the `sqldelight` module). It stores
 * synced rows, remembers each table's pull [cursor], and holds the outbox of local changes that
 * still need to be pushed.
 */
public interface LocalStore {
    /**
     * Applies remote [records] to [table] — the **pull** path. Implementations apply a monotonic
     * guard per row (a record older than the stored one is skipped), so re-applying an earlier pull
     * never clobbers newer local state. Do **not** use this for local edits: a local write stamped
     * with a client clock that trails the row's stored (server) `updatedAt` would be silently
     * dropped by that guard — local edits go through [enqueue] instead.
     */
    public suspend fun upsert(table: String, records: List<Record>)

    /** The row [id] in [table], or `null` if absent. */
    public suspend fun get(table: String, id: String): Record?

    /** The last pull position stored for [table], or `null` if it has never synced. */
    public suspend fun cursor(table: String): Cursor?

    /** Persists the pull position for [table]. */
    public suspend fun setCursor(table: String, cursor: Cursor?)

    /** Local changes for [table] still waiting to be pushed. */
    public suspend fun pending(table: String): List<PendingChange>

    /**
     * Records a local edit — the **optimistic write** path. In one transaction it applies the row
     * (unguarded, so the user's edit always wins locally — unlike [upsert]) **and** appends the
     * change to [table]'s outbox to be pushed on the next [SyncEngine.sync]. The outbox holds one
     * entry per row id (latest intent wins), so re-editing a row before it syncs replaces its
     * pending entry rather than queuing a duplicate — the push then never sends the same id twice.
     */
    public suspend fun enqueue(table: String, change: PendingChange)

    /** Removes the outbox entries for [ids] in [table] (after they were pushed or superseded). */
    public suspend fun clearPending(table: String, ids: List<String>)

    /** Offset page of live (non-tombstone) rows in [table], ordered by id. Drives `SyncStore.paged`. */
    public suspend fun page(table: String, limit: Long, offset: Long): Page<Record>

    /** Keyset page: the next [limit] live rows after [afterId] (`null` = first page), ordered by id. */
    public suspend fun pageAfter(table: String, afterId: String?, limit: Long): List<Record>

    /** Total live (non-tombstone) row count for [table]. */
    public suspend fun count(table: String): Long
}

/**
 * The remote API side of sync (implemented over Supabase PostgREST + Realtime in the `supabase`
 * module). [pull] fetches rows changed since a cursor, [push] sends local changes, and [changes]
 * streams live deltas.
 */
public interface RemoteSource {
    /**
     * Fetches rows changed since [since] (all rows when `null`). Set [PullResult.nextCursor] to
     * the position to resume from, or `null` to leave the stored cursor unchanged (e.g. when
     * nothing changed) — returning `null` must never force a full re-sync.
     */
    public suspend fun pull(table: String, since: Cursor?): PullResult

    /** Sends local [changes] to the server and reports which were accepted. */
    public suspend fun push(table: String, changes: List<PendingChange>): PushResult

    /** A live stream of remote changes for [table] (e.g. Supabase Realtime). */
    public fun changes(table: String): Flow<RemoteChange>
}
