package io.github.androidpoet.supabase.realtime
import kotlinx.coroutines.flow.StateFlow
public interface RealtimeClient {
    public val connectionState: StateFlow<ConnectionState>
    public val isConnected: Boolean
    public fun channel(name: String): RealtimeChannelBuilder
    public suspend fun connect()
    public suspend fun disconnect()
}
