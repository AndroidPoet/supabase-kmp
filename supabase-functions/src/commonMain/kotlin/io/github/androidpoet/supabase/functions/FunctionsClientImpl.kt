package io.github.androidpoet.supabase.functions
import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.core.result.SupabaseResult
internal class FunctionsClientImpl(
    private val client: SupabaseClient,
) : FunctionsClient {
    private var authToken: String? = null

    override fun setAuth(token: String) {
        authToken = token
    }

    override suspend fun invoke(
        functionName: String,
        body: String?,
        headers: Map<String, String>,
        region: FunctionRegion?,
    ): SupabaseResult<String> {
        val merged = buildHeaders(headers, region)
        return client.post(
            endpoint = "/functions/v1/$functionName",
            body = body,
            headers = merged,
        )
    }
    override suspend fun invokeWithBody(
        functionName: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
        region: FunctionRegion?,
    ): SupabaseResult<String> {
        val merged = buildHeaders(headers, region)
        // postRaw already prepends projectUrl (see SupabaseClientImpl.postRaw);
        // pass a relative path or the project URL is duplicated.
        return client.postRaw(
            url = "/functions/v1/$functionName",
            body = body,
            contentType = contentType,
            headers = merged,
        )
    }
    private fun buildHeaders(
        extra: Map<String, String>,
        region: FunctionRegion?,
    ): Map<String, String> = buildMap {
        // Prefer an explicitly pinned token, otherwise fall back to the client's
        // current session token so refreshes are picked up automatically.
        (authToken ?: client.accessTokenOrNull)?.let { put("Authorization", "Bearer $it") }
        if (region != null) {
            put("x-region", region.value)
        }
        putAll(extra)
    }
}
