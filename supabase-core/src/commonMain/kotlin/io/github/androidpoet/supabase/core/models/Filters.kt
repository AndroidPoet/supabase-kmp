package io.github.androidpoet.supabase.core.models

/**
 * Marks DSL components that build PostgREST query-string filters.
 */
@DslMarker
public annotation class FilterDsl

/**
 * Type of PostgreSQL full-text search to perform.
 */
public enum class TextSearchType(internal val postgrestName: String) {
    Plain("pl"),
    Phrase("ph"),
    Websearch("w"),
}

/**
 * A builder that accumulates PostgREST filter parameters as key-value pairs
 * suitable for appending to a query string.
 *
 * Usage:
 * ```kotlin
 * val params = filters {
 *     eq("status", "active")
 *     gte("age", "18")
 *     order("created_at", ascending = false)
 *     limit(10)
 * }
 * ```
 */
@FilterDsl
public class FilterBuilder {

    @PublishedApi
    internal val params: MutableList<Pair<String, String>> = mutableListOf()

    // ── Comparison operators ─────────────────────────────────────────

    public fun eq(column: String, value: String) {
        params += column to "eq.$value"
    }

    public fun neq(column: String, value: String) {
        params += column to "neq.$value"
    }

    public fun gt(column: String, value: String) {
        params += column to "gt.$value"
    }

    public fun gte(column: String, value: String) {
        params += column to "gte.$value"
    }

    public fun lt(column: String, value: String) {
        params += column to "lt.$value"
    }

    public fun lte(column: String, value: String) {
        params += column to "lte.$value"
    }

    // ── Pattern matching ─────────────────────────────────────────────

    public fun like(column: String, pattern: String) {
        params += column to "like.$pattern"
    }

    public fun ilike(column: String, pattern: String) {
        params += column to "ilike.$pattern"
    }

    // ── Null / boolean checks ────────────────────────────────────────

    /**
     * PostgREST `is` operator — used for `null`, `true`, `false`.
     *
     * Named with backticks because `is` is a Kotlin keyword.
     */
    public fun `is`(column: String, value: String) {
        params += column to "is.$value"
    }

    // ── Collection operators ─────────────────────────────────────────

    /**
     * PostgREST `in` operator — matches any value in [values].
     *
     * Named with backticks because `in` is a Kotlin keyword.
     */
    public fun `in`(column: String, values: List<String>) {
        params += column to "in.(${values.joinToString(",")})"
    }

    public fun contains(column: String, value: String) {
        params += column to "cs.$value"
    }

    public fun containedBy(column: String, value: String) {
        params += column to "cd.$value"
    }

    // ── Range operators ──────────────────────────────────────────────

    public fun rangeGt(column: String, value: String) {
        params += column to "sr.$value"
    }

    public fun rangeGte(column: String, value: String) {
        params += column to "nxl.$value"
    }

    public fun rangeLt(column: String, value: String) {
        params += column to "sl.$value"
    }

    public fun rangeLte(column: String, value: String) {
        params += column to "nxr.$value"
    }

    public fun rangeAdj(column: String, value: String) {
        params += column to "adj.$value"
    }

    // ── Logical combinators ──────────────────────────────────────────

    public fun not(block: FilterBuilder.() -> Unit) {
        val inner = FilterBuilder().apply(block).build()
        for ((key, value) in inner) {
            params += key to "not.$value"
        }
    }

    public fun or(block: FilterBuilder.() -> Unit) {
        val inner = FilterBuilder().apply(block).build()
        val combined = inner.joinToString(",") { (k, v) -> "$k.$v" }
        params += "or" to "($combined)"
    }

    public fun and(block: FilterBuilder.() -> Unit) {
        val inner = FilterBuilder().apply(block).build()
        val combined = inner.joinToString(",") { (k, v) -> "$k.$v" }
        params += "and" to "($combined)"
    }

    // ── Full-text search ─────────────────────────────────────────────

    public fun textSearch(
        column: String,
        query: String,
        config: String? = null,
        type: TextSearchType = TextSearchType.Plain,
    ) {
        val configPart = if (config != null) "($config)" else ""
        params += column to "${type.postgrestName}fts$configPart.$query"
    }

    // ── Raw filter ───────────────────────────────────────────────────

    public fun filter(column: String, operator: String, value: String) {
        params += column to "$operator.$value"
    }

    // ── Ordering & pagination ────────────────────────────────────────

    public fun order(
        column: String,
        ascending: Boolean = true,
        nullsFirst: Boolean? = null,
    ) {
        val dir = if (ascending) "asc" else "desc"
        val nulls = when (nullsFirst) {
            true -> ".nullsfirst"
            false -> ".nullslast"
            null -> ""
        }
        params += "order" to "$column.$dir$nulls"
    }

    public fun limit(count: Int) {
        params += "limit" to count.toString()
    }

    public fun range(from: Int, to: Int) {
        params += "offset" to from.toString()
        params += "limit" to (to - from + 1).toString()
    }

    // ── Build ────────────────────────────────────────────────────────

    public fun build(): List<Pair<String, String>> = params.toList()
}

/**
 * Entry-point for the PostgREST filter DSL.
 *
 * ```kotlin
 * val queryParams = filters {
 *     eq("id", "42")
 *     order("name")
 * }
 * ```
 */
public inline fun filters(block: FilterBuilder.() -> Unit): List<Pair<String, String>> =
    FilterBuilder().apply(block).build()
