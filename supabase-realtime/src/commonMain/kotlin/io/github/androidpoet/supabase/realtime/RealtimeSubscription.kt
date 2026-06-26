package io.github.androidpoet.supabase.realtime
import io.github.androidpoet.supabase.core.result.SupabaseResult
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
    public val channelName: String

    /** The channel's join lifecycle as a [StateFlow]; await `SUBSCRIBED` or use
     * `awaitSubscribed`. */
    public val status: StateFlow<Status>

    /**
     * **Hot** [Flow] of every decoded inbound [RealtimeEvent] for this channel —
     * postgres changes, broadcasts and presence events interleaved. This is a shared,
     * best-effort multicast, **not** a cold flow: collecting it does **not** start or stop
     * the subscription (lifecycle is owned by [RealtimeClient.subscribe]/[unsubscribe]), and
     * events delivered before a collector attaches are not replayed (`replay = 0`). Under
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
     * Broadcasts a raw binary [payload] under the [event] name, sent over the
     * WebSocket as a binary frame instead of JSON. Received as
     * [RealtimeEvent.BinaryBroadcast] / `binaryBroadcastFlow`. Prefer this over
     * base64-in-[broadcast] for compact, high-frequency or non-textual data (sensor
     * streams, image frames, encrypted bytes).
     *
     * Fire-and-forget like [broadcast]: if the socket is momentarily disconnected
     * the frame is dropped rather than buffered, since binary broadcasts are
     * transient. Requires the channel to be subscribed to reach other clients.
     */
    public suspend fun broadcastBinary(event: String, payload: ByteArray)

    /**
     * Broadcasts [payload] under the [event] name like [broadcast], but awaits the
     * server's acknowledgement instead of returning fire-and-forget. Requires the
     * channel to have been configured with `acknowledgeBroadcasts` (see
     * [RealtimeChannelBuilder.configureBroadcast]) so the server replies with a
     * `phx_reply` for the push.
     *
     * Returns [SupabaseResult.Success] once the server acknowledges the broadcast,
     * or [SupabaseResult.Failure] if the channel is not subscribed, the server
     * rejects the push, the connection drops before the ack arrives, or no ack is
     * received within [timeoutMillis]. Never throws for these expected failures;
     * genuine caller cancellation still propagates.
     */
    public suspend fun broadcastWithAck(
        event: String,
        payload: JsonObject,
        timeoutMillis: Long = 5_000,
    ): SupabaseResult<Unit>

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
