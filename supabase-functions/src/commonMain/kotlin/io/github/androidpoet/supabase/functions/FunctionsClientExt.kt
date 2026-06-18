package io.github.androidpoet.supabase.functions
import io.github.androidpoet.supabase.client.defaultJson
import io.github.androidpoet.supabase.core.result.SupabaseResult

/**
 * Invokes [functionName] and decodes its response JSON into [T] using the
 * Functions serializer, all within a [SupabaseResult].
 *
 * Wraps [FunctionsClient.invoke]; on success the body is deserialized and a
 * decode failure becomes a [SupabaseResult.Failure] rather than an exception, so
 * the call stays Result-first end to end. Pass a serialized [body] string; use
 * the request-object overload to encode the request too.
 */
public suspend inline fun <reified T> FunctionsClient.invokeTyped(
    functionName: String,
    body: String? = null,
    headers: Map<String, String> = emptyMap(),
    region: FunctionRegion? = null,
): SupabaseResult<T> =
    when (val result = invoke(functionName, body, headers, region)) {
        is SupabaseResult.Success ->
            SupabaseResult.catching {
                defaultJson.decodeFromString<T>(result.value)
            }
        is SupabaseResult.Failure -> result
    }

/**
 * Encodes [request] to JSON, invokes [functionName] with it, and decodes the
 * response into [Response] — both sides typed in one call. The fully typed
 * counterpart to [invokeTyped]; both serialization steps use the Functions
 * serializer and a failure at either end surfaces as [SupabaseResult.Failure].
 *
 * @param request the request body, serialized to JSON via [Request]'s serializer.
 */
public suspend inline fun <reified Request : Any, reified Response> FunctionsClient.invokeTyped(
    functionName: String,
    request: Request,
    headers: Map<String, String> = emptyMap(),
    region: FunctionRegion? = null,
): SupabaseResult<Response> =
    invokeTyped(
        functionName = functionName,
        body = defaultJson.encodeToString(request),
        headers = headers,
        region = region,
    )

/**
 * Invokes [functionName] for its side effect and discards the response body,
 * yielding `SupabaseResult<Unit>`. Use over [invokeTyped] for fire-and-forget
 * functions that return nothing meaningful; a non-2xx response still surfaces as
 * [SupabaseResult.Failure].
 */
public suspend fun FunctionsClient.invokeUnit(
    functionName: String,
    body: String? = null,
    headers: Map<String, String> = emptyMap(),
    region: FunctionRegion? = null,
): SupabaseResult<Unit> =
    when (val result = invoke(functionName, body, headers, region)) {
        is SupabaseResult.Success -> SupabaseResult.Success(Unit)
        is SupabaseResult.Failure -> result
    }

/**
 * Posts a raw binary [body] to [functionName] and decodes the JSON response into
 * [T], within a [SupabaseResult]. The byte-body analogue of [invokeTyped],
 * wrapping [FunctionsClient.invokeWithBody]; a decode failure becomes a
 * [SupabaseResult.Failure].
 *
 * @param contentType the `Content-Type` for the uploaded bytes.
 */
public suspend inline fun <reified T> FunctionsClient.invokeWithBodyTyped(
    functionName: String,
    body: ByteArray,
    contentType: String = "application/octet-stream",
    headers: Map<String, String> = emptyMap(),
    region: FunctionRegion? = null,
): SupabaseResult<T> =
    when (
        val result =
            invokeWithBody(
                functionName = functionName,
                body = body,
                contentType = contentType,
                headers = headers,
                region = region,
            )
    ) {
        is SupabaseResult.Success ->
            SupabaseResult.catching {
                defaultJson.decodeFromString<T>(result.value)
            }
        is SupabaseResult.Failure -> result
    }

/**
 * Posts a raw binary [body] to [functionName] for its side effect and discards
 * the response, yielding `SupabaseResult<Unit>`. The byte-body analogue of
 * [invokeUnit]; a non-2xx response surfaces as [SupabaseResult.Failure].
 *
 * @param contentType the `Content-Type` for the uploaded bytes.
 */
public suspend fun FunctionsClient.invokeWithBodyUnit(
    functionName: String,
    body: ByteArray,
    contentType: String = "application/octet-stream",
    headers: Map<String, String> = emptyMap(),
    region: FunctionRegion? = null,
): SupabaseResult<Unit> =
    when (
        val result =
            invokeWithBody(
                functionName = functionName,
                body = body,
                contentType = contentType,
                headers = headers,
                region = region,
            )
    ) {
        is SupabaseResult.Success -> SupabaseResult.Success(Unit)
        is SupabaseResult.Failure -> result
    }
