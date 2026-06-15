package io.github.androidpoet.supabase.auth

/**
 * Centralized Supabase Auth (GoTrue) endpoint paths.
 *
 * The API version lives in exactly one place and the route prefixes are
 * composed from it, rather than repeating `/auth/v1/...` literals throughout
 * [AuthClientImpl]. Constants are static path prefixes; dynamic segments (factor
 * id, identity id, …) and query strings (`?grant_type=…`, `?provider=…`) are
 * appended at the call site, e.g. `"${AuthPaths.FACTORS}/$factorId/verify"`.
 */
internal object AuthPaths {
    const val API_VERSION: String = "v1"
    const val BASE: String = "/auth/" + API_VERSION

    const val SIGNUP: String = BASE + "/signup"
    const val TOKEN: String = BASE + "/token"
    const val OTP: String = BASE + "/otp"
    const val VERIFY: String = BASE + "/verify"
    const val RESEND: String = BASE + "/resend"
    const val RECOVER: String = BASE + "/recover"
    const val REAUTHENTICATE: String = BASE + "/reauthenticate"
    const val LOGOUT: String = BASE + "/logout"
    const val SSO: String = BASE + "/sso"
    const val AUTHORIZE: String = BASE + "/authorize"
    const val JWKS: String = BASE + "/.well-known/jwks.json"

    const val USER: String = BASE + "/user"
    const val USER_IDENTITIES: String = USER + "/identities"
    const val USER_IDENTITIES_AUTHORIZE: String = USER_IDENTITIES + "/authorize"
    const val USER_OAUTH_GRANTS: String = USER + "/oauth/grants"

    const val OAUTH_AUTHORIZATIONS: String = BASE + "/oauth/authorizations"

    const val FACTORS: String = BASE + "/factors"

    const val PASSKEYS: String = BASE + "/passkeys"
    const val PASSKEYS_AUTH_OPTIONS: String = PASSKEYS + "/authentication/options"
    const val PASSKEYS_AUTH_VERIFY: String = PASSKEYS + "/authentication/verify"
    const val PASSKEYS_REG_OPTIONS: String = PASSKEYS + "/registration/options"
    const val PASSKEYS_REG_VERIFY: String = PASSKEYS + "/registration/verify"
}
