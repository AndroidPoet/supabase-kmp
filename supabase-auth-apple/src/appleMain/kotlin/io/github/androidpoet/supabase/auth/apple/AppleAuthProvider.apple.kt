@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.github.androidpoet.supabase.auth.apple

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import io.github.androidpoet.supabase.auth.models.OAuthProvider
import io.github.androidpoet.supabase.auth.native.NativeAuthCredential
import io.github.androidpoet.supabase.auth.native.NativeAuthProvider
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationScopeEmail
import platform.AuthenticationServices.ASAuthorizationScopeFullName
import platform.AuthenticationServices.ASPresentationAnchor
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.darwin.NSObject
import kotlin.coroutines.resume

/** The presentation window the system sign-in sheet is anchored to. */
internal expect fun keyPresentationAnchor(): ASPresentationAnchor

/**
 * Creates a "Sign in with Apple" [NativeAuthProvider] backed by `AuthenticationServices`. Pass the
 * result to [io.github.androidpoet.supabase.auth.native.signInWith].
 */
public fun appleAuthProvider(config: AppleSignInConfig): NativeAuthProvider = AppleAuthProviderImpl(config)

private class AppleAuthProviderImpl(
    private val config: AppleSignInConfig,
) : NativeAuthProvider {
    override val provider: OAuthProvider = OAuthProvider.APPLE

    // Held for the duration of the async system flow so the ObjC runtime doesn't release them.
    private var controller: ASAuthorizationController? = null
    private var handler: AppleAuthDelegate? = null

    override suspend fun signIn(): SupabaseResult<NativeAuthCredential> {
        val hashedNonce = config.nonce?.let { sha256Hex(it) }
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val request =
                    ASAuthorizationAppleIDProvider().createRequest().apply {
                        setRequestedScopes(
                            buildList {
                                if (config.requestEmail) add(ASAuthorizationScopeEmail)
                                if (config.requestFullName) add(ASAuthorizationScopeFullName)
                            },
                        )
                        hashedNonce?.let { setNonce(it) }
                    }
                val delegate =
                    AppleAuthDelegate(rawNonce = config.nonce) { result ->
                        controller = null
                        handler = null
                        if (continuation.isActive) continuation.resume(result)
                    }
                handler = delegate
                controller =
                    ASAuthorizationController(authorizationRequests = listOf(request)).apply {
                        setDelegate(delegate)
                        setPresentationContextProvider(delegate)
                        performRequests()
                    }
                continuation.invokeOnCancellation {
                    controller = null
                    handler = null
                }
            }
        }
    }
}

private class AppleAuthDelegate(
    private val rawNonce: String?,
    onResult: (SupabaseResult<NativeAuthCredential>) -> Unit,
) : NSObject(),
    ASAuthorizationControllerDelegateProtocol,
    ASAuthorizationControllerPresentationContextProvidingProtocol {
    // AuthenticationServices is documented to invoke exactly one delegate callback, but a platform
    // edge that fired both the success and error callbacks would resume the continuation twice and
    // crash. Latch on first delivery so only the first outcome is forwarded; the underlying
    // continuation resume is additionally guarded by isActive.
    private var delivered = false
    private val onResult: (SupabaseResult<NativeAuthCredential>) -> Unit = onResult

    private fun deliver(result: SupabaseResult<NativeAuthCredential>) {
        if (delivered) return
        delivered = true
        onResult(result)
    }

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization: ASAuthorization,
    ) {
        val credential = didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
        val tokenData = credential?.identityToken
        val token = tokenData?.let { NSString.create(it, NSUTF8StringEncoding)?.toString() }
        if (token.isNullOrEmpty()) {
            deliver(SupabaseResult.Failure(SupabaseError(message = "Apple sign-in returned no identity token")))
        } else {
            deliver(
                SupabaseResult.Success(
                    NativeAuthCredential(provider = OAuthProvider.APPLE, idToken = token, nonce = rawNonce),
                ),
            )
        }
    }

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError: NSError,
    ) {
        deliver(SupabaseResult.Failure(SupabaseError(message = "Apple sign-in failed: ${didCompleteWithError.localizedDescription}")))
    }

    override fun presentationAnchorForAuthorizationController(controller: ASAuthorizationController): ASPresentationAnchor =
        keyPresentationAnchor()
}

private fun sha256Hex(input: String): String {
    // Hashing a short nonce is pure CPU work, so use the synchronous hashBlocking variant rather
    // than the suspend hash() — that keeps this a plain function with no coroutine overhead.
    val digest =
        CryptographyProvider.Default
            .get(SHA256)
            .hasher()
            .hashBlocking(input.encodeToByteArray())
    return digest.joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
