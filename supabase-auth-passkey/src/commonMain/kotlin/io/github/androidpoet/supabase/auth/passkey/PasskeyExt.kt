package io.github.androidpoet.supabase.auth.passkey

import io.github.androidpoet.supabase.auth.AuthClient
import io.github.androidpoet.supabase.auth.models.PasskeyMetadata
import io.github.androidpoet.supabase.auth.models.Session
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
        when (val produced = authenticator.createCredential(options.options)) {
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
        when (val produced = authenticator.getCredential(options.options)) {
            is SupabaseResult.Success -> produced.value
            is SupabaseResult.Failure -> return produced
        }
    return passkeyVerifyAuthentication(
        challengeId = options.challengeId,
        credential = credential,
    )
}
