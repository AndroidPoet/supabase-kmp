package io.github.androidpoet.supabase.functions

/**
 * Centralized Supabase Edge Functions endpoint paths.
 *
 * The API version lives in exactly one place and the route prefix is composed
 * from it, rather than repeating `/functions/v1/...` literals throughout
 * [FunctionsClientImpl]. The function name is appended at the call site, e.g.
 * `"${FunctionsPaths.BASE}/$functionName"`.
 */
internal object FunctionsPaths {
    const val API_VERSION: String = "v1"
    const val BASE: String = "/functions/" + API_VERSION
}
