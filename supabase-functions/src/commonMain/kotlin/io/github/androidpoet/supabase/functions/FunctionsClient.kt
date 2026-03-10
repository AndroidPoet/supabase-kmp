package io.github.androidpoet.supabase.functions

import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * AWS-style region identifiers for Supabase Edge Functions.
 *
 * When specified, the invocation is routed to the given region via the
 * `x-region` header. When omitted, Supabase uses its default routing.
 */
@Serializable
public enum class FunctionRegion(public val value: String) {
    @SerialName("us-east-1")
    US_EAST_1("us-east-1"),

    @SerialName("us-west-1")
    US_WEST_1("us-west-1"),

    @SerialName("eu-west-1")
    EU_WEST_1("eu-west-1"),

    @SerialName("ap-southeast-1")
    AP_SOUTHEAST_1("ap-southeast-1"),

    @SerialName("ap-northeast-1")
    AP_NORTHEAST_1("ap-northeast-1"),
}

/**
 * Client for invoking Supabase Edge Functions.
 *
 * Each function is identified by its deployment name and invoked as a
 * POST request to `/functions/v1/{functionName}`.
 */
public interface FunctionsClient {

    /**
     * Invokes an Edge Function with an optional JSON [body].
     *
     * @param functionName The deployed function name.
     * @param body         Optional JSON string payload.
     * @param headers      Additional headers merged into the request.
     * @param region       Optional region override for routing.
     * @return The raw response body on success.
     */
    public suspend fun invoke(
        functionName: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
        region: FunctionRegion? = null,
    ): SupabaseResult<String>

    /**
     * Invokes an Edge Function with a raw binary [body].
     *
     * @param functionName The deployed function name.
     * @param body         Raw bytes to send as the request body.
     * @param contentType  MIME type for the payload (defaults to `application/octet-stream`).
     * @param headers      Additional headers merged into the request.
     * @param region       Optional region override for routing.
     * @return The raw response body on success.
     */
    public suspend fun invokeWithBody(
        functionName: String,
        body: ByteArray,
        contentType: String = "application/octet-stream",
        headers: Map<String, String> = emptyMap(),
        region: FunctionRegion? = null,
    ): SupabaseResult<String>
}
