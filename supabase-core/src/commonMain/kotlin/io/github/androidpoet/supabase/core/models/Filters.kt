package io.github.androidpoet.supabase.core.models

import kotlin.jvm.JvmInline

/**
 * Confines the receiver of nested DSL blocks (`where`/`or`/`and`/`not`) so an
 * inner block can't accidentally call methods on an outer builder.
 */
@DslMarker
public annotation class FilterDsl

/**
 * A type-safe handle to a table column, carrying both its wire [name] and the
 * Kotlin type [T] of the values it holds. Pass these to the infix filter
 * operators (e.g. [WhereBuilder.eq], [WhereBuilder.greaterEq]) so the compiler
 * rejects type-mismatched comparisons like `age eq "oops"`.
 *
 * Declare columns by hand for ad-hoc queries, or let codegen emit a typed schema
 * object for compile-time-checked filters:
 *
 * ```
 * public object Profiles {
 *     public val age: Column<Int> = Column("age")
 *     public val status: Column<String> = Column("status")
 * }
 * ```
 */
@JvmInline
public value class Column<T>(
    public val name: String,
)

/** Sort direction for [QueryBuilder.orderBy]. */
public enum class Order(
    internal val postgrestName: String,
) {
    /** Ascending (`asc`). */
    ASC("asc"),

    /** Descending (`desc`). */
    DESC("desc"),
}

/** Placement of `NULL` values relative to non-null values for [QueryBuilder.orderBy]. */
public enum class Nulls(
    internal val postgrestName: String,
) {
    /** Sort `NULL` values first (`nullsfirst`). */
    FIRST("nullsfirst"),

    /** Sort `NULL` values last (`nullslast`). */
    LAST("nullslast"),
}

/**
 * Full-text search mode for [WhereBuilder.textSearch], selecting how the query is
 * parsed into a `tsquery`. Maps to a PostgREST operator prefix: `fts`/`plfts`/
 * `phfts`/`wfts`.
 */
public enum class TextSearchType(
    internal val postgrestName: String,
) {
    /** Bare `fts` (passed straight to `to_tsquery`). */
    Raw(""),

    /** Plain `plfts`. */
    Plain("pl"),

    /** Phrase `phfts`. */
    Phrase("ph"),

    /** Web-search `wfts`. */
    Websearch("w"),
}

/**
 * An immutable filter expression — the value the filter DSL builds. A [Filter] is
 * either a single column predicate or a logical combination (`and`/`or`/`not`) of
 * other filters. Build them with the infix operators in [WhereBuilder]; they are
 * rendered to PostgREST query parameters when a request is issued.
 *
 * Modelling a filter as a value (rather than mutating a list of strings) keeps the
 * logical combinators trivial: rendering is a single recursive walk over the tree,
 * with no re-parsing of already-serialized output.
 */
public sealed interface Filter

/** A single column predicate, e.g. `age=gte.18`. [expr] is the wire form after the
 * `=`, including the operator (and a leading `not.` when negated). */
internal class FilterLeaf(
    val column: String,
    val expr: String,
) : Filter

/** A logical group (`and`/`or`), optionally scoped to a referenced table and
 * optionally negated. */
internal class FilterGroup(
    val type: String,
    val referencedTable: String?,
    val children: List<Filter>,
    val negated: Boolean,
) : Filter

/**
 * Renders a list of top-level filters (implicitly AND-ed) into PostgREST query
 * parameters. Leaves become their own `column=expr` pair; logical groups become a
 * single `or=(…)`/`and=(…)` (or `not.and=(…)`, `tbl.or=(…)`) pair.
 */
@PublishedApi
internal fun List<Filter>.toParams(): List<Pair<String, String>> =
    flatMap { it.toTopLevelParams() }

private fun Filter.toTopLevelParams(): List<Pair<String, String>> =
    when (this) {
        is FilterLeaf -> listOf(column to expr)
        is FilterGroup -> {
            val key =
                buildString {
                    if (negated) append("not.")
                    referencedTable?.let { append(it).append('.') }
                    append(type)
                }
            listOf(key to "(${children.joinToString(",") { it.toGroupTerm() }})")
        }
    }

/** Renders a filter as a term usable INSIDE a logical group: `col.op.val` for a
 * leaf, `and(…)`/`not.or(…)` for a nested group (no dot before the parenthesis). */
private fun Filter.toGroupTerm(): String =
    when (this) {
        is FilterLeaf -> "$column.$expr"
        is FilterGroup -> {
            val key =
                buildString {
                    if (negated) append("not.")
                    referencedTable?.let { append(it).append('.') }
                    append(type)
                }
            "$key(${children.joinToString(",") { it.toGroupTerm() }})"
        }
    }

/** Negates a filter: a leaf gains a `not.` operator prefix; a group is flipped to
 * its negated form (`not.and=(…)`). Double negation collapses. */
private fun negate(filter: Filter): Filter =
    when (filter) {
        is FilterLeaf ->
            if (filter.expr.startsWith("not.")) {
                FilterLeaf(filter.column, filter.expr.removePrefix("not."))
            } else {
                FilterLeaf(filter.column, "not.${filter.expr}")
            }
        is FilterGroup ->
            FilterGroup(filter.type, filter.referencedTable, filter.children, !filter.negated)
    }

/**
 * Builds a [Filter] from column predicates combined with the infix operators. Each
 * statement in a `where { }` block adds one predicate; multiple statements are
 * AND-ed together. Use [or]/[and]/[not] for explicit logical groups.
 *
 * String operands are escaped via PostgREST's quoting rules when they contain a
 * structural character (comma, parens, quote, backslash); numbers and booleans are
 * emitted verbatim. Vocabulary mirrors JetBrains Exposed / Ktorm.
 */
@FilterDsl
public class WhereBuilder {
    @PublishedApi
    internal val filters: MutableList<Filter> = mutableListOf()

    private fun add(column: String, expr: String) {
        filters += FilterLeaf(column, expr)
    }

    // ── Comparison ────────────────────────────────────────────────────────────

    /** `column = value` (SQL `=`). */
    public infix fun <T> Column<T>.eq(value: T): Unit = add(name, "eq.${renderValue(value)}")

    /** `column <> value` (SQL `<>`). */
    public infix fun <T> Column<T>.neq(value: T): Unit = add(name, "neq.${renderValue(value)}")

    /** `column > value` (SQL `>`). */
    public infix fun <T : Comparable<T>> Column<T>.greater(value: T): Unit = add(name, "gt.${renderValue(value)}")

    /** `column >= value` (SQL `>=`). */
    public infix fun <T : Comparable<T>> Column<T>.greaterEq(value: T): Unit = add(name, "gte.${renderValue(value)}")

    /** `column < value` (SQL `<`). */
    public infix fun <T : Comparable<T>> Column<T>.less(value: T): Unit = add(name, "lt.${renderValue(value)}")

    /** `column <= value` (SQL `<=`). */
    public infix fun <T : Comparable<T>> Column<T>.lessEq(value: T): Unit = add(name, "lte.${renderValue(value)}")

    /** `column` within [range], inclusive (`>=` AND `<=`). */
    public infix fun <T : Comparable<T>> Column<T>.within(range: ClosedRange<T>) {
        add(name, "gte.${renderValue(range.start)}")
        add(name, "lte.${renderValue(range.endInclusive)}")
    }

    /** Null-aware inequality (SQL `IS DISTINCT FROM`): true when values differ OR
     * [this] column is null, unlike [neq] which is null-unknown. */
    public infix fun <T> Column<T>.isDistinctFrom(value: T): Unit = add(name, "isdistinct.${renderValue(value)}")

    // ── Null / boolean IS ───────────────────────────────────────────────────────

    /** `column IS NULL`. */
    public fun Column<*>.isNull(): Unit = add(name, "is.null")

    /** `column IS NOT NULL`. */
    public fun Column<*>.isNotNull(): Unit = add(name, "not.is.null")

    /** `column IS TRUE/FALSE` (SQL `IS`). For nullable booleans, prefer [isNull]/[isNotNull]. */
    public infix fun Column<Boolean>.isExactly(value: Boolean): Unit = add(name, "is.$value")

    // ── Pattern matching ────────────────────────────────────────────────────────

    /** Case-sensitive `LIKE` against [pattern] (`%` = any run). */
    public infix fun Column<String>.like(pattern: String): Unit = add(name, "like.${encodeValue(pattern)}")

    /** Case-insensitive `ILIKE` against [pattern]. */
    public infix fun Column<String>.ilike(pattern: String): Unit = add(name, "ilike.${encodeValue(pattern)}")

    /** Case-sensitive POSIX regex match (SQL `~`). */
    public infix fun Column<String>.matches(pattern: String): Unit = add(name, "match.${encodeValue(pattern)}")

    /** Case-insensitive POSIX regex match (SQL `~*`). */
    public infix fun Column<String>.imatches(pattern: String): Unit = add(name, "imatch.${encodeValue(pattern)}")

    /** Matches every pattern in [patterns] (`LIKE ALL`). */
    public infix fun Column<String>.likeAllOf(patterns: List<String>): Unit =
        add(name, "like(all).{${patterns.joinToString(",") { encodeValue(it) }}}")

    /** Matches at least one pattern in [patterns] (`LIKE ANY`). */
    public infix fun Column<String>.likeAnyOf(patterns: List<String>): Unit =
        add(name, "like(any).{${patterns.joinToString(",") { encodeValue(it) }}}")

    // ── Membership ──────────────────────────────────────────────────────────────

    /** `column IN (values)` (SQL `IN`). */
    public infix fun <T> Column<T>.inList(values: List<T>): Unit =
        add(name, "in.(${values.joinToString(",") { renderValue(it) }})")

    // ── Array / range ─────────────────────────────────────────────────────────

    /** Array/range column contains every element of [values] (SQL `@>`). */
    public infix fun <T> Column<List<T>>.contains(values: List<T>): Unit = add(name, "cs.${arrayLiteral(values)}")

    /** Array/range column is contained by [values] (SQL `<@`). */
    public infix fun <T> Column<List<T>>.containedBy(values: List<T>): Unit = add(name, "cd.${arrayLiteral(values)}")

    /** Array/range column shares any element with [values] (SQL `&&`). */
    public infix fun <T> Column<List<T>>.overlaps(values: List<T>): Unit = add(name, "ov.${arrayLiteral(values)}")

    // ── Full-text search ──────────────────────────────────────────────────────

    /** Full-text search on [this] column against [query] using parse mode [type]. */
    public fun Column<String>.textSearch(
        query: String,
        config: String? = null,
        type: TextSearchType = TextSearchType.Plain,
    ) {
        val configPart = if (config != null) "($config)" else ""
        add(name, "${type.postgrestName}fts$configPart.${encodeValue(query)}")
    }

    // ── Escape hatch ─────────────────────────────────────────────────────────

    /** Raw operator escape hatch: emits `column=<operator>.<value>` for operators the
     * DSL doesn't expose yet. [value] is quoted/escaped. */
    public fun raw(column: Column<*>, operator: String, value: String): Unit =
        add(column.name, "$operator.${encodeValue(value)}")

    // ── Logical groups ──────────────────────────────────────────────────────────

    /** Combines the predicates in [block] with logical OR. */
    public fun or(referencedTable: String? = null, block: WhereBuilder.() -> Unit) {
        filters += FilterGroup("or", referencedTable, WhereBuilder().apply(block).filters, negated = false)
    }

    /** Combines the predicates in [block] with logical AND as an explicit group
     * (useful nested inside [or]). */
    public fun and(referencedTable: String? = null, block: WhereBuilder.() -> Unit) {
        filters += FilterGroup("and", referencedTable, WhereBuilder().apply(block).filters, negated = false)
    }

    /** Negates every predicate declared inside [block] (SQL `NOT`). */
    public fun not(block: WhereBuilder.() -> Unit) {
        WhereBuilder().apply(block).filters.forEach { filters += negate(it) }
    }

    /** Snapshot of the accumulated top-level filters, in declaration order. */
    @PublishedApi
    internal fun build(): List<Filter> = filters.toList()

    private companion object {
        // PostgREST treats comma, parentheses and double-quote as structural. A value
        // containing one must be double-quoted with `"`/`\` escaped, or it changes the
        // query's meaning. Plain values pass through so simple filters stay readable.
        private fun encodeValue(value: String): String {
            val needsQuoting =
                value.isEmpty() ||
                    value.any { ch -> ch == ',' || ch == '(' || ch == ')' || ch == '"' || ch == '\\' }
            if (!needsQuoting) return value
            val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
            return "\"$escaped\""
        }

        // Renders a typed operand to its wire form: numbers/booleans verbatim, anything
        // else escaped via encodeValue on its toString().
        private fun renderValue(value: Any?): String =
            when (value) {
                null -> "null"
                is Number, is Boolean -> value.toString()
                else -> encodeValue(value.toString())
            }

        private fun arrayLiteral(values: List<Any?>): String =
            "{${values.joinToString(",") { renderValue(it) }}}"
    }
}

/**
 * Builds a full read query: a `where { }` predicate plus result modifiers
 * (`orderBy`, `limit`, `offset`, `range`). This is the receiver of the query DSL
 * block on read methods like `select`. Modifiers are kept distinct from filters, so
 * `not { limit(1) }` is no longer expressible.
 */
@FilterDsl
public class QueryBuilder {
    @PublishedApi
    internal val where: WhereBuilder = WhereBuilder()

    @PublishedApi
    internal val modifiers: MutableList<Pair<String, String>> = mutableListOf()

    /** Filter predicate for this query. Multiple statements are AND-ed. */
    public fun where(block: WhereBuilder.() -> Unit) {
        where.apply(block)
    }

    /** Order by [column]. Successive calls add secondary sort keys. */
    public fun orderBy(
        column: Column<*>,
        order: Order = Order.ASC,
        nulls: Nulls? = null,
        referencedTable: String? = null,
    ) {
        val nullsPart = if (nulls != null) ".${nulls.postgrestName}" else ""
        val key = if (referencedTable == null) "order" else "$referencedTable.order"
        appendOrder(key, "${column.name}.${order.postgrestName}$nullsPart")
    }

    /** Cap the result at [count] rows. Last call wins. */
    public fun limit(count: Int, referencedTable: String? = null): Unit =
        setOnce(if (referencedTable == null) "limit" else "$referencedTable.limit", count.toString())

    /** Skip the first [count] rows. Last call wins. */
    public fun offset(count: Int, referencedTable: String? = null): Unit =
        setOnce(if (referencedTable == null) "offset" else "$referencedTable.offset", count.toString())

    /** Inclusive, zero-based row window `[from, to]`, emitted as `offset` + derived
     * `limit` (`to - from + 1`). */
    public fun range(from: Int, to: Int, referencedTable: String? = null) {
        require(to >= from) { "range 'to' ($to) must be >= 'from' ($from)" }
        setOnce(if (referencedTable == null) "offset" else "$referencedTable.offset", from.toString())
        setOnce(if (referencedTable == null) "limit" else "$referencedTable.limit", (to - from + 1).toString())
    }

    // PostgREST honours only one `limit`/`offset` per scope, so a second call must
    // overwrite rather than append a duplicate (the old builder silently emitted both).
    private fun setOnce(key: String, value: String) {
        val index = modifiers.indexOfFirst { it.first == key }
        if (index >= 0) modifiers[index] = key to value else modifiers += key to value
    }

    // Multi-column ordering is ONE comma-separated `order` param; appending a second
    // `order=` would be silently dropped by PostgREST, so accumulate into one.
    private fun appendOrder(key: String, term: String) {
        val index = modifiers.indexOfFirst { it.first == key }
        if (index >= 0) modifiers[index] = key to "${modifiers[index].second},$term" else modifiers += key to term
    }

    /** All query parameters: filter params followed by modifier params. */
    @PublishedApi
    internal fun build(): List<Pair<String, String>> = where.build().toParams() + modifiers.toList()
}

/** Builds a filter predicate from a [WhereBuilder] [block] — the entry point used by
 * mutation methods (update/delete) that take a `where`-only block. */
public inline fun where(block: WhereBuilder.() -> Unit): List<Pair<String, String>> =
    WhereBuilder().apply(block).build().toParams()

/** Builds a full read query (filter + modifiers) from a [QueryBuilder] [block]. */
public inline fun query(block: QueryBuilder.() -> Unit): List<Pair<String, String>> =
    QueryBuilder().apply(block).build()
