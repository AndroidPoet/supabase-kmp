package io.github.androidpoet.supabase.realtime
import io.github.androidpoet.supabase.realtime.models.PresenceState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

/**
 * A live subscription to one joined Realtime channel: the handle returned by
 * [RealtimeChannelBuilder.subscribe] for observing events and sending on the
 * channel.
 *
 * Collect [asFlow] (or the typed flows in `RealtimeExt`) for incoming
 * `postgres_changes`/`broadcast`/`presence` events as [RealtimeEvent]s; send with
 * [broadcast], publish presence with [track]/[untrack]. Track join progress via
 * [status] and leave with [unsubscribe]. The subscription stays valid across
 * reconnects — the client rejoins automatically.
 */
public interface RealtimeSubscription {
    /** Lifecycle of the channel join, observed via [status]. */
    public enum class Status {
        SUBSCRIBING,
        SUBSCRIBED,
        UNSUBSCRIBING,
        UNSUBSCRIBED,
        ERROR,
    }

    /** The kind of message [send] dispatches, with its Phoenix [wireValue]. */
    public enum class SendType(
        public val wireValue: String,
    ) {
        BROADCAST("broadcast"),
        PRESENCE("presence"),
        POSTGRES_CHANGES("postgres_changes"),
    }

    /** The channel name (the part after `realtime:`). */
    public val channel: String

    /** The channel's join lifecycle as a [StateFlow]; await `SUBSCRIBED` or use
     * `awaitSubscribed`. */
    public val status: StateFlow<Status>

    /**
     * Cold [Flow] of every decoded inbound [RealtimeEvent] for this channel —
     * postgres changes, broadcasts and presence events interleaved. Under
     * backpressure a slow collector loses oldest events (signaled by
     * [RealtimeDebugEvent.InboundEventDropped]) rather than stalling the socket.
     * For one event kind, prefer the typed flows in `RealtimeExt`.
     */
    public fun asFlow(): Flow<RealtimeEvent>

    /**
     * The current cumulative presence state — every member currently tracked on
     * this channel, keyed by presence key — as of the last `presence_state` or
     * `presence_diff` received. Returns an empty map before the first sync.
     */
    public fun presenceState(): PresenceState

    /** Leaves the channel (`UNSUBSCRIBED`) and stops delivery; ends [asFlow]
     * collectors. Equivalent to [RealtimeClient.removeSubscription] for this one. */
    public suspend fun unsubscribe()

    /**
     * Low-level send of a Phoenix message of [type] with the given [event] name
     * and optional [payload] on this channel. Prefer [broadcast]/[track] for the
     * common cases; reach for this only for events they don't cover.
     */
    public suspend fun send(type: SendType, event: String, payload: JsonObject? = null)

    /**
     * Broadcasts [payload] under the [event] name to other subscribers of this
     * channel (and to this client too if `receiveOwnBroadcasts` was set). Received
     * by [RealtimeChannelBuilder.onBroadcast] handlers / `broadcastFlow`.
     */
    public suspend fun broadcast(event: String, payload: JsonObject)

    /**
     * Publishes this client's presence [state] on the channel, making it visible
     * to other members' presence handlers under this subscription's presence key.
     * Call again to replace the tracked state.
     */
    public suspend fun track(state: JsonObject)

    /** Removes this client's presence from the channel (the inverse of [track]),
     * emitting a leave to other members. */
    public suspend fun untrack()
}
