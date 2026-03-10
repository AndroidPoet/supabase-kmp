package io.github.androidpoet.supabase.realtime

import kotlinx.coroutines.flow.StateFlow

/**
 * Entry point for Supabase Realtime operations.
 *
 * Manages the WebSocket lifecycle and provides a builder for subscribing
 * to channels that carry Postgres changes, broadcasts, and presence events.
 *
 * ```kotlin
 * val realtime: RealtimeClient = get()
 * realtime.connect()
 *
 * val subscription = realtime
 *     .channel("chat")
 *     .onPostgresChange(table = "messages") { change -> /* … */ }
 *     .subscribe()
 * ```
 */
public interface RealtimeClient {

    /** Connection state as a hot flow. */
    public val connectionState: StateFlow<ConnectionState>

    /** Whether currently connected. */
    public val isConnected: Boolean

    /** Returns a [RealtimeChannelBuilder] for the given channel [name]. */
    public fun channel(name: String): RealtimeChannelBuilder

    /** Opens the WebSocket connection to the Supabase Realtime server. */
    public suspend fun connect()

    /** Closes the WebSocket connection and cleans up resources. */
    public suspend fun disconnect()
}
