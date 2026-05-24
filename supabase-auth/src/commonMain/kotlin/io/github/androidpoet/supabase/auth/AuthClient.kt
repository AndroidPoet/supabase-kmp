package io.github.androidpoet.supabase.auth
import io.github.androidpoet.supabase.auth.models.AuthenticatorAssuranceLevel
import io.github.androidpoet.supabase.auth.models.MfaChallengeResponse
import io.github.androidpoet.supabase.auth.models.MfaEnrollResponse
import io.github.androidpoet.supabase.auth.models.MfaFactorType
import io.github.androidpoet.supabase.auth.models.MfaListFactorsResponse
import io.github.androidpoet.supabase.auth.models.MfaUnenrollResponse
import io.github.androidpoet.supabase.auth.models.MfaVerifyResponse
import io.github.androidpoet.supabase.auth.models.LinkIdentityResponse
import io.github.androidpoet.supabase.auth.models.OAuthProvider
import io.github.androidpoet.supabase.auth.models.OtpType
import io.github.androidpoet.supabase.auth.models.OtpVerifyResult
import io.github.androidpoet.supabase.auth.models.PkceParams
import io.github.androidpoet.supabase.auth.models.Session
import io.github.androidpoet.supabase.auth.models.SignOutScope
import io.github.androidpoet.supabase.auth.models.SsoResponse
import io.github.androidpoet.supabase.auth.models.User
import io.github.androidpoet.supabase.auth.models.UserUpdateRequest
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.serialization.json.JsonObject
public interface AuthClient {
    public suspend fun signUpWithEmail(
        email: String,
        password: String,
        data: JsonObject? = null,
    ): SupabaseResult<Session>
    public suspend fun signUpWithPhone(
        phone: String,
        password: String,
        data: JsonObject? = null,
    ): SupabaseResult<Session>
    public suspend fun signInWithEmail(
        email: String,
        password: String,
    ): SupabaseResult<Session>
    public suspend fun signInAnonymously(
        data: JsonObject? = null,
        captchaToken: String? = null,
    ): SupabaseResult<Session>
    public suspend fun signInWithPhone(
        phone: String,
        password: String,
    ): SupabaseResult<Session>
    public suspend fun signInWithIdToken(
        provider: OAuthProvider,
        idToken: String,
        accessToken: String? = null,
        nonce: String? = null,
        captchaToken: String? = null,
    ): SupabaseResult<Session>
    public suspend fun signInWithOtp(
        email: String? = null,
        phone: String? = null,
        createUser: Boolean? = null,
        captchaToken: String? = null,
        emailRedirectTo: String? = null,
    ): SupabaseResult<Unit>
    public suspend fun verifyOtp(
        email: String? = null,
        phone: String? = null,
        token: String,
        type: OtpType,
        captchaToken: String? = null,
    ): SupabaseResult<Session>
    public suspend fun verifyOtpWithTokenHash(
        tokenHash: String,
        type: OtpType,
        captchaToken: String? = null,
    ): SupabaseResult<Session>
    public suspend fun verifyOtpWithResult(
        email: String? = null,
        phone: String? = null,
        token: String,
        type: OtpType,
        captchaToken: String? = null,
    ): SupabaseResult<OtpVerifyResult>
    public suspend fun verifyOtpWithTokenHashWithResult(
        tokenHash: String,
        type: OtpType,
        captchaToken: String? = null,
    ): SupabaseResult<OtpVerifyResult>
    public suspend fun resendEmailOtp(
        type: OtpType,
        email: String,
        captchaToken: String? = null,
        redirectTo: String? = null,
    ): SupabaseResult<Unit>
    public suspend fun resendPhoneOtp(
        type: OtpType,
        phone: String,
        captchaToken: String? = null,
    ): SupabaseResult<Unit>
    public suspend fun resetPasswordForEmail(
        email: String,
        redirectTo: String? = null,
        captchaToken: String? = null,
    ): SupabaseResult<Unit>
    public suspend fun reauthenticate(accessToken: String): SupabaseResult<Unit>
    public suspend fun refreshToken(refreshToken: String): SupabaseResult<Session>
    public suspend fun getUser(accessToken: String): SupabaseResult<User>
    public suspend fun updateUser(
        accessToken: String,
        updates: UserUpdateRequest,
    ): SupabaseResult<User>
    public suspend fun signOut(accessToken: String): SupabaseResult<Unit>
    public suspend fun signOut(
        accessToken: String,
        scope: SignOutScope,
    ): SupabaseResult<Unit>
    public suspend fun linkIdentity(
        accessToken: String,
        provider: OAuthProvider,
        redirectTo: String? = null,
        scopes: List<String> = emptyList(),
        queryParams: Map<String, String> = emptyMap(),
    ): SupabaseResult<LinkIdentityResponse>
    public suspend fun linkIdentityWithIdToken(
        accessToken: String,
        provider: OAuthProvider,
        idToken: String,
        providerAccessToken: String? = null,
        nonce: String? = null,
    ): SupabaseResult<Session>
    public suspend fun unlinkIdentity(
        accessToken: String,
        identityId: String,
    ): SupabaseResult<Unit>
    public suspend fun retrieveSsoUrl(
        accessToken: String? = null,
        domain: String? = null,
        providerId: String? = null,
        redirectTo: String? = null,
    ): SupabaseResult<SsoResponse>
    public fun getOAuthSignInUrl(
        provider: OAuthProvider,
        redirectTo: String? = null,
        scopes: List<String> = emptyList(),
        queryParams: Map<String, String> = emptyMap(),
        pkceParams: PkceParams? = null,
    ): String
    public fun generatePkceParams(sha256: ((ByteArray) -> ByteArray)? = null): PkceParams
    public suspend fun exchangeCodeForSession(
        authCode: String,
        codeVerifier: String,
    ): SupabaseResult<Session>
    public suspend fun mfaEnroll(
        factorType: MfaFactorType,
        friendlyName: String? = null,
        issuer: String? = null,
        phone: String? = null,
        accessToken: String,
    ): SupabaseResult<MfaEnrollResponse>
    public suspend fun mfaChallenge(
        factorId: String,
        accessToken: String,
    ): SupabaseResult<MfaChallengeResponse>
    public suspend fun mfaVerify(
        factorId: String,
        challengeId: String,
        code: String,
        accessToken: String,
    ): SupabaseResult<MfaVerifyResponse>
    public suspend fun mfaUnenroll(
        factorId: String,
        accessToken: String,
    ): SupabaseResult<MfaUnenrollResponse>
    public suspend fun mfaListFactors(
        accessToken: String,
    ): SupabaseResult<MfaListFactorsResponse>
    public suspend fun mfaGetAuthenticatorAssuranceLevel(
        accessToken: String,
    ): SupabaseResult<AuthenticatorAssuranceLevel>
}
