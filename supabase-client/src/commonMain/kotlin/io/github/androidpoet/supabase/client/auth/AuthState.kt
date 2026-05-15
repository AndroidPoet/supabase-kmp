package io.github.androidpoet.supabase.client.auth
public sealed interface AuthState {
    public data object None : AuthState
    public data class ApiKey(val key: String) : AuthState
    public data class Bearer(val token: String) : AuthState
}
public fun AuthState.apiKeyHeader(): String? = when (this) {
    is AuthState.None -> null
    is AuthState.ApiKey -> key
    is AuthState.Bearer -> null
}
public fun AuthState.authorizationHeader(): String? = when (this) {
    is AuthState.None -> null
    is AuthState.ApiKey -> "Bearer $key"
    is AuthState.Bearer -> "Bearer $token"
}
