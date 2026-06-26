package io.github.androidpoet.supabase.sync.store

import io.github.androidpoet.supabase.sync.ChangeKind
import io.github.androidpoet.supabase.sync.PendingChange
import io.github.androidpoet.supabase.sync.SyncRecord
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SsotAndPaginationTest {
    private val driver = testDriver()
    private val adapter = FakeTableAdapter("todos")
    private val store = SqlDelightLocalStore(driver, adapters = mapOf("todos" to adapter))

    @AfterTest
    fun tearDown() = driver.close()

    private fun record(id: String, updatedAt: Long, deleted: Boolean = false, title: String = "t"): SyncRecord =
        SyncRecord(id, updatedAt, deleted, buildJsonObject { put("title", JsonPrimitive(title)) })

    private fun JsonObject.title(): String? = (this["title"] as? JsonPrimitive)?.content

    @Test
    fun bound_table_stores_rows_in_the_adapter_not_the_blob_cache() =
        runTest {
            store.upsert("todos", listOf(record("a", updatedAt = 5, title = "hello")))

            // Data landed in the typed adapter (the source of truth)…
            assertEquals("hello", adapter.rows["a"]?.title())

            // …and reads come back through it, enriched with sidecar metadata.
            val got = store.get("todos", "a")
            assertEquals(5, got?.updatedAt)
            assertEquals("hello", got?.fields?.title())

            // A blob-only store on the same driver sees nothing — the row never hit the fallback cache.
            val blobOnly = SqlDelightLocalStore(driver)
            assertNull(blobOnly.get("todos", "a"), "bound rows must not also be written to the blob cache")
        }

    @Test
    fun bound_delete_tombstones_via_the_sidecar() =
        runTest {
            store.upsert("todos", listOf(record("a", updatedAt = 1)))
            store.upsert("todos", listOf(record("a", updatedAt = 2, deleted = true)))

            assertNull(adapter.rows["a"], "the typed row is removed")
            val got = store.get("todos", "a")
            assertEquals(true, got?.deleted, "but the tombstone is still readable from the sidecar")
            assertEquals(2, got?.updatedAt)
        }

    @Test
    fun bound_upsert_never_regresses_to_an_older_version() =
        runTest {
            store.upsert("todos", listOf(record("a", updatedAt = 10, title = "new")))
            store.upsert("todos", listOf(record("a", updatedAt = 5, title = "stale"))) // out-of-order

            val got = store.get("todos", "a")
            assertEquals(10, got?.updatedAt)
            assertEquals("new", got?.fields?.title(), "the typed source-of-truth row is not regressed")
        }

    @Test
    fun local_write_is_optimistic_and_queued() =
        runTest {
            store.enqueue("todos", PendingChange(record("a", updatedAt = 9, title = "draft"), ChangeKind.UPSERT))

            // Reflected in reads immediately (optimistic UI)…
            assertEquals("draft", store.get("todos", "a")?.fields?.title())
            // …and queued for push.
            val pending = store.pending("todos")
            assertEquals(listOf("a"), pending.map { it.record.id })
            assertEquals(
                "draft",
                pending
                    .single()
                    .record.fields
                    .title(),
            )
        }

    @Test
    fun offset_pagination_pages_and_reports_total() =
        runTest {
            store.upsert("todos", (1..5).map { record("id$it", updatedAt = it.toLong(), title = "t$it") })

            val first = store.page("todos", limit = 2, offset = 0)
            assertEquals(listOf("id1", "id2"), first.items.map { it.id })
            assertEquals(5, first.total)
            assertTrue(first.hasMore)

            val last = store.page("todos", limit = 2, offset = 4)
            assertEquals(listOf("id5"), last.items.map { it.id })
            assertTrue(!last.hasMore, "offset 4 + 1 item == total 5")
            assertEquals(5, store.count("todos"))
        }

    @Test
    fun keyset_pagination_walks_by_id() =
        runTest {
            store.upsert("todos", (1..5).map { record("id$it", updatedAt = it.toLong()) })

            val firstPage = store.pageAfter("todos", afterId = null, limit = 2)
            assertEquals(listOf("id1", "id2"), firstPage.map { it.id })

            val nextPage = store.pageAfter("todos", afterId = firstPage.last().id, limit = 2)
            assertEquals(listOf("id3", "id4"), nextPage.map { it.id })
        }

    @Test
    fun pagination_also_works_on_the_blob_fallback() =
        runTest {
            // "notes" has no adapter -> blob cache path, backed by real .sq queries.
            store.upsert("notes", (1..3).map { record("n$it", updatedAt = it.toLong(), title = "note$it") })

            val page = store.page("notes", limit = 2, offset = 0)
            assertEquals(listOf("n1", "n2"), page.items.map { it.id })
            assertEquals(
                "note1",
                page.items
                    .first()
                    .fields
                    .title(),
            )
            assertEquals(3, page.total)

            val after = store.pageAfter("notes", afterId = "n1", limit = 5)
            assertEquals(listOf("n2", "n3"), after.map { it.id })
            assertEquals(3, store.count("notes"))
        }

    @Test
    fun blob_pagination_excludes_tombstoned_rows() =
        runTest {
            // "notes" has no adapter -> blob cache, where tombstones live as deleted=1 rows.
            store.upsert("notes", (1..3).map { record("n$it", updatedAt = it.toLong()) })
            store.upsert("notes", listOf(record("n2", updatedAt = 9, deleted = true)))

            // A deleted row must never surface in a paged list or the total…
            assertEquals(listOf("n1", "n3"), store.page("notes", limit = 10, offset = 0).items.map { it.id })
            assertEquals(listOf("n1", "n3"), store.pageAfter("notes", afterId = null, limit = 10).map { it.id })
            assertEquals(2, store.count("notes"))
            // …but the tombstone itself stays readable for the sync engine.
            assertEquals(true, store.get("notes", "n2")?.deleted)
        }
}
