package io.github.androidpoet.supabase.auth.passkey

import android.content.Context
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
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
    override suspend fun createCredential(options: JsonObject): SupabaseResult<JsonObject> =
        try {
            val request = CreatePublicKeyCredentialRequest(requestJson = options.toRequestJson())
            val response = credentialManager.createCredential(context, request)
            val registration = (response as CreatePublicKeyCredentialResponse).registrationResponseJson
            SupabaseResult.Success(parseJsonObject(registration))
        } catch (e: CreateCredentialException) {
            ceremonyFailure("Passkey registration failed or was cancelled", e)
        }

    override suspend fun getCredential(options: JsonObject): SupabaseResult<JsonObject> =
        try {
            val option = GetPublicKeyCredentialOption(requestJson = options.toRequestJson())
            val request = GetCredentialRequest(listOf(option))
            val response = credentialManager.getCredential(context, request)
            val assertion = (response.credential as PublicKeyCredential).authenticationResponseJson
            SupabaseResult.Success(parseJsonObject(assertion))
        } catch (e: GetCredentialException) {
            ceremonyFailure("Passkey sign-in failed or was cancelled", e)
        }

    private fun ceremonyFailure(fallback: String, cause: Throwable): SupabaseResult.Failure =
        SupabaseResult.Failure(
            SupabaseError(
                message = cause.message ?: fallback,
                code = PASSKEY_CEREMONY_FAILED,
            ),
        )

    private companion object {
        const val PASSKEY_CEREMONY_FAILED = "passkey_ceremony_failed"
        val json = Json { ignoreUnknownKeys = true }

        fun parseJsonObject(raw: String): JsonObject = json.parseToJsonElement(raw).jsonObject

        // Credential Manager expects the bare PublicKeyCredential(Creation|Request)
        // OptionsJSON. Supabase may wrap it under a "publicKey" key (the shape of a
        // browser CredentialCreationOptions); unwrap it when present.
        fun JsonObject.toRequestJson(): String =
            ((this["publicKey"] as? JsonObject) ?: this).toString()
    }
}
