package io.github.androidpoet.supabase.auth

import io.github.androidpoet.supabase.auth.models.OAuthProvider
import io.github.androidpoet.supabase.auth.models.OtpVerifyResult
import io.github.androidpoet.supabase.auth.models.SignOutScope
import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AuthClientImplTest {
    @Test
    fun test_signInWithIdToken_usesIdTokenGrantPayload() = runBlocking {
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
    fun test_signInAnonymously_usesDataAndCaptchaPayload() = runBlocking {
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
    fun test_verifyOtpWithTokenHash_usesVerifyPayload() = runBlocking {
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
    fun test_signInWithOtp_withOptions_usesOtpPayloadFields() = runBlocking {
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
    fun test_verifyOtpWithResult_returnsVerifiedNoSessionWhenNoAccessToken() = runBlocking {
        val client = FakeSupabaseClient()
        client.verifyResponse = "{}"
        val sut = AuthClientImpl(client)

        val result = sut.verifyOtpWithResult(
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
    fun test_verifyOtp_withCaptchaToken_usesVerifyPayload() = runBlocking {
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
    fun test_verifyOtpWithTokenHashWithResult_returnsAuthenticatedWhenSessionPayloadPresent() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = AuthClientImpl(client)

        val result = sut.verifyOtpWithTokenHashWithResult(
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
    fun test_signOut_scopeBuildsEndpoint() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = AuthClientImpl(client)

        val result = sut.signOut("token-1", SignOutScope.GLOBAL)

        assertEquals("/auth/v1/logout?scope=global", client.lastPostEndpoint)
        assertTrue(result is SupabaseResult.Success)
    }

    @Test
    fun test_linkIdentity_buildsAuthorizeEndpoint() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = AuthClientImpl(client)

        val result = sut.linkIdentity(
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
    fun test_linkIdentityWithIdToken_usesBearerAndLinkIdentityPayload() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = AuthClientImpl(client)

        val result = sut.linkIdentityWithIdToken(
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
        runBlocking {
            val client = FakeSupabaseClient()
            val sut = AuthClientImpl(client)

            assertFailsWith<IllegalArgumentException> {
                sut.retrieveSsoUrl(accessToken = "token-1")
            }
        }
    }

    @Test
    fun test_retrieveSsoUrl_rejectsBothDomainAndProviderId() {
        runBlocking {
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
    fun test_reauthenticate_usesAuthorizedEndpoint() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = AuthClientImpl(client)

        val result = sut.reauthenticate("token-reauth")

        assertEquals("/auth/v1/reauthenticate", client.lastGetEndpoint)
        assertEquals("Bearer token-reauth", client.lastGetHeaders["Authorization"])
        assertTrue(result is SupabaseResult.Success)
    }

    @Test
    fun test_retrieveSsoUrl_withoutAccessToken_usesNoAuthorizationHeader() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = AuthClientImpl(client)

        val result = sut.retrieveSsoUrl(
            accessToken = null,
            domain = "example.com",
        )

        assertEquals("/auth/v1/sso", client.lastPostEndpoint)
        assertTrue(client.lastPostHeaders["Authorization"] == null)
        assertTrue(result is SupabaseResult.Success)
    }

    @Test
    fun test_updateUser_includesNonceAndBearerHeader() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = AuthClientImpl(client)

        val result = sut.updateUser(
            accessToken = "token-upd",
            updates = io.github.androidpoet.supabase.auth.models.UserUpdateRequest(
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
    var verifyResponse: String =
        """{"access_token":"acc","refresh_token":"ref","expires_in":3600,"token_type":"bearer","user":{"id":"u1"}}"""

    override suspend fun get(
        endpoint: String,
        queryParams: List<Pair<String, String>>,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastGetEndpoint = endpoint
        lastGetHeaders = headers
        return SupabaseResult.Success("""{"url":"https://example.com/link","provider":"github"}""")
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
            endpoint == "/auth/v1/token?grant_type=id_token" -> SupabaseResult.Success(
                """{"access_token":"acc","refresh_token":"ref","expires_in":3600,"token_type":"bearer","user":{"id":"u1"}}""",
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
        return SupabaseResult.Success("""{"id":"u1"}""")
    }

    override suspend fun delete(
        endpoint: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Success("{}")

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

    override fun close() = Unit
}
