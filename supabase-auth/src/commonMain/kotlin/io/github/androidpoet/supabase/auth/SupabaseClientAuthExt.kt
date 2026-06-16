package io.github.androidpoet.supabase.auth

import io.github.androidpoet.supabase.auth.models.OAuthProvider
import io.github.androidpoet.supabase.auth.models.Session
import io.github.androidpoet.supabase.auth.models.SignOutScope
import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.serialization.json.JsonObject

/*
 * Client-level sign-in helpers.
 *
 * The low-level AuthClient calls (e.g. AuthClient.signInWithEmail) return a
 * Session but do not touch the client — so a bare sign-in leaves the client
 * still sending only the anon key, and a subsequent RLS-protected query silently
 * returns anonymous results instead of failing. These helpers run the same call
 * and, on success, apply the returned access token to the client so every
 * following request is authenticated as the signed-in user.
 *
 * For multi-session / persisted-storage apps, prefer a SessionManager (its
 * saveSession applies the token and handles refresh); these helpers are the
 * lightweight path for apps that just need "sign in and stay signed in" for the
 * process lifetime.
 */

/**
 * Stores [session]'s access token on this client so subsequent requests are
 * authenticated. A no-op token is ignored (e.g. a sign-up awaiting email
 * confirmation returns a session with no access token yet). Returns [session]
 * for chaining.
 */
public fun SupabaseClient.applySession(session: Session): Session {
    if (session.accessToken.isNotBlank()) setAccessToken(session.accessToken)
    return session
}

private fun SupabaseClient.applyOnSuccess(result: SupabaseResult<Session>): SupabaseResult<Session> {
    if (result is SupabaseResult.Success) applySession(result.value)
    return result
}

/** Signs in with email + password and, on success, authenticates this client. */
public suspend fun SupabaseClient.signInWithEmail(
    email: String,
    password: String,
): SupabaseResult<Session> = applyOnSuccess(auth.signInWithEmail(email = email, password = password))

/** Signs in with phone + password and, on success, authenticates this client. */
public suspend fun SupabaseClient.signInWithPhone(
    phone: String,
    password: String,
): SupabaseResult<Session> = applyOnSuccess(auth.signInWithPhone(phone = phone, password = password))

/** Signs in anonymously and, on success, authenticates this client. */
public suspend fun SupabaseClient.signInAnonymously(
    data: JsonObject? = null,
    captchaToken: String? = null,
): SupabaseResult<Session> = applyOnSuccess(auth.signInAnonymously(data = data, captchaToken = captchaToken))

/** Signs in with an OIDC id token (e.g. native Google/Apple) and, on success, authenticates this client. */
public suspend fun SupabaseClient.signInWithIdToken(
    provider: OAuthProvider,
    idToken: String,
    accessToken: String? = null,
    nonce: String? = null,
    captchaToken: String? = null,
): SupabaseResult<Session> =
    applyOnSuccess(
        auth.signInWithIdToken(
            provider = provider,
            idToken = idToken,
            accessToken = accessToken,
            nonce = nonce,
            captchaToken = captchaToken,
        ),
    )

/**
 * Signs up with email + password and, on success and when a session is returned
 * (i.e. email confirmation is not required), authenticates this client.
 */
public suspend fun SupabaseClient.signUpWithEmail(
    email: String,
    password: String,
    data: JsonObject? = null,
): SupabaseResult<Session> = applyOnSuccess(auth.signUpWithEmail(email = email, password = password, data = data))

/**
 * Signs out the current session and clears the access token from this client.
 * If the client is not authenticated the token is cleared and success is
 * returned without a network call. The token is left in place if the server
 * call fails, so a failed sign-out can be retried.
 */
public suspend fun SupabaseClient.signOut(
    scope: SignOutScope = SignOutScope.GLOBAL,
): SupabaseResult<Unit> {
    val token =
        accessTokenOrNull ?: run {
            clearAccessToken()
            return SupabaseResult.Success(Unit)
        }
    return when (val result = auth.signOut(accessToken = token, scope = scope)) {
        is SupabaseResult.Success -> {
            clearAccessToken()
            result
        }
        is SupabaseResult.Failure -> result
    }
}
