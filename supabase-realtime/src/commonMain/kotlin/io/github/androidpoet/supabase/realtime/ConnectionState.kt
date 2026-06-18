package io.github.androidpoet.supabase.realtime

/**
 * The lifecycle state of the Realtime WebSocket, exposed as
 * [RealtimeClient.connectionState]. Drive reconnect indicators and gate work off
 * these; [Reconnecting] and [Failed] carry the attempt count from the
 * [RealtimeConfig] backoff loop.
 */
public sealed interface ConnectionState {
    /** No socket open and no reconnect in progress (the initial state and the
     * resting state after [RealtimeClient.disconnect]). */
    public data object Disconnected : ConnectionState

    /** The first connection attempt is in flight. */
    public data object Connecting : ConnectionState

    /** The socket is open and joins can proceed. */
    public data object Connected : ConnectionState

    /** A graceful shutdown is in progress. */
    public data object Disconnecting : ConnectionState

    /**
     * The socket dropped and an automatic reconnect is scheduled. [attempt] is the
     * 1-based retry number and [nextRetryMs] the backoff delay before it (see
     * [RealtimeConfig]).
     */
    public data class Reconnecting(
        public val attempt: Int,
        public val nextRetryMs: Long,
    ) : ConnectionState

    /**
     * Reconnection gave up after [attempts] tries (when
     * `maxReconnectAttempts` is exceeded), with [reason] describing the last
     * failure. A terminal state until a manual [RealtimeClient.connect].
     */
    public data class Failed(
        public val reason: String,
        public val attempts: Int,
    ) : ConnectionState
}
