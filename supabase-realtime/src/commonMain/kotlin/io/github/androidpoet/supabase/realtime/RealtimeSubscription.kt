package io.github.androidpoet.supabase.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
public interface RealtimeSubscription {
    public val channel: String
    public fun asFlow(): Flow<RealtimeEvent>
    public suspend fun unsubscribe()
    public suspend fun broadcast(event: String, payload: JsonObject)
    public suspend fun track(state: JsonObject)
    public suspend fun untrack()
}
