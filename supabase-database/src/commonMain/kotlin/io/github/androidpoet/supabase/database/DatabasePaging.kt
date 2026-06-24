package io.github.androidpoet.supabase.database

import io.github.androidpoet.supabase.core.models.QueryBuilder
import io.github.androidpoet.supabase.core.paging.Paginator

/**
 * Builds a demand-driven [Paginator] over [table], fetching one offset-based page
 * per [Paginator.loadNext] via [selectTyped] and a `range` window.
 *
 * The returned paginator exposes the accumulated rows, loading/end/error state as
 * `StateFlow`s for a scroll UI to observe — nothing is fetched until you call
 * [Paginator.loadNext]. Express ordering and predicates in [block] (always
 * [QueryBuilder.orderBy] by a stable column); do **not** add your own
 * `range`/`limit`/`offset`, the paginator owns the window. A page failure surfaces
 * via [Paginator.error] (the call throws internally and the paginator captures it).
 *
 * ```
 * val pager = database.paginator<Todo>("todos", pageSize = 20) {
 *     orderBy(Todo.createdAt, order = Order.DESC)
 * }
 * // observe pager.items in the UI; near the bottom:
 * LaunchedEffect(reachedBottom) { if (reachedBottom) pager.loadNext() }
 * ```
 *
 * For large or live tables, prefer keyset/seek paging — build a [Paginator]
 * directly and carry the last row's sort key as a `gt`/`lt` predicate.
 *
 * @param pageSize rows per page; must be greater than 0.
 */
public inline fun <reified T> DatabaseClient.paginator(
    table: String,
    pageSize: Int = 20,
    schema: String? = null,
    columns: String = "*",
    crossinline block: QueryBuilder.() -> Unit = {},
): Paginator<T> =
    Paginator(pageSize) { offset, limit ->
        selectTypedOrThrow<T>(table = table, schema = schema, columns = columns) {
            block()
            limit(limit)
            offset(offset)
        }
    }
