package io.github.androidpoet.supabase.functions
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
public interface FunctionsClient {
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
}
