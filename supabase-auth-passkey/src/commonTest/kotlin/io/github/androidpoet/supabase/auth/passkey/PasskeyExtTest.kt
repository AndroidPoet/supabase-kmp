package io.github.androidpoet.supabase.auth.passkey

import io.github.androidpoet.supabase.auth.auth
import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.client.SupabaseHttpMethod
import io.github.androidpoet.supabase.client.SupabaseHttpResponse
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasskeyExtTest {
    @Test
    fun test_registerPasskey_runsCeremonyAndVerifies() =
        runTest {
            val client = FakePasskeySupabaseClient()
            val authenticator = FakePasskeyAuthenticator()

            val result = client.auth.registerPasskey("access-token", authenticator)

            assertTrue(result is SupabaseResult.Success)
            assertEquals("pk-1", result.value.id)
            // The ceremony received the server's registration options...
            assertEquals("reg-challenge", authenticator.lastCreateOptions?.get("challenge").asContent())
            // ...and the produced credential was sent to the verify endpoint.
            assertTrue(client.postedEndpoints.any { it.endsWith("/passkeys/registration/verify") })
        }

    @Test
    fun test_signInWithPasskey_runsCeremonyAndReturnsSession() =
        runTest {
            val client = FakePasskeySupabaseClient()
            val authenticator = FakePasskeyAuthenticator()

            val result = client.auth.signInWithPasskey(authenticator)

            assertTrue(result is SupabaseResult.Success)
            assertEquals("acc", result.value.accessToken)
            assertEquals("auth-challenge", authenticator.lastGetOptions?.get("challenge").asContent())
        }

    @Test
    fun test_registerPasskey_failsAndSkipsVerifyWhenCeremonyFails() =
        runTest {
            val client = FakePasskeySupabaseClient()
            val authenticator = FakePasskeyAuthenticator(failCeremony = true)

            val result = client.auth.registerPasskey("access-token", authenticator)

            assertTrue(result is SupabaseResult.Failure)
            // The ceremony failed, so the credential is never verified.
            assertFalse(client.postedEndpoints.any { it.endsWith("/passkeys/registration/verify") })
        }
}

private fun kotlinx.serialization.json.JsonElement?.asContent(): String? =
    (this as? kotlinx.serialization.json.JsonPrimitive)?.content

private class FakePasskeyAuthenticator(
    private val failCeremony: Boolean = false,
) : PasskeyAuthenticator {
    var lastCreateOptions: JsonObject? = null
    var lastGetOptions: JsonObject? = null

    private val credential = buildJsonObject { put("id", "cred-1") }

    override suspend fun createCredential(options: JsonObject): SupabaseResult<JsonObject> {
        lastCreateOptions = options
        return if (failCeremony) {
            SupabaseResult.Failure(SupabaseError("user cancelled", code = "passkey_ceremony_failed"))
        } else {
            SupabaseResult.Success(credential)
        }
    }

    override suspend fun getCredential(options: JsonObject): SupabaseResult<JsonObject> {
        lastGetOptions = options
        return if (failCeremony) {
            SupabaseResult.Failure(SupabaseError("user cancelled", code = "passkey_ceremony_failed"))
        } else {
            SupabaseResult.Success(credential)
        }
    }
}

private class FakePasskeySupabaseClient : SupabaseClient {
    override val projectUrl: String = "https://example.supabase.co"
    override val apiKey: String = "anon"
    override val accessTokenOrNull: String? = null
    val postedEndpoints: MutableList<String> = mutableListOf()

    override fun setAccessToken(token: String) = Unit

    override fun clearAccessToken() = Unit

    override fun close() = Unit

    override suspend fun post(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        postedEndpoints += endpoint
        val response =
            when {
                endpoint.endsWith("/passkeys/registration/options") ->
                    """{"challenge_id":"reg-chal","options":{"challenge":"reg-challenge"},"expires_at":9999999999}"""
                endpoint.endsWith("/passkeys/registration/verify") ->
                    """{"id":"pk-1","created_at":"2026-01-01T00:00:00Z","friendly_name":"My Key"}"""
                endpoint.endsWith("/passkeys/authentication/options") ->
                    """{"challenge_id":"auth-chal","options":{"challenge":"auth-challenge"},"expires_at":9999999999}"""
                endpoint.endsWith("/passkeys/authentication/verify") ->
                    """{"access_token":"acc","refresh_token":"ref","expires_in":3600,"token_type":"bearer","user":{"id":"u1"}}"""
                else -> return SupabaseResult.Failure(SupabaseError("unexpected endpoint: $endpoint"))
            }
        return SupabaseResult.Success(response)
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
}
