package io.github.androidpoet.supabase.realtime.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Serializes [RealtimeMessage] in the Realtime **Protocol 2.0.0** array form:
 * `[join_ref, ref, topic, event, payload]`. This is the wire shape the server's V2
 * serializer uses — selected by connecting with `vsn=2.0.0` — and it shares the
 * transport with binary broadcast frames. `join_ref` and `ref` are emitted as JSON
 * `null` when absent; their array slots must still be present.
 */
internal object RealtimeMessageSerializer : KSerializer<RealtimeMessage> {
    private const val ARRAY_SIZE = 5

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RealtimeMessage")

    override fun serialize(encoder: Encoder, value: RealtimeMessage) {
        val jsonEncoder =
            encoder as? JsonEncoder
                ?: throw SerializationException("RealtimeMessage is only serializable as JSON")
        jsonEncoder.encodeJsonElement(
            buildJsonArray {
                add(value.joinRef?.let { JsonPrimitive(it) } ?: JsonNull)
                add(value.ref?.let { JsonPrimitive(it) } ?: JsonNull)
                add(JsonPrimitive(value.topic))
                add(JsonPrimitive(value.event))
                add(value.payload)
            },
        )
    }

    override fun deserialize(decoder: Decoder): RealtimeMessage {
        val jsonDecoder =
            decoder as? JsonDecoder
                ?: throw SerializationException("RealtimeMessage is only deserializable from JSON")
        val array = jsonDecoder.decodeJsonElement().jsonArray
        if (array.size != ARRAY_SIZE) {
            throw SerializationException("Expected a $ARRAY_SIZE-element Realtime v2 frame, got ${array.size}")
        }
        return RealtimeMessage(
            joinRef = array[0].stringOrNull(),
            ref = array[1].stringOrNull(),
            topic = array[2].jsonPrimitive.content,
            event = array[3].jsonPrimitive.content,
            payload = array[4] as? JsonObject ?: JsonObject(emptyMap()),
        )
    }

    private fun JsonElement.stringOrNull(): String? = if (this is JsonNull) null else jsonPrimitive.content
}
