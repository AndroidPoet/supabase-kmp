package io.github.androidpoet.supabase.database
import io.github.androidpoet.supabase.core.models.FilterBuilder
import io.github.androidpoet.supabase.core.result.SupabaseResult
public enum class CountOption(internal val headerValue: String) {
    EXACT("exact"),
    PLANNED("planned"),
    ESTIMATED("estimated"),
}
public enum class ReturnOption(internal val headerValue: String) {
    MINIMAL("minimal"),
    REPRESENTATION("representation"),
}
public enum class UpsertResolution(internal val headerValue: String) {
    MERGE_DUPLICATES("merge-duplicates"),
    IGNORE_DUPLICATES("ignore-duplicates"),
}
public interface DatabaseClient {
    public suspend fun select(
        table: String,
        schema: String? = null,
        columns: String = "*",
        head: Boolean = false,
        single: Boolean = false,
        csv: Boolean = false,
        count: CountOption? = null,
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
    ): SupabaseResult<String>
    public suspend fun update(
        table: String,
        schema: String? = null,
        body: String,
        returning: ReturnOption = ReturnOption.REPRESENTATION,
        count: CountOption? = null,
        filters: FilterBuilder.() -> Unit = {},
    ): SupabaseResult<String>
    public suspend fun delete(
        table: String,
        schema: String? = null,
        returning: ReturnOption = ReturnOption.REPRESENTATION,
        count: CountOption? = null,
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
    ): SupabaseResult<String>
    public suspend fun rpcGet(
        function: String,
        schema: String? = null,
        queryParams: List<Pair<String, String>> = emptyList(),
        head: Boolean = false,
        single: Boolean = false,
        csv: Boolean = false,
        count: CountOption? = null,
    ): SupabaseResult<String>
}
