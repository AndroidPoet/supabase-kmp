package io.github.androidpoet.supabase.auth

import io.github.androidpoet.supabase.auth.models.AuthenticatorAssuranceLevel
import io.github.androidpoet.supabase.auth.models.MfaChallengeResponse
import io.github.androidpoet.supabase.auth.models.MfaEnrollResponse
import io.github.androidpoet.supabase.auth.models.MfaFactorType
import io.github.androidpoet.supabase.auth.models.MfaListFactorsResponse
import io.github.androidpoet.supabase.auth.models.MfaUnenrollResponse
import io.github.androidpoet.supabase.auth.models.MfaVerifyResponse
import io.github.androidpoet.supabase.auth.models.OAuthProvider
import io.github.androidpoet.supabase.auth.models.OtpType
import io.github.androidpoet.supabase.auth.models.PkceParams
import io.github.androidpoet.supabase.auth.models.Session
import io.github.androidpoet.supabase.auth.models.User
import io.github.androidpoet.supabase.auth.models.UserUpdateRequest
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.serialization.json.JsonObject

/**
 * Client for Supabase GoTrue authentication operations.
 *
 * Provides typed methods for sign-up, sign-in, OTP verification,
 * token refresh, and user management against the Auth v1 API.
 */
public interface AuthClient {

    /** Creates a new user with email and password. */
    public suspend fun signUpWithEmail(
        email: String,
        password: String,
        data: JsonObject? = null,
    ): SupabaseResult<Session>

    /** Signs in an existing user with email and password. */
    public suspend fun signInWithEmail(
        email: String,
        password: String,
    ): SupabaseResult<Session>

    /** Signs in an existing user with phone and password. */
    public suspend fun signInWithPhone(
        phone: String,
        password: String,
    ): SupabaseResult<Session>

    /** Sends a one-time password to the given email or phone. */
    public suspend fun signInWithOtp(
        email: String? = null,
        phone: String? = null,
    ): SupabaseResult<Unit>

    /** Verifies an OTP token for the given email or phone. */
    public suspend fun verifyOtp(
        email: String? = null,
        phone: String? = null,
        token: String,
        type: OtpType,
    ): SupabaseResult<Session>

    /** Exchanges a refresh token for a new session. */
    public suspend fun refreshToken(refreshToken: String): SupabaseResult<Session>

    /** Retrieves the user associated with the given access token. */
    public suspend fun getUser(accessToken: String): SupabaseResult<User>

    /** Updates the user associated with the given access token. */
    public suspend fun updateUser(
        accessToken: String,
        updates: UserUpdateRequest,
    ): SupabaseResult<User>

    /** Signs out the user, invalidating the given access token. */
    public suspend fun signOut(accessToken: String): SupabaseResult<Unit>

    // ── OAuth ────────────────────────────────────────────────────────

    /**
     * Builds the OAuth sign-in URL for the given [provider].
     *
     * @param redirectTo URL to redirect back to after authentication.
     * @param scopes OAuth scopes to request from the provider.
     * @param queryParams Additional query parameters appended to the URL.
     * @param pkceParams Optional PKCE parameters for the authorization code flow.
     */
    public fun getOAuthSignInUrl(
        provider: OAuthProvider,
        redirectTo: String? = null,
        scopes: List<String> = emptyList(),
        queryParams: Map<String, String> = emptyMap(),
        pkceParams: PkceParams? = null,
    ): String

    // ── PKCE ─────────────────────────────────────────────────────────

    /**
     * Generates PKCE parameters for the authorization code flow.
     *
     * @param sha256 Optional SHA-256 hash function. When provided, the `S256`
     *   challenge method is used; otherwise falls back to `plain`.
     */
    public fun generatePkceParams(sha256: ((ByteArray) -> ByteArray)? = null): PkceParams

    /** Exchanges an authorization code and PKCE code verifier for a session. */
    public suspend fun exchangeCodeForSession(
        authCode: String,
        codeVerifier: String,
    ): SupabaseResult<Session>

    // ── MFA ──────────────────────────────────────────────────────────

    /** Enrolls a new MFA factor (TOTP or phone). */
    public suspend fun mfaEnroll(
        factorType: MfaFactorType,
        friendlyName: String? = null,
        issuer: String? = null,
        phone: String? = null,
        accessToken: String,
    ): SupabaseResult<MfaEnrollResponse>

    /** Creates a challenge for the given MFA factor. */
    public suspend fun mfaChallenge(
        factorId: String,
        accessToken: String,
    ): SupabaseResult<MfaChallengeResponse>

    /** Verifies a challenge with a one-time code. */
    public suspend fun mfaVerify(
        factorId: String,
        challengeId: String,
        code: String,
        accessToken: String,
    ): SupabaseResult<MfaVerifyResponse>

    /** Unenrolls (removes) an MFA factor. */
    public suspend fun mfaUnenroll(
        factorId: String,
        accessToken: String,
    ): SupabaseResult<MfaUnenrollResponse>

    /** Lists all enrolled MFA factors for the authenticated user. */
    public suspend fun mfaListFactors(
        accessToken: String,
    ): SupabaseResult<MfaListFactorsResponse>

    /** Returns the current authenticator assurance level for the authenticated user. */
    public suspend fun mfaGetAuthenticatorAssuranceLevel(
        accessToken: String,
    ): SupabaseResult<AuthenticatorAssuranceLevel>
}
