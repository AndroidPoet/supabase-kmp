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
 * @param provider the native sign-in implementation to run.
 * @param captchaToken optional captcha token to forward to the token exchange.
 */
public suspend fun AuthClient.signInWith(
    provider: NativeAuthProvider,
    captchaToken: String? = null,
): SupabaseResult<Session> =
    when (val credential = provider.signIn()) {
        is SupabaseResult.Failure -> credential
        is SupabaseResult.Success ->
            signInWithIdToken(
                provider = credential.value.provider,
                idToken = credential.value.idToken,
                accessToken = credential.value.accessToken,
                nonce = credential.value.nonce,
                captchaToken = captchaToken,
            )
    }

/**
 * Signs in with a native [provider] and, on success, persists the session to [sessionManager]. See
 * [signInWith].
 */
public suspend fun AuthClient.signInWithAndSaveSession(
    sessionManager: SessionManager,
    provider: NativeAuthProvider,
    captchaToken: String? = null,
): SupabaseResult<Session> =
    signInWith(provider = provider, captchaToken = captchaToken).also { result ->
        if (result is SupabaseResult.Success) sessionManager.saveSession(result.value)
    }
