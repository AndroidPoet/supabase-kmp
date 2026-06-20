package io.github.androidpoet.supabase.auth.native

import dev.whyoleg.cryptography.random.CryptographyRandom

/**
 * Generates a fresh, high-entropy nonce suitable for a native sign-in flow (Google, Apple, …).
 *
 * Native OIDC providers expect a single-use nonce per sign-in so the issued ID token's `nonce`
 * claim can be bound to *this* request and replays are rejected. The provider configs
 * (`GoogleSignInConfig.nonce`, `AppleSignInConfig.nonce`) all say "use a fresh random value per
 * sign-in" — call this instead of hand-rolling randomness, which is easy to get weak.
 *
 * The value is drawn from a cryptographically secure RNG (`CryptographyRandom`, the same source the
 * SDK uses for PKCE verifiers and OAuth state — **not** `kotlin.random.Random`) and is URL-safe:
 * it contains only the unreserved characters `A–Z a–z 0–9 - . _ ~`, so it is safe to embed in URLs,
 * headers, and JSON without escaping.
 *
 * Pass the returned value straight to the provider config's `nonce`. The provider takes care of
 * hashing it for the provider and returning the raw value in
 * [NativeAuthCredential.nonce] so Supabase can validate the token's hashed `nonce` claim.
 *
 * @param byteCount the amount of entropy to draw, in bytes. Defaults to 32 (256 bits); must be at
 *   least 16 (128 bits). One output character encodes ~6 bits, so the string is longer than
 *   [byteCount].
 * @return a fresh URL-safe nonce string.
 * @throws IllegalArgumentException if [byteCount] is below 16.
 */
public fun generateNonce(byteCount: Int = DEFAULT_NONCE_BYTES): String {
    require(byteCount >= MIN_NONCE_BYTES) {
        "A nonce needs at least $MIN_NONCE_BYTES bytes of entropy, got $byteCount"
    }
    // One CSPRNG draw per character keeps this dependency-free across all KMP targets while staying
    // cryptographically secure — the same approach the SDK uses for PKCE verifiers and OAuth state.
    return buildString(byteCount) {
        repeat(byteCount) {
            append(NONCE_ALPHABET[CryptographyRandom.nextInt(NONCE_ALPHABET.length)])
        }
    }
}

/** URL-safe unreserved characters (RFC 3986 §2.3): no escaping needed in URLs, headers, or JSON. */
private const val NONCE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

/** 256 bits — comfortably above the 128-bit floor and matching the PKCE verifier's strength. */
private const val DEFAULT_NONCE_BYTES = 32

/** 128 bits, the minimum entropy worth calling a nonce secure. */
private const val MIN_NONCE_BYTES = 16
