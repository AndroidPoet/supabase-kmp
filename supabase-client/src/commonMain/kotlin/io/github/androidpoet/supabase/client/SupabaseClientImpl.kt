package io.github.androidpoet.supabase.client
import io.github.androidpoet.supabase.client.transport.HttpTransport
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.flow.Flow

internal class SupabaseClientImpl(
    override val projectUrl: String,
    override val apiKey: String,
    private val transport: HttpTransport,
) : SupabaseClient {
    override val accessTokenOrNull: String?
        get() = transport.accessTokenOrNull

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

    override suspend fun put(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> =
        transport.put(url = "$projectUrl$endpoint", body = body, headers = headers)

    override suspend fun patch(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> =
        transport.patch(url = "$projectUrl$endpoint", body = body, headers = headers)

    override suspend fun delete(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> =
        transport.delete(url = "$projectUrl$endpoint", body = body, headers = headers)

    override suspend fun postRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> =
        transport.postRaw(url = resolveUrl(url), body = body, contentType = contentType, headers = headers)

    override suspend fun putRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> =
        transport.putRaw(url = resolveUrl(url), body = body, contentType = contentType, headers = headers)

    override suspend fun rawRequest(
        method: SupabaseHttpMethod,
        url: String,
        body: ByteArray?,
        contentType: String?,
        headers: Map<String, String>,
    ): SupabaseResult<SupabaseHttpResponse> =
        transport.rawRequest(
            method = method.name,
            url = resolveUrl(url),
            body = body,
            contentType = contentType,
            requestHeaders = headers,
        )

    override fun streamLines(
        endpoint: String,
        body: String?,
        contentType: String?,
        headers: Map<String, String>,
    ): Flow<String> =
        transport.streamLines(url = resolveUrl(endpoint), body = body, contentType = contentType, headers = headers)

    // An already-absolute `http(s)://` URL is used as-is; anything else is treated
    // as an endpoint path and prefixed with the project URL. Shared by every raw
    // verb (postRaw/putRaw/rawRequest/streamLines) so passing a full URL is always
    // safe and never double-prefixed into `https://proj.supabase.cohttps://…`.
    private fun resolveUrl(url: String): String =
        if (url.startsWith("http://") || url.startsWith("https://")) url else "$projectUrl$url"

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
