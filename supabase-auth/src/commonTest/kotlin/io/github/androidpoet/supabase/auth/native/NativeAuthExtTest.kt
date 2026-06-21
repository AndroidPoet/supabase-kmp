package io.github.androidpoet.supabase.auth.native

import io.github.androidpoet.supabase.auth.AuthClientImpl
import io.github.androidpoet.supabase.auth.models.OAuthProvider
import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.client.SupabaseHttpMethod
import io.github.androidpoet.supabase.client.SupabaseHttpResponse
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NativeAuthExtTest {
    @Test
    fun test_signInWith_surfacesFirstSignInNameAndEmail_andStillRedeemsToken() =
        runTest {
            val client = NativeFakeSupabaseClient()
            val authClient = AuthClientImpl(client)
            val provider =
                FakeNativeAuthProvider(
                    NativeAuthCredential(
                        provider = OAuthProvider.APPLE,
                        idToken = "apple-id-token",
                        nonce = "raw-nonce",
                        fullName = "Ada Lovelace",
                        email = "ada@example.com",
                    ),
                )

            var captured: NativeAuthCredential? = null
            val result = authClient.signInWith(provider) { captured = it }

            // The credential's first-sign-in name/email (which the ID token never carries and
            // the token grant cannot forward) must reach the caller via onCredential.
            assertNotNull(captured)
            assertEquals("Ada Lovelace", captured.fullName)
            assertEquals("ada@example.com", captured.email)

            // And the credential is still redeemed for a session through the id_token grant.
            assertTrue(result is SupabaseResult.Success)
            assertEquals("/auth/v1/token?grant_type=id_token", client.lastPostEndpoint)
        }

    @Test
    fun test_signInWith_nativeFailure_skipsExchangeAndCallback() =
        runTest {
            val client = NativeFakeSupabaseClient()
            val authClient = AuthClientImpl(client)
            val provider =
                FakeNativeAuthProvider(
                    SupabaseResult.Failure(SupabaseError(message = "cancelled", code = "apple_sign_in_cancelled")),
                )

            var called = false
            val result = authClient.signInWith(provider) { called = true }

            // A cancelled/failed native flow must not invoke the callback or hit the network.
            assertTrue(result is SupabaseResult.Failure)
            assertEquals(false, called)
            assertEquals(null, client.lastPostEndpoint)
        }
}

private class FakeNativeAuthProvider(
    private val outcome: SupabaseResult<NativeAuthCredential>,
) : NativeAuthProvider {
    constructor(credential: NativeAuthCredential) : this(SupabaseResult.Success(credential))

    override val provider: OAuthProvider = OAuthProvider.APPLE

    override suspend fun signIn(): SupabaseResult<NativeAuthCredential> = outcome
}

private class NativeFakeSupabaseClient : SupabaseClient {
    override val projectUrl: String = "https://example.supabase.co"
    override val apiKey: String = "anon"
    override val accessTokenOrNull: String? = null
    var lastPostEndpoint: String? = null

    override fun setAccessToken(token: String) = Unit

    override fun clearAccessToken() = Unit

    override suspend fun post(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastPostEndpoint = endpoint
        return SupabaseResult.Success(
            """{"access_token":"acc","refresh_token":"ref","expires_in":3600,"token_type":"bearer","user":{"id":"user-1"}}""",
        )
    }

    override suspend fun get(
        endpoint: String,
        queryParams: List<Pair<String, String>>,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Failure(SupabaseError("not used"))

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

    override suspend fun rawRequest(
        method: SupabaseHttpMethod,
        url: String,
        body: ByteArray?,
        contentType: String?,
        headers: Map<String, String>,
    ): SupabaseResult<SupabaseHttpResponse> = SupabaseResult.Failure(SupabaseError("not used"))

    override fun close() = Unit
}
