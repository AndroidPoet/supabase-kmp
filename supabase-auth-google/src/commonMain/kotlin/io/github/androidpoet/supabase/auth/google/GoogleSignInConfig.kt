package io.github.androidpoet.supabase.auth.google

/**
 * Configuration for a native Google sign-in flow.
 *
 * Obtain a [io.github.androidpoet.supabase.auth.native.NativeAuthProvider] for your platform from a
 * platform-specific `googleAuthProvider(...)` factory (e.g. the Android one takes an `android.content.Context`),
 * then pass it to [io.github.androidpoet.supabase.auth.native.signInWith].
 *
 * @property serverClientId your Google **Web** client ID (the OAuth client of type "Web application"),
 *   the same value configured as the Supabase Google provider's client ID. This is what the returned
 *   ID token is audienced for, so Supabase can validate it.
 * @property nonce optional raw nonce. When set it is forwarded to the provider and returned in
 *   [io.github.androidpoet.supabase.auth.native.NativeAuthCredential.nonce] so Supabase can validate
 *   the token's hashed `nonce` claim. Use a fresh random value per sign-in — generate one with
 *   [io.github.androidpoet.supabase.auth.native.generateNonce] rather than hand-rolling randomness.
 * @property filterByAuthorizedAccounts when true, only accounts that previously authorized this app
 *   are offered. Set false to allow first-time sign-ups.
 * @property autoSelectEnabled enables Google's auto sign-in when a single matching credential exists.
 */
public data class GoogleSignInConfig(
    public val serverClientId: String,
    public val nonce: String? = null,
    public val filterByAuthorizedAccounts: Boolean = false,
    public val autoSelectEnabled: Boolean = false,
)
