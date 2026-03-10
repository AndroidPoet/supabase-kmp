package io.github.androidpoet.supabase.auth.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
public data class Session(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("token_type") val tokenType: String,
    val user: User,
)

@Serializable
public data class User(
    val id: String,
    val email: String? = null,
    val phone: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("app_metadata") val appMetadata: JsonObject? = null,
    @SerialName("user_metadata") val userMetadata: JsonObject? = null,
    val factors: List<MfaFactor>? = null,
)

@Serializable
public data class SignUpRequest(
    val email: String? = null,
    val phone: String? = null,
    val password: String,
    val data: JsonObject? = null,
)

@Serializable
public data class SignInRequest(
    val email: String? = null,
    val phone: String? = null,
    val password: String,
)

@Serializable
public data class OtpRequest(
    val email: String? = null,
    val phone: String? = null,
)

@Serializable
public data class OtpVerifyRequest(
    val email: String? = null,
    val phone: String? = null,
    val token: String,
    val type: OtpType,
)

@Serializable
public enum class OtpType {
    @SerialName("sms") SMS,
    @SerialName("email") EMAIL,
    @SerialName("recovery") RECOVERY,
    @SerialName("invite") INVITE,
    @SerialName("email_change") EMAIL_CHANGE,
    @SerialName("phone_change") PHONE_CHANGE,
}

@Serializable
public data class RefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
public data class UserUpdateRequest(
    val email: String? = null,
    val phone: String? = null,
    val password: String? = null,
    val data: JsonObject? = null,
)

// ── OAuth ────────────────────────────────────────────────────────────

@Serializable
public enum class OAuthProvider(@SerialName("value") public val value: String) {
    @SerialName("google") GOOGLE("google"),
    @SerialName("apple") APPLE("apple"),
    @SerialName("github") GITHUB("github"),
    @SerialName("gitlab") GITLAB("gitlab"),
    @SerialName("bitbucket") BITBUCKET("bitbucket"),
    @SerialName("discord") DISCORD("discord"),
    @SerialName("facebook") FACEBOOK("facebook"),
    @SerialName("twitter") TWITTER("twitter"),
    @SerialName("slack") SLACK("slack"),
    @SerialName("spotify") SPOTIFY("spotify"),
    @SerialName("twitch") TWITCH("twitch"),
    @SerialName("azure") AZURE("azure"),
    @SerialName("keycloak") KEYCLOAK("keycloak"),
    @SerialName("linkedin_oidc") LINKEDIN("linkedin_oidc"),
    @SerialName("notion") NOTION("notion"),
    @SerialName("zoom") ZOOM("zoom"),
    @SerialName("figma") FIGMA("figma"),
}

@Serializable
public data class OAuthResponse(
    @SerialName("url") public val url: String,
    @SerialName("provider") public val provider: String,
)

// ── PKCE ─────────────────────────────────────────────────────────────

@Serializable
public data class PkceParams(
    public val codeVerifier: String,
    public val codeChallenge: String,
    public val codeChallengeMethod: String = "S256",
)

@Serializable
public data class ExchangeCodeRequest(
    @SerialName("auth_code") public val authCode: String,
    @SerialName("code_verifier") public val codeVerifier: String,
)

// ── MFA ──────────────────────────────────────────────────────────────

@Serializable
public enum class MfaFactorType {
    @SerialName("totp") TOTP,
    @SerialName("phone") PHONE,
}

@Serializable
public data class MfaEnrollRequest(
    @SerialName("factor_type") public val factorType: MfaFactorType,
    @SerialName("friendly_name") public val friendlyName: String? = null,
    @SerialName("issuer") public val issuer: String? = null,
    @SerialName("phone") public val phone: String? = null,
)

@Serializable
public data class MfaEnrollResponse(
    @SerialName("id") public val id: String,
    @SerialName("type") public val type: MfaFactorType,
    @SerialName("totp") public val totp: MfaTotpDetails? = null,
    @SerialName("friendly_name") public val friendlyName: String? = null,
    @SerialName("phone") public val phone: String? = null,
)

@Serializable
public data class MfaTotpDetails(
    @SerialName("qr_code") public val qrCode: String,
    @SerialName("secret") public val secret: String,
    @SerialName("uri") public val uri: String,
)

@Serializable
public data class MfaChallengeRequest(
    @SerialName("factor_id") public val factorId: String,
)

@Serializable
public data class MfaChallengeResponse(
    @SerialName("id") public val id: String,
    @SerialName("factor_id") public val factorId: String,
    @SerialName("expires_at") public val expiresAt: Long? = null,
)

@Serializable
public data class MfaVerifyRequest(
    @SerialName("factor_id") public val factorId: String,
    @SerialName("challenge_id") public val challengeId: String,
    @SerialName("code") public val code: String,
)

@Serializable
public data class MfaVerifyResponse(
    @SerialName("access_token") public val accessToken: String,
    @SerialName("refresh_token") public val refreshToken: String,
    @SerialName("token_type") public val tokenType: String,
    @SerialName("expires_in") public val expiresIn: Long,
    @SerialName("user") public val user: User,
)

@Serializable
public data class MfaUnenrollResponse(
    @SerialName("id") public val id: String,
)

@Serializable
public enum class AuthenticatorAssuranceLevel {
    @SerialName("aal1") AAL1,
    @SerialName("aal2") AAL2,
}

@Serializable
public data class MfaListFactorsResponse(
    @SerialName("all") public val all: List<MfaFactor>,
    @SerialName("totp") public val totp: List<MfaFactor>,
    @SerialName("phone") public val phone: List<MfaFactor>,
)

@Serializable
public data class MfaFactor(
    @SerialName("id") public val id: String,
    @SerialName("friendly_name") public val friendlyName: String? = null,
    @SerialName("factor_type") public val factorType: MfaFactorType,
    @SerialName("status") public val status: String,
    @SerialName("created_at") public val createdAt: String? = null,
    @SerialName("updated_at") public val updatedAt: String? = null,
)
