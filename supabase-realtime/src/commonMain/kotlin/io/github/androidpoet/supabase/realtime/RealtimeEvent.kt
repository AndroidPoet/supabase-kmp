package io.github.androidpoet.supabase.realtime
import io.github.androidpoet.supabase.realtime.models.PresenceState
import kotlinx.serialization.json.JsonObject
public sealed interface RealtimeEvent {
    public data class PostgresInsert(
        public val record: JsonObject,
        public val oldRecord: JsonObject? = null,
    ) : RealtimeEvent
    public data class PostgresUpdate(
        public val record: JsonObject,
        public val oldRecord: JsonObject? = null,
    ) : RealtimeEvent
    public data class PostgresDelete(
        public val oldRecord: JsonObject,
    ) : RealtimeEvent
    public data class Broadcast(
        public val event: String,
        public val payload: JsonObject,
    ) : RealtimeEvent
    public data class PresenceSync(
        public val state: PresenceState,
    ) : RealtimeEvent
    public data class PresenceJoin(
        public val key: String,
        public val newPresence: JsonObject,
    ) : RealtimeEvent
    public data class PresenceLeave(
        public val key: String,
        public val leftPresence: JsonObject,
    ) : RealtimeEvent
    public data class SystemEvent(
        public val status: String,
        public val message: String? = null,
    ) : RealtimeEvent
}
