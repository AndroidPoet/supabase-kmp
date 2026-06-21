package io.github.androidpoet.supabase.realtime

import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.client.SupabaseHttpMethod
import io.github.androidpoet.supabase.client.SupabaseHttpResponse
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.realtime.models.PostgresChangeEvent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
private data class Message(
    val id: Int,
    val roomId: Int,
    val body: String,
)

/**
 * Conformance tests for the `postgres_changes` binding config the SDK puts in the
 * `phx_join` payload. The Realtime protocol expects each binding to carry
 * `event` / `schema` / `table` / `filter`, where the filter is a single
 * `column=operator.value` string limited to the operators eq, neq, gt, gte, lt,
 * lte, in.
 *
 * Reference: https://supabase.com/docs/guides/realtime/protocol and
 * https://supabase.com/docs/guides/realtime/postgres-changes
 */
class PostgresChangesConformanceTest {
    private fun realtime() = RealtimeClientImpl(FakeClient(), RealtimeConfig(autoReconnect = false))

    @Test
    fun test_typedChange_withRealtimeFilter_propagatesBindingConfig() =
        runTest {
            val subscription =
                realtime()
                    .channel("room-1")
                    .onPostgresChange<Message>(
                        schema = "public",
                        table = "messages",
                        filter = realtimeFilter { eq("room_id", 5) },
                        event = PostgresChangeEvent.INSERT,
                    ) { }
                    .subscribe()

            val binding = (subscription as ChannelSubscriptionImpl).postgresCallbacks.single()
            assertEquals("public", binding.schema)
            assertEquals("messages", binding.table)
            assertEquals("room_id=eq.5", binding.filter)
            assertEquals(PostgresChangeEvent.INSERT, binding.event)
        }

    @Test
    fun test_change_withoutTableOrFilter_leavesThemNull() =
        runTest {
            val subscription =
                realtime()
                    .channel("schema-wide")
                    .onPostgresChange(schema = "public") { }
                    .subscribe()

            val binding = (subscription as ChannelSubscriptionImpl).postgresCallbacks.single()
            assertEquals("public", binding.schema)
            assertEquals(null, binding.table)
            assertEquals(null, binding.filter)
            assertEquals(PostgresChangeEvent.ALL, binding.event)
        }

    @Test
    fun test_phxError_marksChannelErroredAndDoesNotThrow() =
        runTest {
            val subscription = realtime().channel("room-1").subscribe() as ChannelSubscriptionImpl

            // A channel-level phx_error must move the subscription to ERROR. The rejoin
            // it schedules is a no-op here (no live socket), so the handler returns cleanly.
            subscription.handleMessage(
                io.github.androidpoet.supabase.realtime.models.RealtimeMessage(
                    topic = "realtime:room-1",
                    event = "phx_error",
                    payload = kotlinx.serialization.json.buildJsonObject {},
                ),
            )

            assertEquals(RealtimeSubscription.Status.ERROR, subscription.status.value)
        }

    @Test
    fun test_realtimeFilter_inOperator_usesParenList() {
        assertEquals("status=in.(open,pending)", realtimeFilter { isIn("status", listOf("open", "pending")) })
    }

    @Test
    fun test_joinReply_capturesMatchingBinding_routesChangeById() =
        runTest {
            var invoked = false
            val subscription =
                realtime()
                    .channel("room-1")
                    .onPostgresChange(schema = "public", table = "messages", event = PostgresChangeEvent.INSERT) {
                        invoked = true
                    }.subscribe() as ChannelSubscriptionImpl

            // Join reply echoes a binding whose attributes MATCH the local config → id 77 is captured.
            subscription.handleMessage(replyWithBinding(subscription.joinRef, id = 77, event = "INSERT"))
            subscription.handleMessage(insertChange(ids = listOf(77)))

            assertEquals(true, invoked)
        }

    @Test
    fun test_joinReply_attributeMismatch_doesNotCaptureBinding_noMisroute() =
        runTest {
            var invoked = false
            val subscription =
                realtime()
                    .channel("room-1")
                    .onPostgresChange(schema = "public", table = "messages", event = PostgresChangeEvent.INSERT) {
                        invoked = true
                    }.subscribe() as ChannelSubscriptionImpl

            // The server echoes a DIFFERENT event (UPDATE) at this binding's index. Without the
            // attribute check, id 77 would be paired by index to the INSERT callback, so an UPDATE
            // tagged id 77 would wrongly fire an INSERT-only subscriber. The check prevents capture,
            // and the UPDATE then can't match the INSERT binding via the type fallback either.
            subscription.handleMessage(replyWithBinding(subscription.joinRef, id = 77, event = "UPDATE"))
            subscription.handleMessage(updateChange(ids = listOf(77)))

            assertEquals(false, invoked)
        }
}

private fun replyWithBinding(joinRef: String?, id: Long, event: String) =
    io.github.androidpoet.supabase.realtime.models.RealtimeMessage(
        topic = "realtime:room-1",
        event = "phx_reply",
        ref = joinRef,
        payload =
            kotlinx.serialization.json.buildJsonObject {
                put("status", kotlinx.serialization.json.JsonPrimitive("ok"))
                put(
                    "response",
                    kotlinx.serialization.json.buildJsonObject {
                        put(
                            "postgres_changes",
                            kotlinx.serialization.json.buildJsonArray {
                                add(
                                    kotlinx.serialization.json.buildJsonObject {
                                        put("id", kotlinx.serialization.json.JsonPrimitive(id))
                                        put("event", kotlinx.serialization.json.JsonPrimitive(event))
                                        put("schema", kotlinx.serialization.json.JsonPrimitive("public"))
                                        put("table", kotlinx.serialization.json.JsonPrimitive("messages"))
                                    },
                                )
                            },
                        )
                    },
                )
            },
    )

private fun insertChange(ids: List<Long>) = postgresChange("INSERT", ids)

private fun updateChange(ids: List<Long>) = postgresChange("UPDATE", ids)

private fun postgresChange(type: String, ids: List<Long>) =
    io.github.androidpoet.supabase.realtime.models.RealtimeMessage(
        topic = "realtime:room-1",
        event = "postgres_changes",
        payload =
            kotlinx.serialization.json.buildJsonObject {
                put(
                    "ids",
                    kotlinx.serialization.json.buildJsonArray { ids.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } },
                )
                put(
                    "data",
                    kotlinx.serialization.json.buildJsonObject {
                        put("type", kotlinx.serialization.json.JsonPrimitive(type))
                        put("schema", kotlinx.serialization.json.JsonPrimitive("public"))
                        put("table", kotlinx.serialization.json.JsonPrimitive("messages"))
                        put("record", kotlinx.serialization.json.buildJsonObject { put("id", kotlinx.serialization.json.JsonPrimitive(1)) })
                    },
                )
            },
    )

private class FakeClient : SupabaseClient {
    override val projectUrl: String = "https://example.supabase.co"
    override val apiKey: String = "anon"
    override val accessTokenOrNull: String? = null

    override suspend fun get(
        endpoint: String,
        queryParams: List<Pair<String, String>>,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Success("{}")

    override suspend fun post(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Success("{}")

    override suspend fun put(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Success("{}")

    override suspend fun patch(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Success("{}")

    override suspend fun delete(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Success("{}")

    override suspend fun postRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Success("{}")

    override suspend fun putRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Success("{}")

    override suspend fun rawRequest(
        method: SupabaseHttpMethod,
        url: String,
        body: ByteArray?,
        contentType: String?,
        headers: Map<String, String>,
    ): SupabaseResult<SupabaseHttpResponse> = SupabaseResult.Failure(SupabaseError("not used"))

    override fun setAccessToken(token: String) = Unit

    override fun clearAccessToken() = Unit

    override fun close() = Unit
}
