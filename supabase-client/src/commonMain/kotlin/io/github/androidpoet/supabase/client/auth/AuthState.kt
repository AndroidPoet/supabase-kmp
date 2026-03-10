package io.github.androidpoet.supabase.client.auth

/**
 * Represents the authentication state for Supabase API requests.
 *
 * Supabase uses two headers for auth:
 * - `apikey` — always present, carries the project's anon or service-role key.
 * - `Authorization` — carries `Bearer <jwt>` for authenticated user requests,
 *   or falls back to `Bearer <apikey>` when no user session exists.
 */
public sealed interface AuthState {

    /** No authentication configured yet. */
    public data object None : AuthState

    /**
     * Authenticated via project API key (anon or service_role).
     * Used as the baseline auth before a user signs in.
     */
    public data class ApiKey(val key: String) : AuthState

    /**
     * Authenticated via a user JWT obtained from GoTrue.
     * Takes precedence over [ApiKey] for the Authorization header.
     */
    public data class Bearer(val token: String) : AuthState
}

/** Returns the value for the `apikey` header, or `null` if not applicable. */
public fun AuthState.apiKeyHeader(): String? = when (this) {
    is AuthState.None -> null
    is AuthState.ApiKey -> key
    is AuthState.Bearer -> null
}

/** Returns the value for the `Authorization` header, or `null` if not applicable. */
public fun AuthState.authorizationHeader(): String? = when (this) {
    is AuthState.None -> null
    is AuthState.ApiKey -> "Bearer $key"
    is AuthState.Bearer -> "Bearer $token"
}
