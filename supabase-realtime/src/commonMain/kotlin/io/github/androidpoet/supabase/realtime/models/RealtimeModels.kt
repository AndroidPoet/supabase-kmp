package io.github.androidpoet.supabase.realtime.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A wire-format message exchanged over the Phoenix WebSocket protocol.
 *
 * Every frame sent to or received from the Supabase Realtime server
 * is serialized as a [RealtimeMessage].
 */
@Serializable
public data class RealtimeMessage(
    public val topic: String,
    public val event: String,
    public val payload: JsonObject,
    public val ref: String? = null,
)

/**
 * Describes a Postgres change subscription filter.
 *
 * Determines which database events the channel should receive based on
 * [schema], [table], optional row-level [filter], and [event] type.
 */
@Serializable
public data class PostgresChange(
    public val schema: String = "public",
    public val table: String? = null,
    public val filter: String? = null,
    public val event: PostgresChangeEvent = PostgresChangeEvent.ALL,
)

/**
 * The type of Postgres change event to listen for.
 */
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
 * A mapping of presence keys to their associated state objects.
 */
public typealias PresenceState = Map<String, JsonObject>

/**
 * Identifies a realtime channel by its local [name] and server [topic].
 */
public data class RealtimeChannel(
    public val name: String,
    public val topic: String,
)

/**
 * Payload for a broadcast message sent through a realtime channel.
 */
@Serializable
public data class BroadcastPayload(
    public val event: String,
    public val payload: JsonObject,
)
