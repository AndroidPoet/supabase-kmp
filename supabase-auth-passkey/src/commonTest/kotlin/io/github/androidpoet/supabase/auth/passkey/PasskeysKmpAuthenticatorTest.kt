package io.github.androidpoet.supabase.auth.passkey

import io.github.androidpoet.passkeys.PasskeyClient
import io.github.androidpoet.passkeys.PasskeyResult
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationOptions
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationResponse
import io.github.androidpoet.passkeys.models.PasskeyCreationOptions
import io.github.androidpoet.passkeys.models.PasskeyCreationResponse
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertTrue

class PasskeysKmpAuthenticatorTest {
    // A client whose ceremony succeeds but returns the given rawJson verbatim.
    private class FakeClient(
        private val raw: String,
    ) : PasskeyClient {
        override suspend fun create(options: PasskeyCreationOptions): PasskeyResult<PasskeyCreationResponse> =
            PasskeyResult.Success(
                PasskeyCreationResponse(
                    id = "id",
                    rawId = "rawId",
                    type = "public-key",
                    authenticatorAttachment = null,
                    attestationObject = "a",
                    clientDataJson = "c",
                    transports = emptyList(),
                    rawJson = raw,
                ),
            )

        override suspend fun authenticate(options: PasskeyAuthenticationOptions): PasskeyResult<PasskeyAuthenticationResponse> =
            PasskeyResult.Success(
                PasskeyAuthenticationResponse(
                    id = "id",
                    rawId = "rawId",
                    type = "public-key",
                    authenticatorAttachment = null,
                    clientDataJson = "c",
                    authenticatorData = null,
                    signature = null,
                    userHandle = null,
                    rawJson = raw,
                ),
            )
    }

    @Test
    fun test_malformedCeremonyJson_returnsFailureNotThrow() =
        runTest {
            // Invalid JSON, valid-but-non-object JSON, and empty must each become a
            // Failure — never an exception escaping this Result-first API.
            for (raw in listOf("not json {{{", "[]", "")) {
                val auth = PasskeysKmpAuthenticator(FakeClient(raw))
                assertTrue(auth.createCredential(buildJsonObject {}) is SupabaseResult.Failure, "create raw=$raw")
                assertTrue(auth.getCredential(buildJsonObject {}) is SupabaseResult.Failure, "get raw=$raw")
            }
        }
}
