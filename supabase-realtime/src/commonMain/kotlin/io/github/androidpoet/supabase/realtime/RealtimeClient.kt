package io.github.androidpoet.supabase.realtime
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.realtime.models.RealtimeChannel
import io.github.androidpoet.supabase.realtime.models.RealtimeMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

/**
 * The Supabase Realtime client: one WebSocket connection to `/realtime/v1/`
 * multiplexing many Phoenix channels, each carrying `postgres_changes`,
 * `broadcast` and/or `presence`.
 *
 * Build a channel with [channelName] (returning a [RealtimeChannelBuilder] you
 * configure then `subscribe()`), and the socket connects lazily on the first
 * subscription. The connection auto-reconnects per [RealtimeConfig] and replays
 * its joins; observe its lifecycle through [connectionState]. Subscriptions are
 * deduplicated by topic — subscribing to the same channel name twice returns the
 * existing one. Operations that touch the network ([broadcast], lifecycle calls)
 * are Result-first or suspend until settled; queries ([getSubscription],
 * [getActiveChannelNames]) are synchronous snapshots. Create one with
 * [createRealtimeClient]; call [close] when done to release the engine.
 */
public interface RealtimeClient {
    /** Cold-start-safe [StateFlow] of the socket lifecycle; emits the current
     * [ConnectionState] on collection and on every transition. */
    public val connectionState: StateFlow<ConnectionState>

    /** Running counters (messages in/out, heartbeats, last refs) for diagnostics
     * and tests. See [RealtimeDebugState]. */
    public val debugState: StateFlow<RealtimeDebugState>

    /** Hot stream of low-level protocol events ([RealtimeDebugEvent]) — including
     * dropped-message signals — for diagnostics; not needed for normal use. */
    public val debugEvents: Flow<RealtimeDebugEvent>

    /** `true` when the socket is currently in [ConnectionState.Connected]. */
    public val isConnected: Boolean

    /** `true` while the socket is establishing or re-establishing a connection. */
    public val isConnecting: Boolean

    /** `true` while the socket is tearing the connection down. */
    public val isDisconnecting: Boolean

    /**
     * Begins configuring a channel topic [name] (the part after `realtime:`),
     * returning a [RealtimeChannelBuilder] on which to register
     * `postgres_changes`/`broadcast`/`presence` callbacks before `subscribe()`.
     * Building a channel does not join it.
     */
    public fun channel(name: String): RealtimeChannelBuilder

    /** Returns the active subscription for channel [name], or `null` if not
     * subscribed. A synchronous snapshot. */
    public fun getSubscription(name: String): RealtimeSubscription?

    /** Returns the active subscription for the fully-qualified Phoenix [topic]
     * (e.g. `realtime:room1`), or `null`. */
    public fun getSubscriptionByTopic(topic: String): RealtimeSubscription?

    /** Returns a snapshot of all currently active subscriptions. */
    public fun getSubscriptions(): Set<RealtimeSubscription>

    /** Returns the names of all currently subscribed channels. */
    public fun getActiveChannelNames(): Set<String>

    /** Returns name+topic details for all active channels. See [RealtimeChannel]. */
    public fun getActiveChannels(): Set<RealtimeChannel>

    /** Unsubscribes [subscription] and removes it, leaving (`UNSUBSCRIBED`) its
     * Phoenix channel. Suspends until the leave is sent. */
    public suspend fun removeSubscription(subscription: RealtimeSubscription)

    /** Unsubscribes and removes each of [subscriptions]; see [removeSubscription]. */
    public suspend fun removeSubscriptions(subscriptions: List<RealtimeSubscription>)

    /** Unsubscribes and removes the subscription on the fully-qualified Phoenix
     * [topic] (e.g. `realtime:room1`), if any. */
    public suspend fun removeSubscriptionByTopic(topic: String)

    /** Unsubscribes and removes the subscriptions on each of [topics]; see
     * [removeSubscriptionByTopic]. */
    public suspend fun removeSubscriptionsByTopic(topics: List<String>)

    /** Unsubscribes and removes the subscription on channel [name], if any. */
    public suspend fun removeSubscription(name: String)

    /** Unsubscribes and removes every active subscription, leaving the socket
     * open. */
    public suspend fun removeAllSubscriptions()

    /**
     * Updates the auth token used for channel authorization and rejoins active
     * channels with it (Phoenix `access_token`), so RLS-gated `postgres_changes`
     * and private channels stay authorized after a session refresh. Pass `null`
     * to fall back to the wrapped client's current session token.
     */
    public suspend fun setAuth(token: String? = null)

    /** Sends one Phoenix heartbeat immediately, outside the periodic schedule.
     * Rarely needed — heartbeats are sent automatically per [RealtimeConfig]. */
    public suspend fun sendHeartbeat()

    /**
     * Opens the WebSocket connection eagerly. Optional: the socket connects on the
     * first [RealtimeChannelBuilder.subscribe]; call this to pre-warm it or to
     * reconnect after [disconnect]. Suspends until the attempt settles.
     */
    public suspend fun connect()

    /**
     * Sends a broadcast message over HTTP to the realtime broadcast endpoint
     * (`/realtime/v1/api/broadcast`) without joining a channel or opening a
     * WebSocket. Useful for fire-and-forget server-to-client fan-out where the
     * sender doesn't need to subscribe. [channelName] is the channel name (without
     * the `realtime:` prefix). For a private channel, the caller's session JWT
     * (or apikey) is attached automatically.
     */
    public suspend fun broadcast(
        channelName: String,
        event: String,
        payload: JsonObject,
        private: Boolean = false,
    ): SupabaseResult<Unit>

    /**
     * Closes the WebSocket but keeps the client (and its engine) reusable: active
     * subscriptions are retained and rejoined on the next [connect] or
     * subscription. For a permanent teardown that frees the engine, use [close].
     */
    public suspend fun disconnect()

    /**
     * Disconnects and releases the underlying HTTP/WebSocket engine. The client
     * cannot be reused afterwards. Call this when you are done with the client to
     * avoid leaking the engine's connection pool and threads. Prefer [disconnect]
     * if you intend to [connect] again later.
     */
    public suspend fun close()
}

/**
 * A snapshot of cumulative socket counters, exposed via
 * [RealtimeClient.debugState] for diagnostics and tests. Counts every message
 * and heartbeat sent and received since the client was created, plus the last
 * Phoenix message [ref] in each direction.
 */
public data class RealtimeDebugState(
    public val outboundMessageCount: Long = 0,
    public val inboundMessageCount: Long = 0,
    public val heartbeatSentCount: Long = 0,
    public val heartbeatReceivedCount: Long = 0,
    public val lastOutboundRef: String? = null,
    public val lastInboundRef: String? = null,
)

/**
 * A low-level protocol event observed on the socket, streamed by
 * [RealtimeClient.debugEvents]. Covers every raw [RealtimeMessage] in and out,
 * heartbeats, and the buffer-overflow drop signals — for diagnostics and tests,
 * not for normal application logic (use [RealtimeSubscription.asFlow] for that).
 */
public sealed interface RealtimeDebugEvent {
    /** A raw [RealtimeMessage] sent to the server. */
    public data class OutboundMessage(
        public val message: RealtimeMessage,
    ) : RealtimeDebugEvent

    /**
     * Emitted when an outbound application message is dropped from the offline
     * send buffer because it was already full ([capacity] messages). The oldest
     * buffered message is discarded to make room for the newest. Fire-and-forget
     * senders can observe this to detect data lost during a prolonged disconnect.
     */
    public data class OutboundMessageDropped(
        public val message: RealtimeMessage,
        public val capacity: Int,
    ) : RealtimeDebugEvent

    /** A raw [RealtimeMessage] received from the server. */
    public data class InboundMessage(
        public val message: RealtimeMessage,
    ) : RealtimeDebugEvent

    /**
     * Emitted when a decoded inbound [RealtimeEvent] is dropped from a
     * subscription's event flow because a slow collector left the buffer full
     * ([capacity] events). The oldest buffered event is discarded to make room
     * for the newest. Realtime delivery is best-effort under backpressure: a
     * stalled collector loses oldest events rather than stalling the socket
     * (heartbeats and other subscriptions keep flowing). The mirror of
     * [OutboundMessageDropped] for the inbound path.
     */
    public data class InboundEventDropped(
        public val topic: String,
        public val event: RealtimeEvent,
        public val capacity: Int,
    ) : RealtimeDebugEvent

    /** A heartbeat was sent, tagged with its Phoenix message [ref]. */
    public data class HeartbeatSent(
        public val ref: String,
    ) : RealtimeDebugEvent

    /** A heartbeat reply was received, echoing the [ref] of the sent heartbeat
     * (`null` if the server omitted it). */
    public data class HeartbeatReceived(
        public val ref: String?,
    ) : RealtimeDebugEvent
}
