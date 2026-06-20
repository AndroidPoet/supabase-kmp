package io.github.androidpoet.supabase.auth.native

import io.github.androidpoet.supabase.auth.models.OAuthProvider
import io.github.androidpoet.supabase.core.result.SupabaseResult

/**
 * A credential obtained from a platform-native sign-in flow (Google, Apple, …), ready to be
 * exchanged for a Supabase session via [io.github.androidpoet.supabase.auth.AuthClient.signInWithIdToken].
 *
 * This is the single, provider-agnostic hand-off point between *acquiring* a token natively (which is
 * platform- and SDK-specific) and *redeeming* it for a session (which the SDK already does). Anything
 * that can produce an OIDC ID token can produce this.
 *
 * @property provider the Supabase provider this token came from (e.g. [OAuthProvider.GOOGLE]).
 * @property idToken the OIDC ID token (a JWT) issued by the provider.
 * @property accessToken optional provider access token, when the flow returns one.
 * @property nonce the raw (un-hashed) nonce used to request the token, if a nonce was used. Supabase
 *   needs the raw nonce to validate the token's hashed `nonce` claim.
 * @property fullName the user's full name, if the provider surfaced it. Sign in with Apple only
 *   returns the name on the **first** authorization for an app, so capture it here when present —
 *   the ID token does not carry it. Null for providers that don't return a name.
 * @property email the user's email, if the provider surfaced it out-of-band from the ID token. Like
 *   [fullName], Sign in with Apple only returns this on the first authorization. Null otherwise.
 */
public data class NativeAuthCredential(
    public val provider: OAuthProvider,
    public val idToken: String,
    public val accessToken: String? = null,
    public val nonce: String? = null,
    public val fullName: String? = null,
    public val email: String? = null,
) {
    // Mask the provider tokens so a native credential never leaks into logs or crash reports. The
    // name/email aren't secrets, but mask them too so the whole credential is log-safe by default.
    override fun toString(): String =
        "NativeAuthCredential(provider=$provider, idToken=***, " +
            "accessToken=${if (accessToken == null) "null" else "***"}, " +
            "nonce=${if (nonce == null) "null" else "***"}, " +
            "fullName=${if (fullName == null) "null" else "***"}, " +
            "email=${if (email == null) "null" else "***"})"
}

/**
 * Acquires a [NativeAuthCredential] using a platform's native sign-in UI.
 *
 * This is the SDK's one extension point for native sign-in: the core ships implementations for the
 * common cases (see the `supabase-auth-google` and `supabase-auth-apple` modules), but because this
 * is just an interface you can implement your own for any provider, platform, or SDK and hand it to
 * [io.github.androidpoet.supabase.auth.signInWith]. The SDK never bundles a provider SDK you didn't ask for.
 */
public interface NativeAuthProvider {
    /** The Supabase provider this implementation signs in with. */
    public val provider: OAuthProvider

    /**
     * Launches the native sign-in flow and returns the resulting credential, or a failure if the
     * user cancelled, the flow errored, or native sign-in isn't available on this platform.
     */
    public suspend fun signIn(): SupabaseResult<NativeAuthCredential>
}
