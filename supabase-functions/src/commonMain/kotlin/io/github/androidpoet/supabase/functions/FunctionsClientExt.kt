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
    method: FunctionMethod = FunctionMethod.POST,
    headers: Map<String, String> = emptyMap(),
    region: FunctionRegion? = null,
): SupabaseResult<T> =
    when (val result = invoke(functionName, body, method, headers, region)) {
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
    method: FunctionMethod = FunctionMethod.POST,
    headers: Map<String, String> = emptyMap(),
    region: FunctionRegion? = null,
): SupabaseResult<Response> =
    invokeTyped(
        functionName = functionName,
        body = defaultJson.encodeToString(request),
        method = method,
        headers = headers,
        region = region,
    )

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
    method: FunctionMethod = FunctionMethod.POST,
    headers: Map<String, String> = emptyMap(),
    region: FunctionRegion? = null,
): SupabaseResult<T> =
    when (
        val result =
            invokeWithBody(
                functionName = functionName,
                body = body,
                contentType = contentType,
                method = method,
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
