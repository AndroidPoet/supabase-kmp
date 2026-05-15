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
    public fun like(column: String, pattern: String) {
        params += column to "like.$pattern"
    }
    public fun ilike(column: String, pattern: String) {
        params += column to "ilike.$pattern"
    }
    public fun `is`(column: String, value: String) {
        params += column to "is.$value"
    }
    public fun `in`(column: String, values: List<String>) {
        params += column to "in.(${values.joinToString(",")})"
    }
    public fun contains(column: String, value: String) {
        params += column to "cs.$value"
    }
    public fun containedBy(column: String, value: String) {
        params += column to "cd.$value"
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
    public fun build(): List<Pair<String, String>> = params.toList()
}
public inline fun filters(block: FilterBuilder.() -> Unit): List<Pair<String, String>> =
    FilterBuilder().apply(block).build()
