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

    /** Adds an equals filter (`column=eq.value`). */
    public fun eq(column: String, value: String) {
        params += column to "eq.$value"
    }

    /** Adds a not-equals filter (`column=neq.value`). */
    public fun neq(column: String, value: String) {
        params += column to "neq.$value"
    }

    /** Adds a greater-than filter (`column=gt.value`). */
    public fun gt(column: String, value: String) {
        params += column to "gt.$value"
    }

    /** Adds a greater-than-or-equal filter (`column=gte.value`). */
    public fun gte(column: String, value: String) {
        params += column to "gte.$value"
    }

    /** Adds a less-than filter (`column=lt.value`). */
    public fun lt(column: String, value: String) {
        params += column to "lt.$value"
    }

    /** Adds a less-than-or-equal filter (`column=lte.value`). */
    public fun lte(column: String, value: String) {
        params += column to "lte.$value"
    }

    /** Adds a SQL `LIKE` filter (`column=like.pattern`). */
    public fun like(column: String, pattern: String) {
        params += column to "like.$pattern"
    }

    /** Adds a SQL `ILIKE` filter (`column=ilike.pattern`). */
    public fun ilike(column: String, pattern: String) {
        params += column to "ilike.$pattern"
    }

    /**
     * PostgREST `is` operator — used for `null`, `true`, `false`.
     *
     * Named with backticks because `is` is a Kotlin keyword.
     */
    public fun `is`(column: String, value: String) {
        params += column to "is.$value"
    }

    /**
     * PostgREST `in` operator — matches any value in [values].
     *
     * Named with backticks because `in` is a Kotlin keyword.
     */
    public fun `in`(column: String, values: List<String>) {
        params += column to "in.(${values.joinToString(",")})"
    }

    /** Adds a contains filter (`column=cs.value`) for array/json/range columns. */
    public fun contains(column: String, value: String) {
        params += column to "cs.$value"
    }

    /** Adds a contained-by filter (`column=cd.value`) for array/json/range columns. */
    public fun containedBy(column: String, value: String) {
        params += column to "cd.$value"
    }

    /** Adds range strictly-right-of filter (`column=sr.value`). */
    public fun rangeGt(column: String, value: String) {
        params += column to "sr.$value"
    }

    /** Adds range not-extend-left-of filter (`column=nxl.value`). */
    public fun rangeGte(column: String, value: String) {
        params += column to "nxl.$value"
    }

    /** Adds range strictly-left-of filter (`column=sl.value`). */
    public fun rangeLt(column: String, value: String) {
        params += column to "sl.$value"
    }

    /** Adds range not-extend-right-of filter (`column=nxr.value`). */
    public fun rangeLte(column: String, value: String) {
        params += column to "nxr.$value"
    }

    /** Adds range adjacent-to filter (`column=adj.value`). */
    public fun rangeAdj(column: String, value: String) {
        params += column to "adj.$value"
    }

    /** Negates all filters built inside [block]. */
    public fun not(block: FilterBuilder.() -> Unit) {
        val inner = FilterBuilder().apply(block).build()
        for ((key, value) in inner) {
            params += key to "not.$value"
        }
    }

    /** Adds an `or=(...)` group from filters produced in [block]. */
    public fun or(block: FilterBuilder.() -> Unit) {
        val inner = FilterBuilder().apply(block).build()
        val combined = inner.joinToString(",") { (k, v) -> "$k.$v" }
        params += "or" to "($combined)"
    }

    /** Adds an `and=(...)` group from filters produced in [block]. */
    public fun and(block: FilterBuilder.() -> Unit) {
        val inner = FilterBuilder().apply(block).build()
        val combined = inner.joinToString(",") { (k, v) -> "$k.$v" }
        params += "and" to "($combined)"
    }

    /** Adds a full-text search filter for [column]. */
    public fun textSearch(
        column: String,
        query: String,
        config: String? = null,
        type: TextSearchType = TextSearchType.Plain,
    ) {
        val configPart = if (config != null) "($config)" else ""
        params += column to "${type.postgrestName}fts$configPart.$query"
    }

    /** Adds a raw PostgREST filter using the provided [operator]. */
    public fun filter(column: String, operator: String, value: String) {
        params += column to "$operator.$value"
    }

    /** Adds ordering via `order=column.asc|desc[.nullsfirst|nullslast]`. */
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

    /** Adds `limit=count`. */
    public fun limit(count: Int) {
        params += "limit" to count.toString()
    }

    /** Adds offset+limit representing an inclusive range `[from, to]`. */
    public fun range(from: Int, to: Int) {
        params += "offset" to from.toString()
        params += "limit" to (to - from + 1).toString()
    }

    /** Returns all accumulated query parameters in insertion order. */
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
