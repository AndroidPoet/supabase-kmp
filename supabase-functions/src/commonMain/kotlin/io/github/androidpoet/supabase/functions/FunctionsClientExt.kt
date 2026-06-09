package io.github.androidpoet.supabase.functions
import io.github.androidpoet.supabase.client.defaultJson
import io.github.androidpoet.supabase.core.result.SupabaseResult
public suspend inline fun <reified T> FunctionsClient.invokeTyped(
    functionName: String,
    body: String? = null,
    headers: Map<String, String> = emptyMap(),
    region: FunctionRegion? = null,
): SupabaseResult<T> = when (val result = invoke(functionName, body, headers, region)) {
    is SupabaseResult.Success -> SupabaseResult.catching {
        defaultJson.decodeFromString<T>(result.value)
    }
    is SupabaseResult.Failure -> result
}

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

public suspend inline fun <reified T> FunctionsClient.invokeWithBodyTyped(
    functionName: String,
    body: ByteArray,
    contentType: String = "application/octet-stream",
    headers: Map<String, String> = emptyMap(),
    region: FunctionRegion? = null,
): SupabaseResult<T> = when (
    val result = invokeWithBody(
        functionName = functionName,
        body = body,
        contentType = contentType,
        headers = headers,
        region = region,
    )
) {
    is SupabaseResult.Success -> SupabaseResult.catching {
        defaultJson.decodeFromString<T>(result.value)
    }
    is SupabaseResult.Failure -> result
}

public suspend fun FunctionsClient.invokeWithBodyUnit(
    functionName: String,
    body: ByteArray,
    contentType: String = "application/octet-stream",
    headers: Map<String, String> = emptyMap(),
    region: FunctionRegion? = null,
): SupabaseResult<Unit> =
    when (
        val result = invokeWithBody(
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
