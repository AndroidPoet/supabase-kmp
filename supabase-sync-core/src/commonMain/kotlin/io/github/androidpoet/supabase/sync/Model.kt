package io.github.androidpoet.supabase.sync

import kotlinx.serialization.json.JsonObject

/**
 * A synced row in its transport-neutral form. [updatedAt] is the **server-set** modification
 * time (epoch millis) and is what last-write-wins compares; [deleted] is a soft-delete
 * tombstone so deletions propagate through an incremental pull (a hard delete is invisible to
 * a "changed since" query).
 */
public data class Record(
    public val id: String,
    public val updatedAt: Long,
    public val deleted: Boolean = false,
    public val fields: JsonObject,
)

/**
 * A per-table pull position. Incremental pulls resume *after* this point using a keyset on the
 * composite `(updatedAt, id)` — i.e. rows where `updatedAt > [updatedAt]`, plus rows at exactly
 * `[updatedAt]` whose `id > [id]`. The composite (rather than a bare timestamp high-water mark) is
 * what makes the pull stable when several rows share the same `updatedAt`: a plain `updated_at >
 * cursor` skips rows written in the same millisecond at the boundary, while `>=` re-fetches or
 * loops. [id] defaults to empty (the low bound), so a cursor of just an `updatedAt` still works.
 */
public data class Cursor(
    public val updatedAt: Long,
    public val id: String = "",
)

/** The kind of local mutation queued in the outbox. */
public enum class ChangeKind { UPSERT, DELETE }

/** A local mutation waiting to be pushed to the remote. */
public data class PendingChange(
    public val record: Record,
    public val kind: ChangeKind,
)

/** What a pull returned: the rows that changed plus the cursor to resume from next time. */
public data class PullResult(
    public val changed: List<Record>,
    public val nextCursor: Cursor?,
)

/** Per-id outcome of a push: which local changes the server accepted vs rejected. */
public data class PushResult(
    public val accepted: List<String>,
    public val rejected: List<String> = emptyList(),
)

/** A single live change streamed from the remote (e.g. Supabase Realtime). */
public data class RemoteChange(
    public val record: Record,
)

/** Summary of one [SyncEngine.sync] cycle. */
public data class SyncResult(
    public val pulled: Int,
    public val pushed: Int,
    public val rejected: Int,
)

/** Outcome of one [SyncEngine.pullPage]: rows merged, and whether another page likely follows. */
public data class PullProgress(
    public val pulled: Int,
    public val hasMore: Boolean,
)
