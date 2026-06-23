package io.github.androidpoet.supabase.sync

import kotlinx.serialization.json.JsonObject

/**
 * The universal seam that makes a consumer's **own typed table the single source of truth** for a
 * synced table, on *any* local database. A [LocalStore] reads and writes domain rows only through
 * this adapter, so the synced data lives in the typed table you query directly — a SQLDelight
 * table, a Room entity, or anything else — instead of a generic blob cache. Register one per table
 * with your store; upstream `supabase-codegen` generates these over the typed queries, or you can
 * hand-write one.
 *
 * The adapter handles only the domain row's [JsonObject] fields. Sync metadata (the server-set
 * `updatedAt` and the soft-delete tombstone) is the store's responsibility — typically a sidecar
 * keyed by `(table, id)` — so your table's schema stays a clean 1:1 mirror of the remote with no
 * sync columns leaking in. This interface is pure Kotlin (no storage-engine types), so the same
 * adapter shape works across every store implementation.
 *
 * Methods are synchronous; the store calls them from its `suspend` functions.
 */
public interface TableAdapter {
    /** The table name as the engine/remote knows it (e.g. `"todos"`). */
    public val table: String

    /** Inserts or replaces the row [id] with [fields]. */
    public fun upsert(id: String, fields: JsonObject)

    /** The row [id]'s fields, or `null` if absent. */
    public fun get(id: String): JsonObject?

    /** Removes the row [id]. */
    public fun delete(id: String)

    /** Offset pagination: rows ordered by id, returned as `(id, fields)` pairs. */
    public fun page(limit: Long, offset: Long): List<Pair<String, JsonObject>>

    /** Keyset pagination: the next [limit] rows with id greater than [afterId] (`null` = start). */
    public fun pageAfter(afterId: String?, limit: Long): List<Pair<String, JsonObject>>

    /** Total row count, for [Page.total]. */
    public fun count(): Long
}
