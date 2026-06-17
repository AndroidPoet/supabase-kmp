package io.github.androidpoet.supabase.auth.passkey

import io.github.androidpoet.supabase.auth.AuthClient
import io.github.androidpoet.supabase.auth.applySession
import io.github.androidpoet.supabase.auth.auth
import io.github.androidpoet.supabase.auth.models.PasskeyMetadata
import io.github.androidpoet.supabase.auth.models.Session
import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult

/**
 * Registers a new passkey for the signed-in user, driving the whole ceremony:
 * fetch registration options from Supabase, run the device authenticator via
 * [authenticator], then verify the resulting credential with Supabase.
 *
 * Combines [AuthClient.passkeyStartRegistration],
 * [PasskeyAuthenticator.createCredential] and
 * [AuthClient.passkeyVerifyRegistration] so callers don't have to thread the
 * challenge id and credential JSON between them by hand. Short-circuits to
 * [SupabaseResult.Failure] at the first failing step.
 *
 * @param accessToken the current session's access token.
 * @param authenticator the platform ceremony driver (e.g.
 *   `CredentialManagerPasskeyAuthenticator` on Android).
 */
public suspend fun AuthClient.registerPasskey(
    accessToken: String,
    authenticator: PasskeyAuthenticator,
): SupabaseResult<PasskeyMetadata> {
    val options =
        when (val started = passkeyStartRegistration(accessToken)) {
            is SupabaseResult.Success -> started.value
            is SupabaseResult.Failure -> return started
        }
    val credential =
        when (val produced = authenticator.createCredential(normalizePasskeyCeremonyOptions(options.options))) {
            is SupabaseResult.Success -> produced.value
            is SupabaseResult.Failure -> return produced
        }
    return passkeyVerifyRegistration(
        accessToken = accessToken,
        challengeId = options.challengeId,
        credential = credential,
    )
}

/**
 * Signs in passwordlessly with a passkey, driving the whole ceremony: fetch
 * authentication options from Supabase, run the device authenticator via
 * [authenticator], then verify the assertion with Supabase to obtain a
 * [Session].
 *
 * Combines [AuthClient.passkeyStartAuthentication],
 * [PasskeyAuthenticator.getCredential] and
 * [AuthClient.passkeyVerifyAuthentication]. Short-circuits to
 * [SupabaseResult.Failure] at the first failing step.
 *
 * @param authenticator the platform ceremony driver.
 * @param captchaToken optional CAPTCHA token when bot protection is enabled.
 */
public suspend fun AuthClient.signInWithPasskey(
    authenticator: PasskeyAuthenticator,
    captchaToken: String? = null,
): SupabaseResult<Session> {
    val options =
        when (val started = passkeyStartAuthentication(captchaToken)) {
            is SupabaseResult.Success -> started.value
            is SupabaseResult.Failure -> return started
        }
    val credential =
        when (val produced = authenticator.getCredential(normalizePasskeyCeremonyOptions(options.options))) {
            is SupabaseResult.Success -> produced.value
            is SupabaseResult.Failure -> return produced
        }
    return passkeyVerifyAuthentication(
        challengeId = options.challengeId,
        credential = credential,
    )
}

/**
 * Registers a passkey for the currently signed-in user using this client's
 * access token, then runs the ceremony — the client-level convenience over
 * [AuthClient.registerPasskey]. Fails if no session is active.
 */
public suspend fun SupabaseClient.registerPasskey(
    authenticator: PasskeyAuthenticator,
): SupabaseResult<PasskeyMetadata> {
    val accessToken =
        accessTokenOrNull
            ?: return SupabaseResult.Failure(
                SupabaseError(message = "No active session; sign in before registering a passkey"),
            )
    return auth.registerPasskey(accessToken, authenticator)
}

/**
 * Signs in with a passkey and, on success, applies the returned access token to
 * this client so subsequent requests are authenticated — the client-level
 * counterpart to [AuthClient.signInWithPasskey], mirroring
 * `SupabaseClient.signInWithEmail`. For persisted/multi-session apps, prefer a
 * `SessionManager` (its `saveSession` also schedules refresh).
 */
public suspend fun SupabaseClient.signInWithPasskey(
    authenticator: PasskeyAuthenticator,
    captchaToken: String? = null,
): SupabaseResult<Session> {
    val result = auth.signInWithPasskey(authenticator, captchaToken)
    if (result is SupabaseResult.Success) applySession(result.value)
    return result
}
