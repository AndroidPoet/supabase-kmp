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
public interface DatabaseClient {
    public suspend fun select(
        table: String,
        columns: String = "*",
        head: Boolean = false,
        count: CountOption? = null,
        filters: FilterBuilder.() -> Unit = {},
    ): SupabaseResult<String>
    public suspend fun insert(
        table: String,
        body: String,
        upsert: Boolean = false,
        onConflict: String? = null,
        returning: ReturnOption = ReturnOption.REPRESENTATION,
        count: CountOption? = null,
    ): SupabaseResult<String>
    public suspend fun update(
        table: String,
        body: String,
        returning: ReturnOption = ReturnOption.REPRESENTATION,
        count: CountOption? = null,
        filters: FilterBuilder.() -> Unit = {},
    ): SupabaseResult<String>
    public suspend fun delete(
        table: String,
        returning: ReturnOption = ReturnOption.REPRESENTATION,
        count: CountOption? = null,
        filters: FilterBuilder.() -> Unit = {},
    ): SupabaseResult<String>
    public suspend fun rpc(
        function: String,
        params: String? = null,
        head: Boolean = false,
        count: CountOption? = null,
    ): SupabaseResult<String>
}
