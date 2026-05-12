package io.github.androidpoet.supabase.auth.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
/** Auth session returned by GoTrue sign-in and refresh endpoints. */
public data class Session(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("token_type") val tokenType: String,
    val user: User,
)

@Serializable
/** Supabase user profile from GoTrue. */
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
/** Request body for creating a new account with email or phone. */
public data class SignUpRequest(
    val email: String? = null,
    val phone: String? = null,
    val password: String,
    val data: JsonObject? = null,
)

@Serializable
/** Request body for password sign-in using email or phone. */
public data class SignInRequest(
    val email: String? = null,
    val phone: String? = null,
    val password: String,
)

@Serializable
/** Request body for sending OTP to email or phone. */
public data class OtpRequest(
    val email: String? = null,
    val phone: String? = null,
)

@Serializable
/** Request body for verifying a previously issued OTP token. */
public data class OtpVerifyRequest(
    val email: String? = null,
    val phone: String? = null,
    val token: String,
    val type: OtpType,
)

@Serializable
/** Supported OTP verification contexts in GoTrue. */
public enum class OtpType {
    @SerialName("sms") SMS,
    @SerialName("email") EMAIL,
    @SerialName("recovery") RECOVERY,
    @SerialName("invite") INVITE,
    @SerialName("email_change") EMAIL_CHANGE,
    @SerialName("phone_change") PHONE_CHANGE,
}

@Serializable
/** Request body for exchanging a refresh token for a new session. */
public data class RefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
/** Request body for updating user profile fields. */
public data class UserUpdateRequest(
    val email: String? = null,
    val phone: String? = null,
    val password: String? = null,
    val data: JsonObject? = null,
)

@Serializable
/** Supported OAuth providers for browser-based sign-in. */
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
/** OAuth initiation response containing a provider redirect URL. */
public data class OAuthResponse(
    @SerialName("url") public val url: String,
    @SerialName("provider") public val provider: String,
)

@Serializable
/** PKCE parameters used during OAuth code exchange. */
public data class PkceParams(
    public val codeVerifier: String,
    public val codeChallenge: String,
    public val codeChallengeMethod: String = "S256",
)

@Serializable
/** Request body for exchanging auth code + code verifier for a session. */
public data class ExchangeCodeRequest(
    @SerialName("auth_code") public val authCode: String,
    @SerialName("code_verifier") public val codeVerifier: String,
)

@Serializable
/** Supported MFA factor types. */
public enum class MfaFactorType {
    @SerialName("totp") TOTP,
    @SerialName("phone") PHONE,
}

@Serializable
/** Request body for enrolling a new MFA factor. */
public data class MfaEnrollRequest(
    @SerialName("factor_type") public val factorType: MfaFactorType,
    @SerialName("friendly_name") public val friendlyName: String? = null,
    @SerialName("issuer") public val issuer: String? = null,
    @SerialName("phone") public val phone: String? = null,
)

@Serializable
/** Response payload after creating an MFA factor. */
public data class MfaEnrollResponse(
    @SerialName("id") public val id: String,
    @SerialName("type") public val type: MfaFactorType,
    @SerialName("totp") public val totp: MfaTotpDetails? = null,
    @SerialName("friendly_name") public val friendlyName: String? = null,
    @SerialName("phone") public val phone: String? = null,
)

@Serializable
/** TOTP details returned by factor enrollment. */
public data class MfaTotpDetails(
    @SerialName("qr_code") public val qrCode: String,
    @SerialName("secret") public val secret: String,
    @SerialName("uri") public val uri: String,
)

@Serializable
/** Request body for creating an MFA challenge. */
public data class MfaChallengeRequest(
    @SerialName("factor_id") public val factorId: String,
)

@Serializable
/** Challenge details returned by MFA challenge creation. */
public data class MfaChallengeResponse(
    @SerialName("id") public val id: String,
    @SerialName("factor_id") public val factorId: String,
    @SerialName("expires_at") public val expiresAt: Long? = null,
)

@Serializable
/** Request body for verifying an MFA challenge code. */
public data class MfaVerifyRequest(
    @SerialName("factor_id") public val factorId: String,
    @SerialName("challenge_id") public val challengeId: String,
    @SerialName("code") public val code: String,
)

@Serializable
/** Response payload after successful MFA verification. */
public data class MfaVerifyResponse(
    @SerialName("access_token") public val accessToken: String,
    @SerialName("refresh_token") public val refreshToken: String,
    @SerialName("token_type") public val tokenType: String,
    @SerialName("expires_in") public val expiresIn: Long,
    @SerialName("user") public val user: User,
)

@Serializable
/** Response payload after removing an MFA factor. */
public data class MfaUnenrollResponse(
    @SerialName("id") public val id: String,
)

@Serializable
/** Current authenticator assurance level of the active session. */
public enum class AuthenticatorAssuranceLevel {
    @SerialName("aal1") AAL1,
    @SerialName("aal2") AAL2,
}

@Serializable
/** Grouped MFA factors by type for the current user. */
public data class MfaListFactorsResponse(
    @SerialName("all") public val all: List<MfaFactor>,
    @SerialName("totp") public val totp: List<MfaFactor>,
    @SerialName("phone") public val phone: List<MfaFactor>,
)

@Serializable
/** A single enrolled MFA factor. */
public data class MfaFactor(
    @SerialName("id") public val id: String,
    @SerialName("friendly_name") public val friendlyName: String? = null,
    @SerialName("factor_type") public val factorType: MfaFactorType,
    @SerialName("status") public val status: String,
    @SerialName("created_at") public val createdAt: String? = null,
    @SerialName("updated_at") public val updatedAt: String? = null,
)
