package io.github.androidpoet.supabase.realtime

/** A binary broadcast decoded off the wire: its channel [topic], the user [event]
 * name and the raw [payload] bytes. Internal — surfaced to callers as
 * [RealtimeEvent.BinaryBroadcast]. */
internal class DecodedBinaryBroadcast(
    val topic: String,
    val event: String,
    val payload: ByteArray,
)

/**
 * Wire codec for Realtime binary broadcast frames. The Phoenix transport carries
 * these as WebSocket *binary* frames (distinct from the JSON text frames used for
 * everything else), with a fixed-size header of single-byte length fields followed
 * by the UTF-8 strings and then the raw user payload.
 *
 * Outbound push (client → server), 7-byte header:
 * ```
 * [kind=3][joinRef_len][ref_len][topic_len][event_len][meta_len][encoding]
 * [joinRef][ref][topic][event][meta][payload]
 * ```
 * Inbound broadcast (server → client), 5-byte header:
 * ```
 * [kind=4][topic_len][event_len][meta_len][encoding]
 * [topic][event][meta][payload]
 * ```
 * `encoding` is `0` for raw binary payloads (what we send) and `1` for JSON; we
 * surface the payload remainder verbatim regardless. Each length field is a single
 * unsigned byte, so every header string is capped at 255 bytes.
 */
internal object BinaryBroadcastCodec {
    private const val KIND_USER_BROADCAST_PUSH: Byte = 3 // client → server
    private const val KIND_USER_BROADCAST: Byte = 4 // server → client
    private const val ENCODING_BINARY: Byte = 0

    private const val PUSH_HEADER_SIZE = 7
    private const val BROADCAST_HEADER_SIZE = 5
    private const val MAX_FIELD = 255

    /**
     * Encodes a kind-3 binary broadcast push. Metadata is empty (matching the
     * reference clients); the payload is sent raw (encoding `0`). The wire `event`
     * is the user's event name — there is no `{type:"broadcast"}` JSON envelope.
     */
    fun encodePush(
        joinRef: String?,
        ref: String?,
        topic: String,
        event: String,
        payload: ByteArray,
    ): ByteArray {
        val joinRefBytes = (joinRef ?: "").encodeToByteArray()
        val refBytes = (ref ?: "").encodeToByteArray()
        val topicBytes = topic.encodeToByteArray()
        val eventBytes = event.encodeToByteArray()
        require(
            joinRefBytes.size <= MAX_FIELD &&
                refBytes.size <= MAX_FIELD &&
                topicBytes.size <= MAX_FIELD &&
                eventBytes.size <= MAX_FIELD,
        ) { "Binary broadcast header fields must not exceed $MAX_FIELD bytes each" }

        val header = ByteArray(PUSH_HEADER_SIZE)
        header[0] = KIND_USER_BROADCAST_PUSH
        header[1] = joinRefBytes.size.toByte()
        header[2] = refBytes.size.toByte()
        header[3] = topicBytes.size.toByte()
        header[4] = eventBytes.size.toByte()
        header[5] = 0 // metadata size (none)
        header[6] = ENCODING_BINARY
        return header + joinRefBytes + refBytes + topicBytes + eventBytes + payload
    }

    /**
     * Decodes a kind-4 binary broadcast frame, returning `null` when the frame is
     * not a user broadcast we handle or is too short for its declared field lengths.
     * The payload remainder is returned raw; metadata, if present, is skipped.
     */
    fun decode(frame: ByteArray): DecodedBinaryBroadcast? {
        if (frame.size < BROADCAST_HEADER_SIZE) return null
        if (frame[0] != KIND_USER_BROADCAST) return null
        val topicLen = frame[1].toInt() and 0xFF
        val eventLen = frame[2].toInt() and 0xFF
        val metaLen = frame[3].toInt() and 0xFF
        // frame[4] is the encoding flag; we surface the remainder verbatim either way.

        var offset = BROADCAST_HEADER_SIZE
        if (frame.size < offset + topicLen + eventLen + metaLen) return null
        val topic = frame.utf8(offset, topicLen)
        offset += topicLen
        val event = frame.utf8(offset, eventLen)
        offset += eventLen
        offset += metaLen // skip metadata
        val payload = frame.copyOfRange(offset, frame.size)
        return DecodedBinaryBroadcast(topic = topic, event = event, payload = payload)
    }

    private fun ByteArray.utf8(offset: Int, length: Int): String =
        copyOfRange(offset, offset + length).decodeToString()
}
