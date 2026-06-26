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
        method: FunctionMethod,
        headers: Map<String, String>,
        region: FunctionRegion?,
    ): SupabaseResult<String> {
        val endpoint = endpointFor(functionName, region)
        val merged = buildHeaders(headers, region)
        // GET requests carry no body, so a request body and its Content-Type are
        // meaningless; dispatch a bodyless GET and ignore any supplied body.
        if (method == FunctionMethod.GET) {
            return client.get(endpoint = endpoint, headers = merged)
        }
        // Edge Functions commonly branch on Content-Type (req.json() vs req.text()).
        // Default a JSON body's content type unless the caller set one explicitly.
        val withContentType =
            if (body != null && merged.keys.none { it.equals("Content-Type", ignoreCase = true) }) {
                merged + ("Content-Type" to "application/json")
            } else {
                merged
            }
        return when (method) {
            FunctionMethod.POST -> client.post(endpoint = endpoint, body = body, headers = withContentType)
            FunctionMethod.PUT -> client.put(endpoint = endpoint, body = body, headers = withContentType)
            FunctionMethod.PATCH -> client.patch(endpoint = endpoint, body = body, headers = withContentType)
            FunctionMethod.DELETE -> client.delete(endpoint = endpoint, body = body, headers = withContentType)
            // GET is handled above before the body/Content-Type logic.
            FunctionMethod.GET -> client.get(endpoint = endpoint, headers = merged)
        }
    }

    override suspend fun invokeWithBody(
        functionName: String,
        body: ByteArray,
        contentType: String,
        method: FunctionMethod,
        headers: Map<String, String>,
        region: FunctionRegion?,
    ): SupabaseResult<String> {
        // postRaw/putRaw already prepend projectUrl (see SupabaseClientImpl);
        // pass a relative path or the project URL is duplicated.
        val url = endpointFor(functionName, region)
        val merged = buildHeaders(headers, region)
        return when (method) {
            FunctionMethod.PUT -> client.putRaw(url = url, body = body, contentType = contentType, headers = merged)
            // POST is the default; PATCH/DELETE/GET have no raw-byte verb, so fall
            // back to a raw POST rather than dropping the binary body.
            else -> client.postRaw(url = url, body = body, contentType = contentType, headers = merged)
        }
    }

    override fun invokeSse(
        functionName: String,
        body: String?,
        contentType: String,
        headers: Map<String, String>,
        region: FunctionRegion?,
    ): Flow<FunctionServerSentEvent> {
        val merged = buildHeaders(headers, region)
        val lines =
            client.streamLines(
                endpoint = endpointFor(functionName, region),
                body = body,
                contentType = contentType,
                headers = merged,
            )
        return parseServerSentEvents(lines)
    }

    // A pinned region is sent BOTH as the `x-region` header (see buildHeaders) and as
    // the `forceFunctionRegion` query param, matching functions-js — the gateway treats
    // the query param as the authoritative routing override, so the header alone leaves
    // the pin weaker than the reference client. ANY means "no pin", so the param is
    // omitted. Region values are AWS-style codes (alphanumeric + hyphen), so they need
    // no percent-encoding.
    private fun endpointFor(
        functionName: String,
        region: FunctionRegion?,
    ): String {
        val base = "${FunctionsPaths.BASE}/$functionName"
        return if (region != null && region != FunctionRegion.ANY) {
            "$base?forceFunctionRegion=${region.value}"
        } else {
            base
        }
    }

    private fun buildHeaders(
        extra: Map<String, String>,
        region: FunctionRegion?,
    ): Map<String, String> =
        buildMap {
            // Prefer an explicitly pinned token, otherwise fall back to the client's
            // current session token so refreshes are picked up automatically.
            (authToken ?: client.accessTokenOrNull)?.let { put("Authorization", "Bearer $it") }
            // ANY means "no region pin, let the platform route", so omit the header
            // entirely rather than sending a literal `x-region: any`.
            if (region != null && region != FunctionRegion.ANY) {
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
 * `data` lines are joined with `\n`.
 *
 * An event is dispatched when it carries `data`, or carries an `event:` type with
 * no data (streaming functions use that for terminal signals such as
 * `event: done`); a block with neither is skipped. The last-event-id **persists**
 * across events — an event that omits `id:` inherits the most recent one, matching
 * the browser `EventSource.lastEventId` semantics — so it is deliberately NOT reset
 * on dispatch (which is also why an `id:`-only block does not, on its own, emit a
 * spurious event after a later keep-alive). A trailing event with no terminating
 * blank line is flushed on close.
 */
internal fun parseServerSentEvents(lines: Flow<String>): Flow<FunctionServerSentEvent> =
    flow {
        val data = StringBuilder()
        var event: String? = null
        var id: String? = null
        var hasData = false

        suspend fun dispatch() {
            if (hasData || event != null) {
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
            hasData = false
            // `id` is the persistent last-event-id and is intentionally NOT reset here.
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
