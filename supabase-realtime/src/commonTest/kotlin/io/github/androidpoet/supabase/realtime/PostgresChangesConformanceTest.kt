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
    fun test_realtimeFilter_inOperator_usesParenList() {
        assertEquals("status=in.(open,pending)", realtimeFilter { isIn("status", listOf("open", "pending")) })
    }
}

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
