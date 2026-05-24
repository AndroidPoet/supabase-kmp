package io.github.androidpoet.supabase.realtime

import io.github.androidpoet.supabase.realtime.models.PresenceState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class RealtimeExtTest {
    @Test
    fun test_broadcastFlow_filtersByEvent() = runTest {
        val sub = FakeSubscription()
        val expectedPayload = buildJsonObject { put("v", 1) }

        sub.emit(RealtimeEvent.Broadcast("other", buildJsonObject { put("v", 0) }))
        sub.emit(RealtimeEvent.Broadcast("message", expectedPayload))

        assertEquals(expectedPayload, sub.broadcastFlow("message").first())
    }

    @Test
    fun test_presenceSyncFlow_emitsState() = runTest {
        val sub = FakeSubscription()
        val expected: PresenceState = mapOf("user1" to buildJsonObject { put("online", true) })

        sub.emit(RealtimeEvent.PresenceSync(expected))

        assertEquals(expected, sub.presenceSyncFlow().first())
    }

    @Test
    fun test_postgresInsertsFlow_emitsInsertRecord() = runTest {
        val sub = FakeSubscription()
        val expected = buildJsonObject { put("id", "1") }

        sub.emit(RealtimeEvent.PostgresInsert(expected))

        assertEquals(expected, sub.postgresInsertsFlow().first())
    }

    @Test
    fun test_presenceDataFlow_decodesTypedPayloads() = runTest {
        val sub = FakeSubscription()
        val expected = listOf(PresenceUser("u1"), PresenceUser("u2"))
        val syncState: PresenceState = mapOf(
            "u1" to buildJsonObject { put("id", "u1") },
            "u2" to buildJsonObject { put("id", "u2") },
        )

        sub.emit(RealtimeEvent.PresenceSync(syncState))

        assertEquals(expected, sub.presenceDataFlow<PresenceUser>().first())
    }

    @Test
    fun test_systemEventFlow_filtersByStatus() = runTest {
        val sub = FakeSubscription()

        sub.emit(RealtimeEvent.SystemEvent(status = "ok", message = "joined"))

        val event = sub.systemEventFlow(status = "ok").first()
        assertEquals("joined", event.message)
    }

    @Test
    fun test_awaitSubscribed_returnsSubscribedStatus() = runTest {
        val sub = FakeSubscription()
        launch {
            sub.updateStatus(RealtimeSubscription.Status.SUBSCRIBED)
        }

        assertEquals(RealtimeSubscription.Status.SUBSCRIBED, sub.awaitSubscribed(timeoutMs = 1_000))
    }
}

@Serializable
private data class PresenceUser(val id: String)

private class FakeSubscription : RealtimeSubscription {
    private val flow = MutableSharedFlow<RealtimeEvent>(replay = 8, extraBufferCapacity = 8)
    private val statusState = MutableStateFlow(RealtimeSubscription.Status.SUBSCRIBING)

    override val channel: String = "room"
    override val status: StateFlow<RealtimeSubscription.Status> =
        statusState

    override fun asFlow(): Flow<RealtimeEvent> = flow

    suspend fun emit(event: RealtimeEvent) {
        flow.emit(event)
    }

    fun updateStatus(newStatus: RealtimeSubscription.Status) {
        statusState.value = newStatus
    }

    override suspend fun unsubscribe() = Unit

    override suspend fun broadcast(event: String, payload: kotlinx.serialization.json.JsonObject) = Unit

    override suspend fun track(state: kotlinx.serialization.json.JsonObject) = Unit

    override suspend fun untrack() = Unit
}
