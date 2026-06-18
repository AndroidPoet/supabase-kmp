package io.github.androidpoet.supabase.client.transport
import io.github.androidpoet.supabase.client.SupabaseConfig
import io.github.androidpoet.supabase.client.SupabaseHttpResponse
import io.github.androidpoet.supabase.client.SupabaseLogLevel
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseErrorCodes
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.put
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.TimeSource
import io.ktor.client.plugins.logging.Logger as KtorLogger

private const val CLIENT_VERSION = "supabase-kmp/0.6.0"
private const val INTERNAL_RETRY_HEADER = "X-Supabase-Kmp-Retry"
private const val RETRY_COUNT_HEADER = "X-Retry-Count"

internal class HttpTransport(
    private val config: SupabaseConfig,
    engineFactory: HttpClientEngineFactory<*>,
    private val projectUrl: String,
    private val apiKey: String,
) {
    // Written by the session layer on a different coroutine/thread than the
    // request builders that read it; @Volatile guarantees cross-thread visibility
    // (a single reference write/read is already atomic on every target).
    @Volatile
    private var accessToken: String? = null
    internal val accessTokenOrNull: String? get() = accessToken

    // The project key is sent as the `apikey` header on every request and is the
    // authenticator for anonymous requests. Only a JWT belongs in
    // `Authorization: Bearer`: a real session token, or a legacy anon/service_role
    // key (PostgREST derives the DB role from that JWT). The newer non-JWT keys
    // (`sb_publishable_…`/`sb_secret_…`) must NEVER be sent as a Bearer token —
    // for those we omit Authorization and let the `apikey` header authorize the
    // request. JWTs always start with `eyJ` (base64url of `{"alg"…`).
    private fun bearerTokenOrNull(): String? = accessToken ?: apiKey.takeIf { it.startsWith("eyJ") }

    // Resolution order for the Authorization Bearer token, highest priority first:
    //   1. config.accessTokenProvider() when set AND it returns non-null — lets
    //      third-party-auth (Firebase/Auth0) callers inject an external JWT without
    //      our session manager. Resolved fresh per request.
    //   2. the session token set by the auth layer (setAccessToken).
    //   3. a legacy JWT API key (anon/service_role), per bearerTokenOrNull().
    // Returning null from the provider transparently falls through to 2/3. Callers
    // that already supply their own Authorization header bypass this entirely (the
    // per-method guard checks for that before calling here).
    private suspend fun resolveBearerToken(): String? = config.accessTokenProvider?.invoke() ?: bearerTokenOrNull()

    private val errorJson = Json { ignoreUnknownKeys = true }
    private val retry = config.retry
    internal val httpClient: HttpClient =
        HttpClient(engineFactory) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        explicitNulls = false
                    },
                )
            }
            // Timeouts are opt-in (all null by default) so we never silently cap a
            // streaming response or a large transfer. Install only when configured.
            if (config.connectTimeoutMillis != null ||
                config.socketTimeoutMillis != null ||
                config.requestTimeoutMillis != null
            ) {
                install(HttpTimeout) {
                    config.connectTimeoutMillis?.let { connectTimeoutMillis = it }
                    config.socketTimeoutMillis?.let { socketTimeoutMillis = it }
                    config.requestTimeoutMillis?.let { requestTimeoutMillis = it }
                }
            }
            if (config.logging) {
                install(Logging) {
                    level = config.logLevel
                    // Route wire logs into the caller's framework when a logger is supplied;
                    // otherwise fall back to Ktor's default sink.
                    config.logger?.let { sink ->
                        logger =
                            object : KtorLogger {
                                override fun log(message: String) {
                                    sink.log(SupabaseLogLevel.DEBUG, message)
                                }
                            }
                    }
                    // Never let credentials reach the log sink. This covers the anon/service
                    // apikey header and every Bearer token (including the auth-admin
                    // service-role key, which flows through this same client).
                    sanitizeHeader { name ->
                        name.equals("Authorization", ignoreCase = true) ||
                            name.equals("apikey", ignoreCase = true) ||
                            // Cookies carry the GoTrue session (sb-access-token / sb-refresh-token)
                            // on both the request and the response, so redact both directions.
                            name.equals("Cookie", ignoreCase = true) ||
                            name.equals("Set-Cookie", ignoreCase = true)
                    }
                }
            }
            defaultRequest {
                header("apikey", apiKey)
                header("X-Client-Info", CLIENT_VERSION)
                config.headers.forEach { (key, value) -> header(key, value) }
            }
            // Raw Ktor escape hatch — applied LAST so callers can install arbitrary
            // plugins (HttpCache, proxy, custom TLS, …) or override the above. This
            // is not a library plugin DSL; it is the unfiltered Ktor builder.
            config.httpClientConfig?.let { it() }
        }

    suspend fun get(
        url: String,
        queryParams: List<Pair<String, String>> = emptyList(),
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String> {
        val retryEnabled = headers[INTERNAL_RETRY_HEADER]?.toBooleanStrictOrNull() ?: false
        val outgoingHeaders = headers - INTERNAL_RETRY_HEADER
        // Resolve the Bearer token once per request (not per retry attempt) inside
        // execute, so a throwing accessTokenProvider becomes a Failure. The provider
        // lambda is suspend, but Ktor's request builder block is not, so the resolved
        // value is threaded in via the request callback.
        return execute(
            method = "GET",
            url = url,
            retryEnabled = retryEnabled,
            retryableMethod = true,
            resolveBearer = { if (outgoingHeaders.hasAuthorization()) null else resolveBearerToken() },
        ) { attempt, bearer ->
            httpClient.get(url) {
                queryParams.forEach { (k, v) -> this.url.parameters.append(k, v) }
                outgoingHeaders.forEach { (k, v) -> header(k, v) }
                if (attempt > 0) header(RETRY_COUNT_HEADER, attempt.toString())
                if (!outgoingHeaders.hasAuthorization()) {
                    bearer?.let { header("Authorization", "Bearer $it") }
                }
            }
        }
    }

    suspend fun post(
        url: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String> {
        val outgoingHeaders = headers - INTERNAL_RETRY_HEADER
        return execute(
            method = "POST",
            url = url,
            resolveBearer = { if (outgoingHeaders.hasAuthorization()) null else resolveBearerToken() },
        ) { _, bearer ->
            httpClient.post(url) {
                contentType(ContentType.Application.Json)
                body?.let { setBody(it) }
                outgoingHeaders.forEach { (k, v) -> header(k, v) }
                if (!outgoingHeaders.hasAuthorization()) {
                    bearer?.let { header("Authorization", "Bearer $it") }
                }
            }
        }
    }

    /**
     * Streams the response body of a POST as a cold [Flow] of decoded UTF-8 lines,
     * read incrementally off the wire — the basis for consuming Server-Sent Events
     * without buffering the whole (potentially unbounded) response. The connection
     * is opened when collection starts and closed when it ends or is cancelled.
     *
     * A non-2xx status throws inside the flow (so the collector's `catch`/`try`
     * sees it) rather than silently yielding nothing; this mirrors how the buffered
     * helpers surface failures as a terminal signal.
     */
    fun streamLines(
        url: String,
        body: String? = null,
        contentType: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): Flow<String> {
        val outgoingHeaders = headers - INTERNAL_RETRY_HEADER
        return flow {
            val bearer = if (outgoingHeaders.hasAuthorization()) null else resolveBearerToken()
            httpClient
                .preparePost(url) {
                    contentType?.let { contentType(ContentType.parse(it)) }
                    body?.let { setBody(it) }
                    // SSE: ask the server to stream and don't let an intermediary buffer.
                    if (outgoingHeaders.keys.none { it.equals("Accept", ignoreCase = true) }) {
                        header("Accept", "text/event-stream")
                    }
                    outgoingHeaders.forEach { (k, v) -> header(k, v) }
                    if (!outgoingHeaders.hasAuthorization()) {
                        bearer?.let { header("Authorization", "Bearer $it") }
                    }
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        throw IOException("Stream request to $url failed with HTTP ${response.status.value}")
                    }
                    val channel = response.bodyAsChannel()
                    while (true) {
                        val line = channel.readLine() ?: break
                        emit(line)
                    }
                }
        }
    }

    suspend fun put(
        url: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String> {
        val outgoingHeaders = headers - INTERNAL_RETRY_HEADER
        return execute(
            method = "PUT",
            url = url,
            resolveBearer = { if (outgoingHeaders.hasAuthorization()) null else resolveBearerToken() },
        ) { _, bearer ->
            httpClient.put(url) {
                contentType(ContentType.Application.Json)
                body?.let { setBody(it) }
                outgoingHeaders.forEach { (k, v) -> header(k, v) }
                if (!outgoingHeaders.hasAuthorization()) {
                    bearer?.let { header("Authorization", "Bearer $it") }
                }
            }
        }
    }

    suspend fun patch(
        url: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String> {
        val outgoingHeaders = headers - INTERNAL_RETRY_HEADER
        return execute(
            method = "PATCH",
            url = url,
            resolveBearer = { if (outgoingHeaders.hasAuthorization()) null else resolveBearerToken() },
        ) { _, bearer ->
            httpClient.patch(url) {
                contentType(ContentType.Application.Json)
                body?.let { setBody(it) }
                outgoingHeaders.forEach { (k, v) -> header(k, v) }
                if (!outgoingHeaders.hasAuthorization()) {
                    bearer?.let { header("Authorization", "Bearer $it") }
                }
            }
        }
    }

    suspend fun delete(
        url: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String> {
        val outgoingHeaders = headers - INTERNAL_RETRY_HEADER
        return execute(
            method = "DELETE",
            url = url,
            resolveBearer = { if (outgoingHeaders.hasAuthorization()) null else resolveBearerToken() },
        ) { _, bearer ->
            httpClient.delete(url) {
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
                outgoingHeaders.forEach { (k, v) -> header(k, v) }
                if (!outgoingHeaders.hasAuthorization()) {
                    bearer?.let { header("Authorization", "Bearer $it") }
                }
            }
        }
    }

    suspend fun postRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String> {
        val outgoingHeaders = headers - INTERNAL_RETRY_HEADER
        return execute(
            method = "POST",
            url = url,
            resolveBearer = { if (outgoingHeaders.hasAuthorization()) null else resolveBearerToken() },
        ) { _, bearer ->
            httpClient.post(url) {
                contentType(ContentType.parse(contentType))
                setBody(body)
                outgoingHeaders.forEach { (k, v) -> header(k, v) }
                if (!outgoingHeaders.hasAuthorization()) {
                    bearer?.let { header("Authorization", "Bearer $it") }
                }
            }
        }
    }

    suspend fun putRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String> {
        val outgoingHeaders = headers - INTERNAL_RETRY_HEADER
        return execute(
            method = "PUT",
            url = url,
            resolveBearer = { if (outgoingHeaders.hasAuthorization()) null else resolveBearerToken() },
        ) { _, bearer ->
            httpClient.put(url) {
                contentType(ContentType.parse(contentType))
                setBody(body)
                outgoingHeaders.forEach { (k, v) -> header(k, v) }
                if (!outgoingHeaders.hasAuthorization()) {
                    bearer?.let { header("Authorization", "Bearer $it") }
                }
            }
        }
    }

    suspend fun rawRequest(
        method: String,
        url: String,
        body: ByteArray?,
        contentType: String?,
        requestHeaders: Map<String, String>,
    ): SupabaseResult<SupabaseHttpResponse> {
        val mark = TimeSource.Monotonic.markNow()
        config.interceptor?.onRequest(method, url)
        return try {
            val bearer = if (requestHeaders.hasAuthorization()) null else resolveBearerToken()
            val response =
                httpClient.request(url) {
                    this.method = HttpMethod.parse(method)
                    requestHeaders.forEach { (k, v) -> header(k, v) }
                    if (!requestHeaders.hasAuthorization()) {
                        bearer?.let { header("Authorization", "Bearer $it") }
                    }
                    contentType?.let { contentType(ContentType.parse(it)) }
                    body?.let { setBody(it) }
                }
            val bytes = response.readRawBytes()
            config.interceptor?.onResponse(method, url, response.status.value, mark.elapsedNow().inWholeMilliseconds)
            if (response.status.isSuccess()) {
                val flatHeaders = response.headers.entries().associate { (k, v) -> k to v.joinToString(",") }
                SupabaseResult.Success(SupabaseHttpResponse(response.status.value, flatHeaders, bytes))
            } else {
                val retryAfter = parseRetryAfterSeconds(response.headers["Retry-After"], Clock.System.now().epochSeconds)
                SupabaseResult.Failure(parseError(bytes.decodeToString(), response.status.value, retryAfter))
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            val error = networkError(throwable = e)
            config.interceptor?.onError(method, url, error, mark.elapsedNow().inWholeMilliseconds)
            SupabaseResult.Failure(error)
        }
    }

    fun setAccessToken(token: String) {
        accessToken = token
    }

    fun clearAccessToken() {
        accessToken = null
    }

    fun close() {
        httpClient.close()
    }

    private suspend inline fun execute(
        method: String,
        url: String,
        retryEnabled: Boolean = false,
        retryableMethod: Boolean = false,
        noinline resolveBearer: suspend () -> String? = { null },
        crossinline request: suspend (attempt: Int, bearer: String?) -> io.ktor.client.statement.HttpResponse,
    ): SupabaseResult<String> {
        val mark = TimeSource.Monotonic.markNow()
        config.interceptor?.onRequest(method, url)
        return try {
            // Resolve the Bearer token inside the try so a throwing
            // accessTokenProvider surfaces as SupabaseResult.Failure rather than a
            // raw throwable. Resolved once per request, not per retry attempt.
            val bearer = resolveBearer()
            var attempt = 0
            while (attempt <= retry.maxRetries) {
                val response =
                    try {
                        request(attempt, bearer)
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        if (retryEnabled && retryableMethod && attempt < retry.maxRetries) {
                            delay(retry.backoffMillis(attempt))
                            attempt++
                            continue
                        }
                        throw e
                    }
                val text = response.bodyAsText()
                val statusCode = response.status.value
                if (response.status.isSuccess()) {
                    config.interceptor?.onResponse(method, url, statusCode, mark.elapsedNow().inWholeMilliseconds)
                    return SupabaseResult.Success(text)
                }
                if (retryEnabled &&
                    retryableMethod &&
                    statusCode in retry.retryableStatuses &&
                    attempt < retry.maxRetries
                ) {
                    delay(response.retryDelayMillis(attempt))
                    attempt++
                    continue
                }
                val retryAfter = parseRetryAfterSeconds(response.headers["Retry-After"], Clock.System.now().epochSeconds)
                val error = parseError(text, statusCode, retryAfter)
                config.interceptor?.onResponse(method, url, statusCode, mark.elapsedNow().inWholeMilliseconds)
                return SupabaseResult.Failure(error)
            }
            val exhausted = networkError(message = "Retry attempts exhausted")
            config.interceptor?.onError(method, url, exhausted, mark.elapsedNow().inWholeMilliseconds)
            SupabaseResult.Failure(exhausted)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            val error = networkError(throwable = e)
            config.interceptor?.onError(method, url, error, mark.elapsedNow().inWholeMilliseconds)
            SupabaseResult.Failure(error)
        }
    }

    // Caller-supplied Authorization headers must be matched case-insensitively so
    // a lowercased `authorization` isn't missed, which would otherwise cause the
    // SDK to inject a second `Authorization` header.
    private fun Map<String, String>.hasAuthorization(): Boolean = keys.any { it.equals("Authorization", ignoreCase = true) }

    // A request that throws (rather than returning a response) never reached a
    // usable server response: offline, DNS/TLS failure, connection refused, or a
    // timeout. Tag it with a synthetic [SupabaseErrorCodes.Client] code so
    // callers see [SupabaseErrorCategory.Network] instead of Unknown.
    private fun networkError(
        throwable: Throwable? = null,
        message: String? = null,
    ): SupabaseError {
        val code =
            when (throwable) {
                // Match Ktor's typed exceptions first so a future class rename can't
                // silently misroute the error. Fall back to name matching only for
                // engine-specific exceptions we don't model directly.
                is HttpRequestTimeoutException,
                is ConnectTimeoutException,
                is SocketTimeoutException,
                -> SupabaseErrorCodes.Client.TIMEOUT
                is IOException -> SupabaseErrorCodes.Client.CONNECTION_FAILED
                null -> SupabaseErrorCodes.Client.NETWORK_ERROR
                else -> {
                    val name = throwable::class.simpleName.orEmpty()
                    when {
                        name.contains("Timeout", ignoreCase = true) -> SupabaseErrorCodes.Client.TIMEOUT
                        name.contains("Connect", ignoreCase = true) -> SupabaseErrorCodes.Client.CONNECTION_FAILED
                        else -> SupabaseErrorCodes.Client.NETWORK_ERROR
                    }
                }
            }
        return SupabaseError(
            message = message ?: throwable?.message ?: "Network request failed",
            code = code,
        )
    }

    // A Retry-After header wins over the configured exponential backoff. The
    // header may be delta-seconds or an HTTP-date (RFC 7231); both are honored.
    private fun io.ktor.client.statement.HttpResponse.retryDelayMillis(attempt: Int): Long {
        val retryAfterSeconds = parseRetryAfterSeconds(headers["Retry-After"], Clock.System.now().epochSeconds)
        return if (retryAfterSeconds != null) {
            retryAfterSeconds.coerceAtLeast(0) * 1_000
        } else {
            retry.backoffMillis(attempt)
        }
    }

    // Supabase services return heterogeneous error bodies: PostgREST uses
    // {code,message,details,hint}; GoTrue uses {error_code,msg} or
    // {error,error_description}; Storage uses {statusCode,error,message}. Parse
    // tolerantly across all of them and ALWAYS record the HTTP status so
    // categorization works even when the body carries no machine-readable code.
    private fun parseError(body: String, statusCode: Int, retryAfterSeconds: Long?): SupabaseError {
        val obj =
            try {
                (errorJson.parseToJsonElement(body) as? JsonObject)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }

        fun str(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { key -> (obj?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } }

        val message =
            str("message", "msg", "error_description", "error_message", "error")
                ?: body.ifBlank { "HTTP $statusCode" }
        // Prefer the textual machine code. Legacy GoTrue puts the numeric HTTP
        // status in `code` and the real string code in `error_code`, so reading
        // `error_code` first surfaces e.g. "weak_password" instead of "400".
        // PostgREST (only `code`, the SQLSTATE) and new GoTrue/Storage (string
        // `code`) are unaffected since they don't send `error_code`.
        val code = str("error_code", "code", "statusCode")

        return SupabaseError(
            message = message,
            code = code,
            details = obj?.get("details"),
            hint = str("hint"),
            httpStatus = statusCode,
            retryAfterSeconds = retryAfterSeconds,
        )
    }
}

// RFC 7231 allows `Retry-After` to be either delta-seconds (e.g. `120`) or an
// HTTP-date (e.g. `Wed, 21 Oct 2025 07:28:00 GMT`). A bare `toLongOrNull` drops
// the date form, silently discarding the server's hint. Returns the number of
// seconds to wait: delta-seconds verbatim, or `max(0, dateEpoch - now)` for the
// date form; null when [raw] is null/blank or can't be parsed.
private fun parseRetryAfterSeconds(
    raw: String?,
    nowEpochSeconds: Long,
): Long? {
    val trimmed = raw?.trim()
    if (trimmed.isNullOrEmpty()) return null
    trimmed.toLongOrNull()?.let { return it }
    val epoch = parseHttpDateEpochSeconds(trimmed) ?: return null
    return (epoch - nowEpochSeconds).coerceAtLeast(0)
}

// Self-contained parser for the canonical RFC-1123 / IMF-fixdate form servers
// send — `EEE, dd MMM yyyy HH:mm:ss GMT`. Computes epoch seconds directly so it
// compiles on every KMP target (no JDK-only date APIs). Returns null for any
// other shape; the obsolete RFC-850 and asctime forms are intentionally
// unsupported because servers don't emit them for `Retry-After`.
private fun parseHttpDateEpochSeconds(value: String): Long? {
    val parts = value.split(' ')
    val time = parts.getOrElse(4) { "" }.split(':')
    if (parts.size != 6 || parts[5] != "GMT" || time.size != 3) return null
    val month = monthIndex(parts[2]) ?: return null
    val nums = listOf(parts[1], parts[3], time[0], time[1], time[2]).mapNotNull { it.toIntOrNull() }
    if (nums.size != 5) return null
    val day = nums[0]
    val year = nums[1]
    val hour = nums[2]
    val minute = nums[3]
    val second = nums[4]
    val timeOk = hour in 0..23 && minute in 0..59 && second in 0..60
    if (day !in 1..31 || !timeOk) return null
    val days = daysFromCivil(year, month, day)
    return days * 86_400L + hour * 3_600L + minute * 60L + second
}

private fun monthIndex(name: String): Int? {
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val index = months.indexOf(name)
    return if (index >= 0) index + 1 else null
}

// Days from 1970-01-01 (the Unix epoch) using Howard Hinnant's days_from_civil
// algorithm — pure integer math, valid for any proleptic Gregorian date.
private fun daysFromCivil(
    year: Int,
    month: Int,
    day: Int,
): Long {
    val y = if (month <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = (y - era * 400).toLong()
    val doy = ((153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1).toLong()
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era.toLong() * 146_097 + doe - 719_468
}
