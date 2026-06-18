package io.github.androidpoet.supabase.database
import io.github.androidpoet.supabase.core.models.FilterBuilder
import io.github.androidpoet.supabase.core.result.SupabaseResult

public enum class CountOption(
    internal val headerValue: String,
) {
    EXACT("exact"),
    PLANNED("planned"),
    ESTIMATED("estimated"),
}

public enum class ReturnOption(
    internal val headerValue: String,
) {
    MINIMAL("minimal"),
    REPRESENTATION("representation"),
}

public enum class UpsertResolution(
    internal val headerValue: String,
) {
    MERGE_DUPLICATES("merge-duplicates"),
    IGNORE_DUPLICATES("ignore-duplicates"),
}

public enum class ExplainFormat(
    internal val headerValue: String,
) {
    TEXT("text"),
    JSON("json"),
    XML("xml"),
    YAML("yaml"),
}

public data class ExplainOptions(
    public val analyze: Boolean = false,
    public val verbose: Boolean = false,
    public val settings: Boolean = false,
    public val buffers: Boolean = false,
    public val wal: Boolean = false,
    public val format: ExplainFormat = ExplainFormat.TEXT,
)

public interface DatabaseClient {
    public suspend fun select(
        table: String,
        schema: String? = null,
        columns: String = "*",
        head: Boolean = false,
        single: Boolean = false,
        csv: Boolean = false,
        geojson: Boolean = false,
        count: CountOption? = null,
        stripNulls: Boolean = false,
        explain: ExplainOptions? = null,
        retry: Boolean = true,
        headers: Map<String, String> = emptyMap(),
        filters: FilterBuilder.() -> Unit = {},
    ): SupabaseResult<String>

    public suspend fun insert(
        table: String,
        schema: String? = null,
        body: String,
        columns: List<String>? = null,
        upsert: Boolean = false,
        upsertResolution: UpsertResolution = UpsertResolution.MERGE_DUPLICATES,
        defaultToNull: Boolean = true,
        onConflict: String? = null,
        returning: ReturnOption = ReturnOption.REPRESENTATION,
        count: CountOption? = null,
        stripNulls: Boolean = false,
        rollback: Boolean = false,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>

    public suspend fun update(
        table: String,
        schema: String? = null,
        body: String,
        returning: ReturnOption = ReturnOption.REPRESENTATION,
        count: CountOption? = null,
        stripNulls: Boolean = false,
        rollback: Boolean = false,
        maxAffected: Int? = null,
        explain: ExplainOptions? = null,
        headers: Map<String, String> = emptyMap(),
        filters: FilterBuilder.() -> Unit = {},
    ): SupabaseResult<String>

    public suspend fun delete(
        table: String,
        schema: String? = null,
        returning: ReturnOption = ReturnOption.REPRESENTATION,
        count: CountOption? = null,
        stripNulls: Boolean = false,
        rollback: Boolean = false,
        maxAffected: Int? = null,
        explain: ExplainOptions? = null,
        headers: Map<String, String> = emptyMap(),
        filters: FilterBuilder.() -> Unit = {},
    ): SupabaseResult<String>

    public suspend fun rpc(
        function: String,
        schema: String? = null,
        params: String? = null,
        head: Boolean = false,
        single: Boolean = false,
        csv: Boolean = false,
        count: CountOption? = null,
        stripNulls: Boolean = false,
        rollback: Boolean = false,
        maxAffected: Int? = null,
        explain: ExplainOptions? = null,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>

    public suspend fun rpcGet(
        function: String,
        schema: String? = null,
        queryParams: List<Pair<String, String>> = emptyList(),
        head: Boolean = false,
        single: Boolean = false,
        csv: Boolean = false,
        count: CountOption? = null,
        stripNulls: Boolean = false,
        explain: ExplainOptions? = null,
        retry: Boolean = true,
        headers: Map<String, String> = emptyMap(),
    ): SupabaseResult<String>
}
