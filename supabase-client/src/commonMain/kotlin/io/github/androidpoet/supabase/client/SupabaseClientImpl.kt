package io.github.androidpoet.supabase.client
import io.github.androidpoet.supabase.client.transport.HttpTransport
import io.github.androidpoet.supabase.core.result.SupabaseResult
internal class SupabaseClientImpl(
    override val projectUrl: String,
    override val apiKey: String,
    private val transport: HttpTransport,
) : SupabaseClient {
    override suspend fun get(
        endpoint: String,
        queryParams: List<Pair<String, String>>,
        headers: Map<String, String>,
    ): SupabaseResult<String> =
        transport.get(url = "$projectUrl$endpoint", queryParams = queryParams, headers = headers)
    override suspend fun post(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> =
        transport.post(url = "$projectUrl$endpoint", body = body, headers = headers)
    override suspend fun patch(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> =
        transport.patch(url = "$projectUrl$endpoint", body = body, headers = headers)
    override suspend fun delete(
        endpoint: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> =
        transport.delete(url = "$projectUrl$endpoint", headers = headers)
    override suspend fun postRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> =
        transport.postRaw(url = url, body = body, contentType = contentType, headers = headers)
    override fun setAccessToken(token: String) {
        transport.setAccessToken(token)
    }
    override fun clearAccessToken() {
        transport.clearAccessToken()
    }
    override fun close() {
        transport.close()
    }
}
