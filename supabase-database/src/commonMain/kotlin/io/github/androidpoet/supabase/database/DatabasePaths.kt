package io.github.androidpoet.supabase.database

/**
 * Centralized Supabase PostgREST (database) endpoint paths.
 *
 * The API version lives in exactly one place and the route prefixes are
 * composed from it, rather than repeating `/rest/v1/...` literals throughout
 * [DatabaseClientImpl]. The table name or RPC function is appended at the call
 * site, e.g. `"${DatabasePaths.BASE}/$table"` or `"${DatabasePaths.RPC}/$fn"`.
 */
internal object DatabasePaths {
    const val API_VERSION: String = "v1"
    const val BASE: String = "/rest/" + API_VERSION
    const val RPC: String = BASE + "/rpc"
}
