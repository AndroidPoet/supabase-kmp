package io.github.androidpoet.supabase.auth

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import io.github.androidpoet.supabase.auth.models.Jwk
import io.github.androidpoet.supabase.auth.models.JwtClaims
import io.github.androidpoet.supabase.auth.models.JwtClaimsResult
import io.github.androidpoet.supabase.auth.models.JwtHeader
import io.github.androidpoet.supabase.auth.models.LinkIdentityResponse
import io.github.androidpoet.supabase.auth.models.MessagingChannel
import io.github.androidpoet.supabase.auth.models.MfaVerifyResponse
import io.github.androidpoet.supabase.auth.models.OAuthProvider
import io.github.androidpoet.supabase.auth.models.OtpType
import io.github.androidpoet.supabase.auth.models.OtpVerifyResult
import io.github.androidpoet.supabase.auth.models.Session
import io.github.androidpoet.supabase.auth.models.SignOutScope
import io.github.androidpoet.supabase.auth.models.SsoResponse
import io.github.androidpoet.supabase.auth.models.User
import io.github.androidpoet.supabase.auth.models.UserIdentity
import io.github.androidpoet.supabase.auth.models.UserUpdateRequest
import io.github.androidpoet.supabase.auth.session.SessionManager
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.core.result.map
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

/** Email sign-up shorthand for [AuthClient.signUpWithEmail]. */
public suspend fun AuthClient.signUp(
    email: String,
    password: String,
    data: JsonObject? = null,
): SupabaseResult<Session> =
    signUpWithEmail(email = email, password = password, data = data)

/** Email sign-in shorthand for [AuthClient.signInWithEmail]. */
public suspend fun AuthClient.signIn(
    email: String,
    password: String,
): SupabaseResult<Session> =
    signInWithEmail(email = email, password = password)

/** Phone sign-up shorthand for [AuthClient.signUpWithPhone]. */
public suspend fun AuthClient.signUpPhone(
    phone: String,
    password: String,
    data: JsonObject? = null,
): SupabaseResult<Session> =
    signUpWithPhone(phone = phone, password = password, data = data)

/** Phone sign-in shorthand for [AuthClient.signInWithPhone]. */
public suspend fun AuthClient.signInPhone(
    phone: String,
    password: String,
): SupabaseResult<Session> =
    signInWithPhone(phone = phone, password = password)

/** Sends an email magic-link / OTP. Shorthand for [AuthClient.signInWithOtp] with just an email. */
public suspend fun AuthClient.sendOtp(email: String): SupabaseResult<Unit> =
    signInWithOtp(email = email)

/**
 * Sends a phone OTP. Shorthand for [AuthClient.signInWithOtp] with just a phone.
 *
 * @param channel delivery channel ([MessagingChannel.SMS] server default, or `WHATSAPP`).
 */
public suspend fun AuthClient.sendPhoneOtp(
    phone: String,
    channel: MessagingChannel? = null,
): SupabaseResult<Unit> =
    signInWithOtp(phone = phone, channel = channel)

/** Resends the sign-up confirmation email. Shorthand for [AuthClient.resendEmailOtp] with [OtpType.EMAIL]. */
public suspend fun AuthClient.resendSignUpEmailOtp(
    email: String,
    captchaToken: String? = null,
    redirectTo: String? = null,
): SupabaseResult<Unit> =
    resendEmailOtp(
        type = OtpType.EMAIL,
        email = email,
        captchaToken = captchaToken,
        redirectTo = redirectTo,
    )

/** Resends a phone sign-in OTP. Shorthand for [AuthClient.resendPhoneOtp] with [OtpType.SMS]. */
public suspend fun AuthClient.resendPhoneSignInOtp(
    phone: String,
    captchaToken: String? = null,
): SupabaseResult<Unit> =
    resendPhoneOtp(
        type = OtpType.SMS,
        phone = phone,
        captchaToken = captchaToken,
    )

/** Verifies an email OTP. Shorthand for [AuthClient.verifyOtp] keyed by email. */
public suspend fun AuthClient.verifyEmailOtp(
    email: String,
    token: String,
    type: OtpType,
    captchaToken: String? = null,
): SupabaseResult<OtpVerifyResult> =
    verifyOtp(email = email, token = token, type = type, captchaToken = captchaToken)

/** Verifies a phone OTP. Shorthand for [AuthClient.verifyOtp] keyed by phone. */
public suspend fun AuthClient.verifyPhoneOtp(
    phone: String,
    token: String,
    type: OtpType,
    captchaToken: String? = null,
): SupabaseResult<OtpVerifyResult> =
    verifyOtp(phone = phone, token = token, type = type, captchaToken = captchaToken)

/** Verifies a sign-up email OTP with [OtpType.EMAIL] fixed. See [verifyEmailOtp]. */
public suspend fun AuthClient.verifyEmailSignUpOtp(
    email: String,
    token: String,
    captchaToken: String? = null,
): SupabaseResult<OtpVerifyResult> =
    verifyEmailOtp(email = email, token = token, type = OtpType.EMAIL, captchaToken = captchaToken)

/** Verifies a phone sign-in OTP with [OtpType.SMS] fixed. See [verifyPhoneOtp]. */
public suspend fun AuthClient.verifyPhoneSignInOtp(
    phone: String,
    token: String,
    captchaToken: String? = null,
): SupabaseResult<OtpVerifyResult> =
    verifyPhoneOtp(phone = phone, token = token, type = OtpType.SMS, captchaToken = captchaToken)

/** Verifies an email-link confirmation by token hash. Shorthand for [AuthClient.verifyOtpWithTokenHash]. */
public suspend fun AuthClient.verifyEmailOtpWithTokenHash(
    tokenHash: String,
    type: OtpType,
    captchaToken: String? = null,
): SupabaseResult<OtpVerifyResult> =
    verifyOtpWithTokenHash(
        tokenHash = tokenHash,
        type = type,
        captchaToken = captchaToken,
    )

/** Sends a password-reset email. Reads-better alias for [AuthClient.resetPasswordForEmail]. */
public suspend fun AuthClient.forgotPassword(
    email: String,
    redirectTo: String? = null,
    captchaToken: String? = null,
): SupabaseResult<Unit> =
    resetPasswordForEmail(
        email = email,
        redirectTo = redirectTo,
        captchaToken = captchaToken,
    )

/** Signs out every session of the user. Shorthand for [AuthClient.signOut] with [SignOutScope.GLOBAL]. */
public suspend fun AuthClient.signOutGlobal(accessToken: String): SupabaseResult<Unit> =
    signOut(accessToken = accessToken, scope = SignOutScope.GLOBAL)

/** Signs out only this session. Shorthand for [AuthClient.signOut] with [SignOutScope.LOCAL]. */
public suspend fun AuthClient.signOutLocal(accessToken: String): SupabaseResult<Unit> =
    signOut(accessToken = accessToken, scope = SignOutScope.LOCAL)

/** Signs out all *other* sessions, keeping this one. Shorthand for [AuthClient.signOut] with [SignOutScope.OTHERS]. */
public suspend fun AuthClient.signOutOthers(accessToken: String): SupabaseResult<Unit> =
    signOut(accessToken = accessToken, scope = SignOutScope.OTHERS)

/**
 * Fetches the current session's user via [SessionManager.accessToken], failing if
 * there is no active session.
 *
 * @param updateStoredSession when true, writes the refreshed [User] back into the
 *   stored session so cached metadata stays current.
 */
public suspend fun AuthClient.getUserForCurrentSession(
    sessionManager: SessionManager,
    updateStoredSession: Boolean = false,
): SupabaseResult<User> {
    val token =
        sessionManager.accessToken
            ?: return SupabaseResult.Failure(SupabaseError(message = "No active session"))
    return when (val userResult = getUser(token)) {
        is SupabaseResult.Failure -> userResult
        is SupabaseResult.Success -> {
            if (updateStoredSession) {
                val current = sessionManager.currentSession
                if (current != null) {
                    sessionManager.saveSession(current.copy(user = userResult.value))
                }
            }
            userResult
        }
    }
}

/** Requests a reauthentication nonce for the current session. See [AuthClient.reauthenticate]. Fails with no active session. */
public suspend fun AuthClient.reauthenticateCurrentSession(
    sessionManager: SessionManager,
): SupabaseResult<Unit> {
    val token =
        sessionManager.accessToken
            ?: return SupabaseResult.Failure(SupabaseError(message = "No active session"))
    return reauthenticate(token)
}

/**
 * Runs an MFA challenge-then-verify in one call: [AuthClient.mfaChallenge] to
 * obtain a challenge id, then [AuthClient.mfaVerify] with the user's [code].
 * Short-circuits to [SupabaseResult.Failure] if the challenge step fails.
 */
public suspend fun AuthClient.mfaChallengeAndVerify(
    factorId: String,
    code: String,
    accessToken: String,
): SupabaseResult<MfaVerifyResponse> =
    when (val challenge = mfaChallenge(factorId = factorId, accessToken = accessToken)) {
        is SupabaseResult.Failure -> SupabaseResult.Failure(challenge.error)
        is SupabaseResult.Success ->
            mfaVerify(
                factorId = factorId,
                challengeId = challenge.value.id,
                code = code,
                accessToken = accessToken,
            )
    }

/**
 * Signs out the current session and clears it from storage. On success (or when
 * there is no session and [succeedIfNoSession] is true) the stored session is
 * cleared if [clearStoredSessionOnSuccess].
 *
 * @param scope which sessions to revoke server-side. See [AuthClient.signOut].
 * @param succeedIfNoSession when true, an absent session is treated as a
 *   successful (idempotent) sign-out rather than a failure.
 */
public suspend fun AuthClient.signOutCurrentSession(
    sessionManager: SessionManager,
    scope: SignOutScope = SignOutScope.LOCAL,
    clearStoredSessionOnSuccess: Boolean = true,
    succeedIfNoSession: Boolean = true,
): SupabaseResult<Unit> {
    val token = sessionManager.accessToken
    if (token == null) {
        if (clearStoredSessionOnSuccess) {
            sessionManager.clearSession()
        }
        return if (succeedIfNoSession) {
            SupabaseResult.Success(Unit)
        } else {
            SupabaseResult.Failure(SupabaseError(message = "No active session"))
        }
    }
    return when (val result = signOut(token, scope)) {
        is SupabaseResult.Failure -> result
        is SupabaseResult.Success -> {
            if (clearStoredSessionOnSuccess) {
                sessionManager.clearSession()
            }
            result
        }
    }
}

/**
 * Updates the current session's user (see [AuthClient.updateUser]) and, when
 * [updateStoredSession] is true, writes the returned [User] back into the stored
 * session. Fails with no active session.
 */
public suspend fun AuthClient.updateUserForCurrentSession(
    sessionManager: SessionManager,
    updates: UserUpdateRequest,
    updateStoredSession: Boolean = true,
): SupabaseResult<User> {
    val token =
        sessionManager.accessToken
            ?: return SupabaseResult.Failure(SupabaseError(message = "No active session"))
    return when (val result = updateUser(token, updates)) {
        is SupabaseResult.Failure -> result
        is SupabaseResult.Success -> {
            if (updateStoredSession) {
                val current = sessionManager.currentSession
                if (current != null) {
                    sessionManager.saveSession(current.copy(user = result.value))
                }
            }
            result
        }
    }
}

/** Refreshes and persists the current session by delegating to [SessionManager.refreshSession]. */
public suspend fun AuthClient.refreshCurrentSession(
    sessionManager: SessionManager,
): SupabaseResult<Session> = sessionManager.refreshSession()

/**
 * Imports an externally obtained token as a [Session] and saves it — for adopting
 * a token minted outside this client (e.g. by a server or another SDK).
 *
 * @param refreshToken refresh token to store, if any (empty means non-refreshable).
 * @param expiresIn lifetime in seconds; used to schedule auto-refresh.
 * @param tokenType token type, normally `"bearer"`.
 * @param retrieveUser when true, fetches the [User] via [AuthClient.getUser];
 *   when false, reuses the user from the stored session (and fails if none exists).
 */
public suspend fun AuthClient.importAuthToken(
    sessionManager: SessionManager,
    accessToken: String,
    refreshToken: String = "",
    expiresIn: Long = 0L,
    tokenType: String = "bearer",
    retrieveUser: Boolean = true,
): SupabaseResult<Session> {
    val user =
        if (retrieveUser) {
            when (val result = getUser(accessToken)) {
                is SupabaseResult.Failure -> return result
                is SupabaseResult.Success -> result.value
            }
        } else {
            sessionManager.currentSession?.user
                ?: return SupabaseResult.Failure(
                    SupabaseError(message = "No user available. Pass retrieveUser=true or provide an existing session"),
                )
        }
    val session =
        Session(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = expiresIn,
            tokenType = tokenType,
            user = user,
        )
    sessionManager.saveSession(session)
    return SupabaseResult.Success(session)
}

/** Resolves an SSO sign-in URL using the current session's token. See [AuthClient.getSsoUrl]. */
public suspend fun AuthClient.getSsoUrlForCurrentSession(
    sessionManager: SessionManager,
    domain: String? = null,
    providerId: String? = null,
    redirectTo: String? = null,
): SupabaseResult<SsoResponse> =
    getSsoUrl(
        accessToken = sessionManager.accessToken,
        domain = domain,
        providerId = providerId,
        redirectTo = redirectTo,
    )

/** Email sign-in that saves the resulting session on success. See [AuthClient.signInWithEmail] and [SessionManager.saveSession]. */
public suspend fun AuthClient.signInWithEmailAndSaveSession(
    sessionManager: SessionManager,
    email: String,
    password: String,
): SupabaseResult<Session> =
    saveSessionOnSuccess(sessionManager) { signInWithEmail(email = email, password = password) }

/** Phone sign-in that saves the resulting session on success. See [AuthClient.signInWithPhone]. */
public suspend fun AuthClient.signInWithPhoneAndSaveSession(
    sessionManager: SessionManager,
    phone: String,
    password: String,
): SupabaseResult<Session> =
    saveSessionOnSuccess(sessionManager) { signInWithPhone(phone = phone, password = password) }

/** Email sign-up that saves the resulting session on success. See [AuthClient.signUpWithEmail]. */
public suspend fun AuthClient.signUpWithEmailAndSaveSession(
    sessionManager: SessionManager,
    email: String,
    password: String,
    data: JsonObject? = null,
): SupabaseResult<Session> =
    saveSessionOnSuccess(sessionManager) { signUpWithEmail(email = email, password = password, data = data) }

/** Phone sign-up that saves the resulting session on success. See [AuthClient.signUpWithPhone]. */
public suspend fun AuthClient.signUpWithPhoneAndSaveSession(
    sessionManager: SessionManager,
    phone: String,
    password: String,
    data: JsonObject? = null,
): SupabaseResult<Session> =
    saveSessionOnSuccess(sessionManager) { signUpWithPhone(phone = phone, password = password, data = data) }

/** Anonymous sign-in that saves the resulting session on success. See [AuthClient.signInAnonymously]. */
public suspend fun AuthClient.signInAnonymouslyAndSaveSession(
    sessionManager: SessionManager,
    data: JsonObject? = null,
    captchaToken: String? = null,
): SupabaseResult<Session> =
    saveSessionOnSuccess(sessionManager) { signInAnonymously(data = data, captchaToken = captchaToken) }

/** ID-token sign-in that saves the resulting session on success. See [AuthClient.signInWithIdToken]. */
public suspend fun AuthClient.signInWithIdTokenAndSaveSession(
    sessionManager: SessionManager,
    provider: OAuthProvider,
    idToken: String,
    accessToken: String? = null,
    nonce: String? = null,
    captchaToken: String? = null,
): SupabaseResult<Session> =
    saveSessionOnSuccess(sessionManager) {
        signInWithIdToken(
            provider = provider,
            idToken = idToken,
            accessToken = accessToken,
            nonce = nonce,
            captchaToken = captchaToken,
        )
    }

/**
 * Verifies an OTP and, only when the result is [OtpVerifyResult.Authenticated],
 * saves its session — a no-session verification is returned untouched. See
 * [AuthClient.verifyOtp].
 */
public suspend fun AuthClient.verifyOtpAndSaveSession(
    sessionManager: SessionManager,
    email: String? = null,
    phone: String? = null,
    token: String,
    type: OtpType,
    captchaToken: String? = null,
): SupabaseResult<OtpVerifyResult> =
    when (
        val result =
            verifyOtp(
                email = email,
                phone = phone,
                token = token,
                type = type,
                captchaToken = captchaToken,
            )
    ) {
        is SupabaseResult.Failure -> result
        is SupabaseResult.Success -> {
            when (val value = result.value) {
                is OtpVerifyResult.Authenticated -> sessionManager.saveSession(value.session)
                OtpVerifyResult.VerifiedNoSession -> Unit
            }
            result
        }
    }

/**
 * Verifies a token-hash confirmation and, only when the result is
 * [OtpVerifyResult.Authenticated], saves its session — a no-session verification is
 * returned untouched. See [AuthClient.verifyOtpWithTokenHash].
 */
public suspend fun AuthClient.verifyOtpWithTokenHashAndSaveSession(
    sessionManager: SessionManager,
    tokenHash: String,
    type: OtpType,
    captchaToken: String? = null,
): SupabaseResult<OtpVerifyResult> =
    when (
        val result =
            verifyOtpWithTokenHash(
                tokenHash = tokenHash,
                type = type,
                captchaToken = captchaToken,
            )
    ) {
        is SupabaseResult.Failure -> result
        is SupabaseResult.Success -> {
            when (val value = result.value) {
                is OtpVerifyResult.Authenticated -> sessionManager.saveSession(value.session)
                OtpVerifyResult.VerifiedNoSession -> Unit
            }
            result
        }
    }

/** Refreshes using an explicit [refreshToken] and saves the resulting session on success. See [AuthClient.refreshToken]. */
public suspend fun AuthClient.refreshTokenAndSaveSession(
    sessionManager: SessionManager,
    refreshToken: String,
): SupabaseResult<Session> =
    saveSessionOnSuccess(sessionManager) { refreshToken(refreshToken) }

private suspend fun AuthClient.saveSessionOnSuccess(
    sessionManager: SessionManager,
    call: suspend AuthClient.() -> SupabaseResult<Session>,
): SupabaseResult<Session> =
    when (val result = call()) {
        is SupabaseResult.Failure -> result
        is SupabaseResult.Success -> {
            sessionManager.saveSession(result.value)
            result
        }
    }

/**
 * Unlinks an identity (see [AuthClient.unlinkIdentity]) and, when
 * [updateStoredSession] is true, removes it from the stored session's user so the
 * cached identity list stays consistent without a re-fetch.
 */
public suspend fun AuthClient.unlinkIdentityAndUpdateSession(
    accessToken: String,
    identityId: String,
    sessionManager: SessionManager,
    updateStoredSession: Boolean = true,
): SupabaseResult<Unit> =
    when (val result = unlinkIdentity(accessToken = accessToken, identityId = identityId)) {
        is SupabaseResult.Failure -> result
        is SupabaseResult.Success -> {
            if (updateStoredSession) {
                val current = sessionManager.currentSession
                if (current != null) {
                    val updatedUser =
                        current.user.copy(
                            identities = current.user.identities?.filterNot { it.id == identityId },
                        )
                    sessionManager.saveSession(current.copy(user = updatedUser))
                }
            }
            result
        }
    }

/** Lists the current session's linked identities. See [AuthClient.getUserIdentities]. Fails with no active session. */
public suspend fun AuthClient.getUserIdentitiesForCurrentSession(
    sessionManager: SessionManager,
): SupabaseResult<List<UserIdentity>> {
    val token =
        sessionManager.accessToken
            ?: return SupabaseResult.Failure(SupabaseError(message = "No active session"))
    return getUserIdentities(accessToken = token)
}

/** Starts linking an OAuth identity for the current session. See [AuthClient.linkIdentity]. Fails with no active session. */
public suspend fun AuthClient.linkIdentityForCurrentSession(
    sessionManager: SessionManager,
    provider: OAuthProvider,
    redirectTo: String? = null,
    scopes: List<String> = emptyList(),
    queryParams: Map<String, String> = emptyMap(),
): SupabaseResult<LinkIdentityResponse> {
    val token =
        sessionManager.accessToken
            ?: return SupabaseResult.Failure(SupabaseError(message = "No active session"))
    return linkIdentity(
        accessToken = token,
        provider = provider,
        redirectTo = redirectTo,
        scopes = scopes,
        queryParams = queryParams,
    )
}

/**
 * Links an identity by ID token for the current session and, when
 * [updateStoredSession] is true, saves the returned session. See
 * [AuthClient.linkIdentityWithIdToken]. Fails with no active session.
 */
public suspend fun AuthClient.linkIdentityWithIdTokenForCurrentSession(
    sessionManager: SessionManager,
    provider: OAuthProvider,
    idToken: String,
    providerAccessToken: String? = null,
    nonce: String? = null,
    updateStoredSession: Boolean = true,
): SupabaseResult<Session> {
    val token =
        sessionManager.accessToken
            ?: return SupabaseResult.Failure(SupabaseError(message = "No active session"))
    return when (
        val result =
            linkIdentityWithIdToken(
                accessToken = token,
                provider = provider,
                idToken = idToken,
                providerAccessToken = providerAccessToken,
                nonce = nonce,
            )
    ) {
        is SupabaseResult.Failure -> result
        is SupabaseResult.Success -> {
            if (updateStoredSession) {
                sessionManager.saveSession(result.value)
            }
            result
        }
    }
}

/** Unlinks an identity for the current session, updating storage. See [unlinkIdentityAndUpdateSession]. Fails with no active session. */
public suspend fun AuthClient.unlinkIdentityForCurrentSession(
    sessionManager: SessionManager,
    identityId: String,
    updateStoredSession: Boolean = true,
): SupabaseResult<Unit> {
    val token =
        sessionManager.accessToken
            ?: return SupabaseResult.Failure(SupabaseError(message = "No active session"))
    return unlinkIdentityAndUpdateSession(
        accessToken = token,
        identityId = identityId,
        sessionManager = sessionManager,
        updateStoredSession = updateStoredSession,
    )
}

/** Session tokens parsed out of an implicit-flow redirect URL fragment. See [parseSessionTokensFromFragment]. */
public data class ParsedSessionTokens(
    public val accessToken: String,
    public val refreshToken: String,
    public val expiresIn: Long,
    public val tokenType: String,
)

/**
 * Parses [ParsedSessionTokens] from an OAuth implicit-flow URL [fragment] (the
 * `#access_token=…&refresh_token=…&expires_in=…&token_type=…` part the provider
 * appends on redirect). A leading `#` is tolerated and components are
 * percent-decoded. Returns [SupabaseResult.Failure] if any required field is
 * missing.
 */
public fun parseSessionTokensFromFragment(fragment: String): SupabaseResult<ParsedSessionTokens> {
    val normalized = fragment.removePrefix("#")
    val pairs =
        normalized
            .split("&")
            .mapNotNull { part ->
                if (part.isBlank()) return@mapNotNull null
                val i = part.indexOf('=')
                if (i < 0) return@mapNotNull decodeQueryComponent(part) to ""
                decodeQueryComponent(part.substring(0, i)) to decodeQueryComponent(part.substring(i + 1))
            }.toMap()

    val accessToken =
        pairs["access_token"]
            ?: return SupabaseResult.Failure(SupabaseError(message = "No access token found in fragment"))
    val refreshToken =
        pairs["refresh_token"]
            ?: return SupabaseResult.Failure(SupabaseError(message = "No refresh token found in fragment"))
    val expiresIn =
        pairs["expires_in"]?.toLongOrNull()
            ?: return SupabaseResult.Failure(SupabaseError(message = "No expires_in found in fragment"))
    val tokenType =
        pairs["token_type"]
            ?: return SupabaseResult.Failure(SupabaseError(message = "No token_type found in fragment"))

    return SupabaseResult.Success(
        ParsedSessionTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = expiresIn,
            tokenType = tokenType,
        ),
    )
}

/** Extracts the fragment from a full redirect [url] and parses it. See [parseSessionTokensFromFragment]. */
public fun parseSessionTokensFromUrl(url: String): SupabaseResult<ParsedSessionTokens> =
    parseSessionTokensFromFragment(url.substringAfter('#', ""))

/**
 * Parses tokens from an implicit-flow redirect [fragment], fetches the matching
 * [User], builds a [Session] and saves it — the implicit-flow counterpart to
 * [exchangeCodeForSessionAndSave]. See [parseSessionTokensFromFragment].
 */
public suspend fun AuthClient.importSessionFromFragment(
    fragment: String,
    sessionManager: SessionManager,
): SupabaseResult<Session> {
    val parsed = parseSessionTokensFromFragment(fragment)
    return importParsedSessionTokens(parsed, sessionManager)
}

/** Imports and saves a session from a full implicit-flow redirect [url]. See [importSessionFromFragment]. */
public suspend fun AuthClient.importSessionFromUrl(
    url: String,
    sessionManager: SessionManager,
): SupabaseResult<Session> {
    val parsed = parseSessionTokensFromUrl(url)
    return importParsedSessionTokens(parsed, sessionManager)
}

/** Exchanges a PKCE auth code for a session and saves it on success. See [AuthClient.exchangeCodeForSession]. */
public suspend fun AuthClient.exchangeCodeForSessionAndSave(
    authCode: String,
    codeVerifier: String,
    sessionManager: SessionManager,
): SupabaseResult<Session> =
    when (val exchanged = exchangeCodeForSession(authCode, codeVerifier)) {
        is SupabaseResult.Failure -> exchanged
        is SupabaseResult.Success -> {
            sessionManager.saveSession(exchanged.value)
            exchanged
        }
    }

private suspend fun AuthClient.importParsedSessionTokens(
    parsed: SupabaseResult<ParsedSessionTokens>,
    sessionManager: SessionManager,
): SupabaseResult<Session> =
    when (parsed) {
        is SupabaseResult.Failure -> parsed
        is SupabaseResult.Success -> {
            when (val userResult = getUser(parsed.value.accessToken)) {
                is SupabaseResult.Failure -> userResult
                is SupabaseResult.Success -> {
                    val session =
                        Session(
                            accessToken = parsed.value.accessToken,
                            refreshToken = parsed.value.refreshToken,
                            expiresIn = parsed.value.expiresIn,
                            tokenType = parsed.value.tokenType,
                            user = userResult.value,
                        )
                    sessionManager.saveSession(session)
                    SupabaseResult.Success(session)
                }
            }
        }
    }

private fun decodeQueryComponent(input: String): String {
    if ('%' !in input && '+' !in input) return input
    val out = StringBuilder(input.length)
    var i = 0
    while (i < input.length) {
        val c = input[i]
        if (c == '+') {
            out.append(' ')
            i++
            continue
        }
        if (c == '%' && i + 2 < input.length) {
            val hi = input[i + 1].digitToIntOrNull(16)
            val lo = input[i + 2].digitToIntOrNull(16)
            if (hi != null && lo != null) {
                out.append((hi * 16 + lo).toChar())
                i += 3
                continue
            }
        }
        out.append(c)
        i++
    }
    return out.toString()
}

/**
 * Decodes the payload of a [jwt] into its raw claims [JsonObject] **without
 * verifying the signature** — a cheap local read for inspecting claims you don't
 * need to trust. When the signature matters (e.g. authorization decisions), use
 * [getClaims], which verifies it.
 */
public fun parseJwtClaims(jwt: String): SupabaseResult<JsonObject> {
    val parts = jwt.split('.')
    // A JWS is exactly three dot-separated segments (the signature may be empty
    // for an unverified token, e.g. "header.payload."). Reject anything else —
    // notably a 5-segment JWE, whose parts[1] is the encrypted key, not claims.
    if (parts.size != JWT_PART_COUNT) {
        return SupabaseResult.Failure(SupabaseError(message = "Invalid JWT format"))
    }
    val payload =
        decodeBase64Url(parts[1])
            ?: return SupabaseResult.Failure(SupabaseError(message = "Invalid JWT payload encoding"))
    return try {
        val element = Json.parseToJsonElement(payload)
        val claims =
            element as? JsonObject
                ?: return SupabaseResult.Failure(SupabaseError(message = "JWT payload is not a JSON object"))
        SupabaseResult.Success(claims)
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        SupabaseResult.Failure(SupabaseError(message = "Failed to parse JWT claims: ${e.message}"))
    }
}

/** Parses the current session access token's raw claims, without verifying. See [parseJwtClaims]. Fails with no active session. */
public fun SessionManager.parseCurrentSessionJwtClaims(): SupabaseResult<JsonObject> {
    val token =
        accessToken
            ?: return SupabaseResult.Failure(SupabaseError(message = "No active session"))
    return parseJwtClaims(token)
}

private val claimsJson = Json { ignoreUnknownKeys = true }

private fun decodeJwtHeader(segment: String): JwtHeader? {
    val json = decodeBase64Url(segment) ?: return null
    return try {
        claimsJson.decodeFromString(JwtHeader.serializer(), json)
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        null
    }
}

/**
 * Extracts the absolute `exp` claim (seconds since the Unix epoch) from an
 * access token, or null when the token is not a decodable JWT. The session
 * layer uses this to schedule auto-refresh against wall-clock time instead of a
 * relative `expires_in`, which goes stale the moment a session is persisted and
 * restored later.
 */
internal fun accessTokenExpiryEpochSeconds(accessToken: String): Long? =
    (decodeJwt(accessToken) as? SupabaseResult.Success)?.value?.claims?.expiresAt

private fun decodeJwt(jwt: String): SupabaseResult<JwtClaimsResult> {
    val parts = jwt.split('.')
    if (parts.size != JWT_PART_COUNT) {
        return SupabaseResult.Failure(SupabaseError(message = "Invalid JWT format"))
    }
    val header =
        decodeJwtHeader(parts[0])
            ?: return SupabaseResult.Failure(SupabaseError(message = "Invalid JWT header encoding"))
    val payload =
        when (val parsed = parseJwtClaims(jwt)) {
            is SupabaseResult.Failure -> return parsed
            is SupabaseResult.Success -> parsed.value
        }
    val claims =
        try {
            claimsJson.decodeFromJsonElement(JwtClaims.serializer(), payload)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            return SupabaseResult.Failure(SupabaseError(message = "Failed to decode JWT claims: ${e.message}"))
        }
    return SupabaseResult.Success(
        JwtClaimsResult(
            claims = claims,
            header = header,
            signature = parts.getOrNull(2).orEmpty(),
            raw = payload,
        ),
    )
}

/**
 * Decodes and (by default) verifies the claims of a Supabase JWT — the analogue of supabase-js
 * `auth.getClaims()`.
 *
 * The header and payload are decoded locally into [JwtClaims] (use [JwtClaimsResult.raw] for any
 * non-standard claim). When [verify] is true the token's signature is checked:
 *
 * - For asymmetric `ES256` (ECDSA P-256) tokens the signature is verified **locally** against the
 *   project's JWKS (fetched via [AuthClient.getJwks]), avoiding a network round-trip — this is
 *   what distinguishes `getClaims` from [AuthClient.getUser].
 * - For any other case (symmetric `HS256`, other asymmetric algorithms, a missing `kid`, an
 *   unreachable JWKS endpoint, or a platform without local crypto), it falls back to validating the
 *   token against the Auth server, the same check [AuthClient.getUser] performs. Falling back is
 *   always safe because the server re-validates the signature.
 *
 * Local verification uses platform-native crypto (JDK/JCA, Apple Security, WebCrypto, OpenSSL3) via
 * the `cryptography-kotlin` library; signature math is never hand-rolled.
 *
 * Standard registered-claim checks: `exp` and `nbf` are always validated (with a small clock-skew
 * leeway), since a valid token never legitimately fails them. `iss`/`aud` validation is **opt-in** —
 * pass [expectedIssuer]/[expectedAudience] to enforce them — so this stays backward-compatible and
 * works for self-hosted projects with non-default issuers.
 *
 * @param jwt the JWT to inspect, e.g. a session access token.
 * @param verify when true, verify the token's signature before returning the claims.
 * @param allowExpired when false, a token whose `exp` is in the past is rejected without a network call.
 * @param expectedIssuer when non-null, the token's `iss` must equal this value (e.g. `"<project>/auth/v1"`).
 * @param expectedAudience when non-null, the token's `aud` (string or array) must contain this value (e.g. `"authenticated"`).
 */
public suspend fun AuthClient.getClaims(
    jwt: String,
    verify: Boolean = true,
    allowExpired: Boolean = false,
    expectedIssuer: String? = null,
    expectedAudience: String? = null,
): SupabaseResult<JwtClaimsResult> {
    val decoded =
        when (val result = decodeJwt(jwt)) {
            is SupabaseResult.Failure -> return result
            is SupabaseResult.Success -> result.value
        }
    validateRegisteredClaims(decoded, allowExpired, expectedIssuer, expectedAudience)?.let { return it }
    if (verify) {
        val validation = verifyJwt(jwt, decoded.header)
        if (validation is SupabaseResult.Failure) return validation
    }
    return SupabaseResult.Success(decoded)
}

// Validates the standard registered claims locally, returning the first failure or null if all pass.
// `exp`/`nbf` are always checked (with clock-skew leeway); `iss`/`aud` only when an expected value is
// given. Extracted from getClaims so each guard is an early return without tripping the return-count limit.
private fun validateRegisteredClaims(
    decoded: JwtClaimsResult,
    allowExpired: Boolean,
    expectedIssuer: String?,
    expectedAudience: String?,
): SupabaseResult.Failure? {
    val now = Clock.System.now().epochSeconds
    val expiresAt = decoded.claims.expiresAt
    if (!allowExpired && expiresAt != null && expiresAt + JWT_CLOCK_SKEW_LEEWAY_SECONDS <= now) {
        return SupabaseResult.Failure(SupabaseError(message = "JWT has expired"))
    }
    val notBefore = decoded.claims.notBefore
    if (notBefore != null && now + JWT_CLOCK_SKEW_LEEWAY_SECONDS < notBefore) {
        return SupabaseResult.Failure(SupabaseError(message = "JWT is not yet valid (nbf)"))
    }
    if (expectedIssuer != null && decoded.claims.issuer != expectedIssuer) {
        return SupabaseResult.Failure(SupabaseError(message = "JWT issuer mismatch"))
    }
    if (expectedAudience != null && expectedAudience !in audienceValues(decoded.raw)) {
        return SupabaseResult.Failure(SupabaseError(message = "JWT audience mismatch"))
    }
    return null
}

private const val JWT_CLOCK_SKEW_LEEWAY_SECONDS = 60L

/**
 * Verifies an OAuth `state` (CSRF) parameter on callback. Returns a [SupabaseResult.Success] only when
 * [expected] (the value you persisted from [AuthClient.generateOAuthState]) matches [returned] (the
 * `state` query param the provider sent back). A blank/absent [returned] is always a failure. The
 * comparison is constant-time so a mismatch doesn't leak how much of the value was correct.
 */
public fun verifyOAuthState(
    expected: String,
    returned: String?,
): SupabaseResult<Unit> {
    if (returned.isNullOrEmpty() || !constantTimeEquals(expected, returned)) {
        return SupabaseResult.Failure(SupabaseError(message = "OAuth state mismatch (possible CSRF)"))
    }
    return SupabaseResult.Success(Unit)
}

private fun constantTimeEquals(
    a: String,
    b: String,
): Boolean {
    if (a.length != b.length) return false
    var diff = 0
    for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
    return diff == 0
}

/** Extracts the `aud` claim, which may be a single string or an array of strings, as a flat list. */
private fun audienceValues(raw: JsonObject): List<String> =
    when (val aud = raw["aud"]) {
        is JsonPrimitive -> if (aud.isString) listOf(aud.content) else emptyList()
        is JsonArray -> aud.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        else -> emptyList()
    }

/**
 * Verifies [jwt] either locally (asymmetric ES256 against the project JWKS) or, when local
 * verification isn't applicable, by asking the Auth server. The server path is always safe.
 */
private suspend fun AuthClient.verifyJwt(
    jwt: String,
    header: JwtHeader,
): SupabaseResult<Unit> =
    when (verifyEs256Locally(jwt, header)) {
        LocalVerification.VALID -> SupabaseResult.Success(Unit)
        LocalVerification.INVALID ->
            SupabaseResult.Failure(SupabaseError(message = "JWT signature verification failed"))
        LocalVerification.UNSUPPORTED -> getUser(accessToken = jwt).map { }
    }

private enum class LocalVerification { VALID, INVALID, UNSUPPORTED }

/** Material required to verify an ES256 signature: the RAW public key, the signing input, and the signature. */
private class Es256Material(
    val publicKey: ByteArray,
    val signingInput: ByteArray,
    val signature: ByteArray,
)

private const val ES256_ALG = "ES256"
private const val EC_CURVE_P256 = "P-256"
private const val EC_KEY_TYPE = "EC"
private const val EC_COORDINATE_SIZE = 32
private const val EC_UNCOMPRESSED_TAG: Byte = 0x04
private const val JWT_PART_COUNT = 3

/**
 * Attempts a local ES256 signature check. Returns [LocalVerification.UNSUPPORTED] for any token that
 * can't be verified locally (wrong alg, missing key, JWKS fetch failure, or no platform crypto), so
 * the caller can fall back to the server.
 */
private suspend fun AuthClient.verifyEs256Locally(
    jwt: String,
    header: JwtHeader,
): LocalVerification {
    val material = buildEs256Material(jwt, header) ?: return LocalVerification.UNSUPPORTED
    return try {
        if (verifyEs256(material)) LocalVerification.VALID else LocalVerification.INVALID
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        LocalVerification.UNSUPPORTED
    }
}

private suspend fun AuthClient.buildEs256Material(
    jwt: String,
    header: JwtHeader,
): Es256Material? {
    val parts = jwt.split('.')
    val kid = header.keyId
    if (header.algorithm != ES256_ALG || kid == null || parts.size != JWT_PART_COUNT) return null
    val publicKey = resolveJwk(kid)?.let(::ecPublicKeyBytes) ?: return null
    val signature = decodeBase64UrlBytes(parts[2]) ?: return null
    return Es256Material(
        publicKey = publicKey,
        signingInput = "${parts[0]}.${parts[1]}".encodeToByteArray(),
        signature = signature,
    )
}

private suspend fun AuthClient.resolveJwk(kid: String): Jwk? =
    when (val result = resolveSigningKey(kid)) {
        is SupabaseResult.Failure -> null
        is SupabaseResult.Success -> result.value
    }

/** Reconstructs the RAW (uncompressed `0x04 || X || Y`) public key bytes for an EC P-256 JWK. */
private fun ecPublicKeyBytes(jwk: Jwk): ByteArray? {
    if (jwk.keyType != EC_KEY_TYPE || jwk.curve != EC_CURVE_P256) return null
    val x = jwk.x?.let(::decodeBase64UrlBytes) ?: return null
    val y = jwk.y?.let(::decodeBase64UrlBytes) ?: return null
    if (x.size != EC_COORDINATE_SIZE || y.size != EC_COORDINATE_SIZE) return null
    return byteArrayOf(EC_UNCOMPRESSED_TAG) + x + y
}

private suspend fun verifyEs256(material: Es256Material): Boolean {
    val ecdsa = CryptographyProvider.Default.get(ECDSA)
    val publicKey =
        ecdsa
            .publicKeyDecoder(EC.Curve.P256)
            .decodeFromByteArray(EC.PublicKey.Format.RAW, material.publicKey)
    return publicKey
        .signatureVerifier(SHA256, ECDSA.SignatureFormat.RAW)
        .tryVerifySignature(material.signingInput, material.signature)
}

/** Decodes and verifies the claims of the current session's access token. See [getClaims]. */
public suspend fun SessionManager.getClaimsForCurrentSession(
    authClient: AuthClient,
    verify: Boolean = true,
    allowExpired: Boolean = false,
    expectedIssuer: String? = null,
    expectedAudience: String? = null,
): SupabaseResult<JwtClaimsResult> {
    val token =
        accessToken
            ?: return SupabaseResult.Failure(SupabaseError(message = "No active session"))
    return authClient.getClaims(
        jwt = token,
        verify = verify,
        allowExpired = allowExpired,
        expectedIssuer = expectedIssuer,
        expectedAudience = expectedAudience,
    )
}

private fun decodeBase64Url(input: String): String? = decodeBase64UrlBytes(input)?.decodeToString()

private fun decodeBase64UrlBytes(input: String): ByteArray? {
    val normalized =
        input
            .replace('-', '+')
            .replace('_', '/')
            .let {
                when (it.length % 4) {
                    2 -> "$it=="
                    3 -> "$it="
                    else -> it
                }
            }
    val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val output = ArrayList<Byte>((normalized.length * 3) / 4)
    var i = 0
    while (i < normalized.length) {
        val c1 = normalized.getOrNull(i) ?: return null
        val c2 = normalized.getOrNull(i + 1) ?: return null
        val c3 = normalized.getOrNull(i + 2) ?: return null
        val c4 = normalized.getOrNull(i + 3) ?: return null
        val b1 = if (c1 == '=') -1 else table.indexOf(c1)
        val b2 = if (c2 == '=') -1 else table.indexOf(c2)
        val b3 = if (c3 == '=') -1 else table.indexOf(c3)
        val b4 = if (c4 == '=') -1 else table.indexOf(c4)
        if (b1 < 0 || b2 < 0 || (c3 != '=' && b3 < 0) || (c4 != '=' && b4 < 0)) return null
        val n = (b1 shl 18) or (b2 shl 12) or ((if (b3 < 0) 0 else b3) shl 6) or (if (b4 < 0) 0 else b4)
        output += ((n shr 16) and 0xFF).toByte()
        if (c3 != '=') output += ((n shr 8) and 0xFF).toByte()
        if (c4 != '=') output += (n and 0xFF).toByte()
        i += 4
    }
    return output.toByteArray()
}
