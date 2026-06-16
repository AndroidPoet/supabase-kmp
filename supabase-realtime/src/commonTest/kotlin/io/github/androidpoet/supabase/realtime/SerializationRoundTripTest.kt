package io.github.androidpoet.supabase.realtime

import io.github.androidpoet.supabase.realtime.models.BroadcastPayload
import io.github.androidpoet.supabase.realtime.models.PostgresChange
import io.github.androidpoet.supabase.realtime.models.PostgresChangeEvent
import io.github.androidpoet.supabase.realtime.models.RealtimeMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializationRoundTripTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun test_realtimeMessage_decodesFromWireJson() {
        val payload =
            """
            {
              "topic": "realtime:public:messages",
              "event": "phx_reply",
              "payload": { "status": "ok", "response": {} },
              "join_ref": "1",
              "ref": "2"
            }
            """.trimIndent()

        val message = json.decodeFromString<RealtimeMessage>(payload)

        assertEquals("realtime:public:messages", message.topic)
        assertEquals("phx_reply", message.event)
        assertEquals("1", message.joinRef)
        assertEquals("2", message.ref)
        assertEquals(JsonPrimitive("ok"), message.payload["status"])
    }

    @Test
    fun test_realtimeMessage_roundTrip() {
        val original =
            RealtimeMessage(
                topic = "realtime:public:messages",
                event = "broadcast",
                payload = JsonObject(mapOf("type" to JsonPrimitive("broadcast"))),
                joinRef = "1",
                ref = "2",
            )

        val decoded = json.decodeFromString<RealtimeMessage>(json.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun test_postgresChange_roundTrip() {
        val original =
            PostgresChange(
                schema = "public",
                table = "messages",
                filter = "id=eq.1",
                event = PostgresChangeEvent.INSERT,
            )

        val decoded = json.decodeFromString<PostgresChange>(json.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun test_postgresChangeEvent_serialNames() {
        assertEquals(
            PostgresChangeEvent.UPDATE,
            json.decodeFromString<PostgresChangeEvent>("\"UPDATE\""),
        )
        assertEquals("\"*\"", json.encodeToString(PostgresChangeEvent.ALL))
    }

    @Test
    fun test_broadcastPayload_roundTrip() {
        val original =
            BroadcastPayload(
                event = "cursor-move",
                payload = JsonObject(mapOf("x" to JsonPrimitive(10), "y" to JsonPrimitive(20))),
            )

        val decoded = json.decodeFromString<BroadcastPayload>(json.encodeToString(original))

        assertEquals(original, decoded)
    }
}
