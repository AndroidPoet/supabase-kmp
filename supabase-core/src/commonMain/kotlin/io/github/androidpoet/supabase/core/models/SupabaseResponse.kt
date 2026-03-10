package io.github.androidpoet.supabase.core.models

import kotlinx.serialization.Serializable

/**
 * Generic wrapper for PostgREST responses.
 *
 * [data] carries the deserialized payload and [count] is present when the
 * request included a `Prefer: count=exact` (or planned/estimated) header.
 */
@Serializable
public data class PostgrestResponse<T>(
    public val data: T,
    public val count: Long? = null,
)

/**
 * Standard error body returned by Supabase services (PostgREST, GoTrue,
 * Storage, Realtime, Edge Functions).
 */
@Serializable
public data class ErrorResponse(
    public val message: String,
    public val code: String? = null,
    public val details: String? = null,
    public val hint: String? = null,
)
