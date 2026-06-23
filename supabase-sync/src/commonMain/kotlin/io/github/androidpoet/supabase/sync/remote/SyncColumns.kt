package io.github.androidpoet.supabase.sync.remote

/**
 * Names of the three columns the sync engine needs to find on every synced Supabase table.
 *
 * The engine is transport-neutral: a [io.github.androidpoet.supabase.sync.Record] carries an [id],
 * a server-set [updatedAt] (epoch millis), and a soft-delete [deleted] tombstone. This maps those
 * concepts onto your actual Postgres columns so the same table can use whatever naming you already
 * have. Everything *except* [updatedAt] and [deleted] is treated as a domain field and mirrored
 * into your typed local table; [id] stays a domain column (it is part of the row), while
 * [updatedAt]/[deleted] are stripped out as sync metadata.
 *
 * Conventions assumed by [SupabaseRemoteSource]:
 *  - [updatedAt] is an **integer epoch-millis** column (`bigint`), so it sorts and compares as the
 *    `Long` the cursor keyset uses. A `timestamptz` column won't compare correctly against the
 *    numeric cursor — store the millis, or add a generated bigint column.
 *  - [deleted] is a `boolean` defaulting to `false`; deletes are soft (the row stays, tombstoned)
 *    so an incremental "changed since" pull can still see them. A hard `DELETE` is invisible to
 *    such a query.
 */
public data class SyncColumns(
    public val id: String = "id",
    public val updatedAt: String = "updated_at",
    public val deleted: String = "deleted",
)
