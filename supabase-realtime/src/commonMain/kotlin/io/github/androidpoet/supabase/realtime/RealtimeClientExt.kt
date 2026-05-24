package io.github.androidpoet.supabase.realtime

import io.github.androidpoet.supabase.realtime.models.PostgresChangeEvent
import io.github.androidpoet.supabase.realtime.models.PresenceState
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject

public suspend fun RealtimeClient.subscribe(
    channel: String,
    configure: RealtimeChannelBuilder.() -> Unit = {},
): RealtimeSubscription =
    this.channel(channel).apply(configure).subscribe()

public suspend fun RealtimeClient.subscribeToPostgresChanges(
    channel: String,
    schema: String = "public",
    table: String? = null,
    filter: String? = null,
    event: PostgresChangeEvent = PostgresChangeEvent.ALL,
    callback: suspend (JsonObject) -> Unit,
): RealtimeSubscription =
    subscribe(channel) {
        onPostgresChange(
            schema = schema,
            table = table,
            filter = filter,
            event = event,
            callback = callback,
        )
    }

public suspend fun RealtimeClient.subscribeToBroadcast(
    channel: String,
    event: String,
    callback: suspend (JsonObject) -> Unit,
): RealtimeSubscription =
    subscribe(channel) {
        onBroadcast(event = event, callback = callback)
    }

public suspend fun RealtimeClient.subscribeToPresence(
    channel: String,
    callback: suspend (PresenceState) -> Unit,
): RealtimeSubscription =
    subscribe(channel) {
        onPresence(callback = callback)
    }

public suspend fun RealtimeClient.awaitConnected(): ConnectionState.Connected =
    connectionState
        .filter { it is ConnectionState.Connected }
        .first() as ConnectionState.Connected

public suspend fun RealtimeClient.awaitDisconnected(): ConnectionState.Disconnected =
    connectionState
        .filter { it is ConnectionState.Disconnected }
        .first() as ConnectionState.Disconnected
