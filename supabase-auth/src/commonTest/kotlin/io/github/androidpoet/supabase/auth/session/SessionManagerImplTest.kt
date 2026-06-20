package io.github.androidpoet.supabase.auth.session

import io.github.androidpoet.supabase.auth.AuthClientImpl
import io.github.androidpoet.supabase.auth.accessTokenExpiryEpochSeconds
import io.github.androidpoet.supabase.auth.models.Session
import io.github.androidpoet.supabase.auth.models.User
import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionManagerImplTest {
    @Test
    fun test_accessTokenExpiry_readsAbsoluteExpClaimFromJwt() {
        // header {"alg":"none","typ":"JWT"} . payload {"exp":1700000000,"sub":"u1"} .
        val jwt =
            "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0" +
                ".eyJleHAiOjE3MDAwMDAwMDAsInN1YiI6InUxIn0."

        // Refresh scheduling derives the delay from this absolute claim rather
        // than the relative expires_in, so a restored session refreshes on time.
        assertEquals(1700000000L, accessTokenExpiryEpochSeconds(jwt))
    }

    @Test
    fun test_accessTokenExpiry_returnsNullForNonJwt() {
        assertNull(accessTokenExpiryEpochSeconds("opaque-access-token"))
    }

    @Test
    fun test_initialize_restoresAndRefreshesStoredSession() =
        runTest {
            val client = SessionFakeSupabaseClient()
            val storage = FakeSessionStorage(storedSession = staleSession)
            val manager =
                SessionManagerImpl(
                    authClient = AuthClientImpl(client),
                    supabaseClient = client,
                    storage = storage,
                    autoRefresh = false,
                )

            val result = manager.initialize()

            assertTrue(result is SupabaseResult.Success)
            assertEquals("refreshed-acc", result.value.accessToken)
            assertEquals("Bearer refreshed-acc", client.accessTokenHeader)
            assertEquals("/auth/v1/token?grant_type=refresh_token", client.lastPostEndpoint)
        }

    @Test
    fun test_startAndStopAutoRefresh_controlRefreshScheduling() =
        runTest {
            val client = SessionFakeSupabaseClient()
            val storage = FakeSessionStorage()
            val manager =
                SessionManagerImpl(
                    authClient = AuthClientImpl(client),
                    supabaseClient = client,
                    storage = storage,
                    autoRefresh = false,
                    refreshBufferSeconds = 0,
                )
            try {
                manager.saveSession(staleSession.copy(expiresIn = 0))
                assertEquals(0, client.postCount)

                manager.startAutoRefresh()
                client.awaitPostCount(1)
                assertEquals(1, client.postCount)

                manager.stopAutoRefresh()
                manager.saveSession(staleSession.copy(accessToken = "second-acc", expiresIn = 0))
                assertEquals(1, client.postCount)
            } finally {
                manager.close()
            }
        }

    @Test
    fun test_refreshFailure_transient_keepsSessionAuthenticated() =
        runTest {
            val client = SessionFakeSupabaseClient()
            val manager =
                SessionManagerImpl(
                    authClient = AuthClientImpl(client),
                    supabaseClient = client,
                    storage = FakeSessionStorage(),
                    autoRefresh = false,
                )
            try {
                manager.saveSession(staleSession)
                client.nextPostFailure = SupabaseError(message = "server down", httpStatus = 503)

                val result = manager.refreshSession()

                // A 5xx is transient — the refresh token is still valid, so the user
                // must NOT be signed out on a server hiccup.
                assertTrue(result is SupabaseResult.Failure)
                assertTrue(manager.sessionState.value is SessionState.Authenticated)
            } finally {
                manager.close()
            }
        }

    @Test
    fun test_refreshFailure_terminal_expiresSession() =
        runTest {
            val client = SessionFakeSupabaseClient()
            val manager =
                SessionManagerImpl(
                    authClient = AuthClientImpl(client),
                    supabaseClient = client,
                    storage = FakeSessionStorage(),
                    autoRefresh = false,
                )
            try {
                manager.saveSession(staleSession)
                client.nextPostFailure =
                    SupabaseError(message = "invalid_grant", code = "invalid_grant", httpStatus = 400)

                manager.refreshSession()

                // A 4xx invalid_grant means the refresh token is dead — expire.
                assertTrue(manager.sessionState.value is SessionState.Expired)
            } finally {
                manager.close()
            }
        }
}

private val staleSession =
    Session(
        accessToken = "stale-acc",
        refreshToken = "stale-ref",
        expiresIn = 3600,
        tokenType = "bearer",
        user = User(id = "user-1"),
    )

private class FakeSessionStorage(
    private var storedSession: Session? = null,
) : SessionStorage {
    override suspend fun save(session: Session) {
        storedSession = session
    }

    override suspend fun load(): Session? =
        storedSession

    override suspend fun clear() {
        storedSession = null
    }
}

private class SessionFakeSupabaseClient : SupabaseClient {
    override val projectUrl: String = "https://example.supabase.co"
    override val apiKey: String = "anon"
    override val accessTokenOrNull: String? = null
    var accessTokenHeader: String? = null
    var lastPostEndpoint: String? = null
    var postCount: Int = 0
    private val postSignals = Channel<Int>(Channel.UNLIMITED)

    override fun setAccessToken(token: String) {
        accessTokenHeader = "Bearer $token"
    }

    override fun clearAccessToken() {
        accessTokenHeader = null
    }

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

    override suspend fun get(
        endpoint: String,
        queryParams: List<Pair<String, String>>,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

    // When set, the next refresh POST fails with this error instead of succeeding —
    // lets a test exercise transient (network/5xx) vs. terminal (4xx) refresh paths.
    var nextPostFailure: SupabaseError? = null

    override suspend fun post(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastPostEndpoint = endpoint
        postCount++
        postSignals.trySend(postCount)
        nextPostFailure?.let { return SupabaseResult.Failure(it) }
        return SupabaseResult.Success(
            """{"access_token":"refreshed-acc","refresh_token":"refreshed-ref","expires_in":3600,"token_type":"bearer","user":{"id":"user-1"}}""",
        )
    }

    suspend fun awaitPostCount(expectedCount: Int) {
        while (postCount < expectedCount) {
            postSignals.receive()
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
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

    override suspend fun delete(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

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
}
