package io.github.androidpoet.supabase.client

import io.github.androidpoet.supabase.core.result.SupabaseError

/**
 * Observation hooks fired around every HTTP request the SDK makes.
 *
 * Use it for telemetry, tracing, and metrics — emit spans, record latency
 * histograms, count error categories, attach correlation IDs. All methods are
 * `suspend` (so they may do async work) and default to no-ops, so implement only
 * what you need. Hooks are observational: they must not throw and cannot alter
 * the request or its result.
 *
 * Exactly one terminal hook fires per call: [onResponse] when an HTTP response
 * is received (any status, success or error), or [onError] when no response was
 * obtained (offline, DNS/TLS failure, timeout, cancellation-free exception).
 */
public interface SupabaseInterceptor {
    /** Fired before a request is sent. */
    public suspend fun onRequest(
        method: String,
        url: String,
    ) {}

    /** Fired when an HTTP response is received, regardless of status. */
    public suspend fun onResponse(
        method: String,
        url: String,
        statusCode: Int,
        durationMillis: Long,
    ) {}

    /** Fired when the request failed without a response (network/transport error). */
    public suspend fun onError(
        method: String,
        url: String,
        error: SupabaseError,
        durationMillis: Long,
    ) {}
}
