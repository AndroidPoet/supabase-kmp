package io.github.androidpoet.supabase.core.models
import kotlinx.serialization.Serializable

/**
 * A successful PostgREST response: the decoded [data] plus an optional row [count].
 *
 * [count] is populated only when the request asked for it (a `Prefer: count=…`
 * header), e.g. to drive pagination; it is `null` otherwise.
 *
 * @param T the decoded body type (a row, a list of rows, or a scalar).
 */
@Serializable
public data class PostgrestResponse<T>(
    public val data: T,
    public val count: Long? = null,
)

/**
 * The shape of a PostgREST/GoTrue error body, decoded before being mapped into a
 * [io.github.androidpoet.supabase.core.result.SupabaseError].
 *
 * @param message human-readable error message.
 * @param code machine-readable error code, when the server supplies one.
 * @param details additional context about the failure, when present.
 * @param hint a suggested remedy, when present.
 */
@Serializable
public data class ErrorResponse(
    public val message: String = "",
    public val code: String? = null,
    public val details: String? = null,
    public val hint: String? = null,
)
