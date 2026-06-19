package io.github.androidpoet.supabase.storage

import io.github.androidpoet.supabase.core.paging.Paginator
import io.github.androidpoet.supabase.storage.models.FileObject

/**
 * Builds a demand-driven [Paginator] over the objects under [prefix] in [bucket],
 * fetching one offset-based page per [Paginator.loadNext] via [StorageClient.list].
 *
 * Nothing is fetched until [Paginator.loadNext] is called; the accumulated
 * [FileObject]s and the loading/end/error state are exposed as `StateFlow`s for a
 * scroll UI. A failure surfaces via [Paginator.error]. For very large buckets or
 * folder navigation, prefer cursor paging with [StorageClient.listV2] and build a
 * [Paginator] around its `nextCursor`.
 *
 * @param pageSize entries per page; must be greater than 0.
 */
public fun StorageClient.listPaginator(
    bucket: String,
    prefix: String = "",
    pageSize: Int = 100,
    sortBy: String? = null,
    sortOrder: SortOrder = SortOrder.ASC,
    search: String? = null,
): Paginator<FileObject> =
    Paginator(pageSize) { offset, limit ->
        list(
            bucket = bucket,
            prefix = prefix,
            limit = limit,
            offset = offset,
            sortBy = sortBy,
            sortOrder = sortOrder,
            search = search,
        ).getOrThrow()
    }
