package io.github.androidpoet.supabase.auth
import dev.whyoleg.cryptography.random.CryptographyRandom
import io.github.androidpoet.supabase.auth.models.AnonymousSignInRequest
import io.github.androidpoet.supabase.auth.models.AuthHealthStatus
import io.github.androidpoet.supabase.auth.models.AuthSettings
import io.github.androidpoet.supabase.auth.models.AuthenticatorAssuranceLevel
import io.github.androidpoet.supabase.auth.models.AuthenticatorAssuranceLevels
import io.github.androidpoet.supabase.auth.models.ExchangeCodeRequest
import io.github.androidpoet.supabase.auth.models.IdTokenRequest
import io.github.androidpoet.supabase.auth.models.Jwk
import io.github.androidpoet.supabase.auth.models.JwkSet
import io.github.androidpoet.supabase.auth.models.JwtClaims
import io.github.androidpoet.supabase.auth.models.LinkIdentityResponse
import io.github.androidpoet.supabase.auth.models.MessagingChannel
import io.github.androidpoet.supabase.auth.models.MfaChallengeRequest
import io.github.androidpoet.supabase.auth.models.MfaChallengeResponse
import io.github.androidpoet.supabase.auth.models.MfaEnrollRequest
import io.github.androidpoet.supabase.auth.models.MfaEnrollResponse
import io.github.androidpoet.supabase.auth.models.MfaFactorStatus
import io.github.androidpoet.supabase.auth.models.MfaFactorType
import io.github.androidpoet.supabase.auth.models.MfaListFactorsResponse
import io.github.androidpoet.supabase.auth.models.MfaUnenrollResponse
import io.github.androidpoet.supabase.auth.models.MfaVerifyRequest
import io.github.androidpoet.supabase.auth.models.MfaVerifyResponse
import io.github.androidpoet.supabase.auth.models.MfaWebauthnVerification
import io.github.androidpoet.supabase.auth.models.OAuthAuthorizationDetails
import io.github.androidpoet.supabase.auth.models.OAuthConsentRequest
import io.github.androidpoet.supabase.auth.models.OAuthGrant
import io.github.androidpoet.supabase.auth.models.OAuthProvider
import io.github.androidpoet.supabase.auth.models.OAuthRedirect
import io.github.androidpoet.supabase.auth.models.OAuthResponse
import io.github.androidpoet.supabase.auth.models.OtpRequest
import io.github.androidpoet.supabase.auth.models.OtpType
import io.github.androidpoet.supabase.auth.models.OtpVerifyRequest
import io.github.androidpoet.supabase.auth.models.OtpVerifyResult
import io.github.androidpoet.supabase.auth.models.Passkey
import io.github.androidpoet.supabase.auth.models.PasskeyAuthenticationOptionsRequest
import io.github.androidpoet.supabase.auth.models.PasskeyAuthenticationOptionsResponse
import io.github.androidpoet.supabase.auth.models.PasskeyMetadata
import io.github.androidpoet.supabase.auth.models.PasskeyRegistrationOptionsResponse
import io.github.androidpoet.supabase.auth.models.PasskeyUpdateRequest
import io.github.androidpoet.supabase.auth.models.PasskeyVerifyRequest
import io.github.androidpoet.supabase.auth.models.PkceParams
import io.github.androidpoet.supabase.auth.models.RecoverRequest
import io.github.androidpoet.supabase.auth.models.RefreshTokenRequest
import io.github.androidpoet.supabase.auth.models.ResendOtpRequest
import io.github.androidpoet.supabase.auth.models.Session
import io.github.androidpoet.supabase.auth.models.SignInRequest
import io.github.androidpoet.supabase.auth.models.SignOutScope
import io.github.androidpoet.supabase.auth.models.SignUpRequest
import io.github.androidpoet.supabase.auth.models.SsoRequest
import io.github.androidpoet.supabase.auth.models.SsoResponse
import io.github.androidpoet.supabase.auth.models.User
import io.github.androidpoet.supabase.auth.models.UserIdentity
import io.github.androidpoet.supabase.auth.models.UserUpdateRequest
import io.github.androidpoet.supabase.auth.models.Web3Chain
import io.github.androidpoet.supabase.auth.models.Web3SignInRequest
import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.client.defaultJson
import io.github.androidpoet.supabase.client.deserialize
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.core.result.map
import io.github.androidpoet.supabase.core.util.urlEncode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.time.Clock

internal class AuthClientImpl(
    private val client: SupabaseClient,
) : AuthClient {
    // In-memory JWKS cache for local signature verification (see [resolveSigningKey]). The map is the
    // last fetched key set keyed by `kid`; [jwksCacheExpiryEpochMs] gates the TTL. All reads/writes go
    // through [jwksMutex] so a refetch is single-flighted across concurrent verifications — a plain
    // @Volatile would only give visibility, not the compound check-then-fetch atomicity we need.
    private val jwksMutex = Mutex()
    private var jwksCache: Map<String, Jwk> = emptyMap()
    private var jwksCacheExpiryEpochMs: Long = 0L

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        data: JsonObject?,
        emailRedirectTo: String?,
        captchaToken: String?,
        pkceParams: PkceParams?,
    ): SupabaseResult<Session> {
        if (email.isBlank()) return SupabaseResult.Failure(SupabaseError("email must not be blank"))
        val body =
            defaultJson.encodeToString(
                SignUpRequest(
                    email = email,
                    password = password,
                    data = data,
                    captchaToken = captchaToken,
                    codeChallenge = pkceParams?.codeChallenge,
                    codeChallengeMethod = pkceParams?.codeChallengeMethod,
                ),
            )
        return client.post(signUpEndpoint(emailRedirectTo), body = body).deserialize()
    }

    override suspend fun signUpWithPhone(
        phone: String,
        password: String,
        data: JsonObject?,
        redirectTo: String?,
        captchaToken: String?,
        pkceParams: PkceParams?,
        channel: MessagingChannel?,
    ): SupabaseResult<Session> {
        if (phone.isBlank()) return SupabaseResult.Failure(SupabaseError("phone must not be blank"))
        val body =
            defaultJson.encodeToString(
                SignUpRequest(
                    phone = phone,
                    password = password,
                    data = data,
                    captchaToken = captchaToken,
                    codeChallenge = pkceParams?.codeChallenge,
                    codeChallengeMethod = pkceParams?.codeChallengeMethod,
                    channel = channel?.wireValue,
                ),
            )
        return client.post(signUpEndpoint(redirectTo), body = body).deserialize()
    }

    // The signup endpoint takes the post-confirmation redirect as a `redirect_to` query parameter,
    // mirroring how recover and the OAuth flows pass theirs.
    private fun signUpEndpoint(redirectTo: String?): String =
        buildString {
            append(AuthPaths.SIGNUP)
            if (redirectTo != null) {
                append("?redirect_to=")
                append(urlEncode(redirectTo))
            }
        }

    // The OTP endpoint takes the magic-link redirect as a `redirect_to` query parameter, mirroring
    // how signup and the recover/OAuth flows pass theirs — GoTrue ignores it in the body.
    private fun otpEndpoint(emailRedirectTo: String?): String =
        buildString {
            append(AuthPaths.OTP)
            if (emailRedirectTo != null) {
                append("?redirect_to=")
                append(urlEncode(emailRedirectTo))
            }
        }

    override suspend fun signInWithEmail(
        email: String,
        password: String,
        captchaToken: String?,
    ): SupabaseResult<Session> {
        val body =
            defaultJson.encodeToString(
                SignInRequest(email = email, password = password, captchaToken = captchaToken),
            )
        return client.post("${AuthPaths.TOKEN}?grant_type=password", body = body).deserialize()
    }

    override suspend fun signInAnonymously(
        data: JsonObject?,
        captchaToken: String?,
    ): SupabaseResult<Session> {
        val body =
            defaultJson.encodeToString(
                AnonymousSignInRequest(
                    data = data,
                    captchaToken = captchaToken,
                ),
            )
        return client.post(AuthPaths.SIGNUP, body = body).deserialize()
    }

    override suspend fun signInWithPhone(
        phone: String,
        password: String,
        captchaToken: String?,
    ): SupabaseResult<Session> {
        val body =
            defaultJson.encodeToString(
                SignInRequest(phone = phone, password = password, captchaToken = captchaToken),
            )
        return client.post("${AuthPaths.TOKEN}?grant_type=password", body = body).deserialize()
    }

    override suspend fun signInWithIdToken(
        provider: OAuthProvider,
        idToken: String,
        accessToken: String?,
        nonce: String?,
        captchaToken: String?,
        clientId: String?,
        issuer: String?,
    ): SupabaseResult<Session> {
        val body =
            defaultJson.encodeToString(
                IdTokenRequest(
                    idToken = idToken,
                    provider = provider.value,
                    accessToken = accessToken,
                    nonce = nonce,
                    captchaToken = captchaToken,
                    clientId = clientId,
                    issuer = issuer,
                ),
            )
        return client.post("${AuthPaths.TOKEN}?grant_type=id_token", body = body).deserialize()
    }

    override suspend fun signInWithWeb3(
        chain: Web3Chain,
        message: String,
        signature: String,
        captchaToken: String?,
    ): SupabaseResult<Session> {
        val body =
            defaultJson.encodeToString(
                Web3SignInRequest(
                    chain = chain,
                    message = message,
                    signature = signature,
                    captchaToken = captchaToken,
                ),
            )
        return client.post("${AuthPaths.TOKEN}?grant_type=web3", body = body).deserialize()
    }

    override suspend fun signInWithOtp(
        email: String?,
        phone: String?,
        createUser: Boolean?,
        captchaToken: String?,
        emailRedirectTo: String?,
        channel: MessagingChannel?,
        data: JsonObject?,
        pkceParams: PkceParams?,
    ): SupabaseResult<Unit> {
        val body =
            defaultJson.encodeToString(
                OtpRequest(
                    email = email,
                    phone = phone,
                    createUser = createUser,
                    channel = channel?.wireValue,
                    data = data,
                    captchaToken = captchaToken,
                    codeChallenge = pkceParams?.codeChallenge,
                    codeChallengeMethod = pkceParams?.codeChallengeMethod,
                ),
            )
        return client.post(otpEndpoint(emailRedirectTo), body = body).map { }
    }

    override suspend fun verifyOtp(
        email: String?,
        phone: String?,
        token: String,
        type: OtpType,
        captchaToken: String?,
        redirectTo: String?,
    ): SupabaseResult<Session> {
        val body =
            defaultJson.encodeToString(
                OtpVerifyRequest(
                    email = email,
                    phone = phone,
                    token = token,
                    type = type,
                    captchaToken = captchaToken,
                    redirectTo = redirectTo,
                ),
            )
        return verifyOtpSessionFromRawResponse(client.post(AuthPaths.VERIFY, body = body))
    }

    override suspend fun verifyOtpWithTokenHash(
        tokenHash: String,
        type: OtpType,
        captchaToken: String?,
    ): SupabaseResult<Session> {
        val body =
            defaultJson.encodeToString(
                OtpVerifyRequest(
                    tokenHash = tokenHash,
                    type = type,
                    captchaToken = captchaToken,
                ),
            )
        return verifyOtpSessionFromRawResponse(client.post(AuthPaths.VERIFY, body = body))
    }

    override suspend fun verifyOtpWithResult(
        email: String?,
        phone: String?,
        token: String,
        type: OtpType,
        captchaToken: String?,
        redirectTo: String?,
    ): SupabaseResult<OtpVerifyResult> {
        val body =
            defaultJson.encodeToString(
                OtpVerifyRequest(
                    email = email,
                    phone = phone,
                    token = token,
                    type = type,
                    captchaToken = captchaToken,
                    redirectTo = redirectTo,
                ),
            )
        return verifyOtpResultFromRawResponse(client.post(AuthPaths.VERIFY, body = body))
    }

    override suspend fun verifyOtpWithTokenHashWithResult(
        tokenHash: String,
        type: OtpType,
        captchaToken: String?,
    ): SupabaseResult<OtpVerifyResult> {
        val body =
            defaultJson.encodeToString(
                OtpVerifyRequest(
                    tokenHash = tokenHash,
                    type = type,
                    captchaToken = captchaToken,
                ),
            )
        return verifyOtpResultFromRawResponse(client.post(AuthPaths.VERIFY, body = body))
    }

    override suspend fun resendEmailOtp(
        type: OtpType,
        email: String,
        captchaToken: String?,
        redirectTo: String?,
    ): SupabaseResult<Unit> {
        resendTypeGuard(type)?.let { return it }
        val body =
            defaultJson.encodeToString(
                ResendOtpRequest(
                    type = type,
                    email = email,
                    captchaToken = captchaToken,
                ),
            )
        // redirect_to is a query param (read from the request referrer), not a body
        // field — the /resend body has no redirect_to, so sending it there is a no-op.
        val endpoint =
            buildString {
                append(AuthPaths.RESEND)
                if (redirectTo != null) {
                    append("?redirect_to=")
                    append(urlEncode(redirectTo))
                }
            }
        return client.post(endpoint, body = body).map { }
    }

    override suspend fun resendPhoneOtp(
        type: OtpType,
        phone: String,
        captchaToken: String?,
    ): SupabaseResult<Unit> {
        resendTypeGuard(type)?.let { return it }
        val body =
            defaultJson.encodeToString(
                ResendOtpRequest(
                    type = type,
                    phone = phone,
                    captchaToken = captchaToken,
                ),
            )
        return client.post(AuthPaths.RESEND, body = body).map { }
    }

    override suspend fun resetPasswordForEmail(
        email: String,
        redirectTo: String?,
        captchaToken: String?,
        pkceParams: PkceParams?,
    ): SupabaseResult<Unit> {
        val body =
            defaultJson.encodeToString(
                RecoverRequest(
                    email = email,
                    captchaToken = captchaToken,
                    codeChallenge = pkceParams?.codeChallenge,
                    codeChallengeMethod = pkceParams?.codeChallengeMethod,
                ),
            )
        val endpoint =
            buildString {
                append(AuthPaths.RECOVER)
                if (redirectTo != null) {
                    append("?redirect_to=")
                    append(urlEncode(redirectTo))
                }
            }
        return client.post(endpoint = endpoint, body = body).map { }
    }

    override suspend fun reauthenticate(accessToken: String): SupabaseResult<Unit> =
        client
            .get(
                endpoint = AuthPaths.REAUTHENTICATE,
                headers = bearerHeaders(accessToken),
            ).map { }

    override suspend fun refreshToken(refreshToken: String): SupabaseResult<Session> {
        val body = defaultJson.encodeToString(RefreshTokenRequest(refreshToken = refreshToken))
        return client.post("${AuthPaths.TOKEN}?grant_type=refresh_token", body = body).deserialize()
    }

    override suspend fun getUser(accessToken: String): SupabaseResult<User> =
        client
            .get(
                endpoint = AuthPaths.USER,
                headers = bearerHeaders(accessToken),
            ).deserialize()

    override suspend fun getJwks(): SupabaseResult<String> = client.get(endpoint = AuthPaths.JWKS)

    override suspend fun getSettings(): SupabaseResult<AuthSettings> =
        client.get(endpoint = AuthPaths.SETTINGS).deserialize()

    override suspend fun getHealth(): SupabaseResult<AuthHealthStatus> =
        client.get(endpoint = AuthPaths.HEALTH).deserialize()

    override suspend fun resolveSigningKey(kid: String): SupabaseResult<Jwk?> =
        jwksMutex.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            val fresh = now < jwksCacheExpiryEpochMs
            // A fresh cache that already holds the key is an unconditional hit. Any other case (stale
            // cache, or a fresh cache missing this kid — e.g. a rotated key) forces one refetch.
            jwksCache[kid]?.let { if (fresh) return@withLock SupabaseResult.Success(it) }
            val raw =
                when (val result = getJwks()) {
                    is SupabaseResult.Failure -> return@withLock result
                    is SupabaseResult.Success -> result.value
                }
            val parsed =
                try {
                    defaultJson
                        .decodeFromString(JwkSet.serializer(), raw)
                        .keys
                        .mapNotNull { jwk -> jwk.keyId?.let { it to jwk } }
                        .toMap()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    return@withLock SupabaseResult.Failure(
                        SupabaseError(message = "Failed to parse JWKS: ${e.message}"),
                    )
                }
            jwksCache = parsed
            jwksCacheExpiryEpochMs = now + JWKS_CACHE_TTL_MS
            // Cache hit or miss is now settled for this TTL window; an absent kid returns null and
            // won't trigger another refetch until the cache expires (fail closed).
            SupabaseResult.Success(parsed[kid])
        }

    override suspend fun getUserIdentities(accessToken: String): SupabaseResult<List<UserIdentity>> =
        when (val result = getUser(accessToken = accessToken)) {
            is SupabaseResult.Failure -> result
            is SupabaseResult.Success -> SupabaseResult.Success(result.value.identities ?: emptyList())
        }

    override suspend fun updateUser(
        accessToken: String,
        updates: UserUpdateRequest,
    ): SupabaseResult<User> {
        val body = defaultJson.encodeToString(updates)
        return client
            .put(
                endpoint = AuthPaths.USER,
                body = body,
                headers = bearerHeaders(accessToken),
            ).deserialize()
    }

    override suspend fun signOut(accessToken: String): SupabaseResult<Unit> =
        signOut(accessToken = accessToken, scope = SignOutScope.LOCAL)

    override suspend fun signOut(
        accessToken: String,
        scope: SignOutScope,
    ): SupabaseResult<Unit> =
        client
            .post(
                endpoint = "${AuthPaths.LOGOUT}?scope=${scope.name.lowercase()}",
                headers = bearerHeaders(accessToken),
            ).map { }

    override suspend fun linkIdentity(
        accessToken: String,
        provider: OAuthProvider,
        redirectTo: String?,
        scopes: List<String>,
        queryParams: Map<String, String>,
        pkceParams: PkceParams?,
    ): SupabaseResult<LinkIdentityResponse> {
        val endpoint =
            buildString {
                append("${AuthPaths.USER_IDENTITIES_AUTHORIZE}?provider=")
                append(provider.value)
                if (redirectTo != null) {
                    append("&redirect_to=")
                    append(urlEncode(redirectTo))
                }
                if (scopes.isNotEmpty()) {
                    append("&scopes=")
                    append(urlEncode(scopes.joinToString(" ")))
                }
                // Mirror getOAuthSignInUrl: emit the PKCE challenge into the authorize URL.
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
        return client.get(endpoint = endpoint, headers = bearerHeaders(accessToken)).deserialize()
    }

    override suspend fun linkIdentityWithIdToken(
        accessToken: String,
        provider: OAuthProvider,
        idToken: String,
        providerAccessToken: String?,
        nonce: String?,
    ): SupabaseResult<Session> {
        val body =
            defaultJson.encodeToString(
                IdTokenRequest(
                    idToken = idToken,
                    provider = provider.value,
                    accessToken = providerAccessToken,
                    nonce = nonce,
                    linkIdentity = true,
                ),
            )
        return client
            .post(
                endpoint = "${AuthPaths.TOKEN}?grant_type=id_token",
                body = body,
                headers = bearerHeaders(accessToken),
            ).deserialize()
    }

    override suspend fun unlinkIdentity(
        accessToken: String,
        identityId: String,
    ): SupabaseResult<Unit> =
        client
            .delete(
                endpoint = "${AuthPaths.USER_IDENTITIES}/$identityId",
                headers = bearerHeaders(accessToken),
            ).map { }

    override suspend fun getSsoUrl(
        accessToken: String?,
        domain: String?,
        providerId: String?,
        redirectTo: String?,
        pkceParams: PkceParams?,
        captchaToken: String?,
    ): SupabaseResult<SsoResponse> {
        val hasDomain = !domain.isNullOrBlank()
        val hasProviderId = !providerId.isNullOrBlank()
        if (!hasDomain && !hasProviderId) {
            return SupabaseResult.Failure(SupabaseError("domain or providerId must be provided"))
        }
        if (hasDomain && hasProviderId) {
            return SupabaseResult.Failure(SupabaseError("either domain or providerId must be set, not both"))
        }
        val body =
            defaultJson.encodeToString(
                SsoRequest(
                    domain = domain,
                    providerId = providerId,
                    redirectTo = redirectTo,
                    // Default to true so the server returns the JSON {url} body this call decodes
                    // rather than issuing a 303 redirect the SDK can't follow.
                    skipHttpRedirect = true,
                    codeChallenge = pkceParams?.codeChallenge,
                    codeChallengeMethod = pkceParams?.codeChallengeMethod,
                    captchaToken = captchaToken,
                ),
            )
        return client
            .post(
                endpoint = AuthPaths.SSO,
                body = body,
                headers = accessToken?.let(::bearerHeaders).orEmpty(),
            ).deserialize()
    }

    override fun getOAuthSignInUrl(
        provider: OAuthProvider,
        redirectTo: String?,
        scopes: List<String>,
        queryParams: Map<String, String>,
        skipBrowserRedirect: Boolean,
        pkceParams: PkceParams?,
        inviteToken: String?,
    ): String =
        buildString {
            append(client.projectUrl)
            append("${AuthPaths.AUTHORIZE}?provider=")
            append(provider.value)
            if (redirectTo != null) {
                append("&redirect_to=")
                append(urlEncode(redirectTo))
            }
            if (scopes.isNotEmpty()) {
                append("&scopes=")
                append(urlEncode(scopes.joinToString(" ")))
            }
            if (inviteToken != null) {
                append("&invite_token=")
                append(urlEncode(inviteToken))
            }
            if (pkceParams != null) {
                append("&code_challenge=")
                append(urlEncode(pkceParams.codeChallenge))
                append("&code_challenge_method=")
                append(pkceParams.codeChallengeMethod)
            }
            if (skipBrowserRedirect) {
                append("&skip_http_redirect=true")
            }
            for ((key, value) in queryParams) {
                append("&")
                append(urlEncode(key))
                append("=")
                append(urlEncode(value))
            }
        }

    override suspend fun signInWithOAuth(
        provider: OAuthProvider,
        redirectTo: String?,
        scopes: List<String>,
        queryParams: Map<String, String>,
        skipBrowserRedirect: Boolean,
        pkceParams: PkceParams?,
        inviteToken: String?,
    ): SupabaseResult<OAuthResponse> =
        SupabaseResult.Success(
            OAuthResponse(
                provider = provider.value,
                url =
                    getOAuthSignInUrl(
                        provider = provider,
                        redirectTo = redirectTo,
                        scopes = scopes,
                        queryParams = queryParams,
                        skipBrowserRedirect = skipBrowserRedirect,
                        pkceParams = pkceParams,
                        inviteToken = inviteToken,
                    ),
            ),
        )

    override fun generatePkceParams(sha256: ((ByteArray) -> ByteArray)?): PkceParams {
        // PKCE verifiers are security tokens and MUST be drawn from a
        // cryptographically secure RNG (RFC 7636 §7.1), not kotlin.random.Random.
        val verifier =
            buildString(PKCE_VERIFIER_LENGTH) {
                repeat(PKCE_VERIFIER_LENGTH) {
                    append(PKCE_ALPHABET[CryptographyRandom.nextInt(PKCE_ALPHABET.length)])
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

    override fun generateOAuthState(): String =
        // Same CSPRNG and alphabet as the PKCE verifier — a CSRF token has the same secrecy needs.
        buildString(OAUTH_STATE_LENGTH) {
            repeat(OAUTH_STATE_LENGTH) {
                append(PKCE_ALPHABET[CryptographyRandom.nextInt(PKCE_ALPHABET.length)])
            }
        }

    override suspend fun exchangeCodeForSession(
        authCode: String,
        codeVerifier: String,
    ): SupabaseResult<Session> {
        val body =
            defaultJson.encodeToString(
                ExchangeCodeRequest(authCode = authCode, codeVerifier = codeVerifier),
            )
        return client.post("${AuthPaths.TOKEN}?grant_type=pkce", body = body).deserialize()
    }

    override suspend fun mfaEnroll(
        accessToken: String,
        factorType: MfaFactorType,
        friendlyName: String?,
        issuer: String?,
        phone: String?,
    ): SupabaseResult<MfaEnrollResponse> {
        val body =
            defaultJson.encodeToString(
                MfaEnrollRequest(
                    factorType = factorType,
                    friendlyName = friendlyName,
                    issuer = issuer,
                    phone = phone,
                ),
            )
        return client
            .post(
                endpoint = AuthPaths.FACTORS,
                body = body,
                headers = bearerHeaders(accessToken),
            ).deserialize()
    }

    override suspend fun mfaChallenge(
        accessToken: String,
        factorId: String,
        channel: MessagingChannel?,
    ): SupabaseResult<MfaChallengeResponse> =
        client
            .post(
                endpoint = "${AuthPaths.FACTORS}/$factorId/challenge",
                body = channel?.let { defaultJson.encodeToString(MfaChallengeRequest(channel = it.wireValue)) },
                headers = bearerHeaders(accessToken),
            ).deserialize()

    override suspend fun mfaVerify(
        accessToken: String,
        factorId: String,
        challengeId: String,
        code: String,
        webauthn: MfaWebauthnVerification?,
    ): SupabaseResult<MfaVerifyResponse> {
        val body =
            defaultJson.encodeToString(
                MfaVerifyRequest(
                    challengeId = challengeId,
                    code = code,
                    webauthn = webauthn,
                ),
            )
        return client
            .post(
                endpoint = "${AuthPaths.FACTORS}/$factorId/verify",
                body = body,
                headers = bearerHeaders(accessToken),
            ).deserialize()
    }

    override suspend fun mfaUnenroll(
        accessToken: String,
        factorId: String,
    ): SupabaseResult<MfaUnenrollResponse> =
        client
            .delete(
                endpoint = "${AuthPaths.FACTORS}/$factorId",
                headers = bearerHeaders(accessToken),
            ).deserialize()

    override suspend fun mfaListFactors(
        accessToken: String,
    ): SupabaseResult<MfaListFactorsResponse> {
        val userResult: SupabaseResult<User> =
            client
                .get(
                    endpoint = AuthPaths.USER,
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
                        webauthn = factors.filter { it.factorType == MfaFactorType.WEBAUTHN },
                    ),
                )
            }
            is SupabaseResult.Failure -> SupabaseResult.Failure(userResult.error)
        }
    }

    override suspend fun mfaGetAuthenticatorAssuranceLevel(
        accessToken: String,
    ): SupabaseResult<AuthenticatorAssuranceLevel> = currentAuthenticatorAssuranceLevel(accessToken)

    override suspend fun mfaGetAuthenticatorAssuranceLevels(
        accessToken: String,
    ): SupabaseResult<AuthenticatorAssuranceLevels> {
        val current =
            when (val result = currentAuthenticatorAssuranceLevel(accessToken)) {
                is SupabaseResult.Success -> result.value
                is SupabaseResult.Failure -> return SupabaseResult.Failure(result.error)
            }
        val userResult: SupabaseResult<User> =
            client
                .get(
                    endpoint = AuthPaths.USER,
                    headers = bearerHeaders(accessToken),
                ).deserialize()
        return when (userResult) {
            is SupabaseResult.Success -> {
                val hasVerifiedFactor =
                    userResult.value.factors
                        .orEmpty()
                        .any { it.status == MfaFactorStatus.VERIFIED }
                val next =
                    if (hasVerifiedFactor) AuthenticatorAssuranceLevel.AAL2 else AuthenticatorAssuranceLevel.AAL1
                SupabaseResult.Success(AuthenticatorAssuranceLevels(current = current, next = next))
            }
            is SupabaseResult.Failure -> SupabaseResult.Failure(userResult.error)
        }
    }

    // Reads the *current* assurance level from the `aal` claim of the access token rather than
    // inferring it from enrolled factors — a verified factor only means the session *could* reach
    // AAL2, not that it currently holds it.
    private fun currentAuthenticatorAssuranceLevel(
        accessToken: String,
    ): SupabaseResult<AuthenticatorAssuranceLevel> {
        val claims =
            when (val parsed = parseJwtClaims(accessToken)) {
                is SupabaseResult.Success ->
                    try {
                        defaultJson.decodeFromJsonElement(JwtClaims.serializer(), parsed.value)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        return SupabaseResult.Failure(SupabaseError("Failed to decode JWT claims: ${e.message}"))
                    }
                is SupabaseResult.Failure -> return SupabaseResult.Failure(parsed.error)
            }
        val level =
            when (claims.authenticatorAssuranceLevel) {
                "aal2" -> AuthenticatorAssuranceLevel.AAL2
                // GoTrue omits the claim for plain password sessions; treat that as AAL1.
                "aal1", null -> AuthenticatorAssuranceLevel.AAL1
                else ->
                    return SupabaseResult.Failure(
                        SupabaseError("Unknown aal claim: ${claims.authenticatorAssuranceLevel}"),
                    )
            }
        return SupabaseResult.Success(level)
    }

    override suspend fun passkeyStartRegistration(
        accessToken: String,
    ): SupabaseResult<PasskeyRegistrationOptionsResponse> =
        client
            .post(
                endpoint = AuthPaths.PASSKEYS_REG_OPTIONS,
                body = "{}",
                headers = bearerHeaders(accessToken),
            ).deserialize()

    override suspend fun passkeyVerifyRegistration(
        accessToken: String,
        challengeId: String,
        credential: JsonObject,
    ): SupabaseResult<PasskeyMetadata> {
        val body =
            defaultJson.encodeToString(
                PasskeyVerifyRequest(challengeId = challengeId, credential = credential),
            )
        return client
            .post(
                endpoint = AuthPaths.PASSKEYS_REG_VERIFY,
                body = body,
                headers = bearerHeaders(accessToken),
            ).deserialize()
    }

    override suspend fun passkeyStartAuthentication(
        captchaToken: String?,
    ): SupabaseResult<PasskeyAuthenticationOptionsResponse> {
        val body = defaultJson.encodeToString(PasskeyAuthenticationOptionsRequest(captchaToken = captchaToken))
        return client
            .post(
                endpoint = AuthPaths.PASSKEYS_AUTH_OPTIONS,
                body = body,
            ).deserialize()
    }

    override suspend fun passkeyVerifyAuthentication(
        challengeId: String,
        credential: JsonObject,
    ): SupabaseResult<Session> {
        val body =
            defaultJson.encodeToString(
                PasskeyVerifyRequest(challengeId = challengeId, credential = credential),
            )
        return client
            .post(
                endpoint = AuthPaths.PASSKEYS_AUTH_VERIFY,
                body = body,
            ).deserialize()
    }

    override suspend fun passkeyList(
        accessToken: String,
    ): SupabaseResult<List<Passkey>> =
        client
            .get(
                endpoint = AuthPaths.PASSKEYS,
                headers = bearerHeaders(accessToken),
            ).deserialize()

    override suspend fun passkeyUpdate(
        accessToken: String,
        passkeyId: String,
        friendlyName: String,
    ): SupabaseResult<Passkey> {
        val body = defaultJson.encodeToString(PasskeyUpdateRequest(friendlyName = friendlyName))
        return client
            .patch(
                endpoint = "${AuthPaths.PASSKEYS}/$passkeyId",
                body = body,
                headers = bearerHeaders(accessToken),
            ).deserialize()
    }

    override suspend fun passkeyDelete(
        accessToken: String,
        passkeyId: String,
    ): SupabaseResult<Unit> =
        client
            .delete(
                endpoint = "${AuthPaths.PASSKEYS}/$passkeyId",
                headers = bearerHeaders(accessToken),
            ).map { }

    override suspend fun oauthGetAuthorizationDetails(
        accessToken: String,
        authorizationId: String,
    ): SupabaseResult<OAuthAuthorizationDetails> =
        client
            .get(
                endpoint = "${AuthPaths.OAUTH_AUTHORIZATIONS}/$authorizationId",
                headers = bearerHeaders(accessToken),
            ).deserialize()

    override suspend fun oauthApproveAuthorization(
        accessToken: String,
        authorizationId: String,
    ): SupabaseResult<OAuthRedirect> =
        oauthConsent(accessToken = accessToken, authorizationId = authorizationId, action = "approve")

    override suspend fun oauthDenyAuthorization(
        accessToken: String,
        authorizationId: String,
    ): SupabaseResult<OAuthRedirect> =
        oauthConsent(accessToken = accessToken, authorizationId = authorizationId, action = "deny")

    override suspend fun oauthListGrants(
        accessToken: String,
    ): SupabaseResult<List<OAuthGrant>> =
        client
            .get(
                endpoint = AuthPaths.USER_OAUTH_GRANTS,
                headers = bearerHeaders(accessToken),
            ).deserialize()

    override suspend fun oauthRevokeGrant(
        accessToken: String,
        clientId: String,
    ): SupabaseResult<Unit> =
        client
            .delete(
                endpoint = "${AuthPaths.USER_OAUTH_GRANTS}?client_id=${urlEncode(clientId)}",
                headers = bearerHeaders(accessToken),
            ).map { }

    private suspend fun oauthConsent(
        accessToken: String,
        authorizationId: String,
        action: String,
    ): SupabaseResult<OAuthRedirect> {
        val body = defaultJson.encodeToString(OAuthConsentRequest(action = action))
        return client
            .post(
                endpoint = "${AuthPaths.OAUTH_AUTHORIZATIONS}/$authorizationId/consent",
                body = body,
                headers = bearerHeaders(accessToken),
            ).deserialize()
    }

    private fun bearerHeaders(token: String): Map<String, String> =
        mapOf("Authorization" to "Bearer $token")

    // POST /resend only accepts signup / email_change / sms / phone_change. The public methods take the
    // full OtpType (kept for API compatibility), so guard out-of-range types with a clear failure rather
    // than letting the server reject the call with a less specific error. Returns null when [type] is
    // allowed.
    private fun resendTypeGuard(type: OtpType): SupabaseResult<Unit>? =
        when (type) {
            OtpType.SIGNUP, OtpType.EMAIL_CHANGE, OtpType.SMS, OtpType.PHONE_CHANGE -> null
            else ->
                SupabaseResult.Failure(
                    SupabaseError(
                        message =
                            "resend only supports signup, email_change, sms or phone_change; got $type",
                    ),
                )
        }

    // The plain verifyOtp variants promise a Session, but some confirmations (e.g. email_change /
    // phone_change) verify without minting one, so the body has no access_token. Detect that and
    // return a clear failure pointing at verifyOtpWithResult rather than a confusing decode error.
    private fun verifyOtpSessionFromRawResponse(response: SupabaseResult<String>): SupabaseResult<Session> =
        when (response) {
            is SupabaseResult.Failure -> SupabaseResult.Failure(response.error)
            is SupabaseResult.Success -> {
                val element =
                    runCatching { defaultJson.parseToJsonElement(response.value) }.getOrElse {
                        return SupabaseResult.Failure(SupabaseError(message = "Invalid verify response: ${it.message}"))
                    }
                val obj =
                    element as? JsonObject
                        ?: return SupabaseResult.Failure(SupabaseError(message = "Invalid verify response shape"))
                if ("access_token" in obj) {
                    runCatching { defaultJson.decodeFromJsonElement<Session>(obj) }.fold(
                        onSuccess = { SupabaseResult.Success(it) },
                        onFailure = {
                            SupabaseResult.Failure(SupabaseError(message = "Invalid verify session payload: ${it.message}"))
                        },
                    )
                } else {
                    SupabaseResult.Failure(
                        SupabaseError(
                            message = "verification succeeded but produced no session; use verifyOtpWithResult",
                        ),
                    )
                }
            }
        }

    private fun verifyOtpResultFromRawResponse(response: SupabaseResult<String>): SupabaseResult<OtpVerifyResult> =
        when (response) {
            is SupabaseResult.Failure -> SupabaseResult.Failure(response.error)
            is SupabaseResult.Success -> {
                val element =
                    runCatching { defaultJson.parseToJsonElement(response.value) }.getOrElse {
                        return SupabaseResult.Failure(SupabaseError(message = "Invalid verify response: ${it.message}"))
                    }
                val obj =
                    element as? JsonObject
                        ?: return SupabaseResult.Failure(SupabaseError(message = "Invalid verify response shape"))
                if ("access_token" in obj) {
                    runCatching {
                        defaultJson.decodeFromJsonElement<Session>(obj)
                    }.fold(
                        onSuccess = { SupabaseResult.Success(OtpVerifyResult.Authenticated(it)) },
                        onFailure = {
                            SupabaseResult.Failure(SupabaseError(message = "Invalid verify session payload: ${it.message}"))
                        },
                    )
                } else {
                    SupabaseResult.Success(OtpVerifyResult.VerifiedNoSession)
                }
            }
        }

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
        const val PKCE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        const val PKCE_VERIFIER_LENGTH = 43
        const val OAUTH_STATE_LENGTH = 32

        // JWKS rotate rarely; a 10-minute TTL keeps verifications offline while still picking up a
        // rotated key promptly (an unknown kid also forces an immediate refetch — see resolveSigningKey).
        const val JWKS_CACHE_TTL_MS = 10 * 60 * 1000L
    }
}
