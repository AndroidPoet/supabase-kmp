package io.github.androidpoet.supabase.auth.apple

/**
 * Configuration for a native "Sign in with Apple" flow.
 *
 * On Apple platforms, obtain a [io.github.androidpoet.supabase.auth.native.NativeAuthProvider] from
 * `appleAuthProvider(config)` and pass it to [io.github.androidpoet.supabase.auth.native.signInWith].
 *
 * @property nonce optional raw nonce. When set, its SHA-256 hash is sent to Apple and the **raw**
 *   value is returned in the credential so Supabase can validate the token's hashed `nonce` claim.
 *   Use a fresh random value per sign-in.
 * @property requestEmail request the user's email scope.
 * @property requestFullName request the user's full-name scope.
 */
public data class AppleSignInConfig(
    public val nonce: String? = null,
    public val requestEmail: Boolean = true,
    public val requestFullName: Boolean = true,
)
