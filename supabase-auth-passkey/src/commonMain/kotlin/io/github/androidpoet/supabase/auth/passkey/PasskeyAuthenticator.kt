package io.github.androidpoet.supabase.auth.passkey

import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.serialization.json.JsonObject

/**
 * Drives the platform's WebAuthn ceremony — the step the Supabase HTTP API
 * cannot perform itself.
 *
 * A passkey flow is three steps: the server returns **options** (a challenge),
 * the device's authenticator produces a **credential** in response, and the
 * server **verifies** it. [AuthClient.registerPasskey] and
 * [AuthClient.signInWithPasskey] handle the first and third steps over HTTP;
 * this interface is the middle one — invoking the OS authenticator (Android
 * Credential Manager, iOS AuthenticationServices, or the browser's
 * `navigator.credentials`).
 *
 * Implementations are platform-specific. An Android implementation backed by
 * Credential Manager ships in this module's `androidMain`
 * ([CredentialManagerPasskeyAuthenticator]); other platforms can supply their
 * own — the contract is intentionally just two JSON-in/JSON-out calls so it
 * stays a thin, replaceable seam.
 *
 * Both [options] inputs are the WebAuthn options object returned by Supabase
 * (`PasskeyRegistrationOptionsResponse.options` /
 * `PasskeyAuthenticationOptionsResponse.options`); both results are the WebAuthn
 * credential to send back to the verify endpoint. Cancellation or authenticator
 * failure should be reported as [SupabaseResult.Failure], not thrown.
 */
public interface PasskeyAuthenticator {
    /**
     * Runs the registration ceremony (`navigator.credentials.create`): prompts
     * the user to create a passkey for [options] and returns the resulting
     * credential JSON to send to the registration verify endpoint.
     */
    public suspend fun createCredential(options: JsonObject): SupabaseResult<JsonObject>

    /**
     * Runs the authentication ceremony (`navigator.credentials.get`): prompts the
     * user to assert an existing passkey for [options] and returns the resulting
     * credential JSON to send to the authentication verify endpoint.
     */
    public suspend fun getCredential(options: JsonObject): SupabaseResult<JsonObject>
}
