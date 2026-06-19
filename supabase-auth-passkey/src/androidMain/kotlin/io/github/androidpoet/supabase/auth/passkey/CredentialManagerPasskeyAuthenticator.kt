package io.github.androidpoet.supabase.auth.passkey

import android.content.Context
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnsupportedException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * [PasskeyAuthenticator] backed by AndroidX [CredentialManager].
 *
 * Credential Manager speaks the W3C WebAuthn JSON serialization directly, so the
 * Supabase options object is forwarded as the request JSON and the authenticator
 * response JSON is returned verbatim for the verify step.
 *
 * @param context an **Activity** context — Credential Manager shows a system UI
 *   and requires an activity to host it.
 * @param credentialManager overridable for testing; defaults to one created from
 *   [context].
 */
public class CredentialManagerPasskeyAuthenticator(
    private val context: Context,
    private val credentialManager: CredentialManager = CredentialManager.create(context),
) : PasskeyAuthenticator {
    override suspend fun createCredential(options: JsonObject): SupabaseResult<JsonObject> {
        return try {
            // Options are already normalized to the W3C JSON form by
            // registerPasskey, and Credential Manager consumes that JSON directly.
            val request = CreatePublicKeyCredentialRequest(requestJson = options.toString())
            val response = credentialManager.createCredential(context, request)
            // Credential Manager's return type is the platform-agnostic CreateCredentialResponse;
            // the contract is "report Failure, don't throw", so guard the narrowing instead of
            // letting an unexpected subtype surface as a ClassCastException.
            val publicKeyResponse =
                response as? CreatePublicKeyCredentialResponse
                    ?: return unexpectedResponse(
                        "Credential Manager returned an unexpected response type for passkey creation: " +
                            (response::class.simpleName ?: "unknown"),
                    )
            parseCeremonyJson(publicKeyResponse.registrationResponseJson)
        } catch (e: CreateCredentialException) {
            ceremonyFailure(e)
        }
    }

    override suspend fun getCredential(options: JsonObject): SupabaseResult<JsonObject> {
        return try {
            val option = GetPublicKeyCredentialOption(requestJson = options.toString())
            val request = GetCredentialRequest(listOf(option))
            val response = credentialManager.getCredential(context, request)
            // Guard the narrowing for the same reason as createCredential: an unexpected
            // credential type must surface as a Failure, never a thrown ClassCastException.
            val publicKeyCredential =
                response.credential as? PublicKeyCredential
                    ?: return unexpectedResponse(
                        "Credential Manager returned an unexpected credential type for passkey assertion: " +
                            (response.credential::class.simpleName ?: "unknown"),
                    )
            parseCeremonyJson(publicKeyCredential.authenticationResponseJson)
        } catch (e: GetCredentialException) {
            ceremonyFailure(e)
        }
    }

    // Translate Credential Manager's exception types into stable, branchable
    // error codes (mirroring the distinctions the Flutter SDK surfaces) so callers
    // can tell a user cancellation from a missing passkey or an unsupported device.
    private fun ceremonyFailure(cause: Throwable): SupabaseResult.Failure {
        val code =
            when (cause) {
                is CreateCredentialCancellationException, is GetCredentialCancellationException -> PASSKEY_CANCELLED
                is NoCredentialException -> PASSKEY_NO_CREDENTIALS
                is CreateCredentialUnsupportedException, is GetCredentialUnsupportedException -> PASSKEY_UNSUPPORTED
                else -> PASSKEY_CEREMONY_FAILED
            }
        return SupabaseResult.Failure(SupabaseError(message = cause.message ?: code, code = code))
    }

    // Credential Manager hands back base types; if a platform returns a credential/response of an
    // unanticipated subtype, surface it as a Failure rather than crashing on a forced cast.
    private fun unexpectedResponse(message: String): SupabaseResult.Failure =
        SupabaseResult.Failure(SupabaseError(message = message, code = PASSKEY_CEREMONY_FAILED))

    private companion object {
        const val PASSKEY_CANCELLED = "passkey_cancelled"
        const val PASSKEY_NO_CREDENTIALS = "passkey_no_credentials"
        const val PASSKEY_UNSUPPORTED = "passkey_unsupported"
        const val PASSKEY_CEREMONY_FAILED = "passkey_ceremony_failed"
        val json = Json { ignoreUnknownKeys = true }

        // Parsing must not throw out of this Result-first API: a malformed response
        // JSON would otherwise raise an IllegalArgumentException (or its
        // SerializationException subclass) that the Credential-Manager-typed catch
        // above does not cover. Map it to a Failure instead.
        fun parseCeremonyJson(raw: String): SupabaseResult<JsonObject> =
            try {
                SupabaseResult.Success(json.parseToJsonElement(raw).jsonObject)
            } catch (e: IllegalArgumentException) {
                SupabaseResult.Failure(
                    SupabaseError(message = "Malformed passkey ceremony response: ${e.message}", code = PASSKEY_CEREMONY_FAILED),
                )
            }
    }
}
