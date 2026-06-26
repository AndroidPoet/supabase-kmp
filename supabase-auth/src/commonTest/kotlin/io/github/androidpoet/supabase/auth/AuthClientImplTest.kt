package io.github.androidpoet.supabase.auth

import io.github.androidpoet.supabase.auth.models.AuthenticatorAssuranceLevel
import io.github.androidpoet.supabase.auth.models.MessagingChannel
import io.github.androidpoet.supabase.auth.models.OAuthProvider
import io.github.androidpoet.supabase.auth.models.OtpVerifyResult
import io.github.androidpoet.supabase.auth.models.PkceParams
import io.github.androidpoet.supabase.auth.models.SignOutScope
import io.github.androidpoet.supabase.auth.models.UserIdentity
import io.github.androidpoet.supabase.auth.models.Web3Chain
import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthClientImplTest {
    @Test
    fun test_mfaChallenge_withChannel_sendsChannelBody() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val result = sut.mfaChallenge(factorId = "factor-1", accessToken = "tok", channel = MessagingChannel.WHATSAPP)

            assertTrue(result is SupabaseResult.Success)
            assertEquals("/auth/v1/factors/factor-1/challenge", client.lastPostEndpoint)
            assertTrue(client.lastPostBody?.contains("\"channel\":\"whatsapp\"") == true)
        }

    @Test
    fun test_mfaChallenge_withoutChannel_sendsNoBody() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            sut.mfaChallenge(factorId = "factor-1", accessToken = "tok")

            // No channel → no request body (the server defaults the delivery method).
            assertEquals(null, client.lastPostBody)
        }

    @Test
    fun test_signInWithIdToken_usesIdTokenGrantPayload() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            sut.signInWithIdToken(
                provider = OAuthProvider.GOOGLE,
                idToken = "id-token-1",
                accessToken = "provider-access",
                nonce = "nonce-1",
                captchaToken = "captcha-1",
            )

            assertEquals("/auth/v1/token?grant_type=id_token", client.lastPostEndpoint)
            assertTrue(client.lastPostBody?.contains("\"id_token\":\"id-token-1\"") == true)
            assertTrue(client.lastPostBody?.contains("\"provider\":\"google\"") == true)
            assertTrue(client.lastPostBody?.contains("\"access_token\":\"provider-access\"") == true)
            assertTrue(client.lastPostBody?.contains("\"nonce\":\"nonce-1\"") == true)
            // Captcha must be nested under gotrue_meta_security, not sent top-level.
            assertTrue(client.lastPostBody?.contains("\"gotrue_meta_security\":{\"captcha_token\":\"captcha-1\"}") == true)
        }

    @Test
    fun test_signInWithWeb3_usesWeb3GrantPayload() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val result =
                sut.signInWithWeb3(
                    chain = Web3Chain.ETHEREUM,
                    message = "example siwe message",
                    signature = "0xsignature",
                    captchaToken = "captcha-web3",
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("/auth/v1/token?grant_type=web3", client.lastPostEndpoint)
            assertTrue(client.lastPostBody?.contains("\"chain\":\"ethereum\"") == true)
            assertTrue(client.lastPostBody?.contains("\"message\":\"example siwe message\"") == true)
            assertTrue(client.lastPostBody?.contains("\"signature\":\"0xsignature\"") == true)
            assertTrue(client.lastPostBody?.contains("\"captcha_token\":\"captcha-web3\"") == true)
            assertEquals("web3-acc", result.value.accessToken)
        }

    @Test
    fun test_signInWithOAuth_returnsProviderUrlWithOptions() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val result =
                sut.signInWithOAuth(
                    provider = OAuthProvider.GITHUB,
                    redirectTo = "myapp://callback",
                    scopes = listOf("repo", "user"),
                    queryParams = mapOf("prompt" to "consent"),
                    skipBrowserRedirect = true,
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("github", result.value.provider)
            assertTrue(result.value.url.startsWith("https://example.supabase.co/auth/v1/authorize?provider=github"))
            assertTrue(result.value.url.contains("redirect_to=myapp%3A%2F%2Fcallback"))
            assertTrue(result.value.url.contains("scopes=repo%20user"))
            assertTrue(result.value.url.contains("skip_http_redirect=true"))
            assertTrue(result.value.url.contains("prompt=consent"))
        }

    @Test
    fun test_signInAnonymously_usesDataAndCaptchaPayload() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            sut.signInAnonymously(
                data = buildJsonObject { put("role", "guest") },
                captchaToken = "captcha-anon",
            )

            assertEquals("/auth/v1/signup", client.lastPostEndpoint)
            assertTrue(client.lastPostBody?.contains("\"data\":{\"role\":\"guest\"}") == true)
            assertTrue(client.lastPostBody?.contains("\"captcha_token\":\"captcha-anon\"") == true)
        }

    @Test
    fun test_verifyOtpWithTokenHash_usesVerifyPayload() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            sut.verifyOtpWithTokenHash(
                tokenHash = "hash-123",
                type = io.github.androidpoet.supabase.auth.models.OtpType.EMAIL,
                captchaToken = "captcha-1",
            )

            assertEquals("/auth/v1/verify", client.lastPostEndpoint)
            assertTrue(client.lastPostBody?.contains("\"token_hash\":\"hash-123\"") == true)
            assertTrue(client.lastPostBody?.contains("\"captcha_token\":\"captcha-1\"") == true)
        }

    @Test
    fun test_signInWithOtp_withOptions_usesOtpPayloadFields() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            sut.signInWithOtp(
                phone = "+15555550100",
                createUser = false,
                captchaToken = "captcha-otp",
                channel = MessagingChannel.WHATSAPP,
            )

            assertEquals("/auth/v1/otp", client.lastPostEndpoint)
            assertTrue(client.lastPostBody?.contains("\"phone\":\"+15555550100\"") == true)
            assertTrue(client.lastPostBody?.contains("\"create_user\":false") == true)
            // WhatsApp delivery channel must reach the server.
            assertTrue(client.lastPostBody?.contains("\"channel\":\"whatsapp\"") == true)
            // GoTrue only reads the captcha token nested under gotrue_meta_security;
            // a top-level captcha_token is silently ignored. Guard against regressing.
            assertTrue(client.lastPostBody?.contains("\"gotrue_meta_security\":{\"captcha_token\":\"captcha-otp\"}") == true)
        }

    @Test
    fun test_verifyOtp_returnsVerifiedNoSessionWhenNoAccessToken() =
        runTest {
            val client = FakeSupabaseClient()
            client.verifyResponse = "{}"
            val sut = AuthClientImpl(client)

            val result =
                sut.verifyOtp(
                    email = "a@b.com",
                    token = "123456",
                    type = io.github.androidpoet.supabase.auth.models.OtpType.EMAIL,
                    captchaToken = "captcha-verify",
                )

            assertTrue(result is SupabaseResult.Success)
            assertTrue(client.lastPostBody?.contains("\"captcha_token\":\"captcha-verify\"") == true)
            assertTrue(result.value == OtpVerifyResult.VerifiedNoSession)
        }

    @Test
    fun test_verifyOtp_withCaptchaToken_usesVerifyPayload() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            sut.verifyOtp(
                email = "a@b.com",
                token = "123456",
                type = io.github.androidpoet.supabase.auth.models.OtpType.EMAIL,
                captchaToken = "captcha-2",
            )

            assertEquals("/auth/v1/verify", client.lastPostEndpoint)
            assertTrue(client.lastPostBody?.contains("\"captcha_token\":\"captcha-2\"") == true)
        }

    @Test
    fun test_verifyOtpWithTokenHash_returnsAuthenticatedWhenSessionPayloadPresent() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val result =
                sut.verifyOtpWithTokenHash(
                    tokenHash = "hash-123",
                    type = io.github.androidpoet.supabase.auth.models.OtpType.EMAIL,
                    captchaToken = null,
                )

            assertTrue(result is SupabaseResult.Success)
            val value = result.value
            assertTrue(value is OtpVerifyResult.Authenticated)
            assertEquals("acc", value.session.accessToken)
        }

    @Test
    fun test_signOut_scopeBuildsEndpoint() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val result = sut.signOut("token-1", SignOutScope.GLOBAL)

            assertEquals("/auth/v1/logout?scope=global", client.lastPostEndpoint)
            assertTrue(result is SupabaseResult.Success)
        }

    @Test
    fun test_linkIdentity_buildsAuthorizeEndpoint() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val result =
                sut.linkIdentity(
                    accessToken = "token-1",
                    provider = OAuthProvider.GITHUB,
                    redirectTo = "myapp://callback",
                    scopes = listOf("read:user"),
                    queryParams = mapOf("prompt" to "consent"),
                )

            assertTrue(client.lastGetEndpoint?.startsWith("/auth/v1/user/identities/authorize?provider=github") == true)
            assertTrue(client.lastGetEndpoint?.contains("redirect_to=myapp%3A%2F%2Fcallback") == true)
            assertTrue(client.lastGetEndpoint?.contains("scopes=read%3Auser") == true)
            assertTrue(client.lastGetEndpoint?.contains("prompt=consent") == true)
            assertTrue(result is SupabaseResult.Success)
        }

    @Test
    fun test_linkIdentityWithIdToken_usesBearerAndLinkIdentityPayload() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val result =
                sut.linkIdentityWithIdToken(
                    accessToken = "session-token-1",
                    provider = OAuthProvider.APPLE,
                    idToken = "apple-id-token",
                    providerAccessToken = null,
                    nonce = "nonce-2",
                )

            assertEquals("/auth/v1/token?grant_type=id_token", client.lastPostEndpoint)
            assertEquals("Bearer session-token-1", client.lastPostHeaders["Authorization"])
            assertTrue(client.lastPostBody?.contains("\"provider\":\"apple\"") == true)
            assertTrue(client.lastPostBody?.contains("\"id_token\":\"apple-id-token\"") == true)
            assertTrue(client.lastPostBody?.contains("\"link_identity\":true") == true)
            assertTrue(result is SupabaseResult.Success)
        }

    @Test
    fun test_getSsoUrl_requiresDomainOrProviderId() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            // A Result-returning API must not throw on bad input — it returns Failure.
            val result = sut.getSsoUrl(accessToken = "token-1")
            assertTrue(result is SupabaseResult.Failure)
            assertEquals(null, client.lastPostEndpoint)
        }

    @Test
    fun test_getSsoUrl_rejectsBothDomainAndProviderId() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val result =
                sut.getSsoUrl(
                    accessToken = "token-1",
                    domain = "example.com",
                    providerId = "provider-1",
                )
            assertTrue(result is SupabaseResult.Failure)
            assertEquals(null, client.lastPostEndpoint)
        }

    @Test
    fun test_signUpWithEmail_blankEmail_returnsFailureNotThrow() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val result = sut.signUpWithEmail(email = "  ", password = "pw")
            assertTrue(result is SupabaseResult.Failure)
            assertEquals(null, client.lastPostEndpoint)
        }

    @Test
    fun test_signUpWithPhone_blankPhone_returnsFailureNotThrow() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val result = sut.signUpWithPhone(phone = "", password = "pw")
            assertTrue(result is SupabaseResult.Failure)
            assertEquals(null, client.lastPostEndpoint)
        }

    @Test
    fun test_signUpWithEmail_appendsRedirectToQueryParam() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            sut.signUpWithEmail(
                email = "user@example.com",
                password = "pw",
                emailRedirectTo = "https://app.example.com/welcome",
            )

            assertEquals(
                "/auth/v1/signup?redirect_to=https%3A%2F%2Fapp.example.com%2Fwelcome",
                client.lastPostEndpoint,
            )
        }

    @Test
    fun test_signUpWithEmail_withoutRedirect_usesPlainSignupEndpoint() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            sut.signUpWithEmail(email = "user@example.com", password = "pw")

            assertEquals("/auth/v1/signup", client.lastPostEndpoint)
        }

    @Test
    fun test_signUpWithEmail_pkceParams_emitChallengeInBody() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            sut.signUpWithEmail(
                email = "user@example.com",
                password = "pw",
                pkceParams = PkceParams(codeVerifier = "verifier", codeChallenge = "challenge-123", codeChallengeMethod = "S256"),
            )

            assertTrue(client.lastPostBody?.contains("\"code_challenge\":\"challenge-123\"") == true)
            assertTrue(client.lastPostBody?.contains("\"code_challenge_method\":\"S256\"") == true)
        }

    @Test
    fun test_signUpWithEmail_withoutPkce_omitsChallengeFromBody() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            sut.signUpWithEmail(email = "user@example.com", password = "pw")

            assertTrue(client.lastPostBody?.contains("code_challenge") == false)
        }

    @Test
    fun test_signInWithOtp_pkceParams_emitChallengeInBody() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            sut.signInWithOtp(
                email = "user@example.com",
                pkceParams = PkceParams(codeVerifier = "verifier", codeChallenge = "otp-chal", codeChallengeMethod = "S256"),
            )

            assertTrue(client.lastPostBody?.contains("\"code_challenge\":\"otp-chal\"") == true)
        }

    @Test
    fun test_resetPasswordForEmail_pkceParams_emitChallengeInBody() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            sut.resetPasswordForEmail(
                email = "user@example.com",
                pkceParams = PkceParams(codeVerifier = "verifier", codeChallenge = "recover-chal", codeChallengeMethod = "S256"),
            )

            assertTrue(client.lastPostBody?.contains("\"code_challenge\":\"recover-chal\"") == true)
        }

    @Test
    fun test_signUpWithPhone_appendsRedirectToQueryParam() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            sut.signUpWithPhone(
                phone = "+15555550100",
                password = "pw",
                redirectTo = "https://app.example.com/welcome",
            )

            assertEquals(
                "/auth/v1/signup?redirect_to=https%3A%2F%2Fapp.example.com%2Fwelcome",
                client.lastPostEndpoint,
            )
        }

    @Test
    fun test_mfaGetAuthenticatorAssuranceLevel_readsAalClaimFromToken() =
        runTest {
            val client = FakeSupabaseClient()
            // The /user response lists a verified factor; the *current* level must still come from
            // the aal2 claim in the token, not be inferred from the factor.
            client.userResponse =
                """{"id":"u1","factors":[{"id":"f1","factor_type":"totp","status":"verified"}]}"""
            val sut = AuthClientImpl(client)

            val token = jwtWithClaims("""{"sub":"u1","aal":"aal2"}""")
            val result = sut.mfaGetAuthenticatorAssuranceLevel(token)

            assertTrue(result is SupabaseResult.Success)
            assertEquals(AuthenticatorAssuranceLevel.AAL2, result.value)
        }

    @Test
    fun test_mfaGetAuthenticatorAssuranceLevel_missingAalClaimDefaultsToAal1() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val token = jwtWithClaims("""{"sub":"u1"}""")
            val result = sut.mfaGetAuthenticatorAssuranceLevel(token)

            assertTrue(result is SupabaseResult.Success)
            assertEquals(AuthenticatorAssuranceLevel.AAL1, result.value)
        }

    @Test
    fun test_mfaGetAuthenticatorAssuranceLevels_currentFromTokenNextFromFactors() =
        runTest {
            val client = FakeSupabaseClient()
            // Current session is still aal1, but a verified factor means it can be upgraded to aal2.
            client.userResponse =
                """{"id":"u1","factors":[{"id":"f1","factor_type":"totp","status":"verified"}]}"""
            val sut = AuthClientImpl(client)

            val token = jwtWithClaims("""{"sub":"u1","aal":"aal1"}""")
            val result = sut.mfaGetAuthenticatorAssuranceLevels(token)

            assertTrue(result is SupabaseResult.Success)
            assertEquals(AuthenticatorAssuranceLevel.AAL1, result.value.current)
            assertEquals(AuthenticatorAssuranceLevel.AAL2, result.value.next)
        }

    @Test
    fun test_mfaGetAuthenticatorAssuranceLevels_noVerifiedFactorKeepsNextAtAal1() =
        runTest {
            val client = FakeSupabaseClient()
            client.userResponse =
                """{"id":"u1","factors":[{"id":"f1","factor_type":"totp","status":"unverified"}]}"""
            val sut = AuthClientImpl(client)

            val token = jwtWithClaims("""{"sub":"u1","aal":"aal1"}""")
            val result = sut.mfaGetAuthenticatorAssuranceLevels(token)

            assertTrue(result is SupabaseResult.Success)
            assertEquals(AuthenticatorAssuranceLevel.AAL1, result.value.current)
            assertEquals(AuthenticatorAssuranceLevel.AAL1, result.value.next)
        }

    @Test
    fun test_reauthenticate_usesAuthorizedEndpoint() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val result = sut.reauthenticate("token-reauth")

            assertEquals("/auth/v1/reauthenticate", client.lastGetEndpoint)
            assertEquals("Bearer token-reauth", client.lastGetHeaders["Authorization"])
            assertTrue(result is SupabaseResult.Success)
        }

    @Test
    fun test_getUserIdentities_returnsUserIdentities() =
        runTest {
            val client = FakeSupabaseClient()
            client.userResponse =
                """{"id":"u1","identities":[{"id":"provider-user-1","identity_id":"identity-1","provider":"github","user_id":"u1"}]}"""
            val sut = AuthClientImpl(client)

            val result = sut.getUserIdentities("token-identities")

            assertTrue(result is SupabaseResult.Success)
            assertEquals("/auth/v1/user", client.lastGetEndpoint)
            assertEquals("Bearer token-identities", client.lastGetHeaders["Authorization"])
            assertEquals(
                listOf(UserIdentity(id = "provider-user-1", identityId = "identity-1", provider = "github", userId = "u1")),
                result.value,
            )
        }

    @Test
    fun test_getUserIdentities_returnsEmptyListWhenUserHasNoIdentities() =
        runTest {
            val client = FakeSupabaseClient()
            client.userResponse = """{"id":"u1"}"""
            val sut = AuthClientImpl(client)

            val result = sut.getUserIdentities("token-identities")

            assertTrue(result is SupabaseResult.Success)
            assertEquals(emptyList(), result.value)
        }

    @Test
    fun test_getSsoUrl_withoutAccessToken_usesNoAuthorizationHeader() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val result =
                sut.getSsoUrl(
                    accessToken = null,
                    domain = "example.com",
                )

            assertEquals("/auth/v1/sso", client.lastPostEndpoint)
            assertTrue(client.lastPostHeaders["Authorization"] == null)
            assertTrue(result is SupabaseResult.Success)
        }

    @Test
    fun test_updateUser_includesNonceAndBearerHeader() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val result =
                sut.updateUser(
                    accessToken = "token-upd",
                    updates =
                        io.github.androidpoet.supabase.auth.models.UserUpdateRequest(
                            password = "new-password",
                            currentPassword = "old-password",
                            nonce = "nonce-xyz",
                        ),
                )

            assertEquals("/auth/v1/user", client.lastPutEndpoint)
            assertEquals("Bearer token-upd", client.lastPutHeaders["Authorization"])
            assertTrue(client.lastPutBody?.contains("\"current_password\":\"old-password\"") == true)
            assertTrue(client.lastPutBody?.contains("\"nonce\":\"nonce-xyz\"") == true)
            assertTrue(result is SupabaseResult.Success)
        }

    @Test
    fun test_passkeyStartRegistration_usesBearerAndRegistrationOptionsEndpoint() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val result = sut.passkeyStartRegistration(accessToken = "token-passkey")

            assertTrue(result is SupabaseResult.Success)
            assertEquals("/auth/v1/passkeys/registration/options", client.lastPostEndpoint)
            assertEquals("Bearer token-passkey", client.lastPostHeaders["Authorization"])
            assertEquals("challenge-reg", result.value.challengeId)
        }

    @Test
    fun test_passkeyVerifyRegistration_sendsChallengeAndCredential() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val result =
                sut.passkeyVerifyRegistration(
                    accessToken = "token-passkey",
                    challengeId = "challenge-reg",
                    credential = buildJsonObject { put("id", "credential-id") },
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("/auth/v1/passkeys/registration/verify", client.lastPostEndpoint)
            assertEquals("Bearer token-passkey", client.lastPostHeaders["Authorization"])
            assertTrue(client.lastPostBody?.contains("\"challenge_id\":\"challenge-reg\"") == true)
            assertTrue(client.lastPostBody?.contains("\"credential\":{\"id\":\"credential-id\"}") == true)
            assertEquals("passkey1", result.value.id)
        }

    @Test
    fun test_passkeyStartAuthentication_sendsCaptchaPayload() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val result = sut.passkeyStartAuthentication(captchaToken = "captcha-passkey")

            assertTrue(result is SupabaseResult.Success)
            assertEquals("/auth/v1/passkeys/authentication/options", client.lastPostEndpoint)
            assertTrue(client.lastPostBody?.contains("\"captcha_token\":\"captcha-passkey\"") == true)
            assertEquals("challenge-auth", result.value.challengeId)
        }

    @Test
    fun test_passkeyVerifyAuthentication_sendsCredentialAndDecodesSession() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val result =
                sut.passkeyVerifyAuthentication(
                    challengeId = "challenge-auth",
                    credential = buildJsonObject { put("id", "credential-id") },
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("/auth/v1/passkeys/authentication/verify", client.lastPostEndpoint)
            assertTrue(client.lastPostBody?.contains("\"challenge_id\":\"challenge-auth\"") == true)
            assertEquals("passkey-acc", result.value.accessToken)
        }

    @Test
    fun test_passkeyListUpdateAndDelete_useExpectedEndpoints() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val listResult = sut.passkeyList(accessToken = "token-passkey")
            val updateResult =
                sut.passkeyUpdate(
                    accessToken = "token-passkey",
                    passkeyId = "passkey1",
                    friendlyName = "Laptop",
                )
            val deleteResult = sut.passkeyDelete(accessToken = "token-passkey", passkeyId = "passkey1")

            assertTrue(listResult is SupabaseResult.Success)
            assertEquals("passkey1", listResult.value.first().id)
            assertTrue(updateResult is SupabaseResult.Success)
            assertEquals("/auth/v1/passkeys/passkey1", client.lastDeleteEndpoint)
            assertEquals("Bearer token-passkey", client.lastDeleteHeaders["Authorization"])
            assertTrue(deleteResult is SupabaseResult.Success)
        }

    @Test
    fun test_oauthAuthorizationServerMethods_useExpectedEndpointsAndBearerToken() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val details =
                sut.oauthGetAuthorizationDetails(
                    accessToken = "token-oauth",
                    authorizationId = "authz-1",
                )
            val approve =
                sut.oauthApproveAuthorization(
                    accessToken = "token-oauth",
                    authorizationId = "authz-1",
                )
            val deny =
                sut.oauthDenyAuthorization(
                    accessToken = "token-oauth",
                    authorizationId = "authz-1",
                )
            val grants = sut.oauthListGrants(accessToken = "token-oauth")
            val revoke = sut.oauthRevokeGrant(accessToken = "token-oauth", clientId = "client-1")

            assertTrue(details is SupabaseResult.Success)
            assertEquals("authz-1", details.value.authorizationId)
            assertEquals("Example App", details.value.client?.name)
            assertTrue(approve is SupabaseResult.Success)
            assertEquals("https://app.example.com/callback", approve.value.redirectUrl)
            assertTrue(deny is SupabaseResult.Success)
            assertEquals("https://app.example.com/callback?error=access_denied", deny.value.redirectUrl)
            assertTrue(grants is SupabaseResult.Success)
            assertEquals(
                "client-1",
                grants.value
                    .first()
                    .client.id,
            )
            assertEquals("/auth/v1/user/oauth/grants?client_id=client-1", client.lastDeleteEndpoint)
            assertEquals("Bearer token-oauth", client.lastDeleteHeaders["Authorization"])
            assertTrue(revoke is SupabaseResult.Success)
        }

    // ---- A1: POST /sso additive fields ----

    @Test
    fun test_getSsoUrl_defaultsSkipHttpRedirectTrue() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val result = sut.getSsoUrl(domain = "example.com")

            assertTrue(result is SupabaseResult.Success)
            assertEquals("/auth/v1/sso", client.lastPostEndpoint)
            // Without skip_http_redirect=true the server 303-redirects instead of returning {url}.
            assertTrue(client.lastPostBody?.contains("\"skip_http_redirect\":true") == true)
        }

    @Test
    fun test_getSsoUrl_emitsPkceAndCaptcha() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            sut.getSsoUrl(
                domain = "example.com",
                pkceParams = PkceParams(codeVerifier = "v", codeChallenge = "sso-chal", codeChallengeMethod = "S256"),
                captchaToken = "captcha-sso",
            )

            assertTrue(client.lastPostBody?.contains("\"code_challenge\":\"sso-chal\"") == true)
            assertTrue(client.lastPostBody?.contains("\"code_challenge_method\":\"S256\"") == true)
            assertTrue(client.lastPostBody?.contains("\"gotrue_meta_security\":{\"captcha_token\":\"captcha-sso\"}") == true)
        }

    // ---- A2: POST /signup channel ----

    @Test
    fun test_signUpWithPhone_withChannel_sendsChannelBody() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            sut.signUpWithPhone(phone = "+15555550100", password = "pw", channel = MessagingChannel.WHATSAPP)

            assertEquals("/auth/v1/signup", client.lastPostEndpoint)
            assertTrue(client.lastPostBody?.contains("\"channel\":\"whatsapp\"") == true)
        }

    @Test
    fun test_signUpWithPhone_withoutChannel_omitsChannelFromBody() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            sut.signUpWithPhone(phone = "+15555550100", password = "pw")

            assertTrue(client.lastPostBody?.contains("channel") == false)
        }

    // ---- A3: PUT /user channel ----

    @Test
    fun test_updateUser_withChannel_sendsChannelBody() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            sut.updateUser(
                accessToken = "tok",
                updates =
                    io.github.androidpoet.supabase.auth.models.UserUpdateRequest(
                        phone = "+15555550100",
                        channel = "whatsapp",
                    ),
            )

            assertEquals("/auth/v1/user", client.lastPutEndpoint)
            assertTrue(client.lastPutBody?.contains("\"channel\":\"whatsapp\"") == true)
        }

    // ---- A4: POST /token id_token grant client_id + issuer ----

    @Test
    fun test_signInWithIdToken_emitsClientIdAndIssuer() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            sut.signInWithIdToken(
                provider = OAuthProvider.AZURE,
                idToken = "id-token-1",
                clientId = "client-xyz",
                issuer = "https://login.microsoftonline.com/tenant/v2.0",
            )

            assertEquals("/auth/v1/token?grant_type=id_token", client.lastPostEndpoint)
            assertTrue(client.lastPostBody?.contains("\"client_id\":\"client-xyz\"") == true)
            assertTrue(
                client.lastPostBody?.contains("\"issuer\":\"https://login.microsoftonline.com/tenant/v2.0\"") == true,
            )
        }

    // ---- A5: GET /user/identities/authorize PKCE ----

    @Test
    fun test_linkIdentity_pkceParams_emitChallengeInAuthorizeUrl() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            sut.linkIdentity(
                accessToken = "tok",
                provider = OAuthProvider.GITHUB,
                pkceParams = PkceParams(codeVerifier = "v", codeChallenge = "link-chal", codeChallengeMethod = "S256"),
            )

            assertTrue(client.lastGetEndpoint?.contains("code_challenge=link-chal") == true)
            assertTrue(client.lastGetEndpoint?.contains("code_challenge_method=S256") == true)
        }

    @Test
    fun test_linkIdentity_withoutPkce_omitsChallengeFromAuthorizeUrl() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            sut.linkIdentity(accessToken = "tok", provider = OAuthProvider.GITHUB)

            assertTrue(client.lastGetEndpoint?.contains("code_challenge") == false)
        }

    // ---- A6: POST /verify redirect_to (body) ----

    @Test
    fun test_verifyOtp_withRedirectTo_sendsRedirectToInBody() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            sut.verifyOtp(
                email = "a@b.com",
                token = "123456",
                type = io.github.androidpoet.supabase.auth.models.OtpType.EMAIL,
                redirectTo = "myapp://done",
            )

            assertEquals("/auth/v1/verify", client.lastPostEndpoint)
            assertTrue(client.lastPostBody?.contains("\"redirect_to\":\"myapp://done\"") == true)
        }

    @Test
    fun test_verifyOtp_withoutRedirectTo_omitsRedirectToFromBody() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            sut.verifyOtp(
                email = "a@b.com",
                token = "123456",
                type = io.github.androidpoet.supabase.auth.models.OtpType.EMAIL,
            )

            assertTrue(client.lastPostBody?.contains("redirect_to") == false)
        }

    // ---- A7: POST /factors/{id}/verify webauthn ----

    @Test
    fun test_mfaVerify_withWebauthn_sendsWebauthnBody() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            sut.mfaVerify(
                factorId = "factor-1",
                challengeId = "challenge-1",
                code = "",
                accessToken = "tok",
                webauthn =
                    io.github.androidpoet.supabase.auth.models.MfaWebauthnVerification(
                        type = "request",
                        credentialResponse = buildJsonObject { put("id", "cred-1") },
                    ),
            )

            assertEquals("/auth/v1/factors/factor-1/verify", client.lastPostEndpoint)
            assertTrue(client.lastPostBody?.contains("\"webauthn\":{") == true)
            assertTrue(client.lastPostBody?.contains("\"type\":\"request\"") == true)
            assertTrue(client.lastPostBody?.contains("\"credential_response\":{\"id\":\"cred-1\"}") == true)
        }

    @Test
    fun test_mfaVerify_withoutWebauthn_omitsWebauthnFromBody() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            sut.mfaVerify(factorId = "factor-1", challengeId = "challenge-1", code = "123456", accessToken = "tok")

            assertTrue(client.lastPostBody?.contains("webauthn") == false)
            // GoTrue's VerifyFactorParams is {challenge_id, code, webauthn}; the factor lives in the
            // URL path, so the body must NOT carry a factor_id (which the server ignores).
            assertTrue(client.lastPostBody?.contains("\"challenge_id\":\"challenge-1\"") == true)
            assertTrue(client.lastPostBody?.contains("factor_id") == false)
        }

    // ---- A10: POST /resend type guard ----

    @Test
    fun test_resendEmailOtp_rejectsOutOfRangeType() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            // recovery is not a valid /resend type — must fail without hitting the server.
            val result =
                sut.resendEmailOtp(
                    type = io.github.androidpoet.supabase.auth.models.OtpType.RECOVERY,
                    email = "a@b.com",
                )

            assertTrue(result is SupabaseResult.Failure)
            assertEquals(null, client.lastPostEndpoint)
        }

    @Test
    fun test_resendEmailOtp_allowsSignupType() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val result =
                sut.resendEmailOtp(
                    type = io.github.androidpoet.supabase.auth.models.OtpType.SIGNUP,
                    email = "a@b.com",
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("/auth/v1/resend", client.lastPostEndpoint)
        }

    @Test
    fun test_resendPhoneOtp_rejectsOutOfRangeType() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val result =
                sut.resendPhoneOtp(
                    type = io.github.androidpoet.supabase.auth.models.OtpType.MAGIC_LINK,
                    phone = "+15555550100",
                )

            assertTrue(result is SupabaseResult.Failure)
            assertEquals(null, client.lastPostEndpoint)
        }

    // ---- A11: GET /authorize invite_token ----

    @Test
    fun test_getOAuthSignInUrl_withInviteToken_emitsInviteTokenParam() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val url =
                sut.getOAuthSignInUrl(
                    provider = OAuthProvider.GITHUB,
                    inviteToken = "invite-123",
                )

            assertTrue(url.contains("invite_token=invite-123"))
        }

    @Test
    fun test_signInWithOAuth_withInviteToken_emitsInviteTokenParam() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val result =
                sut.signInWithOAuth(
                    provider = OAuthProvider.GITHUB,
                    inviteToken = "invite-456",
                )

            assertTrue(result is SupabaseResult.Success)
            assertTrue(result.value.url.contains("invite_token=invite-456"))
        }

    @Test
    fun test_getOAuthSignInUrl_withoutInviteToken_omitsInviteTokenParam() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val url = sut.getOAuthSignInUrl(provider = OAuthProvider.GITHUB)

            assertTrue(!url.contains("invite_token"))
        }
}

// Builds a syntactically valid JWT (header.payload.signature) whose payload is [claimsJson]. Only
// the payload is decoded by the client, so the header and signature are fixed placeholders.
private fun jwtWithClaims(claimsJson: String): String {
    val header = base64UrlEncode("""{"alg":"HS256","typ":"JWT"}""")
    val payload = base64UrlEncode(claimsJson)
    return "$header.$payload.signature"
}

private fun base64UrlEncode(value: String): String {
    val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    val bytes = value.encodeToByteArray()
    val out = StringBuilder()
    var i = 0
    while (i < bytes.size) {
        val b0 = bytes[i].toInt() and 0xFF
        val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
        val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
        val n = (b0 shl 16) or (b1 shl 8) or b2
        out.append(table[(n shr 18) and 0x3F])
        out.append(table[(n shr 12) and 0x3F])
        if (i + 1 < bytes.size) out.append(table[(n shr 6) and 0x3F])
        if (i + 2 < bytes.size) out.append(table[n and 0x3F])
        i += 3
    }
    return out.toString()
}

private class FakeSupabaseClient : SupabaseClient {
    override val projectUrl: String = "https://example.supabase.co"
    override val apiKey: String = "anon"
    override val accessTokenOrNull: String? = null

    var lastPostEndpoint: String? = null
    var lastPostBody: String? = null
    var lastPostHeaders: Map<String, String> = emptyMap()
    var lastGetEndpoint: String? = null
    var lastGetHeaders: Map<String, String> = emptyMap()
    var lastPatchEndpoint: String? = null
    var lastPatchBody: String? = null
    var lastPatchHeaders: Map<String, String> = emptyMap()
    var lastPutEndpoint: String? = null
    var lastPutBody: String? = null
    var lastPutHeaders: Map<String, String> = emptyMap()
    var lastDeleteEndpoint: String? = null
    var lastDeleteHeaders: Map<String, String> = emptyMap()
    var verifyResponse: String =
        """{"access_token":"acc","refresh_token":"ref","expires_in":3600,"token_type":"bearer","user":{"id":"u1"}}"""
    var userResponse: String = """{"id":"u1"}"""

    override suspend fun get(
        endpoint: String,
        queryParams: List<Pair<String, String>>,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastGetEndpoint = endpoint
        lastGetHeaders = headers
        return when {
            endpoint == "/auth/v1/user" -> SupabaseResult.Success(userResponse)
            endpoint == "/auth/v1/passkeys" ->
                SupabaseResult.Success(
                    """[{"id":"passkey1","friendly_name":"Laptop","created_at":"2026-01-01T00:00:00Z","last_used_at":"2026-01-02T00:00:00Z"}]""",
                )
            endpoint == "/auth/v1/oauth/authorizations/authz-1" ->
                SupabaseResult.Success(
                    """{"authorization_id":"authz-1","redirect_uri":"https://app.example.com/callback","client":{"id":"client-1","name":"Example App","uri":"https://app.example.com","logo_uri":"https://app.example.com/logo.png"},"user":{"id":"u1","email":"user@example.com"},"scope":"openid email"}""",
                )
            endpoint == "/auth/v1/user/oauth/grants" ->
                SupabaseResult.Success(
                    """[{"client":{"id":"client-1","name":"Example App","uri":"https://app.example.com","logo_uri":"https://app.example.com/logo.png"},"scopes":["openid","email"],"granted_at":"2026-01-01T00:00:00Z"}]""",
                )
            else -> SupabaseResult.Success("""{"url":"https://example.com/link","provider":"github"}""")
        }
    }

    override suspend fun post(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastPostEndpoint = endpoint
        lastPostBody = body
        lastPostHeaders = headers
        return when {
            endpoint.startsWith("/auth/v1/logout") -> SupabaseResult.Success("{}")
            endpoint == "/auth/v1/sso" -> SupabaseResult.Success("""{"url":"https://example.com/sso"}""")
            endpoint == "/auth/v1/passkeys/registration/options" ->
                SupabaseResult.Success(
                    """{"challenge_id":"challenge-reg","options":{"challenge":"abc"},"expires_at":1893456000}""",
                )
            endpoint == "/auth/v1/passkeys/registration/verify" ->
                SupabaseResult.Success(
                    """{"id":"passkey1","friendly_name":"Laptop","created_at":"2026-01-01T00:00:00Z"}""",
                )
            endpoint == "/auth/v1/passkeys/authentication/options" ->
                SupabaseResult.Success(
                    """{"challenge_id":"challenge-auth","options":{"challenge":"xyz"},"expires_at":1893456000}""",
                )
            endpoint == "/auth/v1/passkeys/authentication/verify" ->
                SupabaseResult.Success(
                    """{"access_token":"passkey-acc","refresh_token":"passkey-ref","expires_in":3600,"token_type":"bearer","user":{"id":"u1"}}""",
                )
            endpoint == "/auth/v1/oauth/authorizations/authz-1/consent" && body?.contains("\"action\":\"approve\"") == true ->
                SupabaseResult.Success(
                    """{"redirect_url":"https://app.example.com/callback"}""",
                )
            endpoint == "/auth/v1/oauth/authorizations/authz-1/consent" && body?.contains("\"action\":\"deny\"") == true ->
                SupabaseResult.Success(
                    """{"redirect_url":"https://app.example.com/callback?error=access_denied"}""",
                )
            endpoint == "/auth/v1/token?grant_type=id_token" ->
                SupabaseResult.Success(
                    """{"access_token":"acc","refresh_token":"ref","expires_in":3600,"token_type":"bearer","user":{"id":"u1"}}""",
                )
            endpoint == "/auth/v1/token?grant_type=web3" ->
                SupabaseResult.Success(
                    """{"access_token":"web3-acc","refresh_token":"web3-ref","expires_in":3600,"token_type":"bearer","user":{"id":"u1"}}""",
                )
            endpoint == "/auth/v1/verify" -> SupabaseResult.Success(verifyResponse)
            endpoint.endsWith("/challenge") ->
                SupabaseResult.Success("""{"id":"challenge-1","factor_id":"factor-1","expires_at":1893456000}""")
            else -> SupabaseResult.Success("{}")
        }
    }

    override suspend fun put(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastPutEndpoint = endpoint
        lastPutBody = body
        lastPutHeaders = headers
        return SupabaseResult.Success("""{"id":"u1"}""")
    }

    override suspend fun patch(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastPatchEndpoint = endpoint
        lastPatchBody = body
        lastPatchHeaders = headers
        return if (endpoint.startsWith("/auth/v1/passkeys/")) {
            SupabaseResult.Success("""{"id":"passkey1","friendly_name":"Laptop","created_at":"2026-01-01T00:00:00Z","last_used_at":"2026-01-02T00:00:00Z"}""")
        } else {
            SupabaseResult.Success("""{"id":"u1"}""")
        }
    }

    override suspend fun delete(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastDeleteEndpoint = endpoint
        lastDeleteHeaders = headers
        return SupabaseResult.Success("{}")
    }

    override suspend fun postRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun putRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

    override fun setAccessToken(token: String) = Unit

    override fun clearAccessToken() = Unit

    override suspend fun rawRequest(
        method: io.github.androidpoet.supabase.client.SupabaseHttpMethod,
        url: String,
        body: ByteArray?,
        contentType: String?,
        headers: Map<String, String>,
    ): io.github.androidpoet.supabase.core.result.SupabaseResult<io.github.androidpoet.supabase.client.SupabaseHttpResponse> =
        io.github.androidpoet.supabase.core.result.SupabaseResult.Failure(
            io.github.androidpoet.supabase.core.result
                .SupabaseError("rawRequest not supported in test fake"),
        )

    override fun close() = Unit
}
