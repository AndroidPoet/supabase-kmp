package io.github.androidpoet.supabase.auth.passkey

import io.github.androidpoet.passkeys.PasskeyClient
import io.github.androidpoet.passkeys.PasskeyException
import io.github.androidpoet.passkeys.PasskeyResult
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * [PasskeyAuthenticator] backed by the cross-platform passkeys-kmp
 * [PasskeyClient] (`io.github.androidpoet:passkeys`).
 *
 * Unlike [CredentialManagerPasskeyAuthenticator], which is Android-only, this
 * single adapter drives the native ceremony on **every** Supabase target —
 * Android, iOS, macOS, JVM/Compose Desktop, and the browser (Wasm) — because
 * `PasskeyClient` is itself a common contract with a real native authenticator
 * behind each platform.
 *
 * Both layers are already W3C WebAuthn JSON in/out, so the seam is trivial: the
 * Supabase options object is forwarded as the request JSON, and the credential's
 * `rawJson` is returned verbatim for the verify step. [PasskeyException]s are
 * mapped to the same stable error codes the Android authenticator uses, so
 * callers can branch on cancellation / missing credential / unsupported device
 * identically regardless of which authenticator is in play.
 *
 * Construct the platform client with the right presentation anchor and wrap it:
 *
 * ```kotlin
 * // Compose Multiplatform — anchor resolved for you
 * val authenticator = PasskeysKmpAuthenticator(rememberPasskeyClient())
 * supabase.signInWithPasskey(authenticator)
 *
 * // Or a platform client directly, e.g. AndroidPasskeyClient(activity),
 * // IosPasskeyClient(window), JvmPasskeyClient { window.windowHandle } …
 * ```
 *
 * @param client the passkeys-kmp client for the current platform.
 */
public class PasskeysKmpAuthenticator(
    private val client: PasskeyClient,
) : PasskeyAuthenticator {
    override suspend fun createCredential(options: JsonObject): SupabaseResult<JsonObject> =
        when (val result = client.create(options.toString())) {
            is PasskeyResult.Success -> SupabaseResult.Success(parseJsonObject(result.value.rawJson))
            is PasskeyResult.Failure -> ceremonyFailure(result.error)
        }

    override suspend fun getCredential(options: JsonObject): SupabaseResult<JsonObject> =
        when (val result = client.authenticate(options.toString())) {
            is PasskeyResult.Success -> SupabaseResult.Success(parseJsonObject(result.value.rawJson))
            is PasskeyResult.Failure -> ceremonyFailure(result.error)
        }

    // Map passkeys-kmp's typed exceptions onto the same branchable codes as
    // CredentialManagerPasskeyAuthenticator so callers see one error vocabulary.
    private fun ceremonyFailure(error: PasskeyException): SupabaseResult.Failure {
        val code =
            when (error) {
                is PasskeyException.UserCanceled, is PasskeyException.Interrupted -> PASSKEY_CANCELLED
                is PasskeyException.NoCredential -> PASSKEY_NO_CREDENTIALS
                is PasskeyException.Unsupported -> PASSKEY_UNSUPPORTED
                else -> PASSKEY_CEREMONY_FAILED
            }
        return SupabaseResult.Failure(SupabaseError(message = error.message, code = code))
    }

    private companion object {
        const val PASSKEY_CANCELLED = "passkey_cancelled"
        const val PASSKEY_NO_CREDENTIALS = "passkey_no_credentials"
        const val PASSKEY_UNSUPPORTED = "passkey_unsupported"
        const val PASSKEY_CEREMONY_FAILED = "passkey_ceremony_failed"
        val json = Json { ignoreUnknownKeys = true }

        fun parseJsonObject(raw: String): JsonObject = json.parseToJsonElement(raw).jsonObject
    }
}
