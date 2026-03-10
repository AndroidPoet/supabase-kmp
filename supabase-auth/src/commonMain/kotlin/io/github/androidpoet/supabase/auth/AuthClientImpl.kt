package io.github.androidpoet.supabase.auth

import io.github.androidpoet.supabase.auth.models.AuthenticatorAssuranceLevel
import io.github.androidpoet.supabase.auth.models.ExchangeCodeRequest
import io.github.androidpoet.supabase.auth.models.MfaChallengeResponse
import io.github.androidpoet.supabase.auth.models.MfaEnrollRequest
import io.github.androidpoet.supabase.auth.models.MfaEnrollResponse
import io.github.androidpoet.supabase.auth.models.MfaFactor
import io.github.androidpoet.supabase.auth.models.MfaFactorType
import io.github.androidpoet.supabase.auth.models.MfaListFactorsResponse
import io.github.androidpoet.supabase.auth.models.MfaUnenrollResponse
import io.github.androidpoet.supabase.auth.models.MfaVerifyRequest
import io.github.androidpoet.supabase.auth.models.MfaVerifyResponse
import io.github.androidpoet.supabase.auth.models.OAuthProvider
import io.github.androidpoet.supabase.auth.models.OtpRequest
import io.github.androidpoet.supabase.auth.models.OtpType
import io.github.androidpoet.supabase.auth.models.OtpVerifyRequest
import io.github.androidpoet.supabase.auth.models.PkceParams
import io.github.androidpoet.supabase.auth.models.RefreshTokenRequest
import io.github.androidpoet.supabase.auth.models.Session
import io.github.androidpoet.supabase.auth.models.SignInRequest
import io.github.androidpoet.supabase.auth.models.SignUpRequest
import io.github.androidpoet.supabase.auth.models.User
import io.github.androidpoet.supabase.auth.models.UserUpdateRequest
import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.client.defaultJson
import io.github.androidpoet.supabase.client.deserialize
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.core.result.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlin.random.Random

/**
 * Internal implementation of [AuthClient] backed by [SupabaseClient].
 *
 * All requests target the GoTrue Auth v1 endpoints, with the project URL
 * resolved by the underlying client.
 */
internal class AuthClientImpl(
    private val client: SupabaseClient,
) : AuthClient {

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        data: JsonObject?,
    ): SupabaseResult<Session> {
        val body = defaultJson.encodeToString(SignUpRequest(email = email, password = password, data = data))
        return client.post("/auth/v1/signup", body = body).deserialize()
    }

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): SupabaseResult<Session> {
        val body = defaultJson.encodeToString(SignInRequest(email = email, password = password))
        return client.post("/auth/v1/token?grant_type=password", body = body).deserialize()
    }

    override suspend fun signInWithPhone(
        phone: String,
        password: String,
    ): SupabaseResult<Session> {
        val body = defaultJson.encodeToString(SignInRequest(phone = phone, password = password))
        return client.post("/auth/v1/token?grant_type=password", body = body).deserialize()
    }

    override suspend fun signInWithOtp(
        email: String?,
        phone: String?,
    ): SupabaseResult<Unit> {
        val body = defaultJson.encodeToString(OtpRequest(email = email, phone = phone))
        return client.post("/auth/v1/otp", body = body).map { }
    }

    override suspend fun verifyOtp(
        email: String?,
        phone: String?,
        token: String,
        type: OtpType,
    ): SupabaseResult<Session> {
        val body = defaultJson.encodeToString(
            OtpVerifyRequest(email = email, phone = phone, token = token, type = type),
        )
        return client.post("/auth/v1/verify", body = body).deserialize()
    }

    override suspend fun refreshToken(refreshToken: String): SupabaseResult<Session> {
        val body = defaultJson.encodeToString(RefreshTokenRequest(refreshToken = refreshToken))
        return client.post("/auth/v1/token?grant_type=refresh_token", body = body).deserialize()
    }

    override suspend fun getUser(accessToken: String): SupabaseResult<User> =
        client.get(
            endpoint = "/auth/v1/user",
            headers = bearerHeaders(accessToken),
        ).deserialize()

    override suspend fun updateUser(
        accessToken: String,
        updates: UserUpdateRequest,
    ): SupabaseResult<User> {
        val body = defaultJson.encodeToString(updates)
        return client.patch(
            endpoint = "/auth/v1/user",
            body = body,
            headers = bearerHeaders(accessToken),
        ).deserialize()
    }

    override suspend fun signOut(accessToken: String): SupabaseResult<Unit> =
        client.post(
            endpoint = "/auth/v1/logout",
            headers = bearerHeaders(accessToken),
        ).map { }

    // ── OAuth ────────────────────────────────────────────────────────

    override fun getOAuthSignInUrl(
        provider: OAuthProvider,
        redirectTo: String?,
        scopes: List<String>,
        queryParams: Map<String, String>,
        pkceParams: PkceParams?,
    ): String = buildString {
        append(client.projectUrl)
        append("/auth/v1/authorize?provider=")
        append(provider.value)

        if (redirectTo != null) {
            append("&redirect_to=")
            append(urlEncode(redirectTo))
        }
        if (scopes.isNotEmpty()) {
            append("&scopes=")
            append(urlEncode(scopes.joinToString(" ")))
        }
        if (pkceParams != null) {
            append("&code_challenge=")
            append(urlEncode(pkceParams.codeChallenge))
            append("&code_challenge_method=")
            append(pkceParams.codeChallengeMethod)
        }
        for ((key, value) in queryParams) {
            append("&")
            append(urlEncode(key))
            append("=")
            append(urlEncode(value))
        }
    }

    // ── PKCE ─────────────────────────────────────────────────────────

    override fun generatePkceParams(sha256: ((ByteArray) -> ByteArray)?): PkceParams {
        val verifier = buildString(PKCE_VERIFIER_LENGTH) {
            repeat(PKCE_VERIFIER_LENGTH) {
                append(PKCE_ALPHABET[Random.nextInt(PKCE_ALPHABET.length)])
            }
        }
        return if (sha256 != null) {
            val hash = sha256(verifier.encodeToByteArray())
            val challenge = base64UrlEncode(hash)
            PkceParams(
                codeVerifier = verifier,
                codeChallenge = challenge,
                codeChallengeMethod = "S256",
            )
        } else {
            PkceParams(
                codeVerifier = verifier,
                codeChallenge = verifier,
                codeChallengeMethod = "plain",
            )
        }
    }

    override suspend fun exchangeCodeForSession(
        authCode: String,
        codeVerifier: String,
    ): SupabaseResult<Session> {
        val body = defaultJson.encodeToString(
            ExchangeCodeRequest(authCode = authCode, codeVerifier = codeVerifier),
        )
        return client.post("/auth/v1/token?grant_type=pkce", body = body).deserialize()
    }

    // ── MFA ──────────────────────────────────────────────────────────

    override suspend fun mfaEnroll(
        factorType: MfaFactorType,
        friendlyName: String?,
        issuer: String?,
        phone: String?,
        accessToken: String,
    ): SupabaseResult<MfaEnrollResponse> {
        val body = defaultJson.encodeToString(
            MfaEnrollRequest(
                factorType = factorType,
                friendlyName = friendlyName,
                issuer = issuer,
                phone = phone,
            ),
        )
        return client.post(
            endpoint = "/auth/v1/factors",
            body = body,
            headers = bearerHeaders(accessToken),
        ).deserialize()
    }

    override suspend fun mfaChallenge(
        factorId: String,
        accessToken: String,
    ): SupabaseResult<MfaChallengeResponse> =
        client.post(
            endpoint = "/auth/v1/factors/$factorId/challenge",
            headers = bearerHeaders(accessToken),
        ).deserialize()

    override suspend fun mfaVerify(
        factorId: String,
        challengeId: String,
        code: String,
        accessToken: String,
    ): SupabaseResult<MfaVerifyResponse> {
        val body = defaultJson.encodeToString(
            MfaVerifyRequest(
                factorId = factorId,
                challengeId = challengeId,
                code = code,
            ),
        )
        return client.post(
            endpoint = "/auth/v1/factors/$factorId/verify",
            body = body,
            headers = bearerHeaders(accessToken),
        ).deserialize()
    }

    override suspend fun mfaUnenroll(
        factorId: String,
        accessToken: String,
    ): SupabaseResult<MfaUnenrollResponse> =
        client.delete(
            endpoint = "/auth/v1/factors/$factorId",
            headers = bearerHeaders(accessToken),
        ).deserialize()

    override suspend fun mfaListFactors(
        accessToken: String,
    ): SupabaseResult<MfaListFactorsResponse> {
        val userResult: SupabaseResult<User> = client.get(
            endpoint = "/auth/v1/user",
            headers = bearerHeaders(accessToken),
        ).deserialize()

        return when (userResult) {
            is SupabaseResult.Success -> {
                val factors = userResult.value.factors.orEmpty()
                SupabaseResult.Success(
                    MfaListFactorsResponse(
                        all = factors,
                        totp = factors.filter { it.factorType == MfaFactorType.TOTP },
                        phone = factors.filter { it.factorType == MfaFactorType.PHONE },
                    ),
                )
            }
            is SupabaseResult.Failure -> SupabaseResult.Failure(userResult.error)
        }
    }

    override suspend fun mfaGetAuthenticatorAssuranceLevel(
        accessToken: String,
    ): SupabaseResult<AuthenticatorAssuranceLevel> {
        val userResult: SupabaseResult<User> = client.get(
            endpoint = "/auth/v1/user",
            headers = bearerHeaders(accessToken),
        ).deserialize()

        return when (userResult) {
            is SupabaseResult.Success -> {
                val factors = userResult.value.factors.orEmpty()
                val hasVerifiedFactor = factors.any { it.status == "verified" }
                val level = if (hasVerifiedFactor) {
                    AuthenticatorAssuranceLevel.AAL2
                } else {
                    AuthenticatorAssuranceLevel.AAL1
                }
                SupabaseResult.Success(level)
            }
            is SupabaseResult.Failure -> SupabaseResult.Failure(userResult.error)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun bearerHeaders(token: String): Map<String, String> =
        mapOf("Authorization" to "Bearer $token")

    /**
     * Minimal percent-encoding for URL query values.
     * Encodes everything except unreserved characters (RFC 3986).
     */
    private fun urlEncode(value: String): String = buildString {
        for (char in value) {
            when {
                char.isLetterOrDigit() || char in "-._~" -> append(char)
                else -> {
                    for (byte in char.toString().encodeToByteArray()) {
                        append('%')
                        append(HEX_CHARS[(byte.toInt() shr 4) and 0x0F])
                        append(HEX_CHARS[byte.toInt() and 0x0F])
                    }
                }
            }
        }
    }

    /**
     * Base64url encoding without padding (RFC 4648 §5).
     */
    private fun base64UrlEncode(bytes: ByteArray): String {
        val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        return buildString {
            var i = 0
            while (i < bytes.size) {
                val b0 = bytes[i].toInt() and 0xFF
                append(table[b0 shr 2])
                if (i + 1 < bytes.size) {
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    append(table[((b0 and 0x03) shl 4) or (b1 shr 4)])
                    if (i + 2 < bytes.size) {
                        val b2 = bytes[i + 2].toInt() and 0xFF
                        append(table[((b1 and 0x0F) shl 2) or (b2 shr 6)])
                        append(table[b2 and 0x3F])
                    } else {
                        append(table[(b1 and 0x0F) shl 2])
                    }
                } else {
                    append(table[(b0 and 0x03) shl 4])
                }
                i += 3
            }
        }
    }

    private companion object {
        /** Characters allowed in a PKCE code verifier (RFC 7636 §4.1). */
        const val PKCE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

        /** Minimum verifier length per spec (43 characters). */
        const val PKCE_VERIFIER_LENGTH = 43

        val HEX_CHARS = "0123456789ABCDEF".toCharArray()
    }
}
