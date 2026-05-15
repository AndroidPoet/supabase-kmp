package io.github.androidpoet.supabase.realtime.models
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
@Serializable
public data class RealtimeMessage(
    public val topic: String,
    public val event: String,
    public val payload: JsonObject,
    public val ref: String? = null,
)
@Serializable
public data class PostgresChange(
    public val schema: String = "public",
    public val table: String? = null,
    public val filter: String? = null,
    public val event: PostgresChangeEvent = PostgresChangeEvent.ALL,
)
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
public typealias PresenceState = Map<String, JsonObject>
public data class RealtimeChannel(
    public val name: String,
    public val topic: String,
)
@Serializable
public data class BroadcastPayload(
    public val event: String,
    public val payload: JsonObject,
)
