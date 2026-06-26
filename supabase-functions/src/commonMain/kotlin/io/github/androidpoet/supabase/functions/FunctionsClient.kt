package io.github.androidpoet.supabase.functions
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The Edge Functions region to pin a call to, sent as the `x-region` request
 * header by [FunctionsClient.invoke] and friends.
 *
 * Edge Functions are normally routed to the region nearest the caller; pin one
 * when a function must run close to a regional resource (e.g. a co-located
 * database) to avoid the cross-region round trip. [value] is the wire string
 * placed in the header. [ANY] (the default behavior when no region is passed)
 * leaves routing to Supabase.
 */
@Serializable
public enum class FunctionRegion(
    public val value: String,
) {
    /** Let Supabase route the call to the nearest healthy region. */
    @SerialName("any")
    ANY("any"),

    @SerialName("us-east-1")
    US_EAST_1("us-east-1"),

    @SerialName("us-west-1")
    US_WEST_1("us-west-1"),

    @SerialName("us-west-2")
    US_WEST_2("us-west-2"),

    @SerialName("ca-central-1")
    CA_CENTRAL_1("ca-central-1"),

    @SerialName("sa-east-1")
    SA_EAST_1("sa-east-1"),

    @SerialName("eu-west-1")
    EU_WEST_1("eu-west-1"),

    @SerialName("eu-west-2")
    EU_WEST_2("eu-west-2"),

    @SerialName("eu-west-3")
    EU_WEST_3("eu-west-3"),

    @SerialName("eu-central-1")
    EU_CENTRAL_1("eu-central-1"),

    @SerialName("ap-south-1")
    AP_SOUTH_1("ap-south-1"),

    @SerialName("ap-southeast-1")
    AP_SOUTHEAST_1("ap-southeast-1"),

    @SerialName("ap-southeast-2")
    AP_SOUTHEAST_2("ap-southeast-2"),

    @SerialName("ap-northeast-1")
    AP_NORTHEAST_1("ap-northeast-1"),

    @SerialName("ap-northeast-2")
    AP_NORTHEAST_2("ap-northeast-2"),
}

/**
 * The HTTP method an Edge Function call is issued with, sent by
 * [FunctionsClient.invoke] and [FunctionsClient.invokeWithBody].
 *
 * Edge Functions default to [POST]; pass another verb when a function routes on
 * the request method. [GET] carries no request body (a GET with a body is
 * invalid) — any body supplied alongside it is ignored.
 */
public enum class FunctionMethod {
    /** HTTP `POST` — the default; carries the request body. */
    POST,

    /** HTTP `GET` — no request body is sent. */
    GET,

    /** HTTP `PUT` — carries the request body. */
    PUT,

    /** HTTP `PATCH` — carries the request body. */
    PATCH,

    /** HTTP `DELETE` — carries the request body. */
    DELETE,
}

/**
 * Calls Supabase Edge Functions deployed under `/functions/v1/`, returning each
 * result as a [SupabaseResult] rather than throwing.
 *
 * One client wraps a [io.github.androidpoet.supabase.client.SupabaseClient] and
 * reuses its base URL and session token, so calls are authenticated with the
 * current session unless a token is pinned via [setAuth]. [invoke] posts a text
 * body and reads the full response; [invokeWithBody] posts arbitrary bytes; and
 * [invokeSse] streams a `text/event-stream` response as a [Flow]. Obtain an
 * instance with [createFunctionsClient].
 */
public interface FunctionsClient {
    /**
     * Pins [token] as the bearer token for every subsequent call, overriding the
     * wrapped client's current session token. Pass the new token to rotate it;
     * there is no unset — to fall back to the session token, construct a fresh
     * client.
     */
    public fun setAuth(token: String)

    /**
     * Invokes the Edge Function named [functionName] with an optional text [body]
     * and returns its full response body as a [SupabaseResult].
     *
     * Issues [method] (default [FunctionMethod.POST]) to `/functions/v1/$functionName`.
     * When [body] is non-null and the caller didn't set one, a
     * `Content-Type: application/json` header is added (Edge Functions commonly
     * branch on it). With [FunctionMethod.GET] no body is sent — any [body] is
     * ignored. [region] pins the `x-region` header; [headers] are merged last and
     * win over the defaults. A non-2xx response is a [SupabaseResult.Failure].
     *
     * @param method the HTTP method to issue; defaults to [FunctionMethod.POST].
     * @param headers extra request headers, merged over the auth/region defaults.
     * @param region optional region to pin the call to via `x-region`.
     */
    public suspend fun invoke(
        functionName: String,
        body: String? = null,
        method: FunctionMethod = FunctionMethod.POST,
        headers: Map<String, String> = emptyMap(),
        region: FunctionRegion? = null,
    ): SupabaseResult<String>

    /**
     * Invokes [functionName] with a raw binary [body] (e.g. an image or protobuf)
     * under the given [contentType], returning the full response as a
     * [SupabaseResult]. The byte-oriented counterpart to [invoke]; same routing
     * and `x-region`/[headers] behavior.
     *
     * Issues [method] (default [FunctionMethod.POST]). [FunctionMethod.GET] is not
     * meaningful here since it carries no body; pass a body-bearing verb.
     *
     * @param contentType the `Content-Type` for the uploaded bytes.
     * @param method the HTTP method to issue; defaults to [FunctionMethod.POST].
     * @param headers extra request headers, merged over the auth/region defaults.
     * @param region optional region to pin the call to via `x-region`.
     */
    public suspend fun invokeWithBody(
        functionName: String,
        body: ByteArray,
        contentType: String = "application/octet-stream",
        method: FunctionMethod = FunctionMethod.POST,
        headers: Map<String, String> = emptyMap(),
        region: FunctionRegion? = null,
    ): SupabaseResult<String>

    /**
     * Invokes a streaming Edge Function and returns its response as a cold [Flow]
     * of [FunctionServerSentEvent]s, parsed from the `text/event-stream` body as
     * they arrive (nothing is buffered to completion).
     *
     * The request is a POST issued when the flow is collected; collecting twice
     * invokes twice. A non-2xx response surfaces as a terminal exception in the
     * flow rather than an empty stream — collect within `try`/`catch` (or Flow
     * `.catch`) since, unlike the buffered [invoke], a streamed call cannot return
     * a [SupabaseResult] up front.
     */
    public fun invokeSse(
        functionName: String,
        body: String? = null,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap(),
        region: FunctionRegion? = null,
    ): Flow<FunctionServerSentEvent> =
        flow { throw UnsupportedOperationException("invokeSse is not supported by this FunctionsClient") }
}
