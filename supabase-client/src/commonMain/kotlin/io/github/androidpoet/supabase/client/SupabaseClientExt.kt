package io.github.androidpoet.supabase.client
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.serialization.json.Json
public val defaultJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}
public suspend inline fun <reified T> SupabaseClient.getTyped(
    endpoint: String,
    queryParams: List<Pair<String, String>> = emptyList(),
    headers: Map<String, String> = emptyMap(),
): SupabaseResult<T> = get(endpoint, queryParams, headers).deserialize()
public suspend inline fun <reified T> SupabaseClient.postTyped(
    endpoint: String,
    body: String? = null,
    headers: Map<String, String> = emptyMap(),
): SupabaseResult<T> = post(endpoint, body, headers).deserialize()
public suspend inline fun <reified T> SupabaseClient.patchTyped(
    endpoint: String,
    body: String? = null,
    headers: Map<String, String> = emptyMap(),
): SupabaseResult<T> = patch(endpoint, body, headers).deserialize()
public inline fun <reified T> SupabaseResult<String>.deserialize(): SupabaseResult<T> =
    when (this) {
        is SupabaseResult.Success -> try {
            SupabaseResult.Success(defaultJson.decodeFromString<T>(value))
        } catch (e: Exception) {
            SupabaseResult.Failure(
                SupabaseError(message = "Deserialization failed: ${e.message}"),
            )
        }
        is SupabaseResult.Failure -> this
    }
