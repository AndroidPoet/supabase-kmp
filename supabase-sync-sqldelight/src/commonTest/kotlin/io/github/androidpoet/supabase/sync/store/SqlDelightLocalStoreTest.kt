package io.github.androidpoet.supabase.sync.store

import io.github.androidpoet.supabase.sync.ChangeKind
import io.github.androidpoet.supabase.sync.Cursor
import io.github.androidpoet.supabase.sync.PendingChange
import io.github.androidpoet.supabase.sync.Record
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightLocalStoreTest {
    private val driver = testDriver()
    private val store = SqlDelightLocalStore(driver)

    @AfterTest
    fun tearDown() = driver.close()

    private fun record(id: String, updatedAt: Long, deleted: Boolean = false, title: String = "t"): Record =
        Record(
            id = id,
            updatedAt = updatedAt,
            deleted = deleted,
            fields = buildJsonObject { put("title", JsonPrimitive(title)) },
        )

    private fun JsonObject.title(): String? = (this["title"] as? JsonPrimitive)?.content

    @Test
    fun upsert_then_get_round_trips_every_field() =
        runTest {
            store.upsert("todos", listOf(record("a", updatedAt = 10, deleted = true, title = "hello")))

            val got = store.get("todos", "a")
            assertEquals("a", got?.id)
            assertEquals(10, got?.updatedAt)
            assertEquals(true, got?.deleted)
            assertEquals("hello", got?.fields?.title())
        }

    @Test
    fun get_is_scoped_by_table_and_returns_null_when_absent() =
        runTest {
            store.upsert("todos", listOf(record("a", updatedAt = 1)))
            assertNull(store.get("notes", "a"), "same id in a different table must not leak")
            assertNull(store.get("todos", "missing"))
        }

    @Test
    fun upsert_replaces_an_existing_row() =
        runTest {
            store.upsert("todos", listOf(record("a", updatedAt = 1, title = "old")))
            store.upsert("todos", listOf(record("a", updatedAt = 2, title = "new")))

            val got = store.get("todos", "a")
            assertEquals(2, got?.updatedAt)
            assertEquals("new", got?.fields?.title())
        }

    @Test
    fun composite_cursor_is_persisted_and_clearable_per_table() =
        runTest {
            assertNull(store.cursor("todos"))

            store.setCursor("todos", Cursor(updatedAt = 42, id = "msg-7"))
            assertEquals(Cursor(42, "msg-7"), store.cursor("todos"), "both halves of the keyset round-trip")
            assertNull(store.cursor("notes"), "cursors don't bleed across tables")

            store.setCursor("todos", null)
            assertNull(store.cursor("todos"), "null cursor clears the stored position")
        }

    @Test
    fun upsert_never_regresses_to_an_older_version() =
        runTest {
            store.upsert("todos", listOf(record("a", updatedAt = 10, title = "new")))
            store.upsert("todos", listOf(record("a", updatedAt = 5, title = "stale"))) // out-of-order/retry

            val got = store.get("todos", "a")
            assertEquals(10, got?.updatedAt, "the stale write is ignored")
            assertEquals("new", got?.fields?.title())

            // An equal timestamp re-applies (idempotent re-delivery is safe).
            store.upsert("todos", listOf(record("a", updatedAt = 10, title = "same-ts")))
            assertEquals("same-ts", store.get("todos", "a")?.fields?.title())
        }

    @Test
    fun outbox_enqueue_pending_and_clear() =
        runTest {
            store.enqueue("todos", PendingChange(record("a", updatedAt = 1), ChangeKind.UPSERT))
            store.enqueue("todos", PendingChange(record("b", updatedAt = 2, deleted = true), ChangeKind.DELETE))

            val pending = store.pending("todos")
            assertEquals(listOf("a", "b"), pending.map { it.record.id }, "ordered by updatedAt")
            assertEquals(ChangeKind.DELETE, pending.first { it.record.id == "b" }.kind)
            assertTrue(pending.first { it.record.id == "b" }.record.deleted)

            store.clearPending("todos", listOf("a"))
            assertEquals(listOf("b"), store.pending("todos").map { it.record.id })
        }

    @Test
    fun re_enqueuing_the_same_id_keeps_only_the_latest_intent() =
        runTest {
            store.enqueue("todos", PendingChange(record("a", updatedAt = 1, title = "first"), ChangeKind.UPSERT))
            store.enqueue("todos", PendingChange(record("a", updatedAt = 5, title = "second"), ChangeKind.UPSERT))

            val pending = store.pending("todos")
            assertEquals(1, pending.size)
            assertEquals(
                "second",
                pending
                    .single()
                    .record.fields
                    .title(),
            )
        }
}
