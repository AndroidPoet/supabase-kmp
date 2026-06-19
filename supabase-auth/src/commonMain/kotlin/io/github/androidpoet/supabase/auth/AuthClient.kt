package io.github.androidpoet.supabase.auth
import io.github.androidpoet.supabase.auth.models.AuthHealthStatus
import io.github.androidpoet.supabase.auth.models.AuthSettings
import io.github.androidpoet.supabase.auth.models.AuthenticatorAssuranceLevel
import io.github.androidpoet.supabase.auth.models.AuthenticatorAssuranceLevels
import io.github.androidpoet.supabase.auth.models.Jwk
import io.github.androidpoet.supabase.auth.models.LinkIdentityResponse
import io.github.androidpoet.supabase.auth.models.MfaChallengeResponse
import io.github.androidpoet.supabase.auth.models.MfaEnrollResponse
import io.github.androidpoet.supabase.auth.models.MfaFactorType
import io.github.androidpoet.supabase.auth.models.MfaListFactorsResponse
import io.github.androidpoet.supabase.auth.models.MfaUnenrollResponse
import io.github.androidpoet.supabase.auth.models.MfaVerifyResponse
import io.github.androidpoet.supabase.auth.models.OAuthAuthorizationDetails
import io.github.androidpoet.supabase.auth.models.OAuthGrant
import io.github.androidpoet.supabase.auth.models.OAuthProvider
import io.github.androidpoet.supabase.auth.models.OAuthRedirect
import io.github.androidpoet.supabase.auth.models.OAuthResponse
import io.github.androidpoet.supabase.auth.models.OtpType
import io.github.androidpoet.supabase.auth.models.OtpVerifyResult
import io.github.androidpoet.supabase.auth.models.Passkey
import io.github.androidpoet.supabase.auth.models.PasskeyAuthenticationOptionsResponse
import io.github.androidpoet.supabase.auth.models.PasskeyMetadata
import io.github.androidpoet.supabase.auth.models.PasskeyRegistrationOptionsResponse
import io.github.androidpoet.supabase.auth.models.PkceParams
import io.github.androidpoet.supabase.auth.models.Session
import io.github.androidpoet.supabase.auth.models.SignOutScope
import io.github.androidpoet.supabase.auth.models.SsoResponse
import io.github.androidpoet.supabase.auth.models.User
import io.github.androidpoet.supabase.auth.models.UserIdentity
import io.github.androidpoet.supabase.auth.models.UserUpdateRequest
import io.github.androidpoet.supabase.auth.models.Web3Chain
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.serialization.json.JsonObject

/**
 * Typed client over Supabase's GoTrue auth API — sign-up/in, OTP, OAuth, MFA,
 * identity linking, passkeys and JWT/JWKS helpers.
 *
 * Every call follows the Result-first contract: HTTP and parse errors come back
 * as [SupabaseResult.Failure] rather than being thrown. Methods are *stateless*
 * with respect to the session — they return a [Session] (or operate on an
 * `accessToken` you pass in) but do not persist or refresh it. For storage and
 * auto-refresh, layer a `SessionManager` on top, or use the `…AndSaveSession`
 * extensions in `AuthClientExt`. Captcha-protected flows accept a `captchaToken`
 * that GoTrue expects nested under `gotrue_meta_security`.
 */
public interface AuthClient {
    /**
     * Signs up a new user with email + password against `POST /signup`. When email
     * confirmation is enabled the returned [Session] may have no tokens until the
     * user confirms; otherwise it is fully authenticated.
     *
     * @param data optional `user_metadata` stored on the new user.
     * @param emailRedirectTo URL the confirmation email links back to (sent as the
     *   `redirect_to` query param). Fails if [email] is blank.
     * @param captchaToken captcha response when bot protection is enabled.
     */
    public suspend fun signUpWithEmail(
        email: String,
        password: String,
        data: JsonObject? = null,
        emailRedirectTo: String? = null,
        captchaToken: String? = null,
    ): SupabaseResult<Session>

    /**
     * Signs up a new user with phone + password against `POST /signup`. When phone
     * confirmation (SMS OTP) is enabled the user must verify before the session is
     * usable.
     *
     * @param data optional `user_metadata` stored on the new user.
     * @param redirectTo post-confirmation redirect (`redirect_to` query param).
     *   Fails if [phone] is blank.
     * @param captchaToken captcha response when bot protection is enabled.
     */
    public suspend fun signUpWithPhone(
        phone: String,
        password: String,
        data: JsonObject? = null,
        redirectTo: String? = null,
        captchaToken: String? = null,
    ): SupabaseResult<Session>

    /**
     * Signs in with email + password via the `password` grant (`POST /token?grant_type=password`).
     *
     * @param captchaToken captcha response when bot protection is enabled.
     */
    public suspend fun signInWithEmail(
        email: String,
        password: String,
        captchaToken: String? = null,
    ): SupabaseResult<Session>

    /**
     * Creates and signs in an anonymous user via `POST /signup` with no
     * credentials, yielding a session whose [User.isAnonymous] is true. Later
     * convertible to a permanent account by adding an email/phone identity.
     *
     * @param data optional `user_metadata` for the anonymous user.
     * @param captchaToken captcha response when bot protection is enabled.
     */
    public suspend fun signInAnonymously(
        data: JsonObject? = null,
        captchaToken: String? = null,
    ): SupabaseResult<Session>

    /**
     * Signs in with phone + password via the `password` grant (`POST /token?grant_type=password`).
     *
     * @param captchaToken captcha response when bot protection is enabled.
     */
    public suspend fun signInWithPhone(
        phone: String,
        password: String,
        captchaToken: String? = null,
    ): SupabaseResult<Session>

    /**
     * Signs in with an OpenID Connect ID token from a native provider SDK
     * (Sign in with Apple, Google One Tap, …) via the `id_token` grant. The
     * provider's token is exchanged for a Supabase [Session]; no browser redirect
     * is involved.
     *
     * @param accessToken the provider's access token, when it also issues one.
     * @param nonce the nonce that was bound into the ID token, for replay protection.
     * @param captchaToken captcha response when bot protection is enabled.
     */
    public suspend fun signInWithIdToken(
        provider: OAuthProvider,
        idToken: String,
        accessToken: String? = null,
        nonce: String? = null,
        captchaToken: String? = null,
    ): SupabaseResult<Session>

    /**
     * Begins a browser-redirect OAuth flow by building the provider authorize URL —
     * it does *not* perform a network call, it returns the [OAuthResponse] URL for
     * your app to open. Complete the flow with [exchangeCodeForSession] (PKCE) or by
     * importing the redirect fragment. Equivalent to [getOAuthSignInUrl] wrapped in a
     * result.
     *
     * @param redirectTo where the provider sends the user back after consent.
     * @param scopes provider scopes to request (space-joined on the wire).
     * @param queryParams extra authorize-URL params (e.g. `prompt`, or a CSRF `state`).
     * @param skipBrowserRedirect adds `skip_http_redirect=true` so the endpoint
     *   returns JSON instead of a 302.
     * @param pkceParams PKCE challenge to bind the request; pair with [generatePkceParams].
     */
    public suspend fun signInWithOAuth(
        provider: OAuthProvider,
        redirectTo: String? = null,
        scopes: List<String> = emptyList(),
        queryParams: Map<String, String> = emptyMap(),
        skipBrowserRedirect: Boolean = false,
        pkceParams: PkceParams? = null,
    ): SupabaseResult<OAuthResponse>

    /**
     * Signs in by proving control of a Web3 wallet via the `web3` grant: the user
     * signs [message] with their wallet and the [signature] is verified server-side
     * for the given [chain] (Ethereum/Solana).
     *
     * @param captchaToken captcha response when bot protection is enabled.
     */
    public suspend fun signInWithWeb3(
        chain: Web3Chain,
        message: String,
        signature: String,
        captchaToken: String? = null,
    ): SupabaseResult<Session>

    /**
     * Starts a passwordless OTP / magic-link sign-in by requesting a one-time code
     * be sent to an email or phone (`POST /otp`). Returns [Unit] on dispatch;
     * complete the flow with [verifyOtp]. Exactly one of [email] or [phone] should
     * be set.
     *
     * @param createUser whether to auto-create the user if they don't exist
     *   (server default applies when null).
     * @param captchaToken captcha response when bot protection is enabled.
     * @param emailRedirectTo magic-link redirect for the email channel.
     * @param channel phone delivery channel: `"sms"` (server default) or
     *   `"whatsapp"`; ignored for email.
     * @param data optional `user_metadata` stored when the user is auto-created.
     */
    public suspend fun signInWithOtp(
        email: String? = null,
        phone: String? = null,
        createUser: Boolean? = null,
        captchaToken: String? = null,
        emailRedirectTo: String? = null,
        // Phone OTP delivery channel: "sms" (server default) or "whatsapp". Ignored for email OTP.
        channel: String? = null,
        data: JsonObject? = null,
    ): SupabaseResult<Unit>

    /**
     * Verifies an OTP / magic-link code against `POST /verify`, completing a flow
     * started by [signInWithOtp], a confirmation, or a recovery, and returning the
     * resulting [Session]. Pass the same [email] or [phone] the code was sent to.
     *
     * Note: a code may verify without producing a session (e.g. some email-change
     * confirmations); when you need to distinguish that, prefer
     * [verifyOtpWithResult].
     *
     * @param type which OTP flow the [token] belongs to (signup, recovery, …).
     * @param captchaToken captcha response when bot protection is enabled.
     */
    public suspend fun verifyOtp(
        email: String? = null,
        phone: String? = null,
        token: String,
        type: OtpType,
        captchaToken: String? = null,
    ): SupabaseResult<Session>

    /**
     * Verifies an email-link confirmation by its `token_hash` (the value embedded in
     * a Supabase confirmation/recovery link) rather than a user-entered code, via
     * `POST /verify`. Use this when handling a deep link the user clicked.
     *
     * @param captchaToken captcha response when bot protection is enabled.
     */
    public suspend fun verifyOtpWithTokenHash(
        tokenHash: String,
        type: OtpType,
        captchaToken: String? = null,
    ): SupabaseResult<Session>

    /**
     * Like [verifyOtp] but returns an [OtpVerifyResult] that distinguishes a
     * verification which produced a session ([OtpVerifyResult.Authenticated]) from
     * one that succeeded without one ([OtpVerifyResult.VerifiedNoSession]) — useful
     * for email-change confirmations that don't mint a new session.
     *
     * @param captchaToken captcha response when bot protection is enabled.
     */
    public suspend fun verifyOtpWithResult(
        email: String? = null,
        phone: String? = null,
        token: String,
        type: OtpType,
        captchaToken: String? = null,
    ): SupabaseResult<OtpVerifyResult>

    /** Token-hash counterpart to [verifyOtpWithResult]; see [verifyOtpWithTokenHash]. */
    public suspend fun verifyOtpWithTokenHashWithResult(
        tokenHash: String,
        type: OtpType,
        captchaToken: String? = null,
    ): SupabaseResult<OtpVerifyResult>

    /**
     * Re-sends a pending email OTP / confirmation (`POST /resend`), e.g. after the
     * first code expired.
     *
     * @param type the OTP flow to resend (e.g. signup, email_change).
     * @param captchaToken captcha response when bot protection is enabled.
     * @param redirectTo magic-link redirect for the resent email.
     */
    public suspend fun resendEmailOtp(
        type: OtpType,
        email: String,
        captchaToken: String? = null,
        redirectTo: String? = null,
    ): SupabaseResult<Unit>

    /**
     * Re-sends a pending phone OTP (`POST /resend`).
     *
     * @param type the OTP flow to resend (e.g. sms, phone_change).
     * @param captchaToken captcha response when bot protection is enabled.
     */
    public suspend fun resendPhoneOtp(
        type: OtpType,
        phone: String,
        captchaToken: String? = null,
    ): SupabaseResult<Unit>

    /**
     * Sends a password-reset email (`POST /recover`); the link lets the user set a
     * new password. Returns [Unit] on dispatch.
     *
     * @param redirectTo where the recovery link returns the user (`redirect_to` query param).
     * @param captchaToken captcha response when bot protection is enabled.
     */
    public suspend fun resetPasswordForEmail(
        email: String,
        redirectTo: String? = null,
        captchaToken: String? = null,
    ): SupabaseResult<Unit>

    /**
     * Requests a reauthentication nonce for the user identified by [accessToken]
     * (`GET /reauthenticate`). Sends a one-time code the user must supply when
     * performing a sensitive change such as a password update without the current
     * password.
     */
    public suspend fun reauthenticate(accessToken: String): SupabaseResult<Unit>

    /**
     * Exchanges a [refreshToken] for a fresh [Session] via the `refresh_token` grant
     * (`POST /token?grant_type=refresh_token`). The returned session carries new
     * access and refresh tokens.
     */
    public suspend fun refreshToken(refreshToken: String): SupabaseResult<Session>

    /**
     * Fetches the user the [accessToken] belongs to (`GET /user`). Because this hits
     * the Auth server, it also validates the token; for purely local claim checks
     * without a round-trip see [getClaims].
     */
    public suspend fun getUser(accessToken: String): SupabaseResult<User>

    /**
     * Fetches the raw JSON Web Key Set (JWKS) from `/auth/v1/.well-known/jwks.json`. Used by
     * [getClaims] to verify asymmetric token signatures locally without an Auth server round-trip.
     */
    public suspend fun fetchJwks(): SupabaseResult<String>

    /**
     * Fetches the Auth server's public settings (`GET /auth/v1/settings`) — which providers and flows
     * are enabled. Requires only the project `apikey`; no user authentication is needed.
     */
    public suspend fun getSettings(): SupabaseResult<AuthSettings>

    /**
     * Fetches the Auth server's health status (`GET /auth/v1/health`), reporting its name and version.
     * Requires only the project `apikey`; no user authentication is needed.
     */
    public suspend fun getHealth(): SupabaseResult<AuthHealthStatus>

    /**
     * Resolves the signing key for [kid] from the project JWKS, backed by an in-memory cache so
     * repeated local verifications don't re-fetch on every call. A cache miss (unknown [kid]) forces
     * exactly one refetch — to pick up a rotated key — before giving up, then the result (hit or miss)
     * is cached until the TTL expires. Returns the key, `null` if it isn't present even after a forced
     * refetch, or a [SupabaseResult.Failure] only when the JWKS fetch itself failed. Concurrent callers
     * share a single in-flight refetch.
     */
    public suspend fun resolveSigningKey(kid: String): SupabaseResult<Jwk?>

    /** Returns the linked third-party identities of the [accessToken]'s user — the `identities` of [getUser]. */
    public suspend fun getUserIdentities(accessToken: String): SupabaseResult<List<UserIdentity>>

    /**
     * Updates the authenticated user (`PUT /user`) with the non-null fields of
     * [updates] — email, phone, password, or `user_metadata`. Changing email/phone
     * may require confirmation; a password change may require
     * `currentPassword`/`nonce` (see [reauthenticate]).
     */
    public suspend fun updateUser(
        accessToken: String,
        updates: UserUpdateRequest,
    ): SupabaseResult<User>

    /** Signs out the session for [accessToken] (`POST /logout`) with [SignOutScope.LOCAL] scope. */
    public suspend fun signOut(accessToken: String): SupabaseResult<Unit>

    /**
     * Signs out via `POST /logout?scope=…`, revoking refresh tokens for the chosen
     * [scope]: just this session ([SignOutScope.LOCAL]), every session
     * ([SignOutScope.GLOBAL]), or all *other* sessions ([SignOutScope.OTHERS]).
     */
    public suspend fun signOut(
        accessToken: String,
        scope: SignOutScope,
    ): SupabaseResult<Unit>

    /**
     * Starts linking an additional OAuth identity to the signed-in user, returning a
     * [LinkIdentityResponse] whose `url` your app opens to complete provider consent
     * (`GET /user/identities/authorize`). The new identity attaches to the existing
     * account rather than creating a new user.
     *
     * @param redirectTo where the provider returns the user after consent.
     * @param scopes provider scopes to request.
     * @param queryParams extra authorize-URL params.
     */
    public suspend fun linkIdentity(
        accessToken: String,
        provider: OAuthProvider,
        redirectTo: String? = null,
        scopes: List<String> = emptyList(),
        queryParams: Map<String, String> = emptyMap(),
    ): SupabaseResult<LinkIdentityResponse>

    /**
     * Links an OAuth identity using a provider ID token (the native-SDK counterpart
     * to [linkIdentity]) via the `id_token` grant with `link_identity` set, returning
     * the updated [Session]. No browser redirect is involved.
     *
     * @param providerAccessToken the provider's access token, when it issues one.
     * @param nonce the nonce bound into the ID token.
     */
    public suspend fun linkIdentityWithIdToken(
        accessToken: String,
        provider: OAuthProvider,
        idToken: String,
        providerAccessToken: String? = null,
        nonce: String? = null,
    ): SupabaseResult<Session>

    /** Removes a linked identity by its [identityId] from the signed-in user (`DELETE /user/identities/{id}`). */
    public suspend fun unlinkIdentity(
        accessToken: String,
        identityId: String,
    ): SupabaseResult<Unit>

    /**
     * Resolves the SSO sign-in URL for an enterprise provider (`POST /sso`),
     * identified by exactly one of [domain] or [providerId]; the returned
     * [SsoResponse] `url` is opened to start the IdP flow. Fails if neither or both
     * identifiers are supplied.
     *
     * @param accessToken optional bearer token (some SSO setups require an
     *   authenticated caller).
     * @param redirectTo where the IdP returns the user after authentication.
     */
    public suspend fun retrieveSsoUrl(
        accessToken: String? = null,
        domain: String? = null,
        providerId: String? = null,
        redirectTo: String? = null,
    ): SupabaseResult<SsoResponse>

    /**
     * Builds the absolute provider authorize URL (`GET /authorize?provider=…`)
     * locally, without a network call — the synchronous core behind
     * [signInWithOAuth]. Open the returned URL in a browser/custom tab to start the
     * flow.
     *
     * @param redirectTo where the provider returns the user after consent.
     * @param scopes provider scopes (space-joined on the wire).
     * @param queryParams extra authorize-URL params (e.g. a CSRF `state`).
     * @param skipBrowserRedirect adds `skip_http_redirect=true`.
     * @param pkceParams PKCE challenge to bind into the URL.
     */
    public fun getOAuthSignInUrl(
        provider: OAuthProvider,
        redirectTo: String? = null,
        scopes: List<String> = emptyList(),
        queryParams: Map<String, String> = emptyMap(),
        skipBrowserRedirect: Boolean = false,
        pkceParams: PkceParams? = null,
    ): String

    /**
     * Generates a PKCE [PkceParams] (verifier + challenge) for an OAuth flow, drawing
     * the verifier from a cryptographically secure RNG per RFC 7636. Persist the
     * `codeVerifier` and pass it to [exchangeCodeForSession] on callback.
     *
     * @param sha256 a SHA-256 implementation to derive an `S256` challenge; when
     *   `null`, falls back to a `plain` challenge (verifier used verbatim).
     */
    public fun generatePkceParams(sha256: ((ByteArray) -> ByteArray)? = null): PkceParams

    /**
     * Generates a cryptographically-random opaque `state` value for CSRF protection on the OAuth
     * implicit/redirect flow. Pass it to [getOAuthSignInUrl] via `queryParams = mapOf("state" to ...)`,
     * persist it the same way you persist a PKCE [PkceParams.codeVerifier], and on callback compare it
     * to the returned `state` with [verifyOAuthState].
     *
     * Note: the PKCE flow already binds the request via `code_verifier`, so `state` is additive there;
     * it matters most for the implicit flow, which has no verifier.
     */
    public fun generateOAuthState(): String

    /**
     * Completes the PKCE flow by exchanging the authorization [authCode] for a
     * [Session] via the `pkce` grant (`POST /token?grant_type=pkce`). [codeVerifier]
     * must be the `codeVerifier` from the [generatePkceParams] used to start the flow.
     */
    public suspend fun exchangeCodeForSession(
        authCode: String,
        codeVerifier: String,
    ): SupabaseResult<Session>

    /**
     * Enrols a new MFA factor (`POST /factors`), returning a [MfaEnrollResponse]. For
     * a TOTP factor the response carries the QR code / secret to display; the factor
     * starts unverified and must be confirmed via [mfaChallenge] + [mfaVerify].
     *
     * @param factorType the factor kind (TOTP, phone, WebAuthn).
     * @param friendlyName human-readable label for the factor.
     * @param issuer issuer shown in authenticator apps (TOTP).
     * @param phone destination number for a phone factor.
     */
    public suspend fun mfaEnroll(
        factorType: MfaFactorType,
        friendlyName: String? = null,
        issuer: String? = null,
        phone: String? = null,
        accessToken: String,
    ): SupabaseResult<MfaEnrollResponse>

    /**
     * Creates a challenge for an enrolled factor (`POST /factors/{id}/challenge`),
     * returning a [MfaChallengeResponse] whose `id` is passed to [mfaVerify]. For a
     * phone factor this dispatches the code.
     *
     * @param channel phone delivery channel (`"sms"`/`"whatsapp"`); ignored for TOTP.
     */
    public suspend fun mfaChallenge(
        factorId: String,
        accessToken: String,
        channel: String? = null,
    ): SupabaseResult<MfaChallengeResponse>

    /**
     * Verifies a challenge by submitting the user's [code]
     * (`POST /factors/{id}/verify`). On success the [MfaVerifyResponse] carries a new
     * AAL2 session that upgrades the caller's assurance level.
     *
     * @param challengeId the `id` returned by [mfaChallenge].
     */
    public suspend fun mfaVerify(
        factorId: String,
        challengeId: String,
        code: String,
        accessToken: String,
    ): SupabaseResult<MfaVerifyResponse>

    /** Removes an enrolled factor by [factorId] (`DELETE /factors/{id}`). */
    public suspend fun mfaUnenroll(
        factorId: String,
        accessToken: String,
    ): SupabaseResult<MfaUnenrollResponse>

    /**
     * Lists the user's enrolled MFA factors, grouped by type. Derived from the
     * `factors` of [getUser], so it reflects both verified and unverified factors.
     */
    public suspend fun mfaListFactors(
        accessToken: String,
    ): SupabaseResult<MfaListFactorsResponse>

    /**
     * Returns the *current* authenticator assurance level of the session, read from the `aal` claim
     * of [accessToken]. This reflects what the session has actually proven, not what it could reach;
     * use [mfaGetAuthenticatorAssuranceLevels] when you also need the *next* level derived from the
     * user's enrolled factors.
     */
    public suspend fun mfaGetAuthenticatorAssuranceLevel(
        accessToken: String,
    ): SupabaseResult<AuthenticatorAssuranceLevel>

    /**
     * Returns both the *current* assurance level (from the `aal` claim of [accessToken]) and the
     * *next* level the session could reach (derived from the user's enrolled factors).
     */
    public suspend fun mfaGetAuthenticatorAssuranceLevels(
        accessToken: String,
    ): SupabaseResult<AuthenticatorAssuranceLevels>

    /**
     * Requests WebAuthn registration options for the signed-in user — step one of
     * adding a passkey. The [PasskeyRegistrationOptionsResponse] carries the
     * `challengeId` and the WebAuthn `options` to hand to the device authenticator;
     * the produced credential is sent back via [passkeyVerifyRegistration]. The
     * passkey module's `registerPasskey` extension drives all three steps for you.
     */
    public suspend fun passkeyStartRegistration(
        accessToken: String,
    ): SupabaseResult<PasskeyRegistrationOptionsResponse>

    /**
     * Verifies a freshly created passkey [credential] against the [challengeId] from
     * [passkeyStartRegistration], persisting the passkey and returning its
     * [PasskeyMetadata].
     */
    public suspend fun passkeyVerifyRegistration(
        accessToken: String,
        challengeId: String,
        credential: JsonObject,
    ): SupabaseResult<PasskeyMetadata>

    /**
     * Requests WebAuthn authentication options — step one of passwordless passkey
     * sign-in (no access token needed). The [PasskeyAuthenticationOptionsResponse]
     * carries the `challengeId` and `options` for the device authenticator; the
     * resulting assertion is verified via [passkeyVerifyAuthentication].
     *
     * @param captchaToken captcha response when bot protection is enabled.
     */
    public suspend fun passkeyStartAuthentication(
        captchaToken: String? = null,
    ): SupabaseResult<PasskeyAuthenticationOptionsResponse>

    /**
     * Verifies a passkey assertion [credential] against the [challengeId] from
     * [passkeyStartAuthentication], returning the authenticated [Session].
     */
    public suspend fun passkeyVerifyAuthentication(
        challengeId: String,
        credential: JsonObject,
    ): SupabaseResult<Session>

    /** Lists the signed-in user's registered passkeys. */
    public suspend fun passkeyList(
        accessToken: String,
    ): SupabaseResult<List<Passkey>>

    /** Renames a registered passkey by [passkeyId], returning the updated [Passkey]. */
    public suspend fun passkeyUpdate(
        accessToken: String,
        passkeyId: String,
        friendlyName: String,
    ): SupabaseResult<Passkey>

    /** Deletes a registered passkey by [passkeyId]. */
    public suspend fun passkeyDelete(
        accessToken: String,
        passkeyId: String,
    ): SupabaseResult<Unit>

    /**
     * Fetches the details of a pending OAuth authorization request by
     * [authorizationId] — used when this project acts as an OAuth *provider* and must
     * render a consent screen for a third-party client. Returns the requesting
     * client, user and scopes as [OAuthAuthorizationDetails].
     */
    public suspend fun oauthGetAuthorizationDetails(
        accessToken: String,
        authorizationId: String,
    ): SupabaseResult<OAuthAuthorizationDetails>

    /**
     * Approves a pending OAuth authorization (grants consent), returning the
     * [OAuthRedirect] URL to send the third-party client back to. Counterpart to
     * [oauthDenyAuthorization].
     */
    public suspend fun oauthApproveAuthorization(
        accessToken: String,
        authorizationId: String,
    ): SupabaseResult<OAuthRedirect>

    /** Denies a pending OAuth authorization, returning the [OAuthRedirect] back to the client. */
    public suspend fun oauthDenyAuthorization(
        accessToken: String,
        authorizationId: String,
    ): SupabaseResult<OAuthRedirect>

    /** Lists the OAuth grants (consents) the user has previously given to third-party clients. */
    public suspend fun oauthListGrants(
        accessToken: String,
    ): SupabaseResult<List<OAuthGrant>>

    /** Revokes a previously granted OAuth consent for the client identified by [clientId]. */
    public suspend fun oauthRevokeGrant(
        accessToken: String,
        clientId: String,
    ): SupabaseResult<Unit>
}
