package io.github.androidpoet.supabase.realtime
import io.github.androidpoet.supabase.realtime.models.PostgresChangeEvent
import io.github.androidpoet.supabase.realtime.models.PresenceState
import kotlinx.serialization.json.JsonObject
public class RealtimeChannelBuilder internal constructor(
    internal val channelName: String,
    internal val client: RealtimeClientImpl,
) {
    internal val postgresCallbacks: MutableList<PostgresCallbackConfig> = mutableListOf()
    internal val broadcastCallbacks: MutableMap<String, suspend (JsonObject) -> Unit> = mutableMapOf()
    internal var presenceCallback: (suspend (PresenceState) -> Unit)? = null
    public fun onPostgresChange(
        schema: String = "public",
        table: String? = null,
        filter: String? = null,
        event: PostgresChangeEvent = PostgresChangeEvent.ALL,
        callback: suspend (JsonObject) -> Unit,
    ): RealtimeChannelBuilder = apply {
        postgresCallbacks += PostgresCallbackConfig(
            schema = schema,
            table = table,
            filter = filter,
            event = event,
            callback = callback,
        )
    }
    public fun onBroadcast(
        event: String,
        callback: suspend (JsonObject) -> Unit,
    ): RealtimeChannelBuilder = apply {
        broadcastCallbacks[event] = callback
    }
    public fun onPresence(
        callback: suspend (PresenceState) -> Unit,
    ): RealtimeChannelBuilder = apply {
        presenceCallback = callback
    }
    public suspend fun subscribe(): RealtimeSubscription =
        client.subscribe(this)
}
internal data class PostgresCallbackConfig(
    val schema: String,
    val table: String?,
    val filter: String?,
    val event: PostgresChangeEvent,
    val callback: suspend (JsonObject) -> Unit,
)
