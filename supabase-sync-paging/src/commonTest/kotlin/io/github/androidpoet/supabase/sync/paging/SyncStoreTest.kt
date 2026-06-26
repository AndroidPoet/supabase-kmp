package io.github.androidpoet.supabase.sync.paging

import io.github.androidpoet.supabase.sync.ChangeKind
import io.github.androidpoet.supabase.sync.Cursor
import io.github.androidpoet.supabase.sync.LocalStore
import io.github.androidpoet.supabase.sync.Page
import io.github.androidpoet.supabase.sync.PendingChange
import io.github.androidpoet.supabase.sync.PullResult
import io.github.androidpoet.supabase.sync.PushResult
import io.github.androidpoet.supabase.sync.Record
import io.github.androidpoet.supabase.sync.RemoteChange
import io.github.androidpoet.supabase.sync.RemoteSource
import io.github.androidpoet.supabase.sync.SyncEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
private data class Note(
    val id: String,
    val text: String,
)

class SyncStoreTest {
    private fun testEnv(): Triple<MemLocalStore, SyncEngine, MemRemote> {
        val local = MemLocalStore()
        val remote = MemRemote()
        return Triple(local, SyncEngine(local, remote), remote)
    }

    @Test
    fun upsert_then_get_and_observe_reflect_the_value() =
        runTest {
            val (local, engine, _) = testEnv()
            var clock = 1L
            val notes = SyncStore("notes", local, engine, Note.serializer(), { it.id }, backgroundScope, { clock++ })

            notes.upsert(Note("1", "hello"))

            assertEquals("hello", notes.get("1")?.text)
            assertEquals(Note("1", "hello"), notes.observe("1").first())
        }

    @Test
    fun delete_tombstones_so_get_returns_null() =
        runTest {
            val (local, engine, _) = testEnv()
            var clock = 1L
            val notes = SyncStore("notes", local, engine, Note.serializer(), { it.id }, backgroundScope, { clock++ })

            notes.upsert(Note("1", "hello"))
            notes.delete("1")

            assertNull(notes.get("1"))
            assertEquals(0L, local.count("notes"))
        }

    @Test
    fun upsert_queues_the_change_to_the_outbox() =
        runTest {
            val (local, engine, _) = testEnv()
            var clock = 1L
            val notes = SyncStore("notes", local, engine, Note.serializer(), { it.id }, backgroundScope, { clock++ })

            notes.upsert(Note("1", "hello"))

            assertTrue(local.pending("notes").any { it.record.id == "1" && it.kind == ChangeKind.UPSERT })
        }

    @Test
    fun pullPage_reports_no_more_when_the_remote_is_drained() =
        runTest {
            // This is what keeps pagedSynced() from looping: an empty page ends pagination.
            val (_, engine, _) = testEnv()
            assertFalse(engine.pullPage("notes").hasMore)
        }

    @Test
    fun pullPage_drains_pages_until_empty() =
        runTest {
            val (local, engine, remote) = testEnv()
            remote.seed("notes", List(3) { Record("$it", it.toLong(), false, fieldsOf("$it")) })

            val first = engine.pullPage("notes")
            assertEquals(3, first.pulled)
            assertTrue(first.hasMore)
            // Second page is empty → drained → stop.
            assertFalse(engine.pullPage("notes").hasMore)
            assertEquals(3L, local.count("notes"))
        }

    @Test
    fun sync_drains_every_remote_page_in_one_call() =
        runTest {
            // Remote paginates 2 rows at a time; a single sync() must pull all 5, not just page 1.
            val local = MemLocalStore()
            val remote = MemRemote(pageSize = 2)
            remote.seed("notes", List(5) { Record("id$it", it.toLong(), false, fieldsOf("id$it")) })
            val engine = SyncEngine(local, remote)

            val result = engine.sync("notes")

            assertEquals(5, result.pulled)
            assertEquals(5L, local.count("notes"))
        }
}

private fun fieldsOf(id: String) =
    DefaultJson.encodeToJsonElement(Note.serializer(), Note(id, "n$id")) as kotlinx.serialization.json.JsonObject

/** Minimal in-memory [LocalStore] for tests — mirrors the SQLDelight store's contract. */
private class MemLocalStore : LocalStore {
    private val rows = mutableMapOf<String, MutableMap<String, Record>>()
    private val cursors = mutableMapOf<String, Cursor?>()
    private val outbox = mutableMapOf<String, MutableList<PendingChange>>()

    override suspend fun upsert(table: String, records: List<Record>) {
        val t = rows.getOrPut(table) { mutableMapOf() }
        records.forEach { t[it.id] = it }
    }

    override suspend fun get(table: String, id: String): Record? = rows[table]?.get(id)

    override suspend fun cursor(table: String): Cursor? = cursors[table]

    override suspend fun setCursor(table: String, cursor: Cursor?) {
        cursors[table] = cursor
    }

    override suspend fun pending(table: String): List<PendingChange> = outbox[table]?.toList() ?: emptyList()

    override suspend fun enqueue(table: String, change: PendingChange) {
        // Mirror the SQLDelight store: apply the row optimistically (unguarded) AND queue it, atomically;
        // the outbox holds one entry per id (latest local intent), like INSERT OR REPLACE on (table, id).
        rows.getOrPut(table) { mutableMapOf() }[change.record.id] = change.record
        val queue = outbox.getOrPut(table) { mutableListOf() }
        queue.removeAll { it.record.id == change.record.id }
        queue.add(change)
    }

    override suspend fun clearPending(table: String, ids: List<String>) {
        outbox[table]?.removeAll { it.record.id in ids }
    }

    private fun live(table: String) = rows[table]?.values?.filterNot { it.deleted }?.sortedBy { it.id } ?: emptyList()

    override suspend fun page(table: String, limit: Long, offset: Long): Page<Record> {
        val all = live(table)
        return Page(all.drop(offset.toInt()).take(limit.toInt()), offset, limit, all.size.toLong())
    }

    override suspend fun pageAfter(table: String, afterId: String?, limit: Long): List<Record> =
        live(table).filter { afterId == null || it.id > afterId }.take(limit.toInt())

    override suspend fun count(table: String): Long = live(table).size.toLong()
}

/** In-memory [RemoteSource] that serves seeded rows in keyset pages of [pageSize]. */
private class MemRemote(
    private val pageSize: Int = Int.MAX_VALUE,
) : RemoteSource {
    private val seeded = mutableMapOf<String, List<Record>>()

    fun seed(table: String, records: List<Record>) {
        seeded[table] = records.sortedWith(compareBy({ it.updatedAt }, { it.id }))
    }

    override suspend fun pull(table: String, since: Cursor?): PullResult {
        val all = seeded[table].orEmpty()
        val rows =
            all
                .filter { since == null || it.updatedAt > since.updatedAt || (it.updatedAt == since.updatedAt && it.id > since.id) }
                .take(pageSize)
        val next = rows.lastOrNull()?.let { Cursor(it.updatedAt, it.id) }
        return PullResult(rows, next)
    }

    override suspend fun push(table: String, changes: List<PendingChange>): PushResult =
        PushResult(accepted = changes.map { it.record.id })

    override fun changes(table: String) = kotlinx.coroutines.flow.emptyFlow<RemoteChange>()
}
