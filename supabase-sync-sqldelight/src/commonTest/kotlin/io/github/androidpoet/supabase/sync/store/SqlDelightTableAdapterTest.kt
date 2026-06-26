package io.github.androidpoet.supabase.sync.store

import io.github.androidpoet.supabase.sync.SyncRecord
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightTableAdapterTest {
    private val driver = testDriver()

    private val columns =
        listOf(
            Column("id", ColumnKind.TEXT),
            Column("title", ColumnKind.TEXT),
            Column("done", ColumnKind.BOOL),
            Column("rank", ColumnKind.INTEGER),
            Column("price", ColumnKind.REAL),
            Column("payload", ColumnKind.JSON),
            Column("note", ColumnKind.TEXT, nullable = true),
        )
    private val adapter = SqlDelightTableAdapter(driver, "todos", columns, pk = "id")

    init {
        // A typed domain table standing in for one the SQLDelight plugin would generate.
        driver.execute(
            null,
            """CREATE TABLE "todos" (
                 "id" TEXT NOT NULL PRIMARY KEY,
                 "title" TEXT NOT NULL,
                 "done" INTEGER NOT NULL,
                 "rank" INTEGER NOT NULL,
                 "price" REAL NOT NULL,
                 "payload" TEXT NOT NULL,
                 "note" TEXT
               )""",
            0,
        )
    }

    @AfterTest
    fun tearDown() = driver.close()

    private fun row(id: String, title: String, rank: Long): JsonObject =
        buildJsonObject {
            put("id", JsonPrimitive(id))
            put("title", JsonPrimitive(title))
            put("done", JsonPrimitive(true))
            put("rank", JsonPrimitive(rank))
            put("price", JsonPrimitive(9.99))
            put("payload", buildJsonObject { put("k", JsonPrimitive("v")) })
            put("note", JsonNull)
        }

    @Test
    fun round_trips_every_column_type() {
        adapter.upsert("a", row("a", "hello", rank = 3))

        val got = adapter.get("a")!!
        assertEquals(JsonPrimitive("hello"), got["title"])
        assertEquals(JsonPrimitive(true), got["done"], "INTEGER 0/1 decodes back to a JSON boolean")
        assertEquals(JsonPrimitive(3), got["rank"])
        assertEquals(JsonPrimitive(9.99), got["price"])
        assertEquals(buildJsonObject { put("k", JsonPrimitive("v")) }, got["payload"], "json column round-trips")
        assertEquals(JsonNull, got["note"], "nullable column comes back null")
    }

    @Test
    fun delete_and_absent_rows() {
        adapter.upsert("a", row("a", "x", 1))
        adapter.delete("a")
        assertNull(adapter.get("a"))
        assertNull(adapter.get("never-existed"))
    }

    @Test
    fun pagination_offset_keyset_and_count() {
        (1..5).forEach { adapter.upsert("id$it", row("id$it", "t$it", it.toLong())) }

        assertEquals(5, adapter.count())
        assertEquals(listOf("id1", "id2"), adapter.page(limit = 2, offset = 0).map { it.first })
        assertEquals(listOf("id3", "id4"), adapter.page(limit = 2, offset = 2).map { it.first })
        assertEquals(listOf("id3", "id4"), adapter.pageAfter(afterId = "id2", limit = 2).map { it.first })
        // The page payload is the full typed row.
        assertEquals(JsonPrimitive("t1"), adapter.page(1, 0).single().second["title"])
    }

    @Test
    fun works_as_the_stores_source_of_truth() =
        runTest {
            val store = SqlDelightLocalStore(driver, adapters = mapOf("todos" to adapter))
            store.upsert("todos", listOf(SyncRecord("a", updatedAt = 7, fields = row("a", "synced", 1))))

            val got = store.get("todos", "a")
            assertEquals(7, got?.updatedAt)
            assertEquals(JsonPrimitive("synced"), got?.fields?.get("title"))

            val page = store.page("todos", limit = 10, offset = 0)
            assertEquals(listOf("a"), page.items.map { it.id })
            assertTrue(page.total == 1L)
        }
}
