package io.github.androidpoet.supabase.realtime.models
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A raw Phoenix protocol frame. On the wire (Realtime Protocol 2.0.0) this is the
 * array `[join_ref, ref, topic, event, payload]`, encoded by [RealtimeMessageSerializer].
 * Surfaced via [RealtimeDebugEvent] for diagnostics; application code normally works
 * with decoded [RealtimeEvent]s instead.
 *
 * @param topic the channel topic (e.g. `realtime:room1`) or `phoenix` for socket
 *   control frames.
 * @param event the Phoenix event name (`phx_join`, `phx_reply`, `broadcast`, …).
 * @param joinRef the ref of the join that scopes this message (`join_ref`).
 * @param ref the per-message correlation ref used to match replies.
 */
@Serializable(with = RealtimeMessageSerializer::class)
public data class RealtimeMessage(
    public val topic: String,
    public val event: String,
    public val payload: JsonObject,
    public val joinRef: String? = null,
    public val ref: String? = null,
)

/**
 * A `postgres_changes` subscription descriptor: which [schema]/[table] to watch,
 * for which [event] type, optionally narrowed by a single [filter]
 * (`column=op.value`, built with [realtimeFilter]).
 */
@Serializable
public data class PostgresChange(
    public val schema: String = "public",
    public val table: String? = null,
    public val filter: String? = null,
    public val event: PostgresChangeEvent = PostgresChangeEvent.ALL,
)

/** The database change type a `postgres_changes` subscription listens for; [ALL]
 * (the wire `*`) matches every type. */
@Serializable
public enum class PostgresChangeEvent {
    @SerialName("INSERT")
    INSERT,

    @SerialName("UPDATE")
    UPDATE,

    @SerialName("DELETE")
    DELETE,

    @SerialName("*")
    ALL,
}

/**
 * Cumulative presence membership: each presence key mapped to that member's last
 * tracked payload. Delivered by [RealtimeEvent.PresenceSync] and
 * [RealtimeSubscription.presenceState]; published with
 * [RealtimeSubscription.track].
 */
public typealias PresenceState = Map<String, JsonObject>

/** Identifies an active channel by its [name] and fully-qualified Phoenix
 * [topic] (e.g. `realtime:room1`); returned by [RealtimeClient.activeChannelDetails]. */
public data class RealtimeChannel(
    public val name: String,
    public val topic: String,
)

/** A broadcast envelope pairing an [event] name with its [payload]; the shape of
 * a `broadcast` message body on the wire. */
@Serializable
public data class BroadcastPayload(
    public val event: String,
    public val payload: JsonObject,
)
