package io.github.androidpoet.supabase.sync.remote

import io.github.androidpoet.supabase.sync.Cursor
import io.github.androidpoet.supabase.sync.Record
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

// The pure JSON <-> Record translation used by SupabaseRemoteSource. Kept free of any HTTP or
// realtime types so the tricky parts — keyset cursor, metadata stripping, push body shape — are
// unit-testable without a live Supabase.

/**
 * Turns one PostgREST/Realtime row into a [Record]: pulls out [SyncColumns.id],
 * [SyncColumns.updatedAt] and [SyncColumns.deleted], and keeps everything else (including the id
 * column) as the domain [Record.fields] that mirror the typed local table. [forceDeleted] marks
 * the record deleted regardless of the column — used for a hard `DELETE` realtime event whose old
 * row carries no tombstone.
 */
internal fun rowToRecord(row: JsonObject, columns: SyncColumns, forceDeleted: Boolean = false): Record {
    val id = (row[columns.id] ?: error("row is missing id column '${columns.id}'")).jsonPrimitive.content
    val updatedAt = row[columns.updatedAt]?.jsonPrimitive?.longOrNull ?: 0L
    val deleted = forceDeleted || (row[columns.deleted]?.jsonPrimitive?.booleanOrNull ?: false)
    val fields = JsonObject(row.filterKeys { it != columns.updatedAt && it != columns.deleted })
    return Record(id = id, updatedAt = updatedAt, deleted = deleted, fields = fields)
}

/** Parses a PostgREST select body (a JSON array, possibly empty/null) into [Record]s. */
internal fun parseRows(body: String, columns: SyncColumns): List<Record> {
    val trimmed = body.trim()
    if (trimmed.isEmpty() || trimmed == "null") return emptyList()
    return Json.parseToJsonElement(trimmed).jsonArray.map { rowToRecord(it.jsonObject, columns) }
}

/**
 * The cursor to resume from after a pull. `null` means "leave the stored cursor untouched" — never
 * a reset — so an empty page does not force a full re-sync. The high-water mark is the max
 * `(updatedAt, id)` seen, computed defensively rather than trusting row order.
 */
internal fun pullCursor(records: List<Record>): Cursor? {
    if (records.isEmpty()) return null
    val last = records.maxWith(compareBy({ it.updatedAt }, { it.id }))
    return Cursor(last.updatedAt, last.id)
}

/** Reconstructs the full row to upsert: domain [Record.fields] plus the sync-metadata columns. */
internal fun recordToRow(record: Record, columns: SyncColumns): JsonObject =
    buildJsonObject {
        record.fields.forEach { (key, value) -> put(key, value) }
        if (!record.fields.containsKey(columns.id)) put(columns.id, JsonPrimitive(record.id))
        put(columns.updatedAt, JsonPrimitive(record.updatedAt))
        put(columns.deleted, JsonPrimitive(record.deleted))
    }

/** Encodes outbox records as the JSON array body of a bulk PostgREST upsert. */
internal fun encodeRows(records: List<Record>, columns: SyncColumns): String =
    JsonArray(records.map { recordToRow(it, columns) }).toString()
