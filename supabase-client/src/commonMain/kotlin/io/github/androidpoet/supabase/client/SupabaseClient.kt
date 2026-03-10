package io.github.androidpoet.supabase.client

import io.github.androidpoet.supabase.core.result.SupabaseResult

/**
 * The primary interface for communicating with a Supabase project.
 *
 * Consumers obtain an instance via [Supabase.create] and use it to perform
 * REST operations against any Supabase service (PostgREST, Auth, Storage,
 * Edge Functions). Higher-level modules wrap these raw HTTP calls with
 * typed, domain-specific APIs.
 */
public interface SupabaseClient {

    /** The base URL of the Supabase project (e.g. `https://xyz.supabase.co`). */
    public val projectUrl: String

    /** The project's anon or service-role API key. */
    public val apiKey: String

    /** Performs a GET request to [endpoint] with optional [queryParams] and [headers]. */
    public suspend fun get(
        endpoint: String,
        queryParams: List<Pair<String, String>> = emptyList(),
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>

    /** Performs a POST request to [endpoint] with an optional JSON [body] and [headers]. */
    public suspend fun post(
        endpoint: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>

    /** Performs a PATCH request to [endpoint] with an optional JSON [body] and [headers]. */
    public suspend fun patch(
        endpoint: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>

    /** Performs a DELETE request to [endpoint] with optional [headers]. */
    public suspend fun delete(
        endpoint: String,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>

    /** Performs a POST with raw binary [body] and explicit [contentType]. */
    public suspend fun postRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>

    /** Sets an authenticated user's JWT for subsequent requests. */
    public fun setAccessToken(token: String)

    /** Clears the user JWT, reverting to anon-key authorization. */
    public fun clearAccessToken()

    /** Releases underlying HTTP resources. */
    public fun close()
}
