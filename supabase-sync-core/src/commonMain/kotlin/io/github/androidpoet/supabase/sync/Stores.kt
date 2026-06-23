package io.github.androidpoet.supabase.sync

import kotlinx.coroutines.flow.Flow

/**
 * The local database side of sync (backed by SQLDelight in the `sqldelight` module). It stores
 * synced rows, remembers each table's pull [cursor], and holds the outbox of local changes that
 * still need to be pushed.
 */
public interface LocalStore {
    /** Inserts or replaces [records] in [table]. */
    public suspend fun upsert(table: String, records: List<Record>)

    /** The row [id] in [table], or `null` if absent. */
    public suspend fun get(table: String, id: String): Record?

    /** The last pull position stored for [table], or `null` if it has never synced. */
    public suspend fun cursor(table: String): Cursor?

    /** Persists the pull position for [table]. */
    public suspend fun setCursor(table: String, cursor: Cursor?)

    /** Local changes for [table] still waiting to be pushed. */
    public suspend fun pending(table: String): List<PendingChange>

    /** Adds a local change to [table]'s outbox, to be pushed on the next [SyncEngine.sync]. */
    public suspend fun enqueue(table: String, change: PendingChange)

    /** Removes the outbox entries for [ids] in [table] (after they were pushed or superseded). */
    public suspend fun clearPending(table: String, ids: List<String>)
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
