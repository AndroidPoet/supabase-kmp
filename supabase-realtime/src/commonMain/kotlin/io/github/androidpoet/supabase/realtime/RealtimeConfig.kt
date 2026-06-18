package io.github.androidpoet.supabase.realtime

/**
 * Tuning for the Realtime client's reconnect and heartbeat behavior, passed to
 * [createRealtimeClient]. The defaults are production-sane (exponential backoff
 * with jitter, 25s heartbeat); override only what you need.
 *
 * @param autoReconnect reconnect automatically when the socket drops.
 * @param initialReconnectDelayMs delay before the first reconnect attempt.
 * @param maxReconnectDelayMs upper bound the exponential backoff is clamped to.
 * @param backoffMultiplier factor the delay grows by each failed attempt.
 * @param maxReconnectAttempts give up after this many tries (`0` = retry forever),
 *   transitioning to [ConnectionState.Failed].
 * @param heartbeatIntervalMs how often to send a Phoenix heartbeat to keep the
 *   socket alive.
 * @param connectionTimeoutMs how long to wait for a connection or channel join to
 *   settle before failing it.
 */
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
