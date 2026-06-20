package io.github.androidpoet.supabase.realtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BinaryBroadcastCodecTest {
    @Test
    fun encodePush_producesExactKind3Layout() {
        val topic = "realtime:room"
        val event = "evt"
        val payload = byteArrayOf(9, 8, 7, 0, -1)
        val out = BinaryBroadcastCodec.encodePush(joinRef = "1", ref = "22", topic = topic, event = event, payload = payload)

        // 7-byte header: kind=3, joinRef/ref/topic/event/meta sizes, encoding=0 (binary).
        assertEquals(3, out[0].toInt() and 0xFF, "kind = user_broadcast_push")
        assertEquals(1, out[1].toInt() and 0xFF, "joinRef size")
        assertEquals(2, out[2].toInt() and 0xFF, "ref size")
        assertEquals(topic.length, out[3].toInt() and 0xFF, "topic size")
        assertEquals(event.length, out[4].toInt() and 0xFF, "event size")
        assertEquals(0, out[5].toInt() and 0xFF, "metadata size")
        assertEquals(0, out[6].toInt() and 0xFF, "encoding = binary")

        // Body: joinRef("1") ref("22") topic event payload, in order.
        var offset = 7 + 1 + 2 // skip header + joinRef + ref
        assertEquals(topic, out.copyOfRange(offset, offset + topic.length).decodeToString())
        offset += topic.length
        assertEquals(event, out.copyOfRange(offset, offset + event.length).decodeToString())
        offset += event.length
        assertEquals(payload.toList(), out.copyOfRange(offset, out.size).toList())
    }

    @Test
    fun encodePush_handlesNullRefsAsZeroLength() {
        val out = BinaryBroadcastCodec.encodePush(joinRef = null, ref = null, topic = "t", event = "e", payload = byteArrayOf(1))
        assertEquals(0, out[1].toInt() and 0xFF)
        assertEquals(0, out[2].toInt() and 0xFF)
    }

    @Test
    fun decode_parsesKind4Frame_payloadRaw() {
        val topic = "realtime:room"
        val event = "evt"
        val payload = byteArrayOf(1, 2, 3, 42, -7, 0)
        // [kind=4][topicLen][eventLen][metaLen=0][encoding=0] topic event payload
        val frame =
            byteArrayOf(4, topic.length.toByte(), event.length.toByte(), 0, 0) +
                topic.encodeToByteArray() + event.encodeToByteArray() + payload

        val decoded = BinaryBroadcastCodec.decode(frame)!!
        assertEquals(topic, decoded.topic)
        assertEquals(event, decoded.event)
        assertEquals(payload.toList(), decoded.payload.toList())
    }

    @Test
    fun decode_skipsMetadataBeforePayload() {
        val topic = "t"
        val event = "e"
        val meta = "{}".encodeToByteArray()
        val payload = byteArrayOf(5, 6)
        val frame =
            byteArrayOf(4, topic.length.toByte(), event.length.toByte(), meta.size.toByte(), 0) +
                topic.encodeToByteArray() + event.encodeToByteArray() + meta + payload

        val decoded = BinaryBroadcastCodec.decode(frame)!!
        assertEquals(topic, decoded.topic)
        assertEquals(payload.toList(), decoded.payload.toList())
    }

    @Test
    fun decode_rejectsWrongKindAndTruncatedFrames() {
        assertNull(BinaryBroadcastCodec.decode(byteArrayOf(3, 0, 0, 0, 0)), "push kind is not a server broadcast")
        assertNull(BinaryBroadcastCodec.decode(byteArrayOf(4, 1)), "shorter than the 5-byte header")
        // Declares a 10-byte topic but supplies none.
        assertNull(BinaryBroadcastCodec.decode(byteArrayOf(4, 10, 0, 0, 0)), "truncated for declared lengths")
    }
}
