package io.github.androidpoet.supabase.functions
import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlin.concurrent.Volatile

internal class FunctionsClientImpl(
    private val client: SupabaseClient,
) : FunctionsClient {
    // setAuth() and invoke() can run on different threads; @Volatile guarantees a
    // pinned token written on one thread is visible to a request on another.
    @Volatile
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
        // Edge Functions commonly branch on Content-Type (req.json() vs req.text()).
        // Default a JSON body's content type unless the caller set one explicitly.
        val withContentType =
            if (body != null && merged.keys.none { it.equals("Content-Type", ignoreCase = true) }) {
                merged + ("Content-Type" to "application/json")
            } else {
                merged
            }
        return client.post(
            endpoint = "${FunctionsPaths.BASE}/$functionName",
            body = body,
            headers = withContentType,
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
            url = "${FunctionsPaths.BASE}/$functionName",
            body = body,
            contentType = contentType,
            headers = merged,
        )
    }

    private fun buildHeaders(
        extra: Map<String, String>,
        region: FunctionRegion?,
    ): Map<String, String> =
        buildMap {
            // Prefer an explicitly pinned token, otherwise fall back to the client's
            // current session token so refreshes are picked up automatically.
            (authToken ?: client.accessTokenOrNull)?.let { put("Authorization", "Bearer $it") }
            if (region != null) {
                put("x-region", region.value)
            }
            putAll(extra)
        }
}
