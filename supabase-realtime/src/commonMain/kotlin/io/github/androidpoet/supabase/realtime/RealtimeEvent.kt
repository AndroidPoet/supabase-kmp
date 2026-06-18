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
     * absent for INSERT). */
    public data class PostgresInsert(
        public val record: JsonObject,
        public val oldRecord: JsonObject? = null,
    ) : RealtimeEvent

    /** A row was updated; [record] is the new row and [oldRecord] the previous one
     * when the table's replica identity provides it. */
    public data class PostgresUpdate(
        public val record: JsonObject,
        public val oldRecord: JsonObject? = null,
    ) : RealtimeEvent

    /** A row was deleted; [oldRecord] is the removed row (may be partial,
     * depending on replica identity). */
    public data class PostgresDelete(
        public val oldRecord: JsonObject,
    ) : RealtimeEvent

    /** A broadcast message named [event] carrying [payload]. */
    public data class Broadcast(
        public val event: String,
        public val payload: JsonObject,
    ) : RealtimeEvent

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
