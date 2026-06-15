package io.github.androidpoet.supabase.auth

import io.github.androidpoet.supabase.auth.models.JwtClaims
import io.github.androidpoet.supabase.auth.models.JwtClaimsResult
import io.github.androidpoet.supabase.auth.models.JwtHeader
import io.github.androidpoet.supabase.auth.models.LinkIdentityResponse
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
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

public suspend fun AuthClient.signUp(
    email: String,
    password: String,
    data: JsonObject? = null,
): SupabaseResult<Session> =
    signUpWithEmail(email = email, password = password, data = data)

public suspend fun AuthClient.signIn(
    email: String,
    password: String,
): SupabaseResult<Session> =
    signInWithEmail(email = email, password = password)

public suspend fun AuthClient.signUpPhone(
    phone: String,
    password: String,
    data: JsonObject? = null,
): SupabaseResult<Session> =
    signUpWithPhone(phone = phone, password = password, data = data)

public suspend fun AuthClient.signInPhone(
    phone: String,
    password: String,
): SupabaseResult<Session> =
    signInWithPhone(phone = phone, password = password)

public suspend fun AuthClient.sendOtp(email: String): SupabaseResult<Unit> =
    signInWithOtp(email = email)

public suspend fun AuthClient.sendPhoneOtp(phone: String): SupabaseResult<Unit> =
    signInWithOtp(phone = phone)

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

public suspend fun AuthClient.resendPhoneSignInOtp(
    phone: String,
    captchaToken: String? = null,
): SupabaseResult<Unit> =
    resendPhoneOtp(
        type = OtpType.SMS,
        phone = phone,
        captchaToken = captchaToken,
    )

public suspend fun AuthClient.verifyEmailOtp(
    email: String,
    token: String,
    type: OtpType,
    captchaToken: String? = null,
): SupabaseResult<Session> =
    verifyOtp(email = email, token = token, type = type, captchaToken = captchaToken)

public suspend fun AuthClient.verifyPhoneOtp(
    phone: String,
    token: String,
    type: OtpType,
    captchaToken: String? = null,
): SupabaseResult<Session> =
    verifyOtp(phone = phone, token = token, type = type, captchaToken = captchaToken)

public suspend fun AuthClient.verifyEmailSignUpOtp(
    email: String,
    token: String,
    captchaToken: String? = null,
): SupabaseResult<Session> =
    verifyEmailOtp(email = email, token = token, type = OtpType.EMAIL, captchaToken = captchaToken)

public suspend fun AuthClient.verifyPhoneSignInOtp(
    phone: String,
    token: String,
    captchaToken: String? = null,
): SupabaseResult<Session> =
    verifyPhoneOtp(phone = phone, token = token, type = OtpType.SMS, captchaToken = captchaToken)

public suspend fun AuthClient.verifyEmailOtpWithTokenHash(
    tokenHash: String,
    type: OtpType,
    captchaToken: String? = null,
): SupabaseResult<Session> =
    verifyOtpWithTokenHash(
        tokenHash = tokenHash,
        type = type,
        captchaToken = captchaToken,
    )

public suspend fun AuthClient.verifyEmailOtpWithResult(
    email: String,
    token: String,
    type: OtpType,
    captchaToken: String? = null,
): SupabaseResult<OtpVerifyResult> =
    verifyOtpWithResult(
        email = email,
        token = token,
        type = type,
        captchaToken = captchaToken,
    )

public suspend fun AuthClient.verifyPhoneOtpWithResult(
    phone: String,
    token: String,
    type: OtpType,
    captchaToken: String? = null,
): SupabaseResult<OtpVerifyResult> =
    verifyOtpWithResult(
        phone = phone,
        token = token,
        type = type,
        captchaToken = captchaToken,
    )

public suspend fun AuthClient.verifyEmailSignUpOtpWithResult(
    email: String,
    token: String,
    captchaToken: String? = null,
): SupabaseResult<OtpVerifyResult> =
    verifyEmailOtpWithResult(
        email = email,
        token = token,
        type = OtpType.EMAIL,
        captchaToken = captchaToken,
    )

public suspend fun AuthClient.verifyPhoneSignInOtpWithResult(
    phone: String,
    token: String,
    captchaToken: String? = null,
): SupabaseResult<OtpVerifyResult> =
    verifyPhoneOtpWithResult(
        phone = phone,
        token = token,
        type = OtpType.SMS,
        captchaToken = captchaToken,
    )

public suspend fun AuthClient.verifyEmailOtpWithTokenHashWithResult(
    tokenHash: String,
    type: OtpType,
    captchaToken: String? = null,
): SupabaseResult<OtpVerifyResult> =
    verifyOtpWithTokenHashWithResult(
        tokenHash = tokenHash,
        type = type,
        captchaToken = captchaToken,
    )

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

public suspend fun AuthClient.signOutGlobal(accessToken: String): SupabaseResult<Unit> =
    signOut(accessToken = accessToken, scope = SignOutScope.GLOBAL)

public suspend fun AuthClient.signOutLocal(accessToken: String): SupabaseResult<Unit> =
    signOut(accessToken = accessToken, scope = SignOutScope.LOCAL)

public suspend fun AuthClient.signOutOthers(accessToken: String): SupabaseResult<Unit> =
    signOut(accessToken = accessToken, scope = SignOutScope.OTHERS)

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

public suspend fun AuthClient.reauthenticateCurrentSession(
    sessionManager: SessionManager,
): SupabaseResult<Unit> {
    val token =
        sessionManager.accessToken
            ?: return SupabaseResult.Failure(SupabaseError(message = "No active session"))
    return reauthenticate(token)
}

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

public suspend fun AuthClient.refreshCurrentSession(
    sessionManager: SessionManager,
): SupabaseResult<Session> = sessionManager.refreshSession()

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

public suspend fun AuthClient.retrieveSsoUrlForCurrentSession(
    sessionManager: SessionManager,
    domain: String? = null,
    providerId: String? = null,
    redirectTo: String? = null,
): SupabaseResult<SsoResponse> =
    retrieveSsoUrl(
        accessToken = sessionManager.accessToken,
        domain = domain,
        providerId = providerId,
        redirectTo = redirectTo,
    )

public suspend fun AuthClient.signInWithEmailAndSaveSession(
    sessionManager: SessionManager,
    email: String,
    password: String,
): SupabaseResult<Session> =
    saveSessionOnSuccess(sessionManager) { signInWithEmail(email = email, password = password) }

public suspend fun AuthClient.signInWithPhoneAndSaveSession(
    sessionManager: SessionManager,
    phone: String,
    password: String,
): SupabaseResult<Session> =
    saveSessionOnSuccess(sessionManager) { signInWithPhone(phone = phone, password = password) }

public suspend fun AuthClient.signUpWithEmailAndSaveSession(
    sessionManager: SessionManager,
    email: String,
    password: String,
    data: JsonObject? = null,
): SupabaseResult<Session> =
    saveSessionOnSuccess(sessionManager) { signUpWithEmail(email = email, password = password, data = data) }

public suspend fun AuthClient.signUpWithPhoneAndSaveSession(
    sessionManager: SessionManager,
    phone: String,
    password: String,
    data: JsonObject? = null,
): SupabaseResult<Session> =
    saveSessionOnSuccess(sessionManager) { signUpWithPhone(phone = phone, password = password, data = data) }

public suspend fun AuthClient.signInAnonymouslyAndSaveSession(
    sessionManager: SessionManager,
    data: JsonObject? = null,
    captchaToken: String? = null,
): SupabaseResult<Session> =
    saveSessionOnSuccess(sessionManager) { signInAnonymously(data = data, captchaToken = captchaToken) }

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

public suspend fun AuthClient.verifyOtpAndSaveSession(
    sessionManager: SessionManager,
    email: String? = null,
    phone: String? = null,
    token: String,
    type: OtpType,
    captchaToken: String? = null,
): SupabaseResult<Session> =
    saveSessionOnSuccess(sessionManager) {
        verifyOtp(
            email = email,
            phone = phone,
            token = token,
            type = type,
            captchaToken = captchaToken,
        )
    }

public suspend fun AuthClient.verifyOtpWithTokenHashAndSaveSession(
    sessionManager: SessionManager,
    tokenHash: String,
    type: OtpType,
    captchaToken: String? = null,
): SupabaseResult<Session> =
    saveSessionOnSuccess(sessionManager) {
        verifyOtpWithTokenHash(
            tokenHash = tokenHash,
            type = type,
            captchaToken = captchaToken,
        )
    }

public suspend fun AuthClient.verifyOtpWithResultAndSaveSession(
    sessionManager: SessionManager,
    email: String? = null,
    phone: String? = null,
    token: String,
    type: OtpType,
    captchaToken: String? = null,
): SupabaseResult<OtpVerifyResult> =
    when (
        val result =
            verifyOtpWithResult(
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

public suspend fun AuthClient.verifyOtpWithTokenHashWithResultAndSaveSession(
    sessionManager: SessionManager,
    tokenHash: String,
    type: OtpType,
    captchaToken: String? = null,
): SupabaseResult<OtpVerifyResult> =
    when (
        val result =
            verifyOtpWithTokenHashWithResult(
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

public suspend fun AuthClient.getUserIdentitiesForCurrentSession(
    sessionManager: SessionManager,
): SupabaseResult<List<UserIdentity>> {
    val token =
        sessionManager.accessToken
            ?: return SupabaseResult.Failure(SupabaseError(message = "No active session"))
    return getUserIdentities(accessToken = token)
}

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

public data class ParsedSessionTokens(
    public val accessToken: String,
    public val refreshToken: String,
    public val expiresIn: Long,
    public val tokenType: String,
)

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

public fun parseSessionTokensFromUrl(url: String): SupabaseResult<ParsedSessionTokens> =
    parseSessionTokensFromFragment(url.substringAfter('#', ""))

public suspend fun AuthClient.importSessionFromFragment(
    fragment: String,
    sessionManager: SessionManager,
): SupabaseResult<Session> {
    val parsed = parseSessionTokensFromFragment(fragment)
    return importParsedSessionTokens(parsed, sessionManager)
}

public suspend fun AuthClient.importSessionFromUrl(
    url: String,
    sessionManager: SessionManager,
): SupabaseResult<Session> {
    val parsed = parseSessionTokensFromUrl(url)
    return importParsedSessionTokens(parsed, sessionManager)
}

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

public fun parseJwtClaims(jwt: String): SupabaseResult<JsonObject> {
    val parts = jwt.split('.')
    if (parts.size < 2) {
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

private fun decodeJwt(jwt: String): SupabaseResult<JwtClaimsResult> {
    val parts = jwt.split('.')
    if (parts.size < 2) {
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
 * non-standard claim). When [verify] is true the token's authenticity is confirmed against the
 * Auth server (the same validation [AuthClient.getUser] performs), which covers both symmetric
 * (HS256) and asymmetric signing. Local JWKS signature verification is not yet implemented, so a
 * server round-trip is used when [verify] is true.
 *
 * @param jwt the JWT to inspect, e.g. a session access token.
 * @param verify when true, validate the token against the Auth server before returning the claims.
 * @param allowExpired when false, a token whose `exp` is in the past is rejected without a network call.
 */
public suspend fun AuthClient.getClaims(
    jwt: String,
    verify: Boolean = true,
    allowExpired: Boolean = false,
): SupabaseResult<JwtClaimsResult> {
    val decoded =
        when (val result = decodeJwt(jwt)) {
            is SupabaseResult.Failure -> return result
            is SupabaseResult.Success -> result.value
        }
    val expiresAt = decoded.claims.expiresAt
    if (!allowExpired && expiresAt != null && expiresAt <= Clock.System.now().epochSeconds) {
        return SupabaseResult.Failure(SupabaseError(message = "JWT has expired"))
    }
    if (verify) {
        val validation = getUser(accessToken = jwt)
        if (validation is SupabaseResult.Failure) return validation
    }
    return SupabaseResult.Success(decoded)
}

/** Decodes and verifies the claims of the current session's access token. See [getClaims]. */
public suspend fun SessionManager.getClaimsForCurrentSession(
    authClient: AuthClient,
    verify: Boolean = true,
    allowExpired: Boolean = false,
): SupabaseResult<JwtClaimsResult> {
    val token =
        accessToken
            ?: return SupabaseResult.Failure(SupabaseError(message = "No active session"))
    return authClient.getClaims(jwt = token, verify = verify, allowExpired = allowExpired)
}

private fun decodeBase64Url(input: String): String? {
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
    return output.toByteArray().decodeToString()
}
