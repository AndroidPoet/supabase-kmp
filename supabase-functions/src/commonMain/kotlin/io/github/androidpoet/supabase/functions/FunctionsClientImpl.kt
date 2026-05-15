package io.github.androidpoet.supabase.functions
import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.core.result.SupabaseResult
internal class FunctionsClientImpl(
    private val client: SupabaseClient,
) : FunctionsClient {
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
        return client.postRaw(
            url = "${client.projectUrl}/functions/v1/$functionName",
            body = body,
            contentType = contentType,
            headers = merged,
        )
    }
    private fun buildHeaders(
        extra: Map<String, String>,
        region: FunctionRegion?,
    ): Map<String, String> = buildMap {
        putAll(extra)
        if (region != null) {
            put("x-region", region.value)
        }
    }
}
