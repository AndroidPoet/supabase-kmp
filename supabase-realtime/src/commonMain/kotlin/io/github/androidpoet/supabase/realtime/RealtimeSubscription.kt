package io.github.androidpoet.supabase.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

public interface RealtimeSubscription {
    public enum class Status {
        SUBSCRIBING,
        SUBSCRIBED,
        UNSUBSCRIBING,
        UNSUBSCRIBED,
        ERROR,
    }

    public enum class SendType(
        public val wireValue: String,
    ) {
        BROADCAST("broadcast"),
        PRESENCE("presence"),
        POSTGRES_CHANGES("postgres_changes"),
    }

    public val channel: String
    public val status: StateFlow<Status>

    public fun asFlow(): Flow<RealtimeEvent>

    public suspend fun unsubscribe()

    public suspend fun send(type: SendType, event: String, payload: JsonObject? = null)

    public suspend fun broadcast(event: String, payload: JsonObject)

    public suspend fun track(state: JsonObject)

    public suspend fun untrack()
}
