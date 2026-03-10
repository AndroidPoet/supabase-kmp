package io.github.androidpoet.supabase.core.result

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Represents an error returned by the Supabase API.
 *
 * Supabase errors carry a human-readable [message] plus optional structured
 * metadata: a machine-readable [code], arbitrary [details] (JSON), and a
 * [hint] suggesting how to resolve the problem.
 */
@Serializable
public data class SupabaseError(
    public val message: String,
    public val code: String? = null,
    public val details: JsonElement? = null,
    public val hint: String? = null,
)

/**
 * Exception wrapper around [SupabaseError] for use in throw/catch flows.
 */
public class SupabaseException(
    public val error: SupabaseError,
) : Exception(error.message)

/**
 * Converts this [SupabaseError] into a throwable [SupabaseException].
 */
public fun SupabaseError.toException(): SupabaseException = SupabaseException(this)
