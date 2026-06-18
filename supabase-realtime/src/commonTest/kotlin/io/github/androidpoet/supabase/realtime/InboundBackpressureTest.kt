package io.github.androidpoet.supabase.realtime

import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.client.SupabaseHttpMethod
import io.github.androidpoet.supabase.client.SupabaseHttpResponse
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Backpressure tests for the inbound delivery path. A stalled collector must never
 * suspend the WebSocket read loop: inbound events are published with a bounded,
 * DROP_OLDEST [kotlinx.coroutines.flow.MutableSharedFlow], so a slow collector
 * loses oldest events rather than stalling the connection. Each such drop is
 * surfaced as a [RealtimeDebugEvent.InboundEventDropped] on the debug channel,
 * mirroring the outbound [RealtimeDebugEvent.OutboundMessageDropped] semantics.
 *
 * Inbound frames are injected directly via the internal [ChannelSubscriptionImpl.handleMessage]
 * (there is no MockEngine in commonTest to drive a real WebSocket), so these tests
 * exercise the publish/buffer semantics rather than the socket read loop itself.
 */
class InboundBackpressureTest {
    private fun realtime() = RealtimeClientImpl(BackpressureFakeClient(), RealtimeConfig(autoReconnect = false))

    @Test
    fun test_stalledCollector_dropsOldestAndReportsDebugEvent() =
        runTest {
            val client = realtime()
            val subscription = client.channel("room-1").subscribe() as ChannelSubscriptionImpl

            // Record inbound-drop debug events as they are reported.
            val drops = mutableListOf<RealtimeDebugEvent.InboundEventDropped>()
            backgroundScope.launch {
                client.debugEvents
                    .filterIsInstance<RealtimeDebugEvent.InboundEventDropped>()
                    .collect { drops += it }
            }

            // A collector that consumes the first event then stalls forever. This
            // keeps a subscriber attached (so the buffer is live) but stops draining,
            // forcing DROP_OLDEST eviction once the bounded buffer fills.
            val stall = CompletableDeferred<Unit>()
            backgroundScope.launch {
                subscription.asFlow().collect { stall.await() }
            }
            // Let both collectors subscribe before we publish.
            runCurrent()

            // Flood far past the bounded buffer capacity (~64). tryEmit must never
            // suspend, so this loop completes without the read loop ever blocking.
            repeat(500) { index ->
                subscription.handleMessage(broadcastMessage(index))
            }
            runCurrent()

            assertTrue(
                drops.isNotEmpty(),
                "A stalled collector should cause oldest inbound events to be dropped and reported",
            )
            assertTrue(
                drops.all { it.topic == "realtime:room-1" },
                "Reported drops should carry the subscription's topic",
            )
        }

    @Test
    fun test_noCollector_doesNotReportDrops() =
        runTest {
            val client = realtime()
            val subscription = client.channel("room-1").subscribe() as ChannelSubscriptionImpl

            val drops = mutableListOf<RealtimeDebugEvent.InboundEventDropped>()
            backgroundScope.launch {
                client.debugEvents
                    .filterIsInstance<RealtimeDebugEvent.InboundEventDropped>()
                    .collect { drops += it }
            }
            runCurrent()

            // With no collector on asFlow(), the SharedFlow retains nothing (replay = 0):
            // events are simply not delivered, and that is not a "drop" worth reporting.
            repeat(500) { index ->
                subscription.handleMessage(broadcastMessage(index))
            }
            runCurrent()

            assertTrue(drops.isEmpty(), "No drops should be reported when there is no active collector")
        }

    private fun broadcastMessage(index: Int) =
        io.github.androidpoet.supabase.realtime.models.RealtimeMessage(
            topic = "realtime:room-1",
            event = "broadcast",
            payload =
                buildJsonObject {
                    put("event", JsonPrimitive("ping"))
                    put("payload", buildJsonObject { put("n", JsonPrimitive(index)) })
                },
        )
}

private class BackpressureFakeClient : SupabaseClient {
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
