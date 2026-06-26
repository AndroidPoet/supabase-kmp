package io.github.androidpoet.supabase.sync.remote

import io.github.androidpoet.supabase.core.models.QueryBuilder
import io.github.androidpoet.supabase.core.models.WhereBuilder
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.database.CountOption
import io.github.androidpoet.supabase.database.DatabaseClient
import io.github.androidpoet.supabase.database.ExplainOptions
import io.github.androidpoet.supabase.database.PostgrestRange
import io.github.androidpoet.supabase.database.ResponseFormat
import io.github.androidpoet.supabase.database.ReturnOption
import io.github.androidpoet.supabase.database.UpsertResolution
import io.github.androidpoet.supabase.realtime.ConnectionState
import io.github.androidpoet.supabase.realtime.RealtimeChannelBuilder
import io.github.androidpoet.supabase.realtime.RealtimeClient
import io.github.androidpoet.supabase.realtime.RealtimeDebugEvent
import io.github.androidpoet.supabase.realtime.RealtimeDebugState
import io.github.androidpoet.supabase.realtime.RealtimeSubscription
import io.github.androidpoet.supabase.realtime.models.RealtimeChannel
import io.github.androidpoet.supabase.sync.ChangeKind
import io.github.androidpoet.supabase.sync.Cursor
import io.github.androidpoet.supabase.sync.PendingChange
import io.github.androidpoet.supabase.sync.Record
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
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
import kotlin.test.assertTrue

/**
 * Exercises the real [SupabaseRemoteSource] pull/push logic against a fake [DatabaseClient] — no
 * live Supabase. The live PostgREST/Realtime path (including `changes`) is covered by the e2e
 * harness; this proves the wiring: cursor keyset request, row mapping, and the upsert body shape.
 */
class SupabaseRemoteSourceTest {
    @Test
    fun pull_maps_rows_and_advances_cursor() =
        runTest {
            val db =
                FakeDatabaseClient(
                    selectBody = """[{"id":"a","title":"x","updated_at":10,"deleted":false},
                             {"id":"b","title":"y","updated_at":20,"deleted":false}]""",
                )
            val remote = SupabaseRemoteSource(db, FakeRealtimeClient())

            val result = remote.pull("notes", since = null)

            assertEquals("notes", db.lastSelectTable)
            assertEquals(listOf("a", "b"), result.changed.map { it.id })
            assertEquals(
                setOf("id", "title"),
                result.changed
                    .first()
                    .fields.keys,
            ) // metadata stripped
            assertEquals(Cursor(20, "b"), result.nextCursor)
        }

    @Test
    fun pull_empty_does_not_advance_cursor() =
        runTest {
            val remote = SupabaseRemoteSource(FakeDatabaseClient(selectBody = "[]"), FakeRealtimeClient())
            val result = remote.pull("notes", since = Cursor(5, "a"))
            assertTrue(result.changed.isEmpty())
            assertEquals(null, result.nextCursor)
        }

    @Test
    fun push_bulk_upserts_with_conflict_on_id() =
        runTest {
            val db = FakeDatabaseClient(selectBody = "[]")
            val remote = SupabaseRemoteSource(db, FakeRealtimeClient())

            val change =
                PendingChange(
                    Record("a", updatedAt = 7, deleted = false, fields = row("a", "hi")),
                    ChangeKind.UPSERT,
                )
            val result = remote.push("notes", listOf(change))

            assertEquals(listOf("a"), result.accepted)
            assertTrue(db.lastInsertUpsert)
            assertEquals("id", db.lastInsertOnConflict)
            val sent =
                Json
                    .parseToJsonElement(db.lastInsertBody!!)
                    .jsonArray
                    .single()
                    .jsonObject
            assertEquals("a", sent["id"]?.jsonPrimitive?.content)
            assertEquals(7L, sent["updated_at"]?.jsonPrimitive?.long)
            assertEquals(false, sent["deleted"]?.jsonPrimitive?.boolean)
        }

    @Test
    fun push_propagates_soft_delete_tombstone() =
        runTest {
            val db = FakeDatabaseClient(selectBody = "[]")
            val remote = SupabaseRemoteSource(db, FakeRealtimeClient())

            val change =
                PendingChange(
                    Record("a", updatedAt = 9, deleted = true, fields = row("a", "hi")),
                    ChangeKind.DELETE,
                )
            remote.push("notes", listOf(change))

            val sent =
                Json
                    .parseToJsonElement(db.lastInsertBody!!)
                    .jsonArray
                    .single()
                    .jsonObject
            assertEquals(true, sent["deleted"]?.jsonPrimitive?.boolean)
        }

    @Test
    fun push_empty_is_a_noop() =
        runTest {
            val db = FakeDatabaseClient(selectBody = "[]")
            val remote = SupabaseRemoteSource(db, FakeRealtimeClient())
            val result = remote.push("notes", emptyList())
            assertTrue(result.accepted.isEmpty())
            assertEquals(null, db.lastInsertBody) // never called the server
        }

    private fun row(id: String, title: String): JsonObject =
        JsonObject(mapOf("id" to JsonPrimitive(id), "title" to JsonPrimitive(title)))
}

/** Captures the calls [SupabaseRemoteSource] makes; returns canned bodies. Unused ops throw. */
private class FakeDatabaseClient(
    private val selectBody: String,
) : DatabaseClient {
    var lastSelectTable: String? = null
    var lastInsertBody: String? = null
    var lastInsertUpsert: Boolean = false
    var lastInsertOnConflict: String? = null

    override suspend fun select(
        table: String,
        schema: String?,
        columns: String,
        format: ResponseFormat,
        count: CountOption?,
        stripNulls: Boolean,
        explain: ExplainOptions?,
        retry: Boolean,
        headers: Map<String, String>,
        block: QueryBuilder.() -> Unit,
    ): SupabaseResult<String> {
        lastSelectTable = table
        QueryBuilder().apply(block) // run the builder so the keyset lambda is exercised
        return SupabaseResult.Success(selectBody)
    }

    override suspend fun insert(
        table: String,
        schema: String?,
        body: String,
        columns: List<String>?,
        upsert: Boolean,
        upsertResolution: UpsertResolution,
        defaultToNull: Boolean,
        onConflict: String?,
        returning: ReturnOption,
        count: CountOption?,
        stripNulls: Boolean,
        rollback: Boolean,
        contentType: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastInsertBody = body
        lastInsertUpsert = upsert
        lastInsertOnConflict = onConflict
        return SupabaseResult.Success("")
    }

    override suspend fun selectCount(
        table: String,
        schema: String?,
        columns: String,
        count: CountOption,
        headers: Map<String, String>,
        block: QueryBuilder.() -> Unit,
    ): SupabaseResult<PostgrestRange> = error("unused")

    override suspend fun selectRange(
        table: String,
        schema: String?,
        columns: String,
        format: ResponseFormat,
        count: CountOption,
        stripNulls: Boolean,
        headers: Map<String, String>,
        block: QueryBuilder.() -> Unit,
    ): SupabaseResult<Pair<String, PostgrestRange>> = error("unused")

    override suspend fun update(
        table: String,
        schema: String?,
        body: String,
        returning: ReturnOption,
        count: CountOption?,
        stripNulls: Boolean,
        rollback: Boolean,
        maxAffected: Int?,
        explain: ExplainOptions?,
        headers: Map<String, String>,
        block: WhereBuilder.() -> Unit,
    ): SupabaseResult<String> = error("unused")

    override suspend fun replace(
        table: String,
        body: String,
        returning: ReturnOption,
        columns: String,
        block: WhereBuilder.() -> Unit,
    ): SupabaseResult<String> = error("unused")

    override suspend fun delete(
        table: String,
        schema: String?,
        returning: ReturnOption,
        count: CountOption?,
        stripNulls: Boolean,
        rollback: Boolean,
        maxAffected: Int?,
        explain: ExplainOptions?,
        headers: Map<String, String>,
        block: WhereBuilder.() -> Unit,
    ): SupabaseResult<String> = error("unused")

    override suspend fun rpc(
        function: String,
        schema: String?,
        params: String?,
        format: ResponseFormat,
        count: CountOption?,
        stripNulls: Boolean,
        rollback: Boolean,
        maxAffected: Int?,
        explain: ExplainOptions?,
        contentType: String,
        headers: Map<String, String>,
        block: QueryBuilder.() -> Unit,
    ): SupabaseResult<String> = error("unused")

    override suspend fun rpcGet(
        function: String,
        schema: String?,
        queryParams: List<Pair<String, String>>,
        format: ResponseFormat,
        count: CountOption?,
        stripNulls: Boolean,
        explain: ExplainOptions?,
        retry: Boolean,
        headers: Map<String, String>,
    ): SupabaseResult<String> = error("unused")
}

/** Minimal stand-in: only constructed by the source under test; pull/push never call it. */
private class FakeRealtimeClient : RealtimeClient {
    override val connectionState: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Disconnected)
    override val debugState: StateFlow<RealtimeDebugState> = MutableStateFlow(RealtimeDebugState())
    override val debugEvents: Flow<RealtimeDebugEvent> = emptyFlow()
    override val isConnected: Boolean = false
    override val isConnecting: Boolean = false
    override val isDisconnecting: Boolean = false

    override fun channel(name: String): RealtimeChannelBuilder = error("unused")

    override fun getSubscription(name: String): RealtimeSubscription? = null

    override fun getSubscriptionByTopic(topic: String): RealtimeSubscription? = null

    override fun getSubscriptions(): Set<RealtimeSubscription> = emptySet()

    override fun activeChannels(): Set<String> = emptySet()

    override fun activeChannelDetails(): Set<RealtimeChannel> = emptySet()

    override suspend fun removeSubscription(subscription: RealtimeSubscription) = Unit

    override suspend fun removeSubscriptions(subscriptions: List<RealtimeSubscription>) = Unit

    @Deprecated("Use removeSubscription instead", ReplaceWith("removeSubscription(subscription)"))
    override suspend fun removeChannel(subscription: RealtimeSubscription) = Unit

    override suspend fun removeSubscriptionByTopic(topic: String) = Unit

    override suspend fun removeChannelsByTopic(topics: List<String>) = Unit

    override suspend fun removeChannel(name: String) = Unit

    override suspend fun removeAllChannels() = Unit

    override suspend fun setAuth(token: String?) = Unit

    override suspend fun sendHeartbeat() = Unit

    override suspend fun connect() = Unit

    override suspend fun broadcast(
        channel: String,
        event: String,
        payload: JsonObject,
        private: Boolean,
    ): SupabaseResult<Unit> = SupabaseResult.Success(Unit)

    override suspend fun disconnect() = Unit

    override suspend fun close() = Unit
}
