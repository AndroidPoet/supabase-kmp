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
    internal var receiveOwnBroadcasts: Boolean = false
    internal var acknowledgeBroadcasts: Boolean = false
    internal var presenceKey: String = ""
    internal var privateChannel: Boolean = false
    internal var replaySinceMs: Long? = null
    internal var replayLimit: Int? = null

    public fun onPostgresChange(
        schema: String = "public",
        table: String? = null,
        filter: String? = null,
        event: PostgresChangeEvent = PostgresChangeEvent.ALL,
        callback: suspend (JsonObject) -> Unit,
    ): RealtimeChannelBuilder =
        apply {
            postgresCallbacks +=
                PostgresCallbackConfig.Simple(
                    schema = schema,
                    table = table,
                    filter = filter,
                    event = event,
                    callback = callback,
                )
        }

    public fun onPostgresChange(
        schema: String = "public",
        table: String? = null,
        filter: String? = null,
        event: PostgresChangeEvent = PostgresChangeEvent.ALL,
        callback: suspend (PostgresChangeEvent, JsonObject) -> Unit,
    ): RealtimeChannelBuilder =
        apply {
            postgresCallbacks +=
                PostgresCallbackConfig.Typed(
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
    ): RealtimeChannelBuilder =
        apply {
            broadcastCallbacks[event] = callback
        }

    public fun onPresence(
        callback: suspend (PresenceState) -> Unit,
    ): RealtimeChannelBuilder =
        apply {
            presenceCallback = callback
        }

    public fun configureBroadcast(
        receiveOwnBroadcasts: Boolean = false,
        acknowledgeBroadcasts: Boolean = false,
    ): RealtimeChannelBuilder =
        apply {
            this.receiveOwnBroadcasts = receiveOwnBroadcasts
            this.acknowledgeBroadcasts = acknowledgeBroadcasts
        }

    public fun configureBroadcastReplay(
        sinceMs: Long,
        limit: Int? = null,
    ): RealtimeChannelBuilder =
        apply {
            replaySinceMs = sinceMs
            replayLimit = limit
        }

    public fun configurePresence(
        key: String = "",
    ): RealtimeChannelBuilder =
        apply {
            presenceKey = key
        }

    public fun setPrivate(
        enabled: Boolean = true,
    ): RealtimeChannelBuilder =
        apply {
            privateChannel = enabled
        }

    public suspend fun subscribe(): RealtimeSubscription =
        client.subscribe(this)
}

internal sealed class PostgresCallbackConfig {
    abstract val schema: String
    abstract val table: String?
    abstract val filter: String?
    abstract val event: PostgresChangeEvent

    data class Simple(
        override val schema: String,
        override val table: String?,
        override val filter: String?,
        override val event: PostgresChangeEvent,
        val callback: suspend (JsonObject) -> Unit,
    ) : PostgresCallbackConfig()

    data class Typed(
        override val schema: String,
        override val table: String?,
        override val filter: String?,
        override val event: PostgresChangeEvent,
        val callback: suspend (PostgresChangeEvent, JsonObject) -> Unit,
    ) : PostgresCallbackConfig()
}
