package io.github.androidpoet.supabase.auth

import io.github.androidpoet.supabase.auth.models.OAuthProvider
import io.github.androidpoet.supabase.auth.models.OtpVerifyResult
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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AuthClientImplTest {
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
            assertTrue(client.lastPostBody?.contains("\"captcha_token\":\"captcha-1\"") == true)
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
                email = "a@b.com",
                createUser = false,
                captchaToken = "captcha-otp",
                emailRedirectTo = "myapp://otp",
            )

            assertEquals("/auth/v1/otp", client.lastPostEndpoint)
            assertTrue(client.lastPostBody?.contains("\"email\":\"a@b.com\"") == true)
            assertTrue(client.lastPostBody?.contains("\"create_user\":false") == true)
            assertTrue(client.lastPostBody?.contains("\"captcha_token\":\"captcha-otp\"") == true)
            assertTrue(client.lastPostBody?.contains("\"email_redirect_to\":\"myapp://otp\"") == true)
        }

    @Test
    fun test_verifyOtpWithResult_returnsVerifiedNoSessionWhenNoAccessToken() =
        runTest {
            val client = FakeSupabaseClient()
            client.verifyResponse = "{}"
            val sut = AuthClientImpl(client)

            val result =
                sut.verifyOtpWithResult(
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
    fun test_verifyOtpWithTokenHashWithResult_returnsAuthenticatedWhenSessionPayloadPresent() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val result =
                sut.verifyOtpWithTokenHashWithResult(
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
    fun test_retrieveSsoUrl_requiresDomainOrProviderId() {
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            assertFailsWith<IllegalArgumentException> {
                sut.retrieveSsoUrl(accessToken = "token-1")
            }
        }
    }

    @Test
    fun test_retrieveSsoUrl_rejectsBothDomainAndProviderId() {
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            assertFailsWith<IllegalArgumentException> {
                sut.retrieveSsoUrl(
                    accessToken = "token-1",
                    domain = "example.com",
                    providerId = "provider-1",
                )
            }
        }
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
    fun test_retrieveSsoUrl_withoutAccessToken_usesNoAuthorizationHeader() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            val result =
                sut.retrieveSsoUrl(
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

            assertEquals("/auth/v1/user", client.lastPatchEndpoint)
            assertEquals("Bearer token-upd", client.lastPatchHeaders["Authorization"])
            assertTrue(client.lastPatchBody?.contains("\"current_password\":\"old-password\"") == true)
            assertTrue(client.lastPatchBody?.contains("\"nonce\":\"nonce-xyz\"") == true)
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
            else -> SupabaseResult.Success("{}")
        }
    }

    override suspend fun put(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

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
