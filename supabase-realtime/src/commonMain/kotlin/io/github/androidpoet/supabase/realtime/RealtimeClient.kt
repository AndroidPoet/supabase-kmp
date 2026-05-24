package io.github.androidpoet.supabase.realtime
import io.github.androidpoet.supabase.realtime.models.RealtimeChannel
import kotlinx.coroutines.flow.StateFlow
public interface RealtimeClient {
    public val connectionState: StateFlow<ConnectionState>
    public val isConnected: Boolean
    public fun channel(name: String): RealtimeChannelBuilder
    public fun getSubscription(name: String): RealtimeSubscription?
    public fun getSubscriptionByTopic(topic: String): RealtimeSubscription?
    public fun getSubscriptions(): Set<RealtimeSubscription>
    public fun activeChannels(): Set<String>
    public fun activeChannelDetails(): Set<RealtimeChannel>
    public suspend fun removeSubscription(subscription: RealtimeSubscription)
    public suspend fun removeSubscriptions(subscriptions: List<RealtimeSubscription>)
    public suspend fun removeChannel(subscription: RealtimeSubscription)
    public suspend fun removeSubscriptionByTopic(topic: String)
    public suspend fun removeChannelsByTopic(topics: List<String>)
    public suspend fun removeChannel(name: String)
    public suspend fun removeAllChannels()
    public suspend fun setAuth(token: String? = null)
    public suspend fun connect()
    public suspend fun disconnect()
}
