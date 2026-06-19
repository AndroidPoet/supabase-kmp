package io.github.androidpoet.supabase.client
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.serialization.json.Json

public val defaultJson: Json =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        // Coerce an unknown enum value (or a null for a non-null field that has a
        // default) to that property's default instead of failing the whole decode.
        // Server-decoded enums declare an UNKNOWN member + default for this reason,
        // so one new server-side value can't break an entire response.
        coerceInputValues = true
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
        is SupabaseResult.Success ->
            SupabaseResult.catching {
                defaultJson.decodeFromString<T>(value)
            }
        is SupabaseResult.Failure -> this
    }
