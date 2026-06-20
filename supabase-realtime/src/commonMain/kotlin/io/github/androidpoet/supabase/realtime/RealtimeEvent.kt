package io.github.androidpoet.supabase.realtime
import io.github.androidpoet.supabase.realtime.models.PresenceState
import kotlinx.serialization.json.JsonObject

/**
 * A decoded inbound event on a Realtime channel, as delivered by
 * [RealtimeSubscription.asFlow]. Spans the three channel features —
 * `postgres_changes`, `broadcast` and `presence` — plus channel `SystemEvent`s.
 * The typed flows in `RealtimeExt` (`postgresInsertsFlow`, `broadcastFlow`,
 * `presenceSyncFlow`, …) narrow this union to one case so you can avoid the
 * `is`-checks.
 */
public sealed interface RealtimeEvent {
    /** A row was inserted; [record] is the new row ([oldRecord] is normally
     * absent for INSERT). [commitTimestamp] is the server commit time
     * (ISO-8601), [schema] and [table] identify the changed relation; all three
     * are `null` if the server omits them. */
    public data class PostgresInsert(
        public val record: JsonObject,
        public val oldRecord: JsonObject? = null,
        public val commitTimestamp: String? = null,
        public val schema: String? = null,
        public val table: String? = null,
    ) : RealtimeEvent

    /** A row was updated; [record] is the new row and [oldRecord] the previous one
     * when the table's replica identity provides it. [commitTimestamp] is the
     * server commit time (ISO-8601), [schema] and [table] identify the changed
     * relation; all three are `null` if the server omits them. */
    public data class PostgresUpdate(
        public val record: JsonObject,
        public val oldRecord: JsonObject? = null,
        public val commitTimestamp: String? = null,
        public val schema: String? = null,
        public val table: String? = null,
    ) : RealtimeEvent

    /** A row was deleted; [oldRecord] is the removed row (may be partial,
     * depending on replica identity). [commitTimestamp] is the server commit time
     * (ISO-8601), [schema] and [table] identify the changed relation; all three
     * are `null` if the server omits them. */
    public data class PostgresDelete(
        public val oldRecord: JsonObject,
        public val commitTimestamp: String? = null,
        public val schema: String? = null,
        public val table: String? = null,
    ) : RealtimeEvent

    /** A broadcast message named [event] carrying [payload]. [replayed] is `true`
     * when the server is replaying a retained broadcast to a late joiner (from the
     * message's `meta.replayed` flag); `false` for live messages. */
    public data class Broadcast(
        public val event: String,
        public val payload: JsonObject,
        public val replayed: Boolean = false,
    ) : RealtimeEvent

    /** A broadcast named [event] carrying a raw binary [payload], delivered over the
     * WebSocket as a binary frame (sent with [RealtimeSubscription.broadcastBinary]).
     * Use this for compact, non-textual data — sensor frames, image tiles, encrypted
     * bytes — to skip the base64/JSON overhead of [Broadcast]. [replayed] mirrors
     * [Broadcast.replayed]. */
    public class BinaryBroadcast(
        public val event: String,
        public val payload: ByteArray,
        public val replayed: Boolean = false,
    ) : RealtimeEvent {
        // ByteArray has identity equality; provide structural semantics so events
        // compare by content like the other cases.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is BinaryBroadcast) return false
            return event == other.event && replayed == other.replayed && payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = event.hashCode()
            result = 31 * result + payload.contentHashCode()
            result = 31 * result + replayed.hashCode()
            return result
        }

        override fun toString(): String = "BinaryBroadcast(event=$event, payload=${payload.size} bytes, replayed=$replayed)"
    }

    /** A presence sync: [state] is the full cumulative membership after a
     * `presence_state`/`presence_diff`. */
    public data class PresenceSync(
        public val state: PresenceState,
    ) : RealtimeEvent

    /** A member identified by [key] joined, carrying its [newPresence] payload. */
    public data class PresenceJoin(
        public val key: String,
        public val newPresence: JsonObject,
    ) : RealtimeEvent

    /** A member identified by [key] left, carrying its last [leftPresence]
     * payload. */
    public data class PresenceLeave(
        public val key: String,
        public val leftPresence: JsonObject,
    ) : RealtimeEvent

    /** A channel-level system message (e.g. subscription [status] changes), with
     * an optional human-readable [message]. */
    public data class SystemEvent(
        public val status: String,
        public val message: String? = null,
    ) : RealtimeEvent
}
