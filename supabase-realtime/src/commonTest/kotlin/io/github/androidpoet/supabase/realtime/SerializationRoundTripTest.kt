package io.github.androidpoet.supabase.realtime

import io.github.androidpoet.supabase.realtime.models.BroadcastPayload
import io.github.androidpoet.supabase.realtime.models.PostgresChange
import io.github.androidpoet.supabase.realtime.models.PostgresChangeEvent
import io.github.androidpoet.supabase.realtime.models.RealtimeMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializationRoundTripTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun test_realtimeMessage_decodesFromV2ArrayWire() {
        // Protocol 2.0.0 wire form: [join_ref, ref, topic, event, payload].
        val payload =
            """
            ["1", "2", "realtime:public:messages", "phx_reply", { "status": "ok", "response": {} }]
            """.trimIndent()

        val message = json.decodeFromString<RealtimeMessage>(payload)

        assertEquals("realtime:public:messages", message.topic)
        assertEquals("phx_reply", message.event)
        assertEquals("1", message.joinRef)
        assertEquals("2", message.ref)
        assertEquals(JsonPrimitive("ok"), message.payload["status"])
    }

    @Test
    fun test_realtimeMessage_encodesToV2Array() {
        val message =
            RealtimeMessage(
                topic = "realtime:room",
                event = "phx_join",
                payload = JsonObject(emptyMap()),
                joinRef = "7",
                ref = null,
            )

        val array = json.parseToJsonElement(json.encodeToString(message)).jsonArray

        assertEquals(5, array.size)
        assertEquals("7", array[0].jsonPrimitive.content) // join_ref
        assertEquals(JsonNull, array[1]) // ref absent → null slot still present
        assertEquals("realtime:room", array[2].jsonPrimitive.content)
        assertEquals("phx_join", array[3].jsonPrimitive.content)
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
