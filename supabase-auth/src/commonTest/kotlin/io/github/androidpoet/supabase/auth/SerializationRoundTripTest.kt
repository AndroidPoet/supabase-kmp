package io.github.androidpoet.supabase.auth

import io.github.androidpoet.supabase.auth.models.AuthenticatorAssuranceLevel
import io.github.androidpoet.supabase.auth.models.MfaChallengeResponse
import io.github.androidpoet.supabase.auth.models.MfaEnrollResponse
import io.github.androidpoet.supabase.auth.models.MfaFactor
import io.github.androidpoet.supabase.auth.models.MfaFactorStatus
import io.github.androidpoet.supabase.auth.models.MfaFactorType
import io.github.androidpoet.supabase.auth.models.MfaListFactorsResponse
import io.github.androidpoet.supabase.auth.models.MfaTotpDetails
import io.github.androidpoet.supabase.auth.models.MfaVerifyResponse
import io.github.androidpoet.supabase.auth.models.Session
import io.github.androidpoet.supabase.auth.models.User
import io.github.androidpoet.supabase.auth.models.UserIdentity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializationRoundTripTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun test_session_decodesFromApiJson() {
        val payload =
            """
            {
              "access_token": "eyJhbGciOi.access.token",
              "refresh_token": "v1.refresh.token",
              "expires_in": 3600,
              "expires_at": 1718000000,
              "token_type": "bearer",
              "user": {
                "id": "11111111-1111-1111-1111-111111111111",
                "email": "user@example.com",
                "aud": "authenticated",
                "role": "authenticated",
                "created_at": "2024-01-01T00:00:00Z",
                "user_metadata": { "name": "Jane" },
                "app_metadata": { "provider": "email" }
              }
            }
            """.trimIndent()

        val session = json.decodeFromString<Session>(payload)

        assertEquals("eyJhbGciOi.access.token", session.accessToken)
        assertEquals("v1.refresh.token", session.refreshToken)
        assertEquals(3600L, session.expiresIn)
        assertEquals("bearer", session.tokenType)
        assertEquals("user@example.com", session.user.email)
        assertEquals(
            JsonPrimitive("Jane"),
            session.user.userMetadata?.get("name"),
        )
    }

    @Test
    fun test_session_decodesProviderTokens() {
        val payload =
            """
            {
              "access_token": "acc",
              "refresh_token": "ref",
              "expires_in": 3600,
              "token_type": "bearer",
              "provider_token": "ya29.provider-access",
              "provider_refresh_token": "1//provider-refresh",
              "user": { "id": "u1" }
            }
            """.trimIndent()

        val session = json.decodeFromString<Session>(payload)

        assertEquals("ya29.provider-access", session.providerToken)
        assertEquals("1//provider-refresh", session.providerRefreshToken)
    }

    @Test
    fun test_session_providerTokensDefaultToNullWhenAbsent() {
        val payload =
            """{"access_token":"acc","refresh_token":"ref","expires_in":3600,"token_type":"bearer","user":{"id":"u1"}}"""

        val session = json.decodeFromString<Session>(payload)

        assertEquals(null, session.providerToken)
        assertEquals(null, session.providerRefreshToken)
    }

    @Test
    fun test_user_decodesAdditionalServerFields() {
        val payload =
            """
            {
              "id": "u1",
              "role": "authenticated",
              "is_anonymous": false,
              "email_confirmed_at": "2024-01-02T00:00:00Z",
              "phone_confirmed_at": "2024-01-03T00:00:00Z",
              "confirmed_at": "2024-01-02T00:00:00Z",
              "last_sign_in_at": "2024-03-01T00:00:00Z"
            }
            """.trimIndent()

        val user = json.decodeFromString<User>(payload)

        assertEquals("authenticated", user.role)
        assertEquals(false, user.isAnonymous)
        assertEquals("2024-01-02T00:00:00Z", user.emailConfirmedAt)
        assertEquals("2024-01-03T00:00:00Z", user.phoneConfirmedAt)
        assertEquals("2024-01-02T00:00:00Z", user.confirmedAt)
        assertEquals("2024-03-01T00:00:00Z", user.lastSignInAt)
    }

    @Test
    fun test_mfaFactorStatus_serialNames() {
        assertEquals(
            MfaFactorStatus.VERIFIED,
            json.decodeFromString<MfaFactorStatus>("\"verified\""),
        )
        assertEquals(
            MfaFactorStatus.UNVERIFIED,
            json.decodeFromString<MfaFactorStatus>("\"unverified\""),
        )
        assertEquals("\"verified\"", json.encodeToString(MfaFactorStatus.VERIFIED))
    }

    @Test
    fun test_session_roundTrip() {
        val original =
            Session(
                accessToken = "access",
                refreshToken = "refresh",
                expiresIn = 7200,
                tokenType = "bearer",
                user =
                    User(
                        id = "user-id",
                        email = "user@example.com",
                        phone = "+15555550100",
                        createdAt = "2024-01-01T00:00:00Z",
                        updatedAt = "2024-02-01T00:00:00Z",
                        appMetadata = JsonObject(mapOf("provider" to JsonPrimitive("email"))),
                        userMetadata = JsonObject(mapOf("name" to JsonPrimitive("Jane"))),
                    ),
            )

        val decoded = json.decodeFromString<Session>(json.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun test_user_roundTripWithIdentities() {
        val original =
            User(
                id = "user-id",
                email = "user@example.com",
                identities =
                    listOf(
                        UserIdentity(
                            id = "identity-1",
                            identityId = "id-1",
                            provider = "google",
                            userId = "user-id",
                            identityData = JsonObject(mapOf("sub" to JsonPrimitive("abc"))),
                        ),
                    ),
            )

        val decoded = json.decodeFromString<User>(json.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun test_mfaVerifyResponse_decodesFromApiJson() {
        val payload =
            """
            {
              "access_token": "new.access.token",
              "refresh_token": "new.refresh.token",
              "token_type": "bearer",
              "expires_in": 3600,
              "user": { "id": "user-id" }
            }
            """.trimIndent()

        val response = json.decodeFromString<MfaVerifyResponse>(payload)

        assertEquals("new.access.token", response.accessToken)
        assertEquals(3600L, response.expiresIn)
        assertEquals("user-id", response.user.id)
    }

    @Test
    fun test_mfaEnrollResponse_decodesFromApiJson() {
        val payload =
            """
            {
              "id": "factor-id",
              "type": "totp",
              "friendly_name": "My Authenticator",
              "totp": {
                "qr_code": "data:image/svg+xml;base64,abc",
                "secret": "JBSWY3DPEHPK3PXP",
                "uri": "otpauth://totp/Example"
              }
            }
            """.trimIndent()

        val response = json.decodeFromString<MfaEnrollResponse>(payload)

        assertEquals("factor-id", response.id)
        assertEquals(MfaFactorType.TOTP, response.type)
        assertEquals("JBSWY3DPEHPK3PXP", response.totp?.secret)
    }

    @Test
    fun test_mfaEnrollResponse_roundTrip() {
        val original =
            MfaEnrollResponse(
                id = "factor-id",
                type = MfaFactorType.TOTP,
                totp =
                    MfaTotpDetails(
                        qrCode = "qr",
                        secret = "secret",
                        uri = "otpauth://totp/Example",
                    ),
                friendlyName = "name",
            )

        val decoded = json.decodeFromString<MfaEnrollResponse>(json.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun test_mfaChallengeResponse_roundTrip() {
        val original =
            MfaChallengeResponse(
                id = "challenge-id",
                type = "totp",
                expiresAt = 1718000000,
            )

        val decoded = json.decodeFromString<MfaChallengeResponse>(json.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun test_mfaChallengeResponse_decodesRealGoTruePayload() {
        // GoTrue's POST /factors/{id}/challenge returns only id/type/expires_at/webauthn — no
        // `factor_id`. A required factorId field made this decode throw a missing-field error on
        // every challenge; this guards that the wire shape stays decodable.
        val payload =
            """
            { "id": "11111111-1111-1111-1111-111111111111", "type": "totp", "expires_at": 1718000300 }
            """.trimIndent()

        val decoded = json.decodeFromString<MfaChallengeResponse>(payload)

        assertEquals("11111111-1111-1111-1111-111111111111", decoded.id)
        assertEquals("totp", decoded.type)
        assertEquals(1718000300, decoded.expiresAt)
    }

    @Test
    fun test_mfaListFactorsResponse_decodesFromApiJson() {
        val payload =
            """
            {
              "all": [
                {
                  "id": "factor-1",
                  "friendly_name": "Phone",
                  "factor_type": "phone",
                  "status": "verified",
                  "created_at": "2024-01-01T00:00:00Z"
                }
              ],
              "totp": [],
              "phone": [
                {
                  "id": "factor-1",
                  "factor_type": "phone",
                  "status": "verified"
                }
              ]
            }
            """.trimIndent()

        val response = json.decodeFromString<MfaListFactorsResponse>(payload)

        assertEquals(1, response.all.size)
        assertEquals(MfaFactorType.PHONE, response.all.first().factorType)
        assertEquals(MfaFactorStatus.VERIFIED, response.phone.first().status)
        assertEquals(emptyList(), response.webauthn)
    }

    @Test
    fun test_mfaListFactorsResponse_decodesWhenListsOmitted() {
        // The /factors response may omit empty factor lists; non-defaulted List
        // fields would throw MissingFieldException out of a SupabaseResult call.
        val response = json.decodeFromString<MfaListFactorsResponse>("{}")

        assertEquals(emptyList(), response.all)
        assertEquals(emptyList(), response.totp)
        assertEquals(emptyList(), response.phone)
        assertEquals(emptyList(), response.webauthn)
    }

    @Test
    fun test_mfaFactor_roundTrip() {
        val original =
            MfaFactor(
                id = "factor-id",
                friendlyName = "My TOTP",
                factorType = MfaFactorType.TOTP,
                status = MfaFactorStatus.VERIFIED,
                createdAt = "2024-01-01T00:00:00Z",
                updatedAt = "2024-02-01T00:00:00Z",
            )

        val decoded = json.decodeFromString<MfaFactor>(json.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun test_authenticatorAssuranceLevel_serialNames() {
        assertEquals(
            AuthenticatorAssuranceLevel.AAL2,
            json.decodeFromString<AuthenticatorAssuranceLevel>("\"aal2\""),
        )
        assertEquals(
            "\"aal1\"",
            json.encodeToString(AuthenticatorAssuranceLevel.AAL1),
        )
    }
}
