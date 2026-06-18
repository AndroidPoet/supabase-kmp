package io.github.androidpoet.supabase.realtime

public data class RealtimeConfig(
    public val autoReconnect: Boolean = true,
    public val initialReconnectDelayMs: Long = 1_000L,
    public val maxReconnectDelayMs: Long = 30_000L,
    public val backoffMultiplier: Double = 2.0,
    /**
     * When true, the computed reconnect backoff is randomized between half and
     * the full exponential delay ("equal jitter"), so a fleet of clients that
     * all dropped at once don't reconnect in lockstep and stampede the server.
     */
    public val reconnectJitter: Boolean = true,
    public val maxReconnectAttempts: Int = 0,
    public val heartbeatIntervalMs: Long = 25_000L,
    public val connectionTimeoutMs: Long = 10_000L,
)
