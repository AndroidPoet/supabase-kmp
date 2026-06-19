package io.github.androidpoet.supabase.auth.admin

import io.github.androidpoet.supabase.auth.models.User
import io.github.androidpoet.supabase.core.paging.Paginator

/**
 * The plain (no-[io.github.androidpoet.supabase.core.result.SupabaseResult]) form
 * of [AuthAdminClient.listUsers]: returns the `List<User>` directly and **throws**
 * on failure. Service-role only.
 */
public suspend fun AuthAdminClient.listUsersOrThrow(
    page: Int? = null,
    perPage: Int? = null,
): List<User> = listUsers(page = page, perPage = perPage).getOrThrow().users

/**
 * Builds a demand-driven [Paginator] over all users, fetching one page per
 * [Paginator.loadNext] via [AuthAdminClient.listUsers].
 *
 * GoTrue's admin list is page-based and 1-indexed; this adapter converts the
 * offset the [Paginator] tracks into the right `page` number, so callers just
 * observe [Paginator.items] and call [Paginator.loadNext] near the list end. A
 * failure surfaces via [Paginator.error]. Service-role only — same as the rest of
 * this module.
 *
 * @param perPage users per page; must be greater than 0.
 */
public fun AuthAdminClient.usersPaginator(perPage: Int = 50): Paginator<User> =
    Paginator(perPage) { offset, limit ->
        listUsersOrThrow(page = offset / limit + 1, perPage = limit)
    }
