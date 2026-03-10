package io.github.androidpoet.supabase.functions

import io.github.androidpoet.supabase.client.defaultJson
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult

/**
 * Invokes an Edge Function and deserializes the JSON response into [T].
 *
 * This is a convenience wrapper around [FunctionsClient.invoke] for callers
 * that expect structured JSON responses from their Edge Functions.
 */
public suspend inline fun <reified T> FunctionsClient.invokeTyped(
    functionName: String,
    body: String? = null,
    headers: Map<String, String> = emptyMap(),
    region: FunctionRegion? = null,
): SupabaseResult<T> = when (val result = invoke(functionName, body, headers, region)) {
    is SupabaseResult.Success -> try {
        SupabaseResult.Success(defaultJson.decodeFromString<T>(result.value))
    } catch (e: Exception) {
        SupabaseResult.Failure(
            SupabaseError(message = "Deserialization failed: ${e.message}"),
        )
    }
    is SupabaseResult.Failure -> result
}
