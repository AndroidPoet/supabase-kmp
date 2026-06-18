package io.github.androidpoet.supabase.core.models

import kotlin.jvm.JvmName

@DslMarker
public annotation class FilterDsl

public enum class TextSearchType(
    internal val postgrestName: String,
) {
    Plain("pl"),
    Phrase("ph"),
    Websearch("w"),
}

/**
 * Sort direction for [FilterBuilder.order].
 *
 * Maps to the PostgREST `asc`/`desc` tokens in the `order` query parameter
 * (e.g. `order=created_at.desc`).
 */
public enum class OrderDirection(
    internal val postgrestName: String,
) {
    /** Ascending order (`asc`). */
    ASCENDING("asc"),

    /** Descending order (`desc`). */
    DESCENDING("desc"),
}

/**
 * Placement of `NULL` values relative to non-null values for [FilterBuilder.order].
 *
 * Maps to the PostgREST `nullsfirst`/`nullslast` tokens in the `order` query
 * parameter (e.g. `order=name.asc.nullslast`).
 */
public enum class NullsPlacement(
    internal val postgrestName: String,
) {
    /** Sort `NULL` values before non-null values (`nullsfirst`). */
    FIRST("nullsfirst"),

    /** Sort `NULL` values after non-null values (`nullslast`). */
    LAST("nullslast"),
}

@FilterDsl
public class FilterBuilder {
    @PublishedApi
    internal val params: MutableList<Pair<String, String>> = mutableListOf()

    public fun eq(column: String, value: String) {
        params += column to "eq.${encodeValue(value)}"
    }

    public fun eq(column: String, value: Number) {
        params += column to "eq.$value"
    }

    public fun eq(column: String, value: Boolean) {
        params += column to "eq.$value"
    }

    public fun neq(column: String, value: String) {
        params += column to "neq.${encodeValue(value)}"
    }

    public fun neq(column: String, value: Number) {
        params += column to "neq.$value"
    }

    public fun neq(column: String, value: Boolean) {
        params += column to "neq.$value"
    }

    public fun gt(column: String, value: String) {
        params += column to "gt.${encodeValue(value)}"
    }

    public fun gt(column: String, value: Number) {
        params += column to "gt.$value"
    }

    public fun gte(column: String, value: String) {
        params += column to "gte.${encodeValue(value)}"
    }

    public fun gte(column: String, value: Number) {
        params += column to "gte.$value"
    }

    public fun lt(column: String, value: String) {
        params += column to "lt.${encodeValue(value)}"
    }

    public fun lt(column: String, value: Number) {
        params += column to "lt.$value"
    }

    public fun lte(column: String, value: String) {
        params += column to "lte.${encodeValue(value)}"
    }

    public fun lte(column: String, value: Number) {
        params += column to "lte.$value"
    }

    public fun like(column: String, pattern: String) {
        params += column to "like.${encodeValue(pattern)}"
    }

    public fun likeAllOf(column: String, patterns: List<String>) {
        params += column to "like(all).{${patterns.joinToString(",") { encodeValue(it) }}}"
    }

    public fun likeAnyOf(column: String, patterns: List<String>) {
        params += column to "like(any).{${patterns.joinToString(",") { encodeValue(it) }}}"
    }

    public fun ilike(column: String, pattern: String) {
        params += column to "ilike.${encodeValue(pattern)}"
    }

    public fun ilikeAllOf(column: String, patterns: List<String>) {
        params += column to "ilike(all).{${patterns.joinToString(",") { encodeValue(it) }}}"
    }

    public fun ilikeAnyOf(column: String, patterns: List<String>) {
        params += column to "ilike(any).{${patterns.joinToString(",") { encodeValue(it) }}}"
    }

    /**
     * Finds all rows where the value of [column] matches the POSIX regular
     * expression [pattern] (case-sensitive), i.e. the SQL `~` operator. Emits
     * `column=match.<pattern>`.
     */
    public fun match(column: String, pattern: String) {
        params += column to "match.${encodeValue(pattern)}"
    }

    /**
     * Finds all rows where the value of [column] matches the POSIX regular
     * expression [pattern] case-insensitively, i.e. the SQL `~*` operator. Emits
     * `column=imatch.<pattern>`.
     */
    public fun imatch(column: String, pattern: String) {
        params += column to "imatch.${encodeValue(pattern)}"
    }

    public fun `is`(column: String, value: String) {
        params += column to "is.${encodeValue(value)}"
    }

    /**
     * `IS` check for the three values PostgREST accepts here: `null` (pass `null`),
     * `true`, or `false`. Use this for nullable/boolean columns — e.g.
     * `is("deleted_at", null)` → `deleted_at=is.null`.
     */
    public fun `is`(column: String, value: Boolean?) {
        params += column to "is.${value ?: "null"}"
    }

    public fun `in`(column: String, values: List<String>) {
        params += column to "in.(${values.joinToString(",") { encodeValue(it) }})"
    }

    /**
     * Finds all rows where the value of [column] is one of [values]. Numeric
     * members never need quoting, so each is emitted verbatim. Emits
     * `column=in.(1,2,3)`.
     */
    @JvmName("inNumbers")
    public fun `in`(column: String, values: List<Number>) {
        params += column to "in.(${values.joinToString(",")})"
    }

    /**
     * Finds all rows where the value of [column] is one of [values]. Each
     * member is rendered via its `toString()` and then quoted/escaped if it
     * contains a PostgREST-structural character. Emits `column=in.(a,b,c)`.
     */
    @JvmName("inAny")
    public fun `in`(column: String, values: List<Any>) {
        params += column to "in.(${values.joinToString(",") { encodeValue(it.toString()) }})"
    }

    public fun match(values: Map<String, String>) {
        values.forEach { (column, value) -> eq(column, value) }
    }

    // The array/range operators below take a caller-formatted PostgREST literal
    // (e.g. `{a,b}`, `[1,10)`) whose commas/parens are structural to that literal,
    // so the value is passed through verbatim and is NOT quoted. The caller owns
    // formatting (and escaping individual elements) for these operators.
    public fun contains(column: String, value: String) {
        params += column to "cs.$value"
    }

    /**
     * Finds all rows where the array/range column [column] contains every
     * element of [values]. The list is rendered as a PostgREST array literal
     * `{a,b,c}`, with each element quoted/escaped if needed. Emits
     * `column=cs.{a,b,c}`.
     */
    public fun contains(column: String, values: List<Any>) {
        params += column to "cs.${arrayLiteral(values)}"
    }

    public fun containedBy(column: String, value: String) {
        params += column to "cd.$value"
    }

    /**
     * Finds all rows where every element of the array column [column] is
     * contained in [values]. The list is rendered as a PostgREST array literal
     * `{a,b,c}`, with each element quoted/escaped if needed. Emits
     * `column=cd.{a,b,c}`.
     */
    public fun containedBy(column: String, values: List<Any>) {
        params += column to "cd.${arrayLiteral(values)}"
    }

    public fun overlaps(column: String, value: String) {
        params += column to "ov.$value"
    }

    /**
     * Finds all rows where the array column [column] shares any element with
     * [values]. The list is rendered as a PostgREST array literal `{a,b,c}`,
     * with each element quoted/escaped if needed. Emits `column=ov.{a,b,c}`.
     */
    public fun overlaps(column: String, values: List<Any>) {
        params += column to "ov.${arrayLiteral(values)}"
    }

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

    public fun not(block: FilterBuilder.() -> Unit) {
        val inner = FilterBuilder().apply(block).build()
        for ((key, value) in inner) {
            params += key to "not.$value"
        }
    }

    public fun not(column: String, operator: String, value: String) {
        params += column to "not.$operator.${encodeValue(value)}"
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

    public fun textSearch(
        column: String,
        query: String,
        config: String? = null,
        type: TextSearchType = TextSearchType.Plain,
    ) {
        val configPart = if (config != null) "($config)" else ""
        params += column to "${type.postgrestName}fts$configPart.${encodeValue(query)}"
    }

    public fun filter(column: String, operator: String, value: String) {
        params += column to "$operator.${encodeValue(value)}"
    }

    public fun order(
        column: String,
        ascending: Boolean = true,
        nullsFirst: Boolean? = null,
        referencedTable: String? = null,
    ) {
        val dir = if (ascending) "asc" else "desc"
        val nulls =
            when (nullsFirst) {
                true -> ".nullsfirst"
                false -> ".nullslast"
                null -> ""
            }
        val key = if (referencedTable == null) "order" else "$referencedTable.order"
        params += key to "$column.$dir$nulls"
    }

    /**
     * Orders the result by [column] using the type-safe [OrderDirection] and
     * optional [NullsPlacement] instead of the boolean/magic-string overload.
     * Emits `order=column.<asc|desc>[.<nullsfirst|nullslast>]` (or
     * `<referencedTable>.order=...` when [referencedTable] is given).
     *
     * @param direction ascending or descending.
     * @param nulls where to place `NULL`s; omit (`null`) to use PostgreSQL's default.
     * @param referencedTable order on an embedded/joined resource when set.
     */
    public fun order(
        column: String,
        direction: OrderDirection,
        nulls: NullsPlacement? = null,
        referencedTable: String? = null,
    ) {
        val nullsPart = if (nulls != null) ".${nulls.postgrestName}" else ""
        val key = if (referencedTable == null) "order" else "$referencedTable.order"
        params += key to "$column.${direction.postgrestName}$nullsPart"
    }

    public fun limit(count: Int, referencedTable: String? = null) {
        val key = if (referencedTable == null) "limit" else "$referencedTable.limit"
        params += key to count.toString()
    }

    public fun range(from: Int, to: Int, referencedTable: String? = null) {
        val offsetKey = if (referencedTable == null) "offset" else "$referencedTable.offset"
        val limitKey = if (referencedTable == null) "limit" else "$referencedTable.limit"
        params += offsetKey to from.toString()
        params += limitKey to (to - from + 1).toString()
    }

    public fun strictlyLeft(column: String, value: String) {
        rangeLt(column, value)
    }

    public fun strictlyRight(column: String, value: String) {
        rangeGt(column, value)
    }

    public fun notExtendRight(column: String, value: String) {
        rangeLte(column, value)
    }

    public fun notExtendLeft(column: String, value: String) {
        rangeGte(column, value)
    }

    public fun build(): List<Pair<String, String>> = params.toList()

    private companion object {
        // PostgREST treats comma, parentheses and double-quote as structural
        // (element separators in `in(...)`, grouping in `or/and`). A value that
        // contains one must be wrapped in double quotes with `"`/`\` escaped, or
        // it changes the meaning of the query. Plain values pass through unchanged
        // so simple filters stay readable. Dots are left alone — PostgREST only
        // splits the operator from the value on the first dot.
        private fun encodeValue(value: String): String {
            val needsQuoting =
                value.isEmpty() ||
                    value.any { ch ->
                        ch == ',' || ch == '(' || ch == ')' || ch == '"' || ch == '\\'
                    }
            if (!needsQuoting) return value
            val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
            return "\"$escaped\""
        }

        // Renders a PostgREST array literal `{a,b,c}` from a typed list. Inside
        // the braces a comma separates elements, so an element containing one is
        // double-quoted/escaped exactly like a scalar value; braces are added by
        // this method and are structural.
        private fun arrayLiteral(values: List<Any>): String =
            "{${values.joinToString(",") { encodeValue(it.toString()) }}}"
    }
}

public inline fun filters(block: FilterBuilder.() -> Unit): List<Pair<String, String>> =
    FilterBuilder().apply(block).build()
