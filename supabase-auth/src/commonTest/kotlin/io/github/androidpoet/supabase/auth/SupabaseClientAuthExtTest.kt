package io.github.androidpoet.supabase.auth

import io.github.androidpoet.supabase.auth.models.Session
import io.github.androidpoet.supabase.auth.models.User
import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.client.SupabaseHttpMethod
import io.github.androidpoet.supabase.client.SupabaseHttpResponse
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SupabaseClientAuthExtTest {
    @Test
    fun test_signInWithEmail_appliesTokenToClient() =
        runTest {
            val client = TokenTrackingClient()
            val result = client.signInWithEmail("a@b.com", "pw")

            assertTrue(result is SupabaseResult.Success)
            assertEquals("acc", result.value.accessToken)
            // The whole point: the client is now authenticated, not still anonymous.
            assertEquals("acc", client.accessTokenOrNull)
            assertEquals("/auth/v1/token?grant_type=password", client.lastPostEndpoint)
        }

    @Test
    fun test_signInWithEmail_failure_doesNotApplyToken() =
        runTest {
            val client = TokenTrackingClient().apply { failNextPost = true }
            val result = client.signInWithEmail("a@b.com", "wrong")

            assertTrue(result is SupabaseResult.Failure)
            assertNull(client.accessTokenOrNull)
        }

    @Test
    fun test_signInAnonymously_appliesTokenToClient() =
        runTest {
            val client = TokenTrackingClient()
            val result = client.signInAnonymously()

            assertTrue(result is SupabaseResult.Success)
            assertEquals("acc", client.accessTokenOrNull)
            assertEquals("/auth/v1/signup", client.lastPostEndpoint)
        }

    @Test
    fun test_applySession_ignoresBlankToken() {
        // A session with no access token (e.g. a sign-up awaiting email
        // confirmation) must not authenticate the client.
        val client = TokenTrackingClient()
        val session =
            Session(
                accessToken = "",
                refreshToken = "",
                expiresIn = 0,
                tokenType = "bearer",
                user = User(id = "u1"),
            )

        client.applySession(session)

        assertNull(client.accessTokenOrNull)
    }

    @Test
    fun test_applySession_appliesNonBlankToken() {
        val client = TokenTrackingClient()
        val session =
            Session(
                accessToken = "tok",
                refreshToken = "ref",
                expiresIn = 3600,
                tokenType = "bearer",
                user = User(id = "u1"),
            )

        client.applySession(session)

        assertEquals("tok", client.accessTokenOrNull)
    }

    @Test
    fun test_signOut_authenticated_clearsTokenAndCallsLogout() =
        runTest {
            val client = TokenTrackingClient()
            client.signInWithEmail("a@b.com", "pw")
            assertEquals("acc", client.accessTokenOrNull)

            val result = client.signOut()

            assertTrue(result is SupabaseResult.Success)
            assertNull(client.accessTokenOrNull)
            assertTrue(client.lastPostEndpoint?.startsWith("/auth/v1/logout") == true)
        }

    @Test
    fun test_signOut_notAuthenticated_succeedsWithoutNetwork() =
        runTest {
            val client = TokenTrackingClient()
            val result = client.signOut()

            assertTrue(result is SupabaseResult.Success)
            assertNull(client.lastPostEndpoint)
            assertNull(client.accessTokenOrNull)
        }
}

private class TokenTrackingClient : SupabaseClient {
    override val projectUrl: String = "https://example.supabase.co"
    override val apiKey: String = "anon"

    private var token: String? = null
    override val accessTokenOrNull: String? get() = token

    var failNextPost: Boolean = false
    var lastPostEndpoint: String? = null
    var sessionResponse: String =
        """{"access_token":"acc","refresh_token":"ref","expires_in":3600,"token_type":"bearer","user":{"id":"u1"}}"""

    override fun setAccessToken(token: String) {
        this.token = token
    }

    override fun clearAccessToken() {
        token = null
    }

    override suspend fun post(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastPostEndpoint = endpoint
        if (failNextPost) {
            return SupabaseResult.Failure(SupabaseError(message = "invalid credentials", code = "400"))
        }
        return when {
            endpoint.startsWith("/auth/v1/logout") -> SupabaseResult.Success("{}")
            else -> SupabaseResult.Success(sessionResponse)
        }
    }

    override suspend fun get(
        endpoint: String,
        queryParams: List<Pair<String, String>>,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Success("{}")

    override suspend fun put(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun patch(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun delete(
        endpoint: String,
        body: String?,
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

    override suspend fun rawRequest(
        method: SupabaseHttpMethod,
        url: String,
        body: ByteArray?,
        contentType: String?,
        headers: Map<String, String>,
    ): SupabaseResult<SupabaseHttpResponse> = SupabaseResult.Failure(SupabaseError("not used"))

    override fun close() = Unit
}
