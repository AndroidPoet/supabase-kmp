package io.github.androidpoet.supabase.sync.store

import app.cash.sqldelight.db.SqlDriver
import io.github.androidpoet.supabase.sync.ChangeKind
import io.github.androidpoet.supabase.sync.Cursor
import io.github.androidpoet.supabase.sync.LocalStore
import io.github.androidpoet.supabase.sync.Page
import io.github.androidpoet.supabase.sync.PendingChange
import io.github.androidpoet.supabase.sync.Record
import io.github.androidpoet.supabase.sync.TableAdapter
import io.github.androidpoet.supabase.sync.store.db.OfflineSyncDb
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * A [LocalStore] backed by SQLDelight, so synced rows, pull cursors, and the outbox survive
 * process restarts on every Kotlin/Native and JVM target.
 *
 * **Storage model.** For each table you can register a [TableAdapter]: then that table is a
 * *single source of truth* — synced rows live in your own typed SQLDelight (or Room) table, which
 * you query directly, while this store keeps only the sync metadata (server `updatedAt`, the
 * tombstone) in a sidecar. Tables with **no** adapter fall back to a generic blob cache
 * (`SyncRecord`, fields as serialised JSON) — zero-config, but not your typed schema. Mix freely.
 *
 * The store is **driver-agnostic** — you bring your own [SqlDriver] (the standard SQLDelight way),
 * so the same code runs on iOS, macOS, and the JVM. Create the schema once on the driver before
 * constructing the store (`OfflineSyncDb.Schema.create(driver)`), or use [openOfflineSyncStore].
 */
public class SqlDelightLocalStore internal constructor(
    private val db: OfflineSyncDb,
    private val adapters: Map<String, TableAdapter> = emptyMap(),
) : LocalStore {
    private val json = Json { ignoreUnknownKeys = true }

    public constructor(driver: SqlDriver, adapters: Map<String, TableAdapter> = emptyMap()) :
        this(OfflineSyncDb(driver), adapters)

    private fun adapterFor(table: String): TableAdapter? = adapters[table]

    // --- remote rows landing locally (these become what get()/page() read) ---

    override suspend fun upsert(table: String, records: List<Record>) {
        if (records.isEmpty()) return
        val adapter = adapterFor(table)
        db.transaction {
            for (record in records) {
                // Monotonic-apply guard: never let a remote row regress to an older version. Out-of-order
                // delivery (a Realtime echo arriving after a newer pull, or a retry) would otherwise
                // overwrite a newer row with a stale one. Equal timestamps re-apply (idempotent).
                if (record.updatedAt < storedUpdatedAt(adapter, table, record.id)) continue
                if (adapter != null) applyToTyped(adapter, table, record) else blobUpsert(table, record)
            }
        }
    }

    override suspend fun get(table: String, id: String): Record? {
        val adapter = adapterFor(table) ?: return blobGet(table, id)
        val meta = db.syncMetaQueries.get(table, id).executeAsOneOrNull() ?: return null
        return Record(
            id = id,
            updatedAt = meta.updatedAt,
            deleted = meta.deleted.toBoolean(),
            fields = adapter.get(id) ?: emptyFields(),
        )
    }

    // --- pull cursor (always generic) ---

    override suspend fun cursor(table: String): Cursor? =
        db.syncCursorQueries
            .cursor(table)
            .executeAsOneOrNull()
            ?.let { Cursor(it.updatedAt, it.id) }

    override suspend fun setCursor(table: String, cursor: Cursor?) {
        if (cursor == null) {
            db.syncCursorQueries.clearCursor(table)
        } else {
            db.syncCursorQueries.setCursor(table, cursor.updatedAt, cursor.id)
        }
    }

    // --- outbox (generic for both paths: it stores the change payload to push) ---

    override suspend fun pending(table: String): List<PendingChange> =
        db.outboxQueries.pending(table).executeAsList().map { row ->
            PendingChange(
                record = Record(row.id, row.updatedAt, row.deleted.toBoolean(), decode(row.fields)),
                kind = ChangeKind.valueOf(row.kind),
            )
        }

    /**
     * Captures a local change: writes the row into the read store (your typed table for an adapter,
     * else the blob cache) **and** appends the change to the outbox, atomically. Reads reflect the
     * change immediately (optimistic UI); [SyncEngine.sync][io.github.androidpoet.supabase.sync.SyncEngine.sync]
     * later pushes the queued payload.
     */
    override suspend fun enqueue(table: String, change: PendingChange) {
        val adapter = adapterFor(table)
        val record = change.record
        db.transaction {
            if (adapter != null) applyToTyped(adapter, table, record) else blobUpsert(table, record)
            db.outboxQueries.enqueue(
                tableName = table,
                id = record.id,
                kind = change.kind.name,
                updatedAt = record.updatedAt,
                deleted = record.deleted.toLong(),
                fields = encode(record.fields),
            )
        }
    }

    override suspend fun clearPending(table: String, ids: List<String>) {
        if (ids.isEmpty()) return
        db.outboxQueries.clearPending(table, ids)
    }

    // --- pagination: three list slots (offset page / keyset page / count) ---

    /** Offset pagination — a [Page] of [limit] rows starting at [offset], with the table's total. */
    override suspend fun page(table: String, limit: Long, offset: Long): Page<Record> {
        val adapter = adapterFor(table)
        if (adapter != null) {
            val items = adapter.page(limit, offset).map { (id, fields) -> typedRecord(table, id, fields) }
            return Page(items, offset, limit, adapter.count())
        }
        val items =
            db.syncRecordQueries
                .page(table, limit, offset)
                .executeAsList()
                .map { Record(it.id, it.updatedAt, it.deleted.toBoolean(), decode(it.fields)) }
        return Page(items, offset, limit, db.syncRecordQueries.countAll(table).executeAsOne())
    }

    /** Keyset pagination — the next [limit] rows after [afterId] (`null` = first page). */
    override suspend fun pageAfter(table: String, afterId: String?, limit: Long): List<Record> {
        val adapter = adapterFor(table)
        if (adapter != null) {
            return adapter.pageAfter(afterId, limit).map { (id, fields) -> typedRecord(table, id, fields) }
        }
        return db.syncRecordQueries
            .pageAfter(table, afterId ?: "", limit)
            .executeAsList()
            .map { Record(it.id, it.updatedAt, it.deleted.toBoolean(), decode(it.fields)) }
    }

    /** Total row count for [table] (drives [Page.total] / "page x of y"). */
    override suspend fun count(table: String): Long =
        adapterFor(table)?.count() ?: db.syncRecordQueries.countAll(table).executeAsOne()

    // --- internals ---

    /** The stored row's `updatedAt`, or [Long.MIN_VALUE] when the row is absent (so new rows apply). */
    private fun storedUpdatedAt(adapter: TableAdapter?, table: String, id: String): Long =
        if (adapter != null) {
            db.syncMetaQueries
                .get(table, id)
                .executeAsOneOrNull()
                ?.updatedAt ?: Long.MIN_VALUE
        } else {
            db.syncRecordQueries
                .selectById(table, id)
                .executeAsOneOrNull()
                ?.updatedAt ?: Long.MIN_VALUE
        }

    private fun applyToTyped(adapter: TableAdapter, table: String, record: Record) {
        if (record.deleted) adapter.delete(record.id) else adapter.upsert(record.id, record.fields)
        db.syncMetaQueries.put(table, record.id, record.updatedAt, record.deleted.toLong())
    }

    private fun blobUpsert(table: String, record: Record) {
        db.syncRecordQueries.upsert(table, record.id, record.updatedAt, record.deleted.toLong(), encode(record.fields))
    }

    private fun blobGet(table: String, id: String): Record? =
        db.syncRecordQueries.selectById(table, id).executeAsOneOrNull()?.let {
            Record(id, it.updatedAt, it.deleted.toBoolean(), decode(it.fields))
        }

    private fun typedRecord(table: String, id: String, fields: JsonObject): Record {
        val meta = db.syncMetaQueries.get(table, id).executeAsOneOrNull()
        return Record(id, meta?.updatedAt ?: 0L, meta?.deleted.toBoolean(), fields)
    }

    private fun encode(fields: JsonObject): String = json.encodeToString(JsonObject.serializer(), fields)

    private fun decode(text: String): JsonObject = json.decodeFromString(JsonObject.serializer(), text)

    private fun emptyFields(): JsonObject = JsonObject(emptyMap())

    private fun Boolean.toLong(): Long = if (this) 1L else 0L

    private fun Long?.toBoolean(): Boolean = this != null && this != 0L
}

/** Creates the schema on [driver] and returns a ready-to-use store with optional table [adapters]. */
public fun openOfflineSyncStore(
    driver: SqlDriver,
    adapters: Map<String, TableAdapter> = emptyMap(),
): SqlDelightLocalStore {
    OfflineSyncDb.Schema.create(driver)
    return SqlDelightLocalStore(driver, adapters)
}
