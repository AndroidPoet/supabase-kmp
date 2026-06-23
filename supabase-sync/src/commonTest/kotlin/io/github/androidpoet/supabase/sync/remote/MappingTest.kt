package io.github.androidpoet.supabase.sync.remote

import io.github.androidpoet.supabase.sync.Cursor
import io.github.androidpoet.supabase.sync.Record
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MappingTest {
    private val columns = SyncColumns()

    @Test
    fun rowToRecord_strips_metadata_but_keeps_id_and_domain_fields() {
        val row =
            Json
                .parseToJsonElement(
                    """{"id":"a1","title":"hi","done":true,"updated_at":1700,"deleted":false}""",
                ).jsonObject

        val record = rowToRecord(row, columns)

        assertEquals("a1", record.id)
        assertEquals(1700L, record.updatedAt)
        assertTrue(!record.deleted)
        // updated_at / deleted are sync metadata and must not leak into the typed row...
        assertEquals(setOf("id", "title", "done"), record.fields.keys)
        // ...but id stays a domain column.
        assertEquals("a1", record.fields["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun rowToRecord_reads_deleted_tombstone() {
        val row = Json.parseToJsonElement("""{"id":"x","updated_at":5,"deleted":true}""").jsonObject
        assertTrue(rowToRecord(row, columns).deleted)
    }

    @Test
    fun rowToRecord_forceDeleted_for_hard_delete_event() {
        val row = Json.parseToJsonElement("""{"id":"x"}""").jsonObject
        val record = rowToRecord(row, columns, forceDeleted = true)
        assertTrue(record.deleted)
        assertEquals(0L, record.updatedAt) // absent updated_at defaults to 0
    }

    @Test
    fun parseRows_handles_empty_and_null_bodies() {
        assertEquals(emptyList(), parseRows("[]", columns))
        assertEquals(emptyList(), parseRows("", columns))
        assertEquals(emptyList(), parseRows("null", columns))
    }

    @Test
    fun pullCursor_is_high_water_mark_of_updatedAt_then_id() {
        val records =
            listOf(
                record("a", 10),
                record("c", 20),
                record("b", 20), // same updatedAt, larger id wins on the keyset
            )
        assertEquals(Cursor(20, "c"), pullCursor(records))
    }

    @Test
    fun pullCursor_null_on_empty_so_sync_does_not_advance() {
        assertNull(pullCursor(emptyList()))
    }

    @Test
    fun recordToRow_round_trips_through_rowToRecord() {
        val original = record("k", 42, deleted = true)
        val restored = rowToRecord(recordToRow(original, columns), columns)
        assertEquals(original, restored)
    }

    @Test
    fun encodeRows_emits_full_rows_with_metadata_columns() {
        val body = encodeRows(listOf(record("k", 7, deleted = true)), columns)
        val obj =
            Json
                .parseToJsonElement(body)
                .jsonArray
                .single()
                .jsonObject

        assertEquals("k", obj["id"]?.jsonPrimitive?.content)
        assertEquals(7L, obj["updated_at"]?.jsonPrimitive?.long)
        assertEquals(true, obj["deleted"]?.jsonPrimitive?.boolean)
        assertEquals("hi", obj["title"]?.jsonPrimitive?.content)
    }

    private fun record(id: String, updatedAt: Long, deleted: Boolean = false): Record =
        Record(
            id = id,
            updatedAt = updatedAt,
            deleted = deleted,
            fields =
                JsonObject(
                    mapOf(
                        "id" to JsonPrimitive(id),
                        "title" to JsonPrimitive("hi"),
                    ),
                ),
        )
}
