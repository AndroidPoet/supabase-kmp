package io.github.androidpoet.supabase.realtime

/**
 * Configuration for the Realtime WebSocket connection.
 *
 * Controls reconnection behavior, heartbeat timing, and connection timeouts.
 */
public data class RealtimeConfig(
    /** Whether to automatically reconnect on disconnect. Default: true */
    public val autoReconnect: Boolean = true,
    /** Initial reconnect delay in milliseconds. Default: 1000 */
    public val initialReconnectDelayMs: Long = 1_000L,
    /** Maximum reconnect delay in milliseconds. Default: 30000 */
    public val maxReconnectDelayMs: Long = 30_000L,
    /** Backoff multiplier for exponential backoff. Default: 2.0 */
    public val backoffMultiplier: Double = 2.0,
    /** Maximum number of reconnect attempts. 0 = unlimited. Default: 0 */
    public val maxReconnectAttempts: Int = 0,
    /** Heartbeat interval in milliseconds. Default: 25000 (Supabase standard) */
    public val heartbeatIntervalMs: Long = 25_000L,
    /** Connection timeout in milliseconds. Default: 10000 */
    public val connectionTimeoutMs: Long = 10_000L,
)
