package io.github.androidpoet.supabase.client
import io.github.androidpoet.supabase.core.result.SupabaseResult
public interface SupabaseClient {
    public val projectUrl: String
    public val apiKey: String
    public suspend fun get(
        endpoint: String,
        queryParams: List<Pair<String, String>> = emptyList(),
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>
    public suspend fun post(
        endpoint: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>
    public suspend fun patch(
        endpoint: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>
    public suspend fun delete(
        endpoint: String,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>
    public suspend fun postRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>
    public fun setAccessToken(token: String)
    public fun clearAccessToken()
    public fun close()
}
