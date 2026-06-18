package io.github.androidpoet.supabase.auth
import dev.whyoleg.cryptography.random.CryptographyRandom
import io.github.androidpoet.supabase.auth.models.AnonymousSignInRequest
import io.github.androidpoet.supabase.auth.models.AuthenticatorAssuranceLevel
import io.github.androidpoet.supabase.auth.models.ExchangeCodeRequest
import io.github.androidpoet.supabase.auth.models.IdTokenRequest
import io.github.androidpoet.supabase.auth.models.LinkIdentityResponse
import io.github.androidpoet.supabase.auth.models.MfaChallengeResponse
import io.github.androidpoet.supabase.auth.models.MfaEnrollRequest
import io.github.androidpoet.supabase.auth.models.MfaEnrollResponse
import io.github.androidpoet.supabase.auth.models.MfaFactorType
import io.github.androidpoet.supabase.auth.models.MfaListFactorsResponse
import io.github.androidpoet.supabase.auth.models.MfaUnenrollResponse
import io.github.androidpoet.supabase.auth.models.MfaVerifyRequest
import io.github.androidpoet.supabase.auth.models.MfaVerifyResponse
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

internal class AuthClientImpl(
    private val client: SupabaseClient,
) : AuthClient {
    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        data: JsonObject?,
    ): SupabaseResult<Session> {
        if (email.isBlank()) return SupabaseResult.Failure(SupabaseError("email must not be blank"))
        val body = defaultJson.encodeToString(SignUpRequest(email = email, password = password, data = data))
        return client.post(AuthPaths.SIGNUP, body = body).deserialize()
    }

    override suspend fun signUpWithPhone(
        phone: String,
        password: String,
        data: JsonObject?,
    ): SupabaseResult<Session> {
        if (phone.isBlank()) return SupabaseResult.Failure(SupabaseError("phone must not be blank"))
        val body = defaultJson.encodeToString(SignUpRequest(phone = phone, password = password, data = data))
        return client.post(AuthPaths.SIGNUP, body = body).deserialize()
    }

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): SupabaseResult<Session> {
        val body = defaultJson.encodeToString(SignInRequest(email = email, password = password))
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
    ): SupabaseResult<Session> {
        val body = defaultJson.encodeToString(SignInRequest(phone = phone, password = password))
        return client.post("${AuthPaths.TOKEN}?grant_type=password", body = body).deserialize()
    }

    override suspend fun signInWithIdToken(
        provider: OAuthProvider,
        idToken: String,
        accessToken: String?,
        nonce: String?,
        captchaToken: String?,
    ): SupabaseResult<Session> {
        val body =
            defaultJson.encodeToString(
                IdTokenRequest(
                    idToken = idToken,
                    provider = provider.value,
                    accessToken = accessToken,
                    nonce = nonce,
                    captchaToken = captchaToken,
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
    ): SupabaseResult<Unit> {
        val body =
            defaultJson.encodeToString(
                OtpRequest(
                    email = email,
                    phone = phone,
                    createUser = createUser,
                    captchaToken = captchaToken,
                    emailRedirectTo = emailRedirectTo,
                ),
            )
        return client.post(AuthPaths.OTP, body = body).map { }
    }

    override suspend fun verifyOtp(
        email: String?,
        phone: String?,
        token: String,
        type: OtpType,
        captchaToken: String?,
    ): SupabaseResult<Session> {
        val body =
            defaultJson.encodeToString(
                OtpVerifyRequest(
                    email = email,
                    phone = phone,
                    token = token,
                    type = type,
                    captchaToken = captchaToken,
                ),
            )
        return client.post(AuthPaths.VERIFY, body = body).deserialize()
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
        return client.post(AuthPaths.VERIFY, body = body).deserialize()
    }

    override suspend fun verifyOtpWithResult(
        email: String?,
        phone: String?,
        token: String,
        type: OtpType,
        captchaToken: String?,
    ): SupabaseResult<OtpVerifyResult> {
        val body =
            defaultJson.encodeToString(
                OtpVerifyRequest(
                    email = email,
                    phone = phone,
                    token = token,
                    type = type,
                    captchaToken = captchaToken,
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
        val body =
            defaultJson.encodeToString(
                ResendOtpRequest(
                    type = type,
                    email = email,
                    captchaToken = captchaToken,
                    redirectTo = redirectTo,
                ),
            )
        return client.post(AuthPaths.RESEND, body = body).map { }
    }

    override suspend fun resendPhoneOtp(
        type: OtpType,
        phone: String,
        captchaToken: String?,
    ): SupabaseResult<Unit> {
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
    ): SupabaseResult<Unit> {
        val body =
            defaultJson.encodeToString(
                OtpRequest(
                    email = email,
                    createUser = false,
                    captchaToken = captchaToken,
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

    override suspend fun fetchJwks(): SupabaseResult<String> = client.get(endpoint = AuthPaths.JWKS)

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
            .patch(
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

    override suspend fun retrieveSsoUrl(
        accessToken: String?,
        domain: String?,
        providerId: String?,
        redirectTo: String?,
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
        factorType: MfaFactorType,
        friendlyName: String?,
        issuer: String?,
        phone: String?,
        accessToken: String,
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
        factorId: String,
        accessToken: String,
    ): SupabaseResult<MfaChallengeResponse> =
        client
            .post(
                endpoint = "${AuthPaths.FACTORS}/$factorId/challenge",
                headers = bearerHeaders(accessToken),
            ).deserialize()

    override suspend fun mfaVerify(
        factorId: String,
        challengeId: String,
        code: String,
        accessToken: String,
    ): SupabaseResult<MfaVerifyResponse> {
        val body =
            defaultJson.encodeToString(
                MfaVerifyRequest(
                    factorId = factorId,
                    challengeId = challengeId,
                    code = code,
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
        factorId: String,
        accessToken: String,
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
    ): SupabaseResult<AuthenticatorAssuranceLevel> {
        val userResult: SupabaseResult<User> =
            client
                .get(
                    endpoint = AuthPaths.USER,
                    headers = bearerHeaders(accessToken),
                ).deserialize()
        return when (userResult) {
            is SupabaseResult.Success -> {
                val factors = userResult.value.factors.orEmpty()
                val hasVerifiedFactor = factors.any { it.status == "verified" }
                val level =
                    if (hasVerifiedFactor) {
                        AuthenticatorAssuranceLevel.AAL2
                    } else {
                        AuthenticatorAssuranceLevel.AAL1
                    }
                SupabaseResult.Success(level)
            }
            is SupabaseResult.Failure -> SupabaseResult.Failure(userResult.error)
        }
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
    }
}
