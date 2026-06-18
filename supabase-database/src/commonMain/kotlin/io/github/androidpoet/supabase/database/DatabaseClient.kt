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

/**
 * The count and row range parsed from a PostgREST `Content-Range` response
 * header (returned when a request sets `count=`). For `Content-Range: 0-9/27`,
 * [range] is `0..9` and [count] is `27`. Either part may be absent — `*` in the
 * header maps to `null` rather than failing — so an unparseable or count-only
 * header still yields a value.
 */
public data class PostgrestRange(
    public val count: Long? = null,
    public val range: LongRange? = null,
)

/**
 * A page of decoded rows together with the total [count] reported by PostgREST.
 * Produced by the `*WithCount` helpers, which issue the normal request with a
 * `count=` preference and surface the `Content-Range` total alongside the data.
 */
public data class PostgrestPage<T>(
    public val rows: List<T>,
    public val count: Long? = null,
    public val range: LongRange? = null,
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

    /**
     * Issues a count-only `HEAD` request and returns the total reported by
     * PostgREST in the `Content-Range` header. No rows are fetched. [count]
     * defaults to [CountOption.EXACT]; use [CountOption.PLANNED] or
     * [CountOption.ESTIMATED] to trade accuracy for speed on large tables.
     */
    public suspend fun selectCount(
        table: String,
        schema: String? = null,
        columns: String = "*",
        count: CountOption = CountOption.EXACT,
        headers: Map<String, String> = emptyMap(),
        filters: FilterBuilder.() -> Unit = {},
    ): SupabaseResult<PostgrestRange>

    /**
     * Like [select], but also returns the total [PostgrestRange.count] and the
     * fetched [PostgrestRange.range] from the `Content-Range` header alongside
     * the (string) body. Use the `selectWithCount` typed helper to decode rows
     * into a [PostgrestPage].
     */
    public suspend fun selectRange(
        table: String,
        schema: String? = null,
        columns: String = "*",
        single: Boolean = false,
        count: CountOption = CountOption.EXACT,
        stripNulls: Boolean = false,
        headers: Map<String, String> = emptyMap(),
        filters: FilterBuilder.() -> Unit = {},
    ): SupabaseResult<Pair<String, PostgrestRange>>

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
