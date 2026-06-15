package io.github.androidpoet.supabase.realtime
import io.github.androidpoet.supabase.realtime.models.PresenceState
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

    /**
     * The current cumulative presence state — every member currently tracked on
     * this channel, keyed by presence key — as of the last `presence_state` or
     * `presence_diff` received. Returns an empty map before the first sync.
     */
    public fun presenceState(): PresenceState

    public suspend fun unsubscribe()

    public suspend fun send(type: SendType, event: String, payload: JsonObject? = null)

    public suspend fun broadcast(event: String, payload: JsonObject)

    public suspend fun track(state: JsonObject)

    public suspend fun untrack()
}
