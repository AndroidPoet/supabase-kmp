package io.github.androidpoet.supabase.functions
import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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

    override fun invokeSSE(
        functionName: String,
        body: String?,
        contentType: String,
        headers: Map<String, String>,
        region: FunctionRegion?,
    ): Flow<FunctionServerSentEvent> {
        val merged = buildHeaders(headers, region)
        val lines =
            client.streamLines(
                endpoint = "${FunctionsPaths.BASE}/$functionName",
                body = body,
                contentType = contentType,
                headers = merged,
            )
        return parseServerSentEvents(lines)
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

/**
 * Parses a [Flow] of raw SSE text lines into [FunctionServerSentEvent]s per the
 * WHATWG event-stream rules: a blank line dispatches the accumulated event; `:`
 * lines are comments (keep-alives) and ignored; recognised fields are `data`,
 * `event` and `id`; a single leading space after the colon is stripped; multiple
 * `data` lines are joined with `\n`. Events with no field data at dispatch are
 * skipped. A trailing event with no terminating blank line is flushed on close.
 */
private fun parseServerSentEvents(lines: Flow<String>): Flow<FunctionServerSentEvent> =
    flow {
        val data = StringBuilder()
        var event: String? = null
        var id: String? = null
        var hasData = false

        suspend fun dispatch() {
            if (hasData || event != null || id != null) {
                emit(
                    FunctionServerSentEvent(
                        id = id,
                        event = event,
                        data = if (hasData) data.toString() else null,
                    ),
                )
            }
            data.clear()
            event = null
            id = null
            hasData = false
        }

        lines.collect { raw ->
            // Tolerate CRLF streams: readUTF8Line strips \n but a lone \r can remain.
            val line = raw.removeSuffix("\r")
            when {
                line.isEmpty() -> dispatch()
                line.startsWith(":") -> Unit // comment / keep-alive
                else -> {
                    val colon = line.indexOf(':')
                    val field = if (colon == -1) line else line.substring(0, colon)
                    var value = if (colon == -1) "" else line.substring(colon + 1)
                    if (value.startsWith(" ")) value = value.substring(1)
                    when (field) {
                        "data" -> {
                            if (hasData) data.append('\n')
                            data.append(value)
                            hasData = true
                        }
                        "event" -> event = value
                        "id" -> id = value
                        // "retry" and unknown fields are ignored.
                    }
                }
            }
        }
        // Flush a final event that wasn't terminated by a blank line.
        dispatch()
    }
