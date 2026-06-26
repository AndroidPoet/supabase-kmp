package io.github.androidpoet.supabase.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncEngineTest {
    private fun rec(id: String, ts: Long, deleted: Boolean = false) =
        Record(id = id, updatedAt = ts, deleted = deleted, fields = JsonObject(mapOf("v" to JsonPrimitive(ts))))

    @Test
    fun pull_stores_records_and_advances_the_cursor() =
        runTest {
            val local = FakeLocalStore()
            val remote = FakeRemoteSource(PullResult(listOf(rec("a", 10), rec("b", 20)), Cursor(20)))

            val result = SyncEngine(local, remote).sync("todos")

            assertEquals(2, result.pulled)
            assertEquals(rec("a", 10), local.get("todos", "a"))
            assertEquals(Cursor(20), local.cursor("todos"))
        }

    @Test
    fun local_pending_is_pushed_and_cleared_when_accepted() =
        runTest {
            val local = FakeLocalStore()
            local.enqueue("todos", PendingChange(rec("x", 5), ChangeKind.UPSERT))
            val remote = FakeRemoteSource(PullResult(emptyList(), null))

            val result = SyncEngine(local, remote).sync("todos")

            assertEquals(1, result.pushed)
            assertEquals(listOf("x"), remote.pushed.map { it.record.id })
            assertTrue(local.pending("todos").isEmpty())
        }

    @Test
    fun conflict_local_newer_wins_and_is_still_pushed() =
        runTest {
            val local = FakeLocalStore()
            local.enqueue("todos", PendingChange(rec("a", 100), ChangeKind.UPSERT))
            val remote = FakeRemoteSource(PullResult(listOf(rec("a", 50)), Cursor(50)))

            val result = SyncEngine(local, remote).sync("todos")

            assertEquals(100, local.get("todos", "a")?.updatedAt)
            assertEquals(1, result.pushed)
            assertEquals(
                100,
                remote.pushed
                    .single()
                    .record.updatedAt,
            )
        }

    @Test
    fun conflict_remote_newer_wins_and_the_local_change_is_dropped() =
        runTest {
            val local = FakeLocalStore()
            local.enqueue("todos", PendingChange(rec("a", 50), ChangeKind.UPSERT))
            val remote = FakeRemoteSource(PullResult(listOf(rec("a", 100)), Cursor(100)))

            val result = SyncEngine(local, remote).sync("todos")

            assertEquals(100, local.get("todos", "a")?.updatedAt)
            assertEquals(0, result.pushed)
            assertTrue(local.pending("todos").isEmpty())
        }

    @Test
    fun custom_merge_result_is_stored_and_pushed_not_the_stale_local_edit() =
        runTest {
            val merged = rec("a", 70).copy(fields = JsonObject(mapOf("merged" to JsonPrimitive(true))))
            val resolvers = ResolverRegistry().register("todos") { _, _ -> merged }
            val local = FakeLocalStore()
            local.enqueue("todos", PendingChange(rec("a", 60), ChangeKind.UPSERT))
            val remote = FakeRemoteSource(PullResult(listOf(rec("a", 50)), Cursor(50)))

            val result = SyncEngine(local, remote, resolvers).sync("todos")

            assertEquals(merged, local.get("todos", "a")) // merged stored locally
            assertEquals(1, result.pushed)
            assertEquals(merged, remote.pushed.single().record) // and pushed (not the ts=60 edit)
            assertTrue(local.pending("todos").isEmpty())
        }

    @Test
    fun empty_pull_with_null_cursor_keeps_the_existing_cursor() =
        runTest {
            val local = FakeLocalStore()
            local.setCursor("todos", Cursor(99))
            val remote = FakeRemoteSource(PullResult(emptyList(), null))

            SyncEngine(local, remote).sync("todos")

            assertEquals(Cursor(99), local.cursor("todos")) // not reset to null
        }

    @Test
    fun observe_does_not_clobber_a_newer_local_pending_edit() =
        runTest {
            val local = FakeLocalStore()
            local.upsert("todos", listOf(rec("a", 100)))
            local.enqueue("todos", PendingChange(rec("a", 100), ChangeKind.UPSERT))
            val remote = FakeRemoteSource().withChanges(RemoteChange(rec("a", 50)))

            SyncEngine(local, remote).observe("todos").collect {}

            assertEquals(100, local.get("todos", "a")?.updatedAt) // older remote echo ignored
        }
}

private class FakeLocalStore : LocalStore {
    private val tables = mutableMapOf<String, MutableMap<String, Record>>()
    private val cursors = mutableMapOf<String, Cursor?>()
    private val outbox = mutableMapOf<String, MutableList<PendingChange>>()

    override suspend fun upsert(table: String, records: List<Record>) {
        val t = tables.getOrPut(table) { mutableMapOf() }
        records.forEach { t[it.id] = it }
    }

    override suspend fun get(table: String, id: String): Record? = tables[table]?.get(id)

    override suspend fun cursor(table: String): Cursor? = cursors[table]

    override suspend fun setCursor(table: String, cursor: Cursor?) {
        cursors[table] = cursor
    }

    override suspend fun pending(table: String): List<PendingChange> = outbox[table]?.toList() ?: emptyList()

    override suspend fun enqueue(table: String, change: PendingChange) {
        // Mirror the SQLDelight store: apply the row optimistically (unguarded) AND queue it, atomically;
        // the outbox holds one entry per id (latest local intent), like INSERT OR REPLACE on (table, id).
        tables.getOrPut(table) { mutableMapOf() }[change.record.id] = change.record
        val queue = outbox.getOrPut(table) { mutableListOf() }
        queue.removeAll { it.record.id == change.record.id }
        queue.add(change)
    }

    override suspend fun clearPending(table: String, ids: List<String>) {
        outbox[table]?.removeAll { it.record.id in ids }
    }

    private fun live(table: String): List<Record> =
        tables[table]?.values?.filterNot { it.deleted }?.sortedBy { it.id } ?: emptyList()

    override suspend fun page(table: String, limit: Long, offset: Long): Page<Record> {
        val all = live(table)
        return Page(all.drop(offset.toInt()).take(limit.toInt()), offset, limit, all.size.toLong())
    }

    override suspend fun pageAfter(table: String, afterId: String?, limit: Long): List<Record> =
        live(table).filter { afterId == null || it.id > afterId }.take(limit.toInt())

    override suspend fun count(table: String): Long = live(table).size.toLong()
}

private class FakeRemoteSource(
    private vararg val scriptedPulls: PullResult,
) : RemoteSource {
    val pushed = mutableListOf<PendingChange>()
    private var pullIndex = 0
    private var changeFlow: Flow<RemoteChange> = emptyFlow()

    fun withChanges(vararg changes: RemoteChange): FakeRemoteSource {
        changeFlow = flowOf(*changes)
        return this
    }

    override suspend fun pull(table: String, since: Cursor?): PullResult =
        scriptedPulls.getOrNull(pullIndex++) ?: PullResult(emptyList(), since)

    override suspend fun push(table: String, changes: List<PendingChange>): PushResult {
        pushed += changes
        return PushResult(accepted = changes.map { it.record.id })
    }

    override fun changes(table: String): Flow<RemoteChange> = changeFlow
}
