package io.github.androidpoet.supabase.realtime
import io.github.androidpoet.supabase.realtime.models.RealtimeChannel
import io.github.androidpoet.supabase.realtime.models.RealtimeMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

public interface RealtimeClient {
    public val connectionState: StateFlow<ConnectionState>
    public val debugState: StateFlow<RealtimeDebugState>
    public val debugEvents: Flow<RealtimeDebugEvent>
    public val isConnected: Boolean
    public val isConnecting: Boolean
    public val isDisconnecting: Boolean

    public fun channel(name: String): RealtimeChannelBuilder

    public fun getSubscription(name: String): RealtimeSubscription?

    public fun getSubscriptionByTopic(topic: String): RealtimeSubscription?

    public fun getSubscriptions(): Set<RealtimeSubscription>

    public fun activeChannels(): Set<String>

    public fun activeChannelDetails(): Set<RealtimeChannel>

    public suspend fun removeSubscription(subscription: RealtimeSubscription)

    public suspend fun removeSubscriptions(subscriptions: List<RealtimeSubscription>)

    @Deprecated("Use removeSubscription instead", ReplaceWith("removeSubscription(subscription)"))
    public suspend fun removeChannel(subscription: RealtimeSubscription)

    public suspend fun removeSubscriptionByTopic(topic: String)

    public suspend fun removeChannelsByTopic(topics: List<String>)

    public suspend fun removeChannel(name: String)

    public suspend fun removeAllChannels()

    public suspend fun setAuth(token: String? = null)

    public suspend fun sendHeartbeat()

    public suspend fun connect()

    public suspend fun disconnect()

    /**
     * Disconnects and releases the underlying HTTP/WebSocket engine. The client
     * cannot be reused afterwards. Call this when you are done with the client to
     * avoid leaking the engine's connection pool and threads. Prefer [disconnect]
     * if you intend to [connect] again later.
     */
    public suspend fun close()
}

public data class RealtimeDebugState(
    public val outboundMessageCount: Long = 0,
    public val inboundMessageCount: Long = 0,
    public val heartbeatSentCount: Long = 0,
    public val heartbeatReceivedCount: Long = 0,
    public val lastOutboundRef: String? = null,
    public val lastInboundRef: String? = null,
)

public sealed interface RealtimeDebugEvent {
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

    public data class InboundMessage(
        public val message: RealtimeMessage,
    ) : RealtimeDebugEvent

    public data class HeartbeatSent(
        public val ref: String,
    ) : RealtimeDebugEvent

    public data class HeartbeatReceived(
        public val ref: String?,
    ) : RealtimeDebugEvent
}
