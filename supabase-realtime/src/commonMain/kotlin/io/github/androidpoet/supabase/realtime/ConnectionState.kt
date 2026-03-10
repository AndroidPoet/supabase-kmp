package io.github.androidpoet.supabase.realtime

/**
 * Represents the current state of the Realtime WebSocket connection.
 *
 * Observe via [RealtimeClient.connectionState] to react to connection
 * lifecycle events in your UI or business logic.
 */
public sealed interface ConnectionState {
    /** Not connected and not attempting to connect. */
    public data object Disconnected : ConnectionState

    /** Attempting to establish connection. */
    public data object Connecting : ConnectionState

    /** Connected and operational. */
    public data object Connected : ConnectionState

    /** Connection lost, waiting to reconnect. */
    public data class Reconnecting(
        public val attempt: Int,
        public val nextRetryMs: Long,
    ) : ConnectionState

    /** Reconnection attempts exhausted or manually stopped. */
    public data class Failed(
        public val reason: String,
        public val attempts: Int,
    ) : ConnectionState
}
