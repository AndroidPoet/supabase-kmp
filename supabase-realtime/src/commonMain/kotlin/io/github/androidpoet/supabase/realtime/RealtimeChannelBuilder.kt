package io.github.androidpoet.supabase.realtime

import io.github.androidpoet.supabase.realtime.models.PostgresChangeEvent
import io.github.androidpoet.supabase.realtime.models.PresenceState
import kotlinx.serialization.json.JsonObject

/**
 * DSL builder for configuring a Realtime channel subscription.
 *
 * Register callbacks for Postgres changes, broadcasts, and presence events
 * before calling [subscribe] to join the channel.
 *
 * ```kotlin
 * realtime.channel("room")
 *     .onPostgresChange(table = "messages") { record -> /* … */ }
 *     .onBroadcast("cursor") { payload -> /* … */ }
 *     .onPresence { state -> /* … */ }
 *     .subscribe()
 * ```
 */
public class RealtimeChannelBuilder internal constructor(
    internal val channelName: String,
    internal val client: RealtimeClientImpl,
) {
    internal val postgresCallbacks: MutableList<PostgresCallbackConfig> = mutableListOf()
    internal val broadcastCallbacks: MutableMap<String, suspend (JsonObject) -> Unit> = mutableMapOf()
    internal var presenceCallback: (suspend (PresenceState) -> Unit)? = null

    /**
     * Registers a callback for Postgres change events matching the given filters.
     */
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

    /**
     * Registers a callback for broadcast messages with the given [event] name.
     */
    public fun onBroadcast(
        event: String,
        callback: suspend (JsonObject) -> Unit,
    ): RealtimeChannelBuilder = apply {
        broadcastCallbacks[event] = callback
    }

    /**
     * Registers a callback for presence state synchronization events.
     */
    public fun onPresence(
        callback: suspend (PresenceState) -> Unit,
    ): RealtimeChannelBuilder = apply {
        presenceCallback = callback
    }

    /**
     * Joins the channel on the server and begins receiving events.
     */
    public suspend fun subscribe(): RealtimeSubscription =
        client.subscribe(this)
}

/**
 * Internal configuration for a single Postgres change callback registration.
 */
internal data class PostgresCallbackConfig(
    val schema: String,
    val table: String?,
    val filter: String?,
    val event: PostgresChangeEvent,
    val callback: suspend (JsonObject) -> Unit,
)
