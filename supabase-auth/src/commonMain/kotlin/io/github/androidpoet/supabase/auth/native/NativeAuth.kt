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
 */
public data class NativeAuthCredential(
    public val provider: OAuthProvider,
    public val idToken: String,
    public val accessToken: String? = null,
    public val nonce: String? = null,
)

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
