package io.github.androidpoet.supabase.realtime
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.realtime.models.PostgresChangeEvent
import io.github.androidpoet.supabase.realtime.models.PresenceState
import kotlinx.serialization.json.JsonObject

/**
 * Configures one Realtime channel — its `postgres_changes`, `broadcast` and
 * `presence` callbacks plus channel options — before joining it.
 *
 * Obtained from [RealtimeClient.channel]; the `onXxx`/`configureXxx` methods
 * return `this` so they chain, and nothing is sent until [subscribe] (or
 * [subscribeWithResult]) joins the Phoenix channel. Callbacks registered here
 * fire for matching server events for the life of the subscription. Once
 * subscribed, work with the resulting [RealtimeSubscription].
 */
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

    /**
     * Subscribes to database `postgres_changes` and delivers each affected row as
     * a raw [JsonObject] to [callback] (the new row for INSERT/UPDATE, the old row
     * for DELETE).
     *
     * Pass [table] (and [schema], default `public`) to scope the subscription and
     * [event] to one change type. [filter] is a single `column=op.value` string —
     * build it with [realtimeFilter] rather than by hand. Realtime allows only one
     * filter per subscription. For typed rows use the reified `onPostgresChange`
     * extension; for the change type alongside the row, use the
     * `(PostgresChangeEvent, JsonObject)` overload.
     *
     * @param filter optional single server-side filter; see [realtimeFilter].
     */
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

    /**
     * Like the single-argument [onPostgresChange] but also passes the resolved
     * [PostgresChangeEvent] (INSERT/UPDATE/DELETE) to [callback], so one handler
     * can branch on the change type while still receiving the affected row.
     *
     * @param filter optional single server-side filter; see [realtimeFilter].
     */
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

    /**
     * Registers [callback] for `broadcast` messages whose `event` name equals
     * [event], delivering the message payload as a [JsonObject]. Send broadcasts
     * with [RealtimeSubscription.broadcast] or [RealtimeClient.broadcast].
     */
    public fun onBroadcast(
        event: String,
        callback: suspend (JsonObject) -> Unit,
    ): RealtimeChannelBuilder =
        apply {
            broadcastCallbacks[event] = callback
        }

    /**
     * Registers [callback] for `presence` sync, invoked with the full cumulative
     * [PresenceState] (every tracked member, keyed by presence key) on each
     * `presence_state`/`presence_diff`. Publish your own state with
     * [RealtimeSubscription.track].
     */
    public fun onPresence(
        callback: suspend (PresenceState) -> Unit,
    ): RealtimeChannelBuilder =
        apply {
            presenceCallback = callback
        }

    /**
     * Tunes broadcast behavior for this channel: [receiveOwnBroadcasts] echoes the
     * sender's own messages back to it (off by default), and
     * [acknowledgeBroadcasts] asks the server to ack each broadcast.
     */
    public fun configureBroadcast(
        receiveOwnBroadcasts: Boolean = false,
        acknowledgeBroadcasts: Boolean = false,
    ): RealtimeChannelBuilder =
        apply {
            this.receiveOwnBroadcasts = receiveOwnBroadcasts
            this.acknowledgeBroadcasts = acknowledgeBroadcasts
        }

    /**
     * Requests replay of recent broadcasts on join: messages from the last
     * [sinceMs] milliseconds, capped at [limit] if given. Lets a late joiner catch
     * up on missed broadcasts the server still retains.
     */
    public fun configureBroadcastReplay(
        sinceMs: Long,
        limit: Int? = null,
    ): RealtimeChannelBuilder =
        apply {
            replaySinceMs = sinceMs
            replayLimit = limit
        }

    /**
     * Sets the presence [key] identifying this client among channel members
     * (defaults to a server-assigned key). Use a stable per-user key to dedupe a
     * user across reconnects or devices.
     */
    public fun configurePresence(
        key: String = "",
    ): RealtimeChannelBuilder =
        apply {
            presenceKey = key
        }

    /**
     * Marks the channel as private ([enabled], default `true`), so the server
     * applies Realtime authorization (RLS) using the client's session JWT. Required
     * for RLS-protected `postgres_changes` and private broadcast/presence channels.
     */
    public fun setPrivate(
        enabled: Boolean = true,
    ): RealtimeChannelBuilder =
        apply {
            privateChannel = enabled
        }

    /**
     * Joins the Phoenix channel with the configured callbacks and returns its
     * [RealtimeSubscription], connecting the socket first if needed. Returns as
     * soon as the join is sent — the subscription may still be `SUBSCRIBING`;
     * use [subscribeWithResult] (or [RealtimeSubscription.status]) to await the
     * server's reply.
     */
    public suspend fun subscribe(): RealtimeSubscription =
        client.subscribe(this)

    /**
     * Like [subscribe], but suspends until the channel join resolves and reports
     * the outcome as a [SupabaseResult] instead of handing back a subscription
     * that may still be joining. Returns [SupabaseResult.Failure] if the join is
     * rejected by the server or times out (after `connectionTimeoutMs`), so a
     * failed subscription no longer has to be detected by polling
     * [RealtimeSubscription.status].
     */
    public suspend fun subscribeWithResult(): SupabaseResult<RealtimeSubscription> =
        client.subscribeWithResult(this)
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
