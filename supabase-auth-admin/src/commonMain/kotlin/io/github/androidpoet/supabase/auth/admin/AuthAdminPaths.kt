package io.github.androidpoet.supabase.auth.admin

/**
 * Centralized Supabase Auth admin endpoint paths.
 *
 * The API version lives in exactly one place and the route prefixes are
 * composed from it, rather than repeating `/auth/v1/admin/...` literals
 * throughout [AuthAdminClientImpl]. Dynamic segments (user id, client id, …) are
 * appended at the call site, e.g. `"${AuthAdminPaths.ADMIN_USERS}/$userId"`.
 *
 * Mirrors `supabase-auth`'s internal `AuthPaths`; the two can't share a constant
 * because module-internal symbols are not visible across module boundaries.
 */
internal object AuthAdminPaths {
    const val API_VERSION: String = "v1"
    const val BASE: String = "/auth/" + API_VERSION

    const val INVITE: String = BASE + "/invite"
    const val LOGOUT: String = BASE + "/logout"

    const val ADMIN: String = BASE + "/admin"
    const val ADMIN_AUDIT: String = ADMIN + "/audit"
    const val ADMIN_USERS: String = ADMIN + "/users"
    const val ADMIN_CUSTOM_PROVIDERS: String = ADMIN + "/custom-providers"
    const val ADMIN_GENERATE_LINK: String = ADMIN + "/generate_link"
    const val ADMIN_OAUTH_CLIENTS: String = ADMIN + "/oauth/clients"
    const val ADMIN_SSO_PROVIDERS: String = ADMIN + "/sso/providers"

    fun adminSsoProvider(id: String): String = "$ADMIN_SSO_PROVIDERS/$id"
}
