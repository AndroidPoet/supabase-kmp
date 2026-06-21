package io.github.androidpoet.supabase.auth.admin

import io.github.androidpoet.supabase.auth.admin.models.GenerateLinkProperties
import io.github.androidpoet.supabase.auth.admin.models.GenerateLinkResponse
import io.github.androidpoet.supabase.auth.admin.models.ListUsersResponse
import io.github.androidpoet.supabase.auth.admin.models.OAuthClient
import io.github.androidpoet.supabase.auth.admin.models.OAuthClientRegistrationType
import io.github.androidpoet.supabase.auth.admin.models.OAuthClientTokenEndpointAuthMethod
import io.github.androidpoet.supabase.auth.admin.models.OAuthClientType
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializationRoundTripTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun test_listUsersResponse_decodesFromApiJson() {
        val payload =
            """
            {
              "users": [
                {
                  "id": "user-1",
                  "email": "a@example.com",
                  "created_at": "2024-01-01T00:00:00Z"
                },
                {
                  "id": "user-2",
                  "phone": "+15555550100"
                }
              ],
              "aud": "authenticated"
            }
            """.trimIndent()

        val response = json.decodeFromString<ListUsersResponse>(payload)

        assertEquals(2, response.users.size)
        assertEquals("a@example.com", response.users.first().email)
        assertEquals("authenticated", response.aud)
    }

    @Test
    fun test_generateLinkResponse_decodesFromApiJson() {
        val payload =
            """
            {
              "properties": {
                "action_link": "https://example.com/verify?token=abc",
                "email_otp": "123456",
                "hashed_token": "hashed",
                "redirect_to": "https://app.example.com",
                "verification_type": "signup"
              },
              "user": { "id": "user-1", "email": "a@example.com" }
            }
            """.trimIndent()

        val response = json.decodeFromString<GenerateLinkResponse>(payload)

        assertEquals(
            "https://example.com/verify?token=abc",
            response.properties.actionLink,
        )
        assertEquals("123456", response.properties.emailOtp)
        assertEquals("user-1", response.user?.id)
    }

    @Test
    fun test_generateLinkResponse_roundTrip() {
        val original =
            GenerateLinkResponse(
                properties =
                    GenerateLinkProperties(
                        actionLink = "https://example.com/verify",
                        emailOtp = "654321",
                        hashedToken = "hashed",
                        redirectTo = "https://app.example.com",
                        verificationType = "magiclink",
                    ),
                user = null,
            )

        val decoded = json.decodeFromString<GenerateLinkResponse>(json.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun test_oAuthClient_decodesFromApiJson() {
        val payload =
            """
            {
              "client_id": "client-123",
              "client_name": "My App",
              "client_secret": "secret",
              "client_type": "confidential",
              "token_endpoint_auth_method": "client_secret_basic",
              "registration_type": "dynamic",
              "client_uri": "https://app.example.com",
              "redirect_uris": ["https://app.example.com/callback"],
              "grant_types": ["authorization_code"],
              "response_types": ["code"],
              "scope": "openid email",
              "created_at": "2024-01-01T00:00:00Z"
            }
            """.trimIndent()

        val client = json.decodeFromString<OAuthClient>(payload)

        assertEquals("client-123", client.clientId)
        assertEquals(OAuthClientType.CONFIDENTIAL, client.clientType)
        assertEquals(
            OAuthClientTokenEndpointAuthMethod.CLIENT_SECRET_BASIC,
            client.tokenEndpointAuthMethod,
        )
        assertEquals(OAuthClientRegistrationType.DYNAMIC, client.registrationType)
        assertEquals(listOf("https://app.example.com/callback"), client.redirectUris)
    }

    @Test
    fun test_oAuthClient_decodesWhenOmitemptyFieldsAbsent() {
        // GoTrue tags client_name/redirect_uris/grant_types/response_types with `omitempty`, so a
        // client missing them drops the keys. Only client_id and client_type are always present.
        // These must decode (not throw MissingFieldException).
        val payload = """{ "client_id": "client-123", "client_type": "public" }"""

        val client = json.decodeFromString<OAuthClient>(payload)

        assertEquals("client-123", client.clientId)
        assertEquals(OAuthClientType.PUBLIC, client.clientType)
        assertEquals(null, client.clientName)
        assertEquals(emptyList<String>(), client.redirectUris)
        assertEquals(emptyList<String>(), client.grantTypes)
        assertEquals(emptyList<String>(), client.responseTypes)
    }

    @Test
    fun test_oAuthClient_roundTrip() {
        val original =
            OAuthClient(
                clientId = "client-123",
                clientName = "My App",
                clientSecret = null,
                clientType = OAuthClientType.PUBLIC,
                tokenEndpointAuthMethod = OAuthClientTokenEndpointAuthMethod.NONE,
                registrationType = OAuthClientRegistrationType.MANUAL,
                redirectUris = listOf("https://app.example.com/callback"),
                grantTypes = listOf("authorization_code"),
                responseTypes = listOf("code"),
            )

        val decoded = json.decodeFromString<OAuthClient>(json.encodeToString(original))

        assertEquals(original, decoded)
    }
}
