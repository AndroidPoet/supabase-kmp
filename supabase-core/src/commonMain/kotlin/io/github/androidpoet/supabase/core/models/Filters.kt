package io.github.androidpoet.supabase.core.models
@DslMarker
public annotation class FilterDsl
public enum class TextSearchType(internal val postgrestName: String) {
    Plain("pl"),
    Phrase("ph"),
    Websearch("w"),
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
    public fun neq(column: String, value: String) {
        params += column to "neq.${encodeValue(value)}"
    }
    public fun neq(column: String, value: Number) {
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
        params += column to "like.$pattern"
    }
    public fun likeAllOf(column: String, patterns: List<String>) {
        params += column to "like(all).{${patterns.joinToString(",")}}"
    }
    public fun likeAnyOf(column: String, patterns: List<String>) {
        params += column to "like(any).{${patterns.joinToString(",")}}"
    }
    public fun ilike(column: String, pattern: String) {
        params += column to "ilike.$pattern"
    }
    public fun ilikeAllOf(column: String, patterns: List<String>) {
        params += column to "ilike(all).{${patterns.joinToString(",")}}"
    }
    public fun ilikeAnyOf(column: String, patterns: List<String>) {
        params += column to "ilike(any).{${patterns.joinToString(",")}}"
    }
    public fun `is`(column: String, value: String) {
        params += column to "is.$value"
    }
    public fun `in`(column: String, values: List<String>) {
        params += column to "in.(${values.joinToString(",") { encodeValue(it) }})"
    }
    public fun match(values: Map<String, String>) {
        values.forEach { (column, value) -> eq(column, value) }
    }
    public fun contains(column: String, value: String) {
        params += column to "cs.$value"
    }
    public fun containedBy(column: String, value: String) {
        params += column to "cd.$value"
    }
    public fun overlaps(column: String, value: String) {
        params += column to "ov.$value"
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
        params += column to "not.$operator.$value"
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
        params += column to "${type.postgrestName}fts$configPart.$query"
    }
    public fun filter(column: String, operator: String, value: String) {
        params += column to "$operator.$value"
    }
    public fun order(
        column: String,
        ascending: Boolean = true,
        nullsFirst: Boolean? = null,
        referencedTable: String? = null,
    ) {
        val dir = if (ascending) "asc" else "desc"
        val nulls = when (nullsFirst) {
            true -> ".nullsfirst"
            false -> ".nullslast"
            null -> ""
        }
        val key = if (referencedTable == null) "order" else "$referencedTable.order"
        params += key to "$column.$dir$nulls"
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
            val needsQuoting = value.isEmpty() || value.any { ch ->
                ch == ',' || ch == '(' || ch == ')' || ch == '"' || ch == '\\'
            }
            if (!needsQuoting) return value
            val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
            return "\"$escaped\""
        }
    }
}
public inline fun filters(block: FilterBuilder.() -> Unit): List<Pair<String, String>> =
    FilterBuilder().apply(block).build()
