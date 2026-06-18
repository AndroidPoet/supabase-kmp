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
    val identities: List<UserIdentity>? = null,
    val factors: List<MfaFactor>? = null,
)

@Serializable
public data class UserIdentity(
    val id: String,
    @SerialName("identity_id") val identityId: String? = null,
    val provider: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("identity_data") val identityData: JsonObject? = null,
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
public data class IdTokenRequest(
    @SerialName("id_token") val idToken: String,
    val provider: String,
    @SerialName("access_token") val accessToken: String? = null,
    val nonce: String? = null,
    @SerialName("gotrue_meta_security") val gotrueMetaSecurity: GotrueMetaSecurity? = null,
    @SerialName("link_identity") val linkIdentity: Boolean? = null,
) {
    public constructor(
        idToken: String,
        provider: String,
        accessToken: String? = null,
        nonce: String? = null,
        captchaToken: String?,
    ) : this(
        idToken = idToken,
        provider = provider,
        accessToken = accessToken,
        nonce = nonce,
        gotrueMetaSecurity = captchaToken?.let(::GotrueMetaSecurity),
    )
}

@Serializable
public data class AnonymousSignInRequest(
    val data: JsonObject? = null,
    @SerialName("gotrue_meta_security") val gotrueMetaSecurity: GotrueMetaSecurity? = null,
) {
    public constructor(
        data: JsonObject? = null,
        captchaToken: String?,
    ) : this(
        data = data,
        gotrueMetaSecurity = captchaToken?.let(::GotrueMetaSecurity),
    )
}

@Serializable
public data class OtpRequest(
    val email: String? = null,
    val phone: String? = null,
    @SerialName("create_user") val createUser: Boolean? = null,
    // Delivery channel for phone OTP: "sms" (default, server-side) or "whatsapp".
    val channel: String? = null,
    @SerialName("gotrue_meta_security") val gotrueMetaSecurity: GotrueMetaSecurity? = null,
    @SerialName("email_redirect_to") val emailRedirectTo: String? = null,
) {
    public constructor(
        email: String? = null,
        phone: String? = null,
        createUser: Boolean? = null,
        channel: String? = null,
        emailRedirectTo: String? = null,
        captchaToken: String?,
    ) : this(
        email = email,
        phone = phone,
        createUser = createUser,
        channel = channel,
        gotrueMetaSecurity = captchaToken?.let(::GotrueMetaSecurity),
        emailRedirectTo = emailRedirectTo,
    )
}

@Serializable
public data class OtpVerifyRequest(
    val email: String? = null,
    val phone: String? = null,
    val token: String? = null,
    val type: OtpType,
    @SerialName("token_hash") val tokenHash: String? = null,
    @SerialName("gotrue_meta_security") val gotrueMetaSecurity: GotrueMetaSecurity? = null,
) {
    public constructor(
        type: OtpType,
        email: String? = null,
        phone: String? = null,
        token: String? = null,
        tokenHash: String? = null,
        captchaToken: String?,
    ) : this(
        email = email,
        phone = phone,
        token = token,
        type = type,
        tokenHash = tokenHash,
        gotrueMetaSecurity = captchaToken?.let(::GotrueMetaSecurity),
    )
}

public sealed interface OtpVerifyResult {
    public data class Authenticated(
        public val session: Session,
    ) : OtpVerifyResult

    public data object VerifiedNoSession : OtpVerifyResult
}

@Serializable
public data class ResendOtpRequest(
    val type: OtpType,
    val email: String? = null,
    val phone: String? = null,
    @SerialName("gotrue_meta_security") val gotrueMetaSecurity: GotrueMetaSecurity? = null,
    @SerialName("redirect_to") val redirectTo: String? = null,
) {
    public constructor(
        type: OtpType,
        email: String? = null,
        phone: String? = null,
        redirectTo: String? = null,
        captchaToken: String?,
    ) : this(
        type = type,
        email = email,
        phone = phone,
        gotrueMetaSecurity = captchaToken?.let(::GotrueMetaSecurity),
        redirectTo = redirectTo,
    )
}

@Serializable
public enum class OtpType {
    @SerialName("sms")
    SMS,

    @SerialName("email")
    EMAIL,

    @SerialName("recovery")
    RECOVERY,

    @SerialName("invite")
    INVITE,

    @SerialName("email_change")
    EMAIL_CHANGE,

    @SerialName("phone_change")
    PHONE_CHANGE,

    // Token-hash verify types (email-link confirmations); the `email` token-hash
    // type reuses the EMAIL entry above.
    @SerialName("signup")
    SIGNUP,

    @SerialName("magiclink")
    MAGIC_LINK,
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
    @SerialName("current_password") val currentPassword: String? = null,
    val data: JsonObject? = null,
    val nonce: String? = null,
)

@Serializable
public enum class SignOutScope {
    @SerialName("global")
    GLOBAL,

    @SerialName("local")
    LOCAL,

    @SerialName("others")
    OTHERS,
}

@Serializable
public data class LinkIdentityResponse(
    @SerialName("url") public val url: String,
    @SerialName("provider") public val provider: String? = null,
)

@Serializable
public data class SsoRequest(
    @SerialName("domain") public val domain: String? = null,
    @SerialName("provider_id") public val providerId: String? = null,
    @SerialName("redirect_to") public val redirectTo: String? = null,
)

@Serializable
public data class SsoResponse(
    @SerialName("url") public val url: String,
)

@Serializable
public enum class OAuthProvider(
    @SerialName("value") public val value: String,
) {
    @SerialName("google")
    GOOGLE("google"),

    @SerialName("apple")
    APPLE("apple"),

    @SerialName("github")
    GITHUB("github"),

    @SerialName("gitlab")
    GITLAB("gitlab"),

    @SerialName("bitbucket")
    BITBUCKET("bitbucket"),

    @SerialName("discord")
    DISCORD("discord"),

    @SerialName("facebook")
    FACEBOOK("facebook"),

    @SerialName("twitter")
    TWITTER("twitter"),

    @SerialName("slack")
    SLACK("slack"),

    @SerialName("spotify")
    SPOTIFY("spotify"),

    @SerialName("twitch")
    TWITCH("twitch"),

    @SerialName("azure")
    AZURE("azure"),

    @SerialName("keycloak")
    KEYCLOAK("keycloak"),

    @SerialName("linkedin_oidc")
    LINKEDIN("linkedin_oidc"),

    @SerialName("notion")
    NOTION("notion"),

    @SerialName("zoom")
    ZOOM("zoom"),

    @SerialName("figma")
    FIGMA("figma"),
}

@Serializable
public data class OAuthResponse(
    @SerialName("url") public val url: String,
    @SerialName("provider") public val provider: String,
)

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

@Serializable
public enum class MfaFactorType {
    @SerialName("totp")
    TOTP,

    @SerialName("phone")
    PHONE,

    @SerialName("webauthn")
    WEBAUTHN,
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
    @SerialName("aal1")
    AAL1,

    @SerialName("aal2")
    AAL2,
}

@Serializable
public data class MfaListFactorsResponse(
    @SerialName("all") public val all: List<MfaFactor> = emptyList(),
    @SerialName("totp") public val totp: List<MfaFactor> = emptyList(),
    @SerialName("phone") public val phone: List<MfaFactor> = emptyList(),
    @SerialName("webauthn") public val webauthn: List<MfaFactor> = emptyList(),
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

@Serializable
public data class PasskeyRegistrationOptionsResponse(
    @SerialName("challenge_id") public val challengeId: String,
    public val options: JsonObject,
    @SerialName("expires_at") public val expiresAt: Long,
)

@Serializable
public data class PasskeyAuthenticationOptionsResponse(
    @SerialName("challenge_id") public val challengeId: String,
    public val options: JsonObject,
    @SerialName("expires_at") public val expiresAt: Long,
)

@Serializable
public data class PasskeyMetadata(
    public val id: String,
    @SerialName("friendly_name") public val friendlyName: String? = null,
    @SerialName("created_at") public val createdAt: String,
)

@Serializable
public data class Passkey(
    public val id: String,
    @SerialName("friendly_name") public val friendlyName: String? = null,
    @SerialName("created_at") public val createdAt: String,
    @SerialName("last_used_at") public val lastUsedAt: String? = null,
)

@Serializable
public data class OAuthAuthorizationClient(
    public val id: String,
    public val name: String,
    public val uri: String,
    @SerialName("logo_uri") public val logoUri: String,
)

@Serializable
public data class OAuthAuthorizationUser(
    public val id: String,
    public val email: String,
)

@Serializable
public data class OAuthAuthorizationDetails(
    @SerialName("authorization_id") public val authorizationId: String? = null,
    @SerialName("redirect_url") public val redirectUrl: String? = null,
    @SerialName("redirect_uri") public val redirectUri: String? = null,
    public val client: OAuthAuthorizationClient? = null,
    public val user: OAuthAuthorizationUser? = null,
    public val scope: String? = null,
)

@Serializable
public data class OAuthRedirect(
    @SerialName("redirect_url") public val redirectUrl: String,
)

@Serializable
public data class OAuthGrant(
    public val client: OAuthAuthorizationClient,
    public val scopes: List<String>,
    @SerialName("granted_at") public val grantedAt: String,
)

@Serializable
public data class OAuthConsentRequest(
    public val action: String,
)

@Serializable
public enum class Web3Chain {
    @SerialName("ethereum")
    ETHEREUM,

    @SerialName("solana")
    SOLANA,
}

@Serializable
public data class Web3SignInRequest(
    public val chain: Web3Chain,
    public val message: String,
    public val signature: String,
    @SerialName("gotrue_meta_security") public val gotrueMetaSecurity: GotrueMetaSecurity? = null,
) {
    public constructor(
        chain: Web3Chain,
        message: String,
        signature: String,
        captchaToken: String?,
    ) : this(
        chain = chain,
        message = message,
        signature = signature,
        gotrueMetaSecurity = captchaToken?.let(::GotrueMetaSecurity),
    )
}

@Serializable
public data class PasskeyVerifyRequest(
    @SerialName("challenge_id") public val challengeId: String,
    public val credential: JsonObject,
)

@Serializable
public data class PasskeyUpdateRequest(
    @SerialName("friendly_name") public val friendlyName: String,
)

@Serializable
public data class PasskeyAuthenticationOptionsRequest(
    @SerialName("gotrue_meta_security") public val gotrueMetaSecurity: GotrueMetaSecurity? = null,
) {
    public constructor(captchaToken: String?) : this(
        gotrueMetaSecurity = captchaToken?.let(::GotrueMetaSecurity),
    )
}

@Serializable
public data class GotrueMetaSecurity(
    @SerialName("captcha_token") public val captchaToken: String,
)

/** Decoded header of a Supabase JWT. */
@Serializable
public data class JwtHeader(
    @SerialName("alg") public val algorithm: String? = null,
    @SerialName("typ") public val type: String? = null,
    @SerialName("kid") public val keyId: String? = null,
)

/** Strongly-typed view of the standard Supabase JWT claims. Use [JwtClaimsResult.raw] for any non-standard claim. */
@Serializable
public data class JwtClaims(
    @SerialName("sub") public val subject: String? = null,
    @SerialName("email") public val email: String? = null,
    @SerialName("phone") public val phone: String? = null,
    @SerialName("role") public val role: String? = null,
    @SerialName("iss") public val issuer: String? = null,
    @SerialName("exp") public val expiresAt: Long? = null,
    @SerialName("iat") public val issuedAt: Long? = null,
    @SerialName("session_id") public val sessionId: String? = null,
    @SerialName("aal") public val authenticatorAssuranceLevel: String? = null,
    @SerialName("is_anonymous") public val isAnonymous: Boolean? = null,
    @SerialName("app_metadata") public val appMetadata: JsonObject? = null,
    @SerialName("user_metadata") public val userMetadata: JsonObject? = null,
)

/** Result of decoding a JWT: typed [claims], the [header], the raw [signature] segment, and the full [raw] payload. */
public data class JwtClaimsResult(
    public val claims: JwtClaims,
    public val header: JwtHeader,
    public val signature: String,
    public val raw: JsonObject,
)

/**
 * A single JSON Web Key from a project's JWKS endpoint. Only the fields needed to reconstruct a
 * public key for signature verification are modelled; unknown fields are ignored.
 */
@Serializable
public data class Jwk(
    @SerialName("kty") public val keyType: String,
    @SerialName("alg") public val algorithm: String? = null,
    @SerialName("kid") public val keyId: String? = null,
    @SerialName("crv") public val curve: String? = null,
    @SerialName("use") public val use: String? = null,
    @SerialName("x") public val x: String? = null,
    @SerialName("y") public val y: String? = null,
    @SerialName("n") public val modulus: String? = null,
    @SerialName("e") public val exponent: String? = null,
)

/** A JSON Web Key Set, as returned by `/auth/v1/.well-known/jwks.json`. */
@Serializable
public data class JwkSet(
    @SerialName("keys") public val keys: List<Jwk> = emptyList(),
)
