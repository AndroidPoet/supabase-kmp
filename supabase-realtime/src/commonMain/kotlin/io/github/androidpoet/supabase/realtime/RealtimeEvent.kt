package io.github.androidpoet.supabase.realtime

import io.github.androidpoet.supabase.realtime.models.PresenceState
import kotlinx.serialization.json.JsonObject

/**
 * Represents a single event received from a Supabase Realtime channel.
 *
 * Consumers observe these via [RealtimeSubscription.asFlow] and pattern-match
 * on the concrete subtypes.
 */
public sealed interface RealtimeEvent {

    /** A new row was inserted. */
    public data class PostgresInsert(
        public val record: JsonObject,
        public val oldRecord: JsonObject? = null,
    ) : RealtimeEvent

    /** An existing row was updated. */
    public data class PostgresUpdate(
        public val record: JsonObject,
        public val oldRecord: JsonObject? = null,
    ) : RealtimeEvent

    /** A row was deleted. */
    public data class PostgresDelete(
        public val oldRecord: JsonObject,
    ) : RealtimeEvent

    /** A broadcast message was received on the channel. */
    public data class Broadcast(
        public val event: String,
        public val payload: JsonObject,
    ) : RealtimeEvent

    /** The full presence state was synchronized. */
    public data class PresenceSync(
        public val state: PresenceState,
    ) : RealtimeEvent

    /** A new participant joined the channel. */
    public data class PresenceJoin(
        public val key: String,
        public val newPresence: JsonObject,
    ) : RealtimeEvent

    /** A participant left the channel. */
    public data class PresenceLeave(
        public val key: String,
        public val leftPresence: JsonObject,
    ) : RealtimeEvent

    /** A system-level event (e.g. connection status changes). */
    public data class SystemEvent(
        public val status: String,
        public val message: String? = null,
    ) : RealtimeEvent
}
