package io.github.androidpoet.supabase.realtime

import io.github.androidpoet.supabase.realtime.models.PostgresChangeEvent
import io.github.androidpoet.supabase.realtime.models.PresenceState
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject

/**
 * One-call channel subscribe: builds the channel [channelName], applies [configure]
 * (where you register `onPostgresChange`/`onBroadcast`/`onPresence` and channel
 * options), and joins it. The lambda form of [RealtimeClient.channel] +
 * [RealtimeChannelBuilder.subscribe]; returns as soon as the join is sent.
 */
public suspend fun RealtimeClient.subscribe(
    channelName: String,
    configure: RealtimeChannelBuilder.() -> Unit = {},
): RealtimeSubscription =
    this.channel(channelName).apply(configure).subscribe()

/**
 * Subscribes to [channelName] and registers a single raw `postgres_changes` callback
 * in one call — the shortcut for the common "watch this table" case. See
 * [RealtimeChannelBuilder.onPostgresChange] for the parameters and use
 * [realtimeFilter] to build [filter].
 */
public suspend fun RealtimeClient.subscribeToPostgresChanges(
    channelName: String,
    schema: String = "public",
    table: String? = null,
    filter: String? = null,
    event: PostgresChangeEvent = PostgresChangeEvent.ALL,
    callback: suspend (JsonObject) -> Unit,
): RealtimeSubscription =
    subscribe(channelName) {
        onPostgresChange(
            schema = schema,
            table = table,
            filter = filter,
            event = event,
            callback = callback,
        )
    }

/**
 * Like the single-argument [subscribeToPostgresChanges] but the callback also
 * receives the resolved [PostgresChangeEvent], so one handler can branch on
 * INSERT/UPDATE/DELETE.
 */
public suspend fun RealtimeClient.subscribeToPostgresChanges(
    channelName: String,
    schema: String = "public",
    table: String? = null,
    filter: String? = null,
    event: PostgresChangeEvent = PostgresChangeEvent.ALL,
    callback: suspend (PostgresChangeEvent, JsonObject) -> Unit,
): RealtimeSubscription =
    subscribe(channelName) {
        onPostgresChange(
            schema = schema,
            table = table,
            filter = filter,
            event = event,
            callback = callback,
        )
    }

/**
 * Subscribes to [channelName] and registers a single broadcast [callback] for the
 * named [event] in one call. See [RealtimeChannelBuilder.onBroadcast].
 */
public suspend fun RealtimeClient.subscribeToBroadcast(
    channelName: String,
    event: String,
    callback: suspend (JsonObject) -> Unit,
): RealtimeSubscription =
    subscribe(channelName) {
        onBroadcast(event = event, callback = callback)
    }

/**
 * Subscribes to [channelName] and registers a single presence sync [callback] in one
 * call, invoked with the full [PresenceState]. See
 * [RealtimeChannelBuilder.onPresence].
 */
public suspend fun RealtimeClient.subscribeToPresence(
    channelName: String,
    callback: suspend (PresenceState) -> Unit,
): RealtimeSubscription =
    subscribe(channelName) {
        onPresence(callback = callback)
    }

/** Suspends until [connectionState] reaches [ConnectionState.Connected] and
 * returns it — useful right after [RealtimeClient.connect] to gate setup work. */
public suspend fun RealtimeClient.awaitConnected(): ConnectionState.Connected =
    connectionState
        .filter { it is ConnectionState.Connected }
        .first() as ConnectionState.Connected

/** Suspends until [connectionState] reaches [ConnectionState.Disconnected] and
 * returns it — useful to confirm teardown after [RealtimeClient.disconnect]. */
public suspend fun RealtimeClient.awaitDisconnected(): ConnectionState.Disconnected =
    connectionState
        .filter { it is ConnectionState.Disconnected }
        .first() as ConnectionState.Disconnected
