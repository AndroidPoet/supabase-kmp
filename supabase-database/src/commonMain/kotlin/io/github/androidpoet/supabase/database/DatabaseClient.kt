package io.github.androidpoet.supabase.database

import io.github.androidpoet.supabase.core.models.FilterBuilder
import io.github.androidpoet.supabase.core.result.SupabaseResult

/**
 * Count algorithm used by PostgREST for the `Prefer: count=` header.
 */
public enum class CountOption(internal val headerValue: String) {
    EXACT("exact"),
    PLANNED("planned"),
    ESTIMATED("estimated"),
}

/**
 * Controls whether PostgREST returns the affected rows in the response body.
 */
public enum class ReturnOption(internal val headerValue: String) {
    MINIMAL("minimal"),
    REPRESENTATION("representation"),
}

/**
 * Client for Supabase PostgREST database operations.
 *
 * Provides typed methods for SELECT, INSERT, UPDATE, DELETE, and RPC
 * calls against the PostgREST v1 API. Filters are built using the
 * [FilterBuilder] DSL from supabase-core.
 */
public interface DatabaseClient {

    /**
     * Reads rows from [table], optionally selecting specific [columns].
     *
     * @param table   The table (or view) name.
     * @param columns Comma-separated column names, or `*` for all.
     * @param head    When `true`, issues a HEAD request (count-only, no body).
     * @param count   Optional count algorithm to include in the response.
     * @param filters DSL block to build PostgREST query-string filters.
     */
    public suspend fun select(
        table: String,
        columns: String = "*",
        head: Boolean = false,
        count: CountOption? = null,
        filters: FilterBuilder.() -> Unit = {},
    ): SupabaseResult<String>

    /**
     * Inserts one or more rows into [table].
     *
     * @param table      The target table name.
     * @param body       JSON string — a single object or an array of objects.
     * @param upsert     When `true`, performs an upsert (merge-duplicates).
     * @param onConflict Comma-separated column names for the upsert conflict target.
     * @param returning  Whether to return the inserted rows.
     * @param count      Optional count algorithm.
     */
    public suspend fun insert(
        table: String,
        body: String,
        upsert: Boolean = false,
        onConflict: String? = null,
        returning: ReturnOption = ReturnOption.REPRESENTATION,
        count: CountOption? = null,
    ): SupabaseResult<String>

    /**
     * Updates rows in [table] matching the given [filters].
     *
     * @param table     The target table name.
     * @param body      JSON object with the columns to update.
     * @param returning Whether to return the updated rows.
     * @param count     Optional count algorithm.
     * @param filters   DSL block to restrict which rows are updated.
     */
    public suspend fun update(
        table: String,
        body: String,
        returning: ReturnOption = ReturnOption.REPRESENTATION,
        count: CountOption? = null,
        filters: FilterBuilder.() -> Unit = {},
    ): SupabaseResult<String>

    /**
     * Deletes rows from [table] matching the given [filters].
     *
     * @param table     The target table name.
     * @param returning Whether to return the deleted rows.
     * @param count     Optional count algorithm.
     * @param filters   DSL block to restrict which rows are deleted.
     */
    public suspend fun delete(
        table: String,
        returning: ReturnOption = ReturnOption.REPRESENTATION,
        count: CountOption? = null,
        filters: FilterBuilder.() -> Unit = {},
    ): SupabaseResult<String>

    /**
     * Calls a PostgreSQL function (RPC) via PostgREST.
     *
     * @param function The function name.
     * @param params   Optional JSON string of function parameters.
     * @param head     When `true`, issues a HEAD request (count-only, no body).
     * @param count    Optional count algorithm.
     */
    public suspend fun rpc(
        function: String,
        params: String? = null,
        head: Boolean = false,
        count: CountOption? = null,
    ): SupabaseResult<String>
}
