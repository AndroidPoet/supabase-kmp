package io.github.androidpoet.supabase.functions
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

public interface FunctionsClient {
    public fun setAuth(token: String)

    public suspend fun invoke(
        functionName: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
        region: FunctionRegion? = null,
    ): SupabaseResult<String>

    public suspend fun invokeWithBody(
        functionName: String,
        body: ByteArray,
        contentType: String = "application/octet-stream",
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
    public fun invokeSSE(
        functionName: String,
        body: String? = null,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap(),
        region: FunctionRegion? = null,
    ): Flow<FunctionServerSentEvent> =
        throw UnsupportedOperationException("invokeSSE is not supported by this FunctionsClient")
}
