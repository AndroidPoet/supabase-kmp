package io.github.androidpoet.supabase.sync

/**
 * One page of rows from a [LocalStore] list query. [total] is the full row count for the table
 * (so a UI can render "page x of y" or an infinite list), [offset]/[limit] echo the request, and
 * [hasMore] is `true` when more rows follow this page.
 */
public data class Page<T>(
    public val items: List<T>,
    public val offset: Long,
    public val limit: Long,
    public val total: Long,
) {
    public val hasMore: Boolean get() = offset + items.size < total
}
