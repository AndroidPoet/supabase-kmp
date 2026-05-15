package io.github.androidpoet.supabase.core.models
import kotlinx.serialization.Serializable
@Serializable
public data class PostgrestResponse<T>(
    public val data: T,
    public val count: Long? = null,
)
@Serializable
public data class ErrorResponse(
    public val message: String,
    public val code: String? = null,
    public val details: String? = null,
    public val hint: String? = null,
)
