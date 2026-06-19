package io.github.androidpoet.supabase.auth.models
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * An authenticated session: the access/refresh tokens, their lifetime, and the
 * signed-in [user]. Returned by every successful sign-in/up flow and refresh, and
 * the unit a `SessionManager` persists and auto-refreshes.
 */
@Serializable
public data class Session(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("token_type") val tokenType: String = "bearer",
    val user: User,
    /**
     * Access token issued by the third-party provider after an OAuth or native sign-in. Present only
     * for provider flows that return it; use it to call the provider's own APIs on the user's behalf.
     */
    @SerialName("provider_token") val providerToken: String? = null,
    /**
     * Refresh token issued by the third-party provider, when the provider supports refreshing its
     * access token. Null for flows that do not return one.
     */
    @SerialName("provider_refresh_token") val providerRefreshToken: String? = null,
) {
    // Mask the bearer credentials so a session never leaks into logs or crash reports; the
    // generated toString() would print every token verbatim.
    override fun toString(): String =
        "Session(tokenType=$tokenType, expiresIn=$expiresIn, accessToken=***, refreshToken=***, " +
            "providerToken=${if (providerToken == null) "null" else "***"}, " +
            "providerRefreshToken=${if (providerRefreshToken == null) "null" else "***"}, user=$user)"
}

/** A Supabase auth user: stable [id] plus profile, metadata, linked [identities] and MFA [factors]. */
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
    /** True when the user was created via anonymous sign-in and has no confirmed email or phone. */
    @SerialName("is_anonymous") val isAnonymous: Boolean? = null,
    /** The user's role, e.g. `authenticated`. */
    val role: String? = null,
    /** Timestamp at which the user's email was confirmed, or null if never confirmed. */
    @SerialName("email_confirmed_at") val emailConfirmedAt: String? = null,
    /** Timestamp at which the user's phone was confirmed, or null if never confirmed. */
    @SerialName("phone_confirmed_at") val phoneConfirmedAt: String? = null,
    /** Timestamp at which the user was first confirmed (via email or phone), or null if never confirmed. */
    @SerialName("confirmed_at") val confirmedAt: String? = null,
    /** Timestamp of the user's most recent sign-in, or null if they have never signed in. */
    @SerialName("last_sign_in_at") val lastSignInAt: String? = null,
)

/** One third-party identity linked to a [User] (e.g. a Google or GitHub login), with the [provider]'s raw `identity_data`. */
@Serializable
public data class UserIdentity(
    val id: String,
    @SerialName("identity_id") val identityId: String? = null,
    val provider: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("identity_data") val identityData: JsonObject? = null,
)

/** Request body for `POST /signup` (email or phone + password); the captcha constructor nests the token under `gotrue_meta_security`. */
@Serializable
public data class SignUpRequest(
    val email: String? = null,
    val phone: String? = null,
    val password: String,
    val data: JsonObject? = null,
    @SerialName("gotrue_meta_security") val gotrueMetaSecurity: GotrueMetaSecurity? = null,
    @SerialName("code_challenge") val codeChallenge: String? = null,
    @SerialName("code_challenge_method") val codeChallengeMethod: String? = null,
    // Delivery channel for phone signup OTP: "sms" (default, server-side) or "whatsapp".
    @SerialName("channel") val channel: String? = null,
) {
    public constructor(
        password: String,
        email: String? = null,
        phone: String? = null,
        data: JsonObject? = null,
        captchaToken: String?,
        codeChallenge: String? = null,
        codeChallengeMethod: String? = null,
        channel: String? = null,
    ) : this(
        email = email,
        phone = phone,
        password = password,
        data = data,
        gotrueMetaSecurity = captchaToken?.let(::GotrueMetaSecurity),
        codeChallenge = codeChallenge,
        codeChallengeMethod = codeChallengeMethod,
        channel = channel,
    )

    // Mask the password so credentials never leak into logs or crash reports.
    override fun toString(): String =
        "SignUpRequest(email=$email, phone=$phone, password=***, data=$data, " +
            "gotrueMetaSecurity=$gotrueMetaSecurity, channel=$channel)"
}

/** Request body for the password grant (email or phone + password); the captcha constructor nests the token under `gotrue_meta_security`. */
@Serializable
public data class SignInRequest(
    val email: String? = null,
    val phone: String? = null,
    val password: String,
    @SerialName("gotrue_meta_security") val gotrueMetaSecurity: GotrueMetaSecurity? = null,
) {
    public constructor(
        password: String,
        email: String? = null,
        phone: String? = null,
        captchaToken: String?,
    ) : this(
        email = email,
        phone = phone,
        password = password,
        gotrueMetaSecurity = captchaToken?.let(::GotrueMetaSecurity),
    )

    // Mask the password so credentials never leak into logs or crash reports.
    override fun toString(): String =
        "SignInRequest(email=$email, phone=$phone, password=***, gotrueMetaSecurity=$gotrueMetaSecurity)"
}

/**
 * Request body for the `id_token` grant — signing in (or linking) with a provider
 * OIDC ID token. The captcha [constructor] nests a token under
 * `gotrue_meta_security` as GoTrue expects.
 */
@Serializable
public data class IdTokenRequest(
    @SerialName("id_token") val idToken: String,
    val provider: String,
    @SerialName("access_token") val accessToken: String? = null,
    val nonce: String? = null,
    @SerialName("gotrue_meta_security") val gotrueMetaSecurity: GotrueMetaSecurity? = null,
    @SerialName("link_identity") val linkIdentity: Boolean? = null,
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("issuer") val issuer: String? = null,
) {
    public constructor(
        idToken: String,
        provider: String,
        accessToken: String? = null,
        nonce: String? = null,
        captchaToken: String?,
        clientId: String? = null,
        issuer: String? = null,
    ) : this(
        idToken = idToken,
        provider = provider,
        accessToken = accessToken,
        nonce = nonce,
        gotrueMetaSecurity = captchaToken?.let(::GotrueMetaSecurity),
        clientId = clientId,
        issuer = issuer,
    )

    // Mask the ID/access tokens so provider credentials never leak into logs or crash reports.
    override fun toString(): String =
        "IdTokenRequest(idToken=***, provider=$provider, " +
            "accessToken=${if (accessToken == null) "null" else "***"}, nonce=$nonce, " +
            "gotrueMetaSecurity=$gotrueMetaSecurity, linkIdentity=$linkIdentity, " +
            "clientId=$clientId, issuer=$issuer)"
}

/** Request body for anonymous sign-up; the captcha constructor nests the token under `gotrue_meta_security`. */
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

/**
 * Request body for `POST /otp` (passwordless code / magic-link); the captcha constructor nests the
 * token under `gotrue_meta_security`. The magic-link redirect is sent as a `redirect_to` query
 * param, not a body field — GoTrue ignores it in the body.
 */
@Serializable
public data class OtpRequest(
    val email: String? = null,
    val phone: String? = null,
    @SerialName("create_user") val createUser: Boolean? = null,
    // Delivery channel for phone OTP: "sms" (default, server-side) or "whatsapp".
    val channel: String? = null,
    @SerialName("data") val data: JsonObject? = null,
    @SerialName("gotrue_meta_security") val gotrueMetaSecurity: GotrueMetaSecurity? = null,
    @SerialName("code_challenge") val codeChallenge: String? = null,
    @SerialName("code_challenge_method") val codeChallengeMethod: String? = null,
) {
    public constructor(
        email: String? = null,
        phone: String? = null,
        createUser: Boolean? = null,
        channel: String? = null,
        data: JsonObject? = null,
        captchaToken: String?,
        codeChallenge: String? = null,
        codeChallengeMethod: String? = null,
    ) : this(
        email = email,
        phone = phone,
        createUser = createUser,
        channel = channel,
        data = data,
        gotrueMetaSecurity = captchaToken?.let(::GotrueMetaSecurity),
        codeChallenge = codeChallenge,
        codeChallengeMethod = codeChallengeMethod,
    )
}

/**
 * Request body for `POST /recover` (send a password-reset email); the captcha constructor nests the
 * token under `gotrue_meta_security`. The reset redirect is sent as a `redirect_to` query param, not
 * a body field. This endpoint defines only `email` (+ optional captcha), so nothing else is sent.
 */
@Serializable
public data class RecoverRequest(
    val email: String,
    @SerialName("gotrue_meta_security") val gotrueMetaSecurity: GotrueMetaSecurity? = null,
    @SerialName("code_challenge") val codeChallenge: String? = null,
    @SerialName("code_challenge_method") val codeChallengeMethod: String? = null,
) {
    public constructor(
        email: String,
        captchaToken: String?,
        codeChallenge: String? = null,
        codeChallengeMethod: String? = null,
    ) : this(
        email = email,
        gotrueMetaSecurity = captchaToken?.let(::GotrueMetaSecurity),
        codeChallenge = codeChallenge,
        codeChallengeMethod = codeChallengeMethod,
    )
}

/** Request body for `POST /verify` (by `token` or `token_hash`); the captcha constructor nests the token under `gotrue_meta_security`. */
@Serializable
public data class OtpVerifyRequest(
    val email: String? = null,
    val phone: String? = null,
    val token: String? = null,
    val type: OtpType,
    @SerialName("token_hash") val tokenHash: String? = null,
    @SerialName("gotrue_meta_security") val gotrueMetaSecurity: GotrueMetaSecurity? = null,
    @SerialName("redirect_to") val redirectTo: String? = null,
) {
    public constructor(
        type: OtpType,
        email: String? = null,
        phone: String? = null,
        token: String? = null,
        tokenHash: String? = null,
        captchaToken: String?,
        redirectTo: String? = null,
    ) : this(
        email = email,
        phone = phone,
        token = token,
        type = type,
        tokenHash = tokenHash,
        gotrueMetaSecurity = captchaToken?.let(::GotrueMetaSecurity),
        redirectTo = redirectTo,
    )
}

/**
 * Outcome of a result-returning OTP verification: either a new [Session] was
 * minted or the code verified without one (some confirmations, e.g. an email
 * change, don't issue a session). See `AuthClient.verifyOtpWithResult`.
 */
public sealed interface OtpVerifyResult {
    /** Verification succeeded and produced an authenticated [session]. */
    public data class Authenticated(
        public val session: Session,
    ) : OtpVerifyResult

    /** Verification succeeded but did not produce a session. */
    public data object VerifiedNoSession : OtpVerifyResult
}

/** Request body for `POST /resend`; the captcha constructor nests the token under `gotrue_meta_security`. */
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

/** The flow an OTP / email-link belongs to, selecting how GoTrue verifies it. */
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

/** Request body for the `refresh_token` grant. */
@Serializable
public data class RefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String,
) {
    // Mask the refresh token so it never leaks into logs or crash reports.
    override fun toString(): String = "RefreshTokenRequest(refreshToken=***)"
}

/**
 * Fields to change on the authenticated user (`PUT /user`); only non-null fields
 * are applied. A password change may require [currentPassword] or a reauth
 * [nonce]; [data] sets `user_metadata`.
 */
@Serializable
public data class UserUpdateRequest(
    val email: String? = null,
    val phone: String? = null,
    val password: String? = null,
    @SerialName("current_password") val currentPassword: String? = null,
    val data: JsonObject? = null,
    val nonce: String? = null,
    // Delivery channel for a phone-change OTP: "sms" (default, server-side) or "whatsapp".
    @SerialName("channel") val channel: String? = null,
) {
    // Mask the password fields so credentials never leak into logs or crash reports.
    override fun toString(): String =
        "UserUpdateRequest(email=$email, phone=$phone, " +
            "password=${if (password == null) "null" else "***"}, " +
            "currentPassword=${if (currentPassword == null) "null" else "***"}, " +
            "data=$data, nonce=$nonce, channel=$channel)"
}

/** How widely a sign-out revokes sessions: just this one, all of them, or all others. */
@Serializable
public enum class SignOutScope {
    @SerialName("global")
    GLOBAL,

    @SerialName("local")
    LOCAL,

    @SerialName("others")
    OTHERS,
}

/** Response from starting identity linking: the [url] to open for provider consent. */
@Serializable
public data class LinkIdentityResponse(
    @SerialName("url") public val url: String,
    @SerialName("provider") public val provider: String? = null,
)

/**
 * Request body for `POST /sso`; supply exactly one of [domain] or [providerId].
 *
 * Set [skipHttpRedirect] to `true` so the server returns the JSON `{url}` body the SDK decodes
 * instead of issuing a 303 redirect. The captcha constructor nests the token under
 * `gotrue_meta_security`.
 */
@Serializable
public data class SsoRequest(
    @SerialName("domain") public val domain: String? = null,
    @SerialName("provider_id") public val providerId: String? = null,
    @SerialName("redirect_to") public val redirectTo: String? = null,
    @SerialName("skip_http_redirect") public val skipHttpRedirect: Boolean? = null,
    @SerialName("code_challenge") public val codeChallenge: String? = null,
    @SerialName("code_challenge_method") public val codeChallengeMethod: String? = null,
    @SerialName("gotrue_meta_security") public val gotrueMetaSecurity: GotrueMetaSecurity? = null,
) {
    public constructor(
        domain: String? = null,
        providerId: String? = null,
        redirectTo: String? = null,
        skipHttpRedirect: Boolean? = null,
        codeChallenge: String? = null,
        codeChallengeMethod: String? = null,
        captchaToken: String?,
    ) : this(
        domain = domain,
        providerId = providerId,
        redirectTo = redirectTo,
        skipHttpRedirect = skipHttpRedirect,
        codeChallenge = codeChallenge,
        codeChallengeMethod = codeChallengeMethod,
        gotrueMetaSecurity = captchaToken?.let(::GotrueMetaSecurity),
    )
}

/** Response from `POST /sso`: the IdP [url] to open to begin SSO. */
@Serializable
public data class SsoResponse(
    @SerialName("url") public val url: String,
)

/** Supported third-party OAuth providers; [value] is the slug GoTrue expects on the wire. */
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

/** Result of starting an OAuth flow: the authorize [url] to open and the [provider] it targets. */
@Serializable
public data class OAuthResponse(
    @SerialName("url") public val url: String,
    @SerialName("provider") public val provider: String,
)

/**
 * PKCE parameters for an OAuth flow. Persist [codeVerifier] when starting the flow
 * and replay it to `exchangeCodeForSession`; [codeChallenge]/[codeChallengeMethod]
 * go into the authorize URL.
 */
@Serializable
public data class PkceParams(
    public val codeVerifier: String,
    public val codeChallenge: String,
    public val codeChallengeMethod: String = "S256",
)

/** Request body for the `pkce` grant: the authorization code plus its original verifier. */
@Serializable
public data class ExchangeCodeRequest(
    @SerialName("auth_code") public val authCode: String,
    @SerialName("code_verifier") public val codeVerifier: String,
)

/** Kind of MFA factor: authenticator-app TOTP, SMS phone, or WebAuthn. */
@Serializable
public enum class MfaFactorType {
    @SerialName("totp")
    TOTP,

    @SerialName("phone")
    PHONE,

    @SerialName("webauthn")
    WEBAUTHN,

    /** A factor type the server returned that this version does not recognise. */
    @SerialName("unknown")
    UNKNOWN,
}

/** Request body for `POST /factors` (enroll a new MFA factor). */
@Serializable
public data class MfaEnrollRequest(
    @SerialName("factor_type") public val factorType: MfaFactorType,
    @SerialName("friendly_name") public val friendlyName: String? = null,
    @SerialName("issuer") public val issuer: String? = null,
    @SerialName("phone") public val phone: String? = null,
)

/** Result of enrolling a factor: its [id] plus, for TOTP, the [totp] secret/QR to present to the user. */
@Serializable
public data class MfaEnrollResponse(
    @SerialName("id") public val id: String,
    @SerialName("type") public val type: MfaFactorType = MfaFactorType.UNKNOWN,
    @SerialName("totp") public val totp: MfaTotpDetails? = null,
    @SerialName("friendly_name") public val friendlyName: String? = null,
    @SerialName("phone") public val phone: String? = null,
)

/** TOTP enrollment material: the [secret], a provisioning [uri], and a rendered [qrCode] for authenticator apps. */
@Serializable
public data class MfaTotpDetails(
    @SerialName("qr_code") public val qrCode: String,
    @SerialName("secret") public val secret: String,
    @SerialName("uri") public val uri: String,
) {
    // Mask the TOTP secret (and the uri, which embeds it) so enrollment material never lands in logs.
    override fun toString(): String = "MfaTotpDetails(qrCode=$qrCode, secret=***, uri=***)"
}

/** Request body for `POST /factors/{id}/challenge`; carries only the phone delivery [channel] (the factor id is in the path). */
@Serializable
public data class MfaChallengeRequest(
    // Phone factors accept a delivery channel ("sms" or "whatsapp"). The factor id
    // is carried in the URL path, not the body, so the body only needs the channel.
    @SerialName("channel") public val channel: String? = null,
)

/** Result of creating an MFA challenge: the challenge [id] to pass to verify, and when it [expiresAt]. */
@Serializable
public data class MfaChallengeResponse(
    @SerialName("id") public val id: String,
    @SerialName("factor_id") public val factorId: String,
    @SerialName("expires_at") public val expiresAt: Long? = null,
)

/**
 * Request body for `POST /factors/{id}/verify`: the challenge id plus the user-entered [code].
 * For a WebAuthn factor the [webauthn] credential response stands in for [code].
 */
@Serializable
public data class MfaVerifyRequest(
    @SerialName("factor_id") public val factorId: String,
    @SerialName("challenge_id") public val challengeId: String,
    @SerialName("code") public val code: String,
    @SerialName("webauthn") public val webauthn: MfaWebauthnVerification? = null,
)

/**
 * WebAuthn credential payload for a `POST /factors/{id}/verify` of a WebAuthn factor: the operation
 * [type] (`"create"` for registration, `"request"` for authentication) and the device's raw
 * [credentialResponse].
 */
@Serializable
public data class MfaWebauthnVerification(
    @SerialName("type") public val type: String? = null,
    @SerialName("credential_response") public val credentialResponse: JsonObject? = null,
)

/** New AAL2 session minted by a successful MFA verification — same token shape as a [Session]. */
@Serializable
public data class MfaVerifyResponse(
    @SerialName("access_token") public val accessToken: String,
    @SerialName("refresh_token") public val refreshToken: String,
    @SerialName("token_type") public val tokenType: String = "bearer",
    @SerialName("expires_in") public val expiresIn: Long,
    @SerialName("user") public val user: User,
) {
    // Mask the bearer credentials so this MFA session never leaks into logs or crash reports.
    override fun toString(): String =
        "MfaVerifyResponse(tokenType=$tokenType, expiresIn=$expiresIn, accessToken=***, " +
            "refreshToken=***, user=$user)"
}

/** Result of un-enrolling a factor: the [id] of the removed factor. */
@Serializable
public data class MfaUnenrollResponse(
    @SerialName("id") public val id: String,
)

/** Authenticator assurance level of a session: [AAL1] (single factor) or [AAL2] (MFA satisfied). */
@Serializable
public enum class AuthenticatorAssuranceLevel {
    @SerialName("aal1")
    AAL1,

    @SerialName("aal2")
    AAL2,
}

/**
 * The authenticator assurance level of a session, split into the level the session currently holds
 * and the level it could reach.
 *
 * [current] is read from the `aal` claim of the access token, so it reflects what the session has
 * actually proven. [next] is derived from the user's enrolled factors: it is [AuthenticatorAssuranceLevel.AAL2]
 * when at least one verified factor exists (meaning the session can be upgraded by completing an MFA
 * challenge) and [AuthenticatorAssuranceLevel.AAL1] otherwise.
 */
public data class AuthenticatorAssuranceLevels(
    public val current: AuthenticatorAssuranceLevel,
    public val next: AuthenticatorAssuranceLevel,
)

/** The user's enrolled MFA factors, both flat ([all]) and grouped by type for convenience. */
@Serializable
public data class MfaListFactorsResponse(
    @SerialName("all") public val all: List<MfaFactor> = emptyList(),
    @SerialName("totp") public val totp: List<MfaFactor> = emptyList(),
    @SerialName("phone") public val phone: List<MfaFactor> = emptyList(),
    @SerialName("webauthn") public val webauthn: List<MfaFactor> = emptyList(),
)

/** A single enrolled MFA factor: its [id], type, [status], and label. */
@Serializable
public data class MfaFactor(
    @SerialName("id") public val id: String,
    @SerialName("friendly_name") public val friendlyName: String? = null,
    @SerialName("factor_type") public val factorType: MfaFactorType = MfaFactorType.UNKNOWN,
    @SerialName("status") public val status: MfaFactorStatus = MfaFactorStatus.UNKNOWN,
    @SerialName("created_at") public val createdAt: String? = null,
    @SerialName("updated_at") public val updatedAt: String? = null,
)

/** Verification status of an enrolled MFA factor. */
@Serializable
public enum class MfaFactorStatus {
    /** The factor has completed enrollment and can satisfy an MFA challenge. */
    @SerialName("verified")
    VERIFIED,

    /** The factor was enrolled but its verification challenge has not been completed yet. */
    @SerialName("unverified")
    UNVERIFIED,

    /** A status the server returned that this version does not recognise. */
    @SerialName("unknown")
    UNKNOWN,
}

/**
 * Server's response to starting passkey registration: the [challengeId] to echo
 * back on verify and the WebAuthn [options] to hand to the device authenticator.
 */
@Serializable
public data class PasskeyRegistrationOptionsResponse(
    @SerialName("challenge_id") public val challengeId: String,
    public val options: JsonObject,
    @SerialName("expires_at") public val expiresAt: Long,
)

/** Server's response to starting passkey authentication: the [challengeId] and WebAuthn [options] for the authenticator. */
@Serializable
public data class PasskeyAuthenticationOptionsResponse(
    @SerialName("challenge_id") public val challengeId: String,
    public val options: JsonObject,
    @SerialName("expires_at") public val expiresAt: Long,
)

/** Metadata of a passkey just registered (returned by verify-registration). */
@Serializable
public data class PasskeyMetadata(
    public val id: String,
    @SerialName("friendly_name") public val friendlyName: String? = null,
    @SerialName("created_at") public val createdAt: String,
)

/** A passkey already registered to the user, as returned when listing passkeys. */
@Serializable
public data class Passkey(
    public val id: String,
    @SerialName("friendly_name") public val friendlyName: String? = null,
    @SerialName("created_at") public val createdAt: String,
    @SerialName("last_used_at") public val lastUsedAt: String? = null,
)

/** The third-party client in an OAuth authorization request, for rendering a consent screen. */
@Serializable
public data class OAuthAuthorizationClient(
    public val id: String,
    public val name: String,
    public val uri: String,
    @SerialName("logo_uri") public val logoUri: String,
)

/** The resource-owner user shown on an OAuth consent screen. */
@Serializable
public data class OAuthAuthorizationUser(
    public val id: String,
    public val email: String,
)

/** Details of a pending OAuth authorization to display for consent: requesting [client], [user], and requested [scope]. */
@Serializable
public data class OAuthAuthorizationDetails(
    @SerialName("authorization_id") public val authorizationId: String? = null,
    @SerialName("redirect_url") public val redirectUrl: String? = null,
    @SerialName("redirect_uri") public val redirectUri: String? = null,
    public val client: OAuthAuthorizationClient? = null,
    public val user: OAuthAuthorizationUser? = null,
    public val scope: String? = null,
)

/** Where to send a third-party client after an approve/deny consent decision. */
@Serializable
public data class OAuthRedirect(
    @SerialName("redirect_url") public val redirectUrl: String,
)

/** A consent the user previously granted to a third-party OAuth [client], with its [scopes] and grant time. */
@Serializable
public data class OAuthGrant(
    public val client: OAuthAuthorizationClient,
    public val scopes: List<String>,
    @SerialName("granted_at") public val grantedAt: String,
)

/** Request body for an OAuth consent decision; [action] is `"approve"` or `"deny"`. */
@Serializable
public data class OAuthConsentRequest(
    public val action: String,
)

/** Blockchain network for a Web3 wallet sign-in. */
@Serializable
public enum class Web3Chain {
    @SerialName("ethereum")
    ETHEREUM,

    @SerialName("solana")
    SOLANA,
}

/** Request body for the `web3` grant: the signed [message] and its [signature] for the given [chain]. */
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

/** Request body for verifying a passkey registration or authentication: the [challengeId] plus the WebAuthn [credential]. */
@Serializable
public data class PasskeyVerifyRequest(
    @SerialName("challenge_id") public val challengeId: String,
    public val credential: JsonObject,
)

/** Request body for renaming a passkey. */
@Serializable
public data class PasskeyUpdateRequest(
    @SerialName("friendly_name") public val friendlyName: String,
)

/** Request body for starting passkey authentication; the captcha constructor nests the token under `gotrue_meta_security`. */
@Serializable
public data class PasskeyAuthenticationOptionsRequest(
    @SerialName("gotrue_meta_security") public val gotrueMetaSecurity: GotrueMetaSecurity? = null,
) {
    public constructor(captchaToken: String?) : this(
        gotrueMetaSecurity = captchaToken?.let(::GotrueMetaSecurity),
    )
}

/** GoTrue's `gotrue_meta_security` wrapper that carries a captcha token on protected flows. */
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
    @SerialName("nbf") public val notBefore: Long? = null,
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

/**
 * The Auth server's public settings, as returned by `/auth/v1/settings`: which providers and flows
 * are enabled. Every field is nullable so a server that omits a key (or adds one this version does not
 * model) still decodes.
 */
@Serializable
public data class AuthSettings(
    /** Per-provider enablement map (provider slug -> enabled), e.g. `{"google": true, "github": false}`. */
    @SerialName("external") public val external: Map<String, Boolean>? = null,
    @SerialName("disable_signup") public val disableSignup: Boolean? = null,
    @SerialName("mailer_autoconfirm") public val mailerAutoconfirm: Boolean? = null,
    @SerialName("phone_autoconfirm") public val phoneAutoconfirm: Boolean? = null,
    @SerialName("sms_provider") public val smsProvider: String? = null,
    @SerialName("mfa_enabled") public val mfaEnabled: Boolean? = null,
    @SerialName("saml_enabled") public val samlEnabled: Boolean? = null,
)

/** The Auth server's health/version info, as returned by `/auth/v1/health`. */
@Serializable
public data class AuthHealthStatus(
    @SerialName("name") public val name: String? = null,
    @SerialName("version") public val version: String? = null,
    @SerialName("description") public val description: String? = null,
)
