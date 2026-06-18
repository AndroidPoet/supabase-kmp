package io.github.androidpoet.supabase.auth.google

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.androidpoet.supabase.auth.models.OAuthProvider
import io.github.androidpoet.supabase.auth.native.NativeAuthCredential
import io.github.androidpoet.supabase.auth.native.NativeAuthProvider
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest

/**
 * Creates a Google [NativeAuthProvider] backed by Android's Credential Manager + Google Identity
 * Services. Pass the result to [io.github.androidpoet.supabase.auth.native.signInWith].
 *
 * @param context an Activity context — Credential Manager presents UI from it.
 */
public fun googleAuthProvider(
    context: Context,
    config: GoogleSignInConfig,
): NativeAuthProvider = AndroidGoogleAuthProvider(context, config)

private class AndroidGoogleAuthProvider(
    private val context: Context,
    private val config: GoogleSignInConfig,
) : NativeAuthProvider {
    override val provider: OAuthProvider = OAuthProvider.GOOGLE

    override suspend fun signIn(): SupabaseResult<NativeAuthCredential> =
        try {
            // Google embeds whatever we pass to setNonce() verbatim into the ID token's `nonce`
            // claim. Supabase validates that claim by SHA-256-hashing the *raw* nonce we hand it
            // (NativeAuthCredential.nonce) and comparing. So we must send the HASHED nonce to
            // Google and the RAW nonce to Supabase — passing the raw value to both makes the
            // claim mismatch and every nonce sign-in fail. Mirrors the Apple provider.
            val hashedNonce = config.nonce?.let(::sha256Hex)
            val option =
                GetGoogleIdOption
                    .Builder()
                    .setServerClientId(config.serverClientId)
                    .setFilterByAuthorizedAccounts(config.filterByAuthorizedAccounts)
                    .setAutoSelectEnabled(config.autoSelectEnabled)
                    .apply { hashedNonce?.let(::setNonce) }
                    .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val response = CredentialManager.create(context).getCredential(context, request)
            toCredential(response.credential)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            SupabaseResult.Failure(SupabaseError(message = "Google sign-in failed: ${e.message}"))
        }

    private fun toCredential(credential: androidx.credentials.Credential): SupabaseResult<NativeAuthCredential> {
        val isGoogleIdToken =
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        if (!isGoogleIdToken) {
            return SupabaseResult.Failure(SupabaseError(message = "Unexpected credential type: ${credential.type}"))
        }
        val googleIdToken = GoogleIdTokenCredential.createFrom((credential as CustomCredential).data)
        return SupabaseResult.Success(
            NativeAuthCredential(
                provider = OAuthProvider.GOOGLE,
                idToken = googleIdToken.idToken,
                nonce = config.nonce,
            ),
        )
    }
}

private fun sha256Hex(input: String): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(input.encodeToByteArray())
        .joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
