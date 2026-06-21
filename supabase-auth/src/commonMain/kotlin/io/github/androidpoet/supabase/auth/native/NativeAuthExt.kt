package io.github.androidpoet.supabase.auth.native

import io.github.androidpoet.supabase.auth.AuthClient
import io.github.androidpoet.supabase.auth.models.Session
import io.github.androidpoet.supabase.auth.session.SessionManager
import io.github.androidpoet.supabase.core.result.SupabaseResult

/**
 * Signs in with a platform-native [provider] (Google, Apple, …) and exchanges the resulting token
 * for a Supabase [Session].
 *
 * This is sugar over [AuthClient.signInWithIdToken]: it runs the native flow via
 * [NativeAuthProvider.signIn] and, on success, redeems the credential. A failure from the native flow
 * (cancellation, error, unsupported platform) is propagated unchanged.
 *
 * **First-sign-in name/email.** The token exchange only carries the ID token, so the
 * [NativeAuthCredential.fullName]/[NativeAuthCredential.email] that Sign in with Apple returns *once*
 * (on the first authorization, never in the ID token) would otherwise be lost here. Pass
 * [onCredential] to capture the credential the moment the native flow succeeds — typically you stash
 * the name/email and, after this returns a session, write it to the user's metadata or a profile row.
 * It runs only on a successful native flow, before the token exchange.
 *
 * @param provider the native sign-in implementation to run.
 * @param captchaToken optional captcha token to forward to the token exchange.
 * @param onCredential optional hook invoked with the raw [NativeAuthCredential] on a successful native
 *   flow, before it is redeemed — the only way to reach Apple's first-sign-in name/email.
 */
public suspend fun AuthClient.signInWith(
    provider: NativeAuthProvider,
    captchaToken: String? = null,
    onCredential: ((NativeAuthCredential) -> Unit)? = null,
): SupabaseResult<Session> =
    when (val credential = provider.signIn()) {
        is SupabaseResult.Failure -> credential
        is SupabaseResult.Success -> {
            onCredential?.invoke(credential.value)
            signInWithIdToken(
                provider = credential.value.provider,
                idToken = credential.value.idToken,
                accessToken = credential.value.accessToken,
                nonce = credential.value.nonce,
                captchaToken = captchaToken,
            )
        }
    }

/**
 * Signs in with a native [provider] and, on success, persists the session to [sessionManager]. See
 * [signInWith] — including [onCredential] for capturing Apple's first-sign-in name/email.
 */
public suspend fun AuthClient.signInWithAndSaveSession(
    sessionManager: SessionManager,
    provider: NativeAuthProvider,
    captchaToken: String? = null,
    onCredential: ((NativeAuthCredential) -> Unit)? = null,
): SupabaseResult<Session> =
    signInWith(provider = provider, captchaToken = captchaToken, onCredential = onCredential).also { result ->
        if (result is SupabaseResult.Success) sessionManager.saveSession(result.value)
    }
