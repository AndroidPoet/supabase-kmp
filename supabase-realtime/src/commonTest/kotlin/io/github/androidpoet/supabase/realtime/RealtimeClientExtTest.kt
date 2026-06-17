package io.github.androidpoet.supabase.realtime

import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.realtime.models.PostgresChangeEvent
import io.github.androidpoet.supabase.realtime.models.PresenceState
import io.github.androidpoet.supabase.realtime.models.RealtimeChannel
import io.github.androidpoet.supabase.realtime.models.RealtimeMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RealtimeClientExtTest {
    @Test
    fun test_awaitConnected_returnsWhenStateBecomesConnected() =
        runTest {
            val realtime = AwaitFakeRealtimeClient()

            val waiter = async { realtime.awaitConnected() }
            delay(20)
            realtime.emit(ConnectionState.Connected)

            val state = waiter.await()
            assertEquals(ConnectionState.Connected, state)
        }

    @Test
    fun test_awaitDisconnected_returnsWhenStateBecomesDisconnected() =
        runTest {
            val realtime = AwaitFakeRealtimeClient(initial = ConnectionState.Connected)

            val waiter = async { realtime.awaitDisconnected() }
            delay(20)
            realtime.emit(ConnectionState.Disconnected)

            val state = waiter.await()
            assertEquals(ConnectionState.Disconnected, state)
        }

    @Test
    fun test_connectionStateBooleans_reflectCurrentState() =
        runTest {
            val realtime = AwaitFakeRealtimeClient()

            assertEquals(false, realtime.isConnected)
            assertEquals(false, realtime.isConnecting)
            assertEquals(false, realtime.isDisconnecting)

            realtime.emit(ConnectionState.Connecting)
            assertEquals(true, realtime.isConnecting)

            realtime.emit(ConnectionState.Connected)
            assertEquals(true, realtime.isConnected)

            realtime.emit(ConnectionState.Disconnecting)
            assertEquals(true, realtime.isDisconnecting)
        }

    @Test
    fun test_subscribe_appliesConfigureBlock() =
        runTest {
            val realtime = RealtimeClientImpl(ExtFakeSupabaseClient(), RealtimeConfig(autoReconnect = false))

            val subscription =
                realtime.subscribe("room-x") {
                    setPrivate(true)
                    configurePresence("user-1")
                } as ChannelSubscriptionImpl

            assertEquals("room-x", subscription.channel)
            assertTrue(subscription.privateChannel)
            assertEquals("user-1", subscription.presenceKey)
        }

    @Test
    fun test_subscribeToPostgresChanges_registersCallback() =
        runTest {
            val realtime = RealtimeClientImpl(ExtFakeSupabaseClient(), RealtimeConfig(autoReconnect = false))

            val subscription =
                realtime.subscribeToPostgresChanges(
                    channel = "room-pg",
                    schema = "public",
                    table = "messages",
                    event = PostgresChangeEvent.INSERT,
                    callback = {},
                ) as ChannelSubscriptionImpl

            assertEquals(1, subscription.postgresCallbacks.size)
            assertEquals("messages", subscription.postgresCallbacks.first().table)
        }

    @Test
    fun test_subscribeToPostgresChanges_typedCallback_receivesEventType() =
        runTest {
            val realtime = RealtimeClientImpl(ExtFakeSupabaseClient(), RealtimeConfig(autoReconnect = false))
            var capturedEvent: PostgresChangeEvent? = null
            var capturedPayload: JsonObject? = null

            val subscription =
                realtime.subscribeToPostgresChanges(
                    channel = "room-pg-typed",
                    table = "messages",
                    event = PostgresChangeEvent.ALL,
                ) { event, payload ->
                    capturedEvent = event
                    capturedPayload = payload
                } as ChannelSubscriptionImpl

            assertEquals(1, subscription.postgresCallbacks.size)
            assertTrue(subscription.postgresCallbacks.first() is PostgresCallbackConfig.Typed)

            subscription.handleMessage(
                io.github.androidpoet.supabase.realtime.models.RealtimeMessage(
                    topic = "realtime:room-pg-typed",
                    event = "postgres_changes",
                    payload =
                        buildJsonObject {
                            put(
                                "data",
                                buildJsonObject {
                                    put("type", "INSERT")
                                    put("record", buildJsonObject { put("id", "1") })
                                    put("old_record", buildJsonObject {})
                                },
                            )
                        },
                    ref = "1",
                ),
            )

            assertEquals(PostgresChangeEvent.INSERT, capturedEvent)
            assertEquals("1", capturedPayload?.get("id")?.jsonPrimitive?.content)
        }

    @Test
    fun test_subscribeToBroadcast_registersEventCallback() =
        runTest {
            val realtime = RealtimeClientImpl(ExtFakeSupabaseClient(), RealtimeConfig(autoReconnect = false))
            var called = false

            val subscription =
                realtime.subscribeToBroadcast(
                    channel = "room-b",
                    event = "message",
                    callback = { called = true },
                ) as ChannelSubscriptionImpl

            subscription.handleMessage(
                io.github.androidpoet.supabase.realtime.models.RealtimeMessage(
                    topic = "realtime:room-b",
                    event = "broadcast",
                    payload =
                        buildJsonObject {
                            put("event", "message")
                            put("payload", buildJsonObject { put("v", 1) })
                        },
                    ref = "1",
                ),
            )

            assertTrue(called)
        }

    @Test
    fun test_subscribeToPresence_registersPresenceHandler() =
        runTest {
            val realtime = RealtimeClientImpl(ExtFakeSupabaseClient(), RealtimeConfig(autoReconnect = false))
            var stateSize = -1

            val subscription =
                realtime.subscribeToPresence(
                    channel = "room-presence",
                    callback = { state: PresenceState -> stateSize = state.size },
                ) as ChannelSubscriptionImpl

            subscription.handleMessage(
                io.github.androidpoet.supabase.realtime.models.RealtimeMessage(
                    topic = "realtime:room-presence",
                    event = "presence_state",
                    payload =
                        buildJsonObject {
                            put(
                                "user-1",
                                buildJsonObject {
                                    put("metas", kotlinx.serialization.json.buildJsonArray {})
                                },
                            )
                        },
                    ref = "1",
                ),
            )

            assertEquals(1, stateSize)
        }

    @Test
    fun test_sendHeartbeat_recordsDebugEventAndState() =
        runTest {
            val realtime = RealtimeClientImpl(ExtFakeSupabaseClient(), RealtimeConfig(autoReconnect = false))

            val heartbeat =
                async {
                    realtime.debugEvents.filterIsInstance<RealtimeDebugEvent.HeartbeatSent>().first()
                }
            yield()
            realtime.sendHeartbeat()

            assertEquals("1", heartbeat.await().ref)
            assertEquals(1, realtime.debugState.value.outboundMessageCount)
            assertEquals(1, realtime.debugState.value.heartbeatSentCount)
            assertEquals("1", realtime.debugState.value.lastOutboundRef)
        }

    @Test
    fun test_sendMessage_dropsOldestAndSignalsWhenOfflineBufferFull() =
        runTest {
            val realtime = RealtimeClientImpl(ExtFakeSupabaseClient(), RealtimeConfig(autoReconnect = false))

            // Never connected -> the socket is null, so application messages buffer
            // offline instead of being sent.
            val dropped =
                async {
                    realtime.debugEvents
                        .filterIsInstance<RealtimeDebugEvent.OutboundMessageDropped>()
                        .first()
                }
            yield()

            // Fill the 100-message buffer, then send one more to force an eviction.
            // Yield each iteration so the debug collector drains events as they are
            // emitted instead of overflowing the SharedFlow's replay buffer.
            repeat(101) { i ->
                realtime.sendMessage(
                    RealtimeMessage(
                        topic = "realtime:test",
                        event = "broadcast",
                        payload = buildJsonObject { put("i", i) },
                        ref = i.toString(),
                    ),
                )
                yield()
            }

            val event = dropped.await()
            // The oldest message (ref "0") is the one discarded.
            assertEquals("0", event.message.ref)
            assertEquals(100, event.capacity)
        }
}

private class ExtFakeSupabaseClient : SupabaseClient {
    override val projectUrl: String = "https://example.supabase.co"
    override val apiKey: String = "anon"
    override val accessTokenOrNull: String? = null

    override suspend fun get(
        endpoint: String,
        queryParams: List<Pair<String, String>>,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun post(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun put(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun patch(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun delete(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun postRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun putRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

    override fun setAccessToken(token: String) = Unit

    override fun clearAccessToken() = Unit

    override suspend fun rawRequest(
        method: io.github.androidpoet.supabase.client.SupabaseHttpMethod,
        url: String,
        body: ByteArray?,
        contentType: String?,
        headers: Map<String, String>,
    ): io.github.androidpoet.supabase.core.result.SupabaseResult<io.github.androidpoet.supabase.client.SupabaseHttpResponse> =
        io.github.androidpoet.supabase.core.result.SupabaseResult.Failure(
            io.github.androidpoet.supabase.core.result
                .SupabaseError("rawRequest not supported in test fake"),
        )

    override fun close() = Unit
}

private class AwaitFakeRealtimeClient(
    initial: ConnectionState = ConnectionState.Disconnected,
) : RealtimeClient {
    private val state = MutableStateFlow(initial)
    override val connectionState: StateFlow<ConnectionState> = state
    override val debugState: StateFlow<RealtimeDebugState> = MutableStateFlow(RealtimeDebugState())
    override val debugEvents: Flow<RealtimeDebugEvent> = emptyFlow()
    override val isConnected: Boolean
        get() = state.value is ConnectionState.Connected
    override val isConnecting: Boolean
        get() = state.value is ConnectionState.Connecting
    override val isDisconnecting: Boolean
        get() = state.value is ConnectionState.Disconnecting

    override fun channel(name: String): RealtimeChannelBuilder = error("not used")

    override fun getSubscription(name: String): RealtimeSubscription? = null

    override fun getSubscriptionByTopic(topic: String): RealtimeSubscription? = null

    override fun getSubscriptions(): Set<RealtimeSubscription> = emptySet()

    override fun activeChannels(): Set<String> = emptySet()

    override fun activeChannelDetails(): Set<RealtimeChannel> = emptySet()

    override suspend fun removeSubscription(subscription: RealtimeSubscription) = Unit

    override suspend fun removeSubscriptions(subscriptions: List<RealtimeSubscription>) = Unit

    @Suppress("DEPRECATION")
    @Deprecated("Test override", ReplaceWith(""), level = DeprecationLevel.HIDDEN)
    override suspend fun removeChannel(subscription: RealtimeSubscription) = Unit

    override suspend fun removeSubscriptionByTopic(topic: String) = Unit

    override suspend fun removeChannelsByTopic(topics: List<String>) = Unit

    override suspend fun removeChannel(name: String) = Unit

    override suspend fun removeAllChannels() = Unit

    override suspend fun setAuth(token: String?) = Unit

    override suspend fun sendHeartbeat() = Unit

    override suspend fun connect() = Unit

    override suspend fun disconnect() = Unit

    override suspend fun close() = Unit

    fun emit(value: ConnectionState) {
        state.value = value
    }
}
