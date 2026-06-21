package io.github.androidpoet.supabase.core.models

import kotlin.jvm.JvmName

/**
 * Confines the receiver of nested [FilterBuilder] blocks (`not`/`or`/`and`) so an
 * inner block can't accidentally call methods on an outer builder.
 *
 * Standard Kotlin DSL scoping marker; see [FilterBuilder] for the DSL it guards.
 */
@DslMarker
public annotation class FilterDsl

/**
 * Full-text search mode for [FilterBuilder.textSearch], selecting how [query] is
 * parsed into a `tsquery`.
 *
 * Each constant maps to a PostgREST operator prefix: `fts` ([Raw]), `plfts`
 * ([Plain]), `phfts` ([Phrase]) or `wfts` ([Websearch]) — see the wire forms below.
 */
public enum class TextSearchType(
    internal val postgrestName: String,
) {
    /**
     * Passes [query] straight to `to_tsquery` (the bare `fts` operator). Carries no
     * prefix, so it emits `fts.<query>` (or `fts(english).<query>` with a config).
     */
    Raw(""),

    /** Treats [query] as space-separated lexemes (`plfts`, `to_tsquery`'s plain form). */
    Plain("pl"),

    /** Matches the lexemes of [query] as an adjacent phrase (`phfts`). */
    Phrase("ph"),

    /** Parses [query] with web-search syntax such as quotes and `or` (`wfts`). */
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

/**
 * Builds the list of PostgREST query parameters for a request's `WHERE`/`ORDER`
 * clauses — the type-safe entry point to the filter DSL.
 *
 * Each method appends one `column to "operator.value"` pair (the wire form
 * PostgREST expects, e.g. `id=eq.7`); call [build] to get the accumulated
 * `(name, value)` pairs. String operands are run through `encodeValue` so a value
 * containing PostgREST-structural characters (comma, parentheses, quote,
 * backslash) is double-quoted rather than changing the query's meaning; numeric
 * and boolean operands are emitted verbatim. The logical combinators [not], [or]
 * and [and] take nested blocks scoped by [FilterDsl]. Prefer the [filters]
 * builder function to instantiate this. Not thread-safe — build on one coroutine.
 */
@FilterDsl
public class FilterBuilder {
    @PublishedApi
    internal val params: MutableList<Pair<String, String>> = mutableListOf()

    /** Matches rows where [column] equals [value] (SQL `=`). Emits `column=eq.<value>`. */
    public fun eq(column: String, value: String) {
        params += column to "eq.${encodeValue(value)}"
    }

    /** Matches rows where [column] equals the number [value] (SQL `=`). Emits `column=eq.<value>`. */
    public fun eq(column: String, value: Number) {
        params += column to "eq.$value"
    }

    /** Matches rows where [column] equals the boolean [value] (SQL `=`). Emits `column=eq.true`/`eq.false`. */
    public fun eq(column: String, value: Boolean) {
        params += column to "eq.$value"
    }

    /** Matches rows where [column] does not equal [value] (SQL `<>`). Emits `column=neq.<value>`. */
    public fun neq(column: String, value: String) {
        params += column to "neq.${encodeValue(value)}"
    }

    /** Matches rows where [column] does not equal the number [value] (SQL `<>`). Emits `column=neq.<value>`. */
    public fun neq(column: String, value: Number) {
        params += column to "neq.$value"
    }

    /**
     * Matches rows where [column] does not equal the boolean [value] (SQL `<>`).
     * Emits `column=neq.true`/`neq.false`.
     */
    public fun neq(column: String, value: Boolean) {
        params += column to "neq.$value"
    }

    /** Matches rows where [column] is greater than [value] (SQL `>`). Emits `column=gt.<value>`. */
    public fun gt(column: String, value: String) {
        params += column to "gt.${encodeValue(value)}"
    }

    /** Matches rows where [column] is greater than the number [value] (SQL `>`). Emits `column=gt.<value>`. */
    public fun gt(column: String, value: Number) {
        params += column to "gt.$value"
    }

    /** Matches rows where [column] is greater than or equal to [value] (SQL `>=`). Emits `column=gte.<value>`. */
    public fun gte(column: String, value: String) {
        params += column to "gte.${encodeValue(value)}"
    }

    /**
     * Matches rows where [column] is greater than or equal to the number [value]
     * (SQL `>=`). Emits `column=gte.<value>`.
     */
    public fun gte(column: String, value: Number) {
        params += column to "gte.$value"
    }

    /** Matches rows where [column] is less than [value] (SQL `<`). Emits `column=lt.<value>`. */
    public fun lt(column: String, value: String) {
        params += column to "lt.${encodeValue(value)}"
    }

    /** Matches rows where [column] is less than the number [value] (SQL `<`). Emits `column=lt.<value>`. */
    public fun lt(column: String, value: Number) {
        params += column to "lt.$value"
    }

    /** Matches rows where [column] is less than or equal to [value] (SQL `<=`). Emits `column=lte.<value>`. */
    public fun lte(column: String, value: String) {
        params += column to "lte.${encodeValue(value)}"
    }

    /**
     * Matches rows where [column] is less than or equal to the number [value]
     * (SQL `<=`). Emits `column=lte.<value>`.
     */
    public fun lte(column: String, value: Number) {
        params += column to "lte.$value"
    }

    /**
     * Matches rows where [column] matches the case-sensitive [pattern] (SQL `LIKE`,
     * `%` = any run, `*` is accepted by PostgREST as an alias). Emits `column=like.<pattern>`.
     */
    public fun like(column: String, pattern: String) {
        params += column to "like.${encodeValue(pattern)}"
    }

    /**
     * Matches rows where [column] satisfies every pattern in [patterns]
     * (case-sensitive `LIKE ALL`). Emits `column=like(all).{p1,p2}`.
     */
    public fun likeAllOf(column: String, patterns: List<String>) {
        params += column to "like(all).{${patterns.joinToString(",") { encodeValue(it) }}}"
    }

    /**
     * Matches rows where [column] satisfies at least one pattern in [patterns]
     * (case-sensitive `LIKE ANY`). Emits `column=like(any).{p1,p2}`.
     */
    public fun likeAnyOf(column: String, patterns: List<String>) {
        params += column to "like(any).{${patterns.joinToString(",") { encodeValue(it) }}}"
    }

    /**
     * Matches rows where [column] matches the case-insensitive [pattern]
     * (SQL `ILIKE`). Emits `column=ilike.<pattern>`.
     */
    public fun ilike(column: String, pattern: String) {
        params += column to "ilike.${encodeValue(pattern)}"
    }

    /**
     * Matches rows where [column] satisfies every pattern in [patterns]
     * (case-insensitive `ILIKE ALL`). Emits `column=ilike(all).{p1,p2}`.
     */
    public fun ilikeAllOf(column: String, patterns: List<String>) {
        params += column to "ilike(all).{${patterns.joinToString(",") { encodeValue(it) }}}"
    }

    /**
     * Matches rows where [column] satisfies at least one pattern in [patterns]
     * (case-insensitive `ILIKE ANY`). Emits `column=ilike(any).{p1,p2}`.
     */
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

    /**
     * `IS` check against a raw token (SQL `IS`). Pass the PostgREST literal you
     * want, e.g. `"null"`, `"true"`, `"unknown"`. Prefer the [Boolean]`?` overload
     * for the common nullable/boolean case. Emits `column=is.<value>`.
     */
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

    /**
     * Matches rows where [column] is distinct from [value] — a null-aware inequality
     * (SQL `IS DISTINCT FROM`): true when the values differ OR when [column] is null,
     * unlike [neq] which is null-unknown. Emits `column=isdistinct.<value>`.
     */
    public fun isDistinct(column: String, value: String) {
        params += column to "isdistinct.${encodeValue(value)}"
    }

    /** Null-aware inequality against the number [value] (SQL `IS DISTINCT FROM`). Emits `column=isdistinct.<value>`. */
    public fun isDistinct(column: String, value: Number) {
        params += column to "isdistinct.$value"
    }

    /**
     * Null-aware inequality against `null`/`true`/`false` (SQL `IS DISTINCT FROM`);
     * pass `null` to match rows where [column] is non-null. Emits `column=isdistinct.<value>`.
     */
    public fun isDistinct(column: String, value: Boolean?) {
        params += column to "isdistinct.${value ?: "null"}"
    }

    /**
     * Matches rows where [column] equals one of [values] (SQL `IN`). Each member is
     * quoted/escaped if it contains a structural character. Emits `column=in.(a,b,c)`.
     */
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

    /**
     * Adds an equality filter per entry in [values], i.e. `column = value` for
     * each `(column, value)` pair — the AND of several [eq] calls. Note this is the
     * column/value-map overload, distinct from the regex [match] (column, pattern).
     */
    public fun match(values: Map<String, String>) {
        values.forEach { (column, value) -> eq(column, value) }
    }

    // The array/range operators below take a caller-formatted PostgREST literal
    // (e.g. `{a,b}`, `[1,10)`) whose commas/parens are structural to that literal,
    // so the value is passed through verbatim and is NOT quoted. The caller owns
    // formatting (and escaping individual elements) for these operators.

    /**
     * Matches rows where the array/range [column] contains [value] (SQL `@>`).
     * [value] is a verbatim PostgREST literal such as `{a,b}` or `[1,10)` — the
     * caller owns its formatting/escaping. Emits `column=cs.<value>`. Prefer the
     * [List] overload to have the array literal built for you.
     */
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

    /**
     * Matches rows where the array/range [column] is contained by [value]
     * (SQL `<@`). [value] is a verbatim PostgREST literal (e.g. `{a,b}`, `[1,10)`);
     * the caller owns formatting/escaping. Emits `column=cd.<value>`. Prefer the
     * [List] overload to have the array literal built for you.
     */
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

    /**
     * Matches rows where the array/range [column] shares any element with [value]
     * (SQL `&&`). [value] is a verbatim PostgREST literal (e.g. `{a,b}`, `[1,10)`);
     * the caller owns formatting/escaping. Emits `column=ov.<value>`. Prefer the
     * [List] overload to have the array literal built for you.
     */
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

    /**
     * Matches rows where the range [column] is strictly right of the range [value]
     * (operator `sr`, SQL `>>`). [value] is a verbatim range literal such as
     * `[1,10)`. Emits `column=sr.<value>`. Aliased by [strictlyRight].
     */
    public fun rangeGt(column: String, value: String) {
        params += column to "sr.$value"
    }

    /**
     * Matches rows where the range [column] does not extend to the left of [value]
     * (operator `nxl`, SQL `&>`). [value] is a verbatim range literal. Emits
     * `column=nxl.<value>`. Aliased by [notExtendLeft].
     */
    public fun rangeGte(column: String, value: String) {
        params += column to "nxl.$value"
    }

    /**
     * Matches rows where the range [column] is strictly left of the range [value]
     * (operator `sl`, SQL `<<`). [value] is a verbatim range literal such as
     * `[1,10)`. Emits `column=sl.<value>`. Aliased by [strictlyLeft].
     */
    public fun rangeLt(column: String, value: String) {
        params += column to "sl.$value"
    }

    /**
     * Matches rows where the range [column] does not extend to the right of [value]
     * (operator `nxr`, SQL `&<`). [value] is a verbatim range literal. Emits
     * `column=nxr.<value>`. Aliased by [notExtendRight].
     */
    public fun rangeLte(column: String, value: String) {
        params += column to "nxr.$value"
    }

    /**
     * Matches rows where the range [column] is adjacent to the range [value]
     * (operator `adj`, SQL `-|-`). [value] is a verbatim range literal such as
     * `[1,10)`. Emits `column=adj.<value>`.
     */
    public fun rangeAdj(column: String, value: String) {
        params += column to "adj.$value"
    }

    /**
     * Negates every filter declared inside [block] (SQL `NOT`). Each inner pair is
     * re-emitted with a `not.` prefix, e.g. an inner `eq` becomes
     * `column=not.eq.<value>`. A negated nested logical group prefixes the KEY
     * instead, e.g. `not { and { … } }` renders as `not.and=(…)`. Use the
     * [not] (column, operator, value) overload for a single negated operator.
     *
     * @param block a nested DSL block whose filters are individually negated.
     */
    public fun not(block: FilterBuilder.() -> Unit) {
        val inner = FilterBuilder().apply(block).build()
        for ((key, value) in inner) {
            if (isLogicalKey(key)) {
                params += "not.$key" to value
            } else {
                params += key to "not.$value"
            }
        }
    }

    /**
     * Negates a single filter expressed by raw [operator]/[value] (SQL `NOT`).
     * Emits `column=not.<operator>.<value>`, e.g. `not("age", "gt", "18")` →
     * `age=not.gt.18`.
     *
     * @param operator the bare PostgREST operator token (`eq`, `gt`, `like`, …).
     */
    public fun not(column: String, operator: String, value: String) {
        params += column to "not.$operator.${encodeValue(value)}"
    }

    /**
     * Combines the filters declared inside [block] with logical OR (SQL `OR`). The
     * inner filters are flattened into PostgREST's grouped form, e.g.
     * `or { eq("a", 1); eq("b", 2) }` → `or=(a.eq.1,b.eq.2)`. When
     * [referencedTable] is set the key is scoped to that embedded/joined resource,
     * e.g. `or(referencedTable = "authors") { … }` → `authors.or=(…)`.
     *
     * @param block a nested DSL block whose filters are OR-ed together.
     * @param referencedTable scope the OR group to an embedded/joined resource when set.
     */
    public fun or(referencedTable: String? = null, block: FilterBuilder.() -> Unit) {
        val inner = FilterBuilder().apply(block).build()
        val combined = flattenLogical(inner)
        val key = if (referencedTable == null) "or" else "$referencedTable.or"
        params += key to "($combined)"
    }

    /**
     * Combines the filters declared inside [block] with logical AND (SQL `AND`) as
     * an explicit group — useful nested inside [or]. Emits the grouped form, e.g.
     * `and { gte("a", 1); lt("a", 9) }` → `and=(a.gte.1,a.lt.9)`. When
     * [referencedTable] is set the key is scoped to that embedded/joined resource,
     * e.g. `and(referencedTable = "authors") { … }` → `authors.and=(…)`.
     *
     * @param block a nested DSL block whose filters are AND-ed together.
     * @param referencedTable scope the AND group to an embedded/joined resource when set.
     */
    public fun and(referencedTable: String? = null, block: FilterBuilder.() -> Unit) {
        val inner = FilterBuilder().apply(block).build()
        val combined = flattenLogical(inner)
        val key = if (referencedTable == null) "and" else "$referencedTable.and"
        params += key to "($combined)"
    }

    /**
     * Returns `true` when [key] denotes a nested logical group (`and`/`or`),
     * optionally qualified by a referenced table (e.g. `tbl.and`/`tbl.or`).
     * Such entries carry a value already wrapped in `(…)` and must NOT have a
     * dot inserted between key and value when flattened.
     */
    private fun isLogicalKey(key: String): Boolean =
        key == "and" || key == "or" || key.endsWith(".and") || key.endsWith(".or")

    /**
     * Flattens the inner pairs of a logical group into PostgREST's comma-joined
     * form. Normal entries render as `key.value`; nested logical-group entries
     * (whose value already begins with `(`) render as `keyvalue` with no dot.
     */
    private fun flattenLogical(inner: List<Pair<String, String>>): String =
        inner.joinToString(",") { (k, v) ->
            if (isLogicalKey(k)) "$k$v" else "$k.$v"
        }

    /**
     * Full-text search on [column] against [query], using the parse mode [type]
     * (see [TextSearchType]). Emits the FTS operator with an optional text-search
     * config, e.g. `column=plfts.<query>` or `column=plfts(english).<query>`.
     *
     * @param config optional text-search configuration name (e.g. `english`).
     * @param type how [query] is parsed; defaults to [TextSearchType.Plain].
     */
    public fun textSearch(
        column: String,
        query: String,
        config: String? = null,
        type: TextSearchType = TextSearchType.Plain,
    ) {
        val configPart = if (config != null) "($config)" else ""
        params += column to "${type.postgrestName}fts$configPart.${encodeValue(query)}"
    }

    /**
     * Escape hatch for any operator not covered by a dedicated method: emits
     * `column=<operator>.<value>` with [value] quoted/escaped. Use when PostgREST
     * adds an operator the DSL doesn't yet expose.
     *
     * @param operator the bare PostgREST operator token (e.g. `eq`, `fts`).
     */
    public fun filter(column: String, operator: String, value: String) {
        params += column to "$operator.${encodeValue(value)}"
    }

    /**
     * Orders the result by [column] using a boolean direction and optional nulls
     * placement. Emits `order=column.<asc|desc>[.<nullsfirst|nullslast>]` (or
     * `<referencedTable>.order=...`). Prefer the type-safe [OrderDirection]
     * overload for readability.
     *
     * @param ascending `true` for `asc` (default), `false` for `desc`.
     * @param nullsFirst `true` → `nullsfirst`, `false` → `nullslast`, `null` → DB default.
     * @param referencedTable order on an embedded/joined resource when set.
     */
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
        appendOrder(key, "$column.$dir$nulls")
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
        appendOrder(key, "$column.${direction.postgrestName}$nullsPart")
    }

    /**
     * Accumulates an order term into a SINGLE `order` (or `<table>.order`) parameter,
     * comma-joining successive [order] calls. PostgREST expects multi-column ordering
     * as one comma-separated `order` value (`order=a.desc,b.asc`) and honours only one
     * of several repeated `order=` params — so emitting a separate pair per call would
     * silently drop every sort column but one. Terms scoped to different referenced
     * tables keep their own keys.
     */
    private fun appendOrder(key: String, term: String) {
        val index = params.indexOfFirst { it.first == key }
        if (index >= 0) {
            params[index] = key to "${params[index].second},$term"
        } else {
            params += key to term
        }
    }

    /**
     * Caps the result at [count] rows. Emits `limit=<count>` (or
     * `<referencedTable>.limit=<count>` for an embedded resource).
     *
     * @param referencedTable limit an embedded/joined resource when set.
     */
    public fun limit(count: Int, referencedTable: String? = null) {
        val key = if (referencedTable == null) "limit" else "$referencedTable.limit"
        params += key to count.toString()
    }

    /**
     * Skips the first [count] rows. Emits `offset=<count>` (or
     * `<referencedTable>.offset=<count>` for an embedded resource).
     *
     * @param referencedTable offset an embedded/joined resource when set.
     */
    public fun offset(count: Int, referencedTable: String? = null) {
        val key = if (referencedTable == null) "offset" else "$referencedTable.offset"
        params += key to count.toString()
    }

    /**
     * Selects the inclusive, zero-based row window `[from, to]` by emitting both an
     * `offset` and a derived `limit` (`to - from + 1`), e.g. `range(0, 9)` →
     * `offset=0` + `limit=10`. Prefixed with [referencedTable] for embedded
     * resources.
     *
     * @param from first row index, inclusive (zero-based).
     * @param to last row index, inclusive.
     * @param referencedTable page an embedded/joined resource when set.
     */
    public fun range(from: Int, to: Int, referencedTable: String? = null) {
        require(to >= from) { "range 'to' ($to) must be >= 'from' ($from)" }
        val offsetKey = if (referencedTable == null) "offset" else "$referencedTable.offset"
        val limitKey = if (referencedTable == null) "limit" else "$referencedTable.limit"
        params += offsetKey to from.toString()
        params += limitKey to (to - from + 1).toString()
    }

    /** Range column [column] is strictly left of [value] (SQL `<<`). Alias for [rangeLt]. */
    public fun strictlyLeft(column: String, value: String) {
        rangeLt(column, value)
    }

    /** Range column [column] is strictly right of [value] (SQL `>>`). Alias for [rangeGt]. */
    public fun strictlyRight(column: String, value: String) {
        rangeGt(column, value)
    }

    /** Range column [column] does not extend right of [value] (SQL `&<`). Alias for [rangeLte]. */
    public fun notExtendRight(column: String, value: String) {
        rangeLte(column, value)
    }

    /** Range column [column] does not extend left of [value] (SQL `&>`). Alias for [rangeGte]. */
    public fun notExtendLeft(column: String, value: String) {
        rangeGte(column, value)
    }

    /** Returns a snapshot of the accumulated `(name, value)` query parameters in declaration order. */
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

/**
 * Builds a list of PostgREST query parameters from a [FilterBuilder] [block] — the
 * idiomatic entry point to the filter DSL.
 *
 * Shorthand for `FilterBuilder().apply(block).build()`; the returned
 * `(name, value)` pairs are appended to a request's query string. See
 * [FilterBuilder] for the available operators.
 */
public inline fun filters(block: FilterBuilder.() -> Unit): List<Pair<String, String>> =
    FilterBuilder().apply(block).build()
