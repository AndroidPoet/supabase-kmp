package io.github.androidpoet.supabase.client

import kotlin.random.Random

/**
 * Tunable retry policy for transient request failures.
 *
 * Retries are still opt-in per call (e.g. PostgREST `select` with `retry = true`,
 * which is safe for idempotent reads); this config controls *how* a retried call
 * backs off and which statuses count as transient. A `Retry-After` response
 * header always takes precedence over the computed backoff.
 *
 * The backoff ceiling is exponential — `min(maxDelayMillis, baseDelayMillis shl attempt)` —
 * and (when [jitter] is enabled, the default) the actual delay is drawn uniformly from
 * `floor..ceiling` ("equal jitter", where floor is half the ceiling capped at the base).
 * Jitter de-synchronises clients that failed together so they don't all retry on the same
 * beat and re-overload a recovering service.
 */
public data class RetryConfig(
    /** Maximum retry attempts after the initial try (0 disables retries). */
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    /** Base backoff for the first retry; doubles each subsequent attempt. */
    val baseDelayMillis: Long = DEFAULT_BASE_DELAY_MILLIS,
    /** Upper bound on a single backoff delay. */
    val maxDelayMillis: Long = DEFAULT_MAX_DELAY_MILLIS,
    /** HTTP status codes treated as transient and therefore retryable. */
    val retryableStatuses: Set<Int> = DEFAULT_RETRYABLE_STATUSES,
    /** When true (default), randomise each backoff within its exponential ceiling. */
    val jitter: Boolean = true,
) {
    init {
        require(maxRetries >= 0) { "maxRetries must be >= 0 (got $maxRetries)" }
        require(baseDelayMillis >= 0) { "baseDelayMillis must be >= 0 (got $baseDelayMillis)" }
        require(maxDelayMillis >= baseDelayMillis) {
            "maxDelayMillis ($maxDelayMillis) must be >= baseDelayMillis ($baseDelayMillis)"
        }
    }

    /**
     * Backoff delay (ms) before the given zero-based retry [attempt].
     *
     * Returns the exponential ceiling when [jitter] is off, or a uniform draw from
     * `floor..ceiling` when on. The floor (half the ceiling, capped at [baseDelayMillis])
     * keeps a jittered delay from collapsing to ~0 while still spreading clients out.
     */
    public fun backoffMillis(attempt: Int): Long {
        // shl can overflow for large attempts; clamp the shift then the result.
        val shift = attempt.coerceIn(0, MAX_SHIFT)
        val raw = baseDelayMillis shl shift
        val ceiling = if (raw < 0) maxDelayMillis else raw.coerceAtMost(maxDelayMillis)
        if (!jitter || ceiling <= 0) return ceiling
        val floor = (ceiling / 2).coerceAtMost(baseDelayMillis)
        // nextLong's upper bound is exclusive; +1 keeps the ceiling reachable.
        return Random.nextLong(floor, ceiling + 1)
    }

    public companion object {
        public const val DEFAULT_MAX_RETRIES: Int = 3
        public const val DEFAULT_BASE_DELAY_MILLIS: Long = 1_000
        public const val DEFAULT_MAX_DELAY_MILLIS: Long = 30_000
        private const val MAX_SHIFT: Int = 16

        /** 429 + the 5xx codes Supabase services return for transient failures. */
        public val DEFAULT_RETRYABLE_STATUSES: Set<Int> = setOf(429, 500, 502, 503, 504, 520)

        /** Default policy: 3 retries, 1s base, 30s cap. */
        public val Default: RetryConfig = RetryConfig()
    }
}
