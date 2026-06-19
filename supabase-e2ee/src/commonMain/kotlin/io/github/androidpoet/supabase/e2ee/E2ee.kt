package io.github.androidpoet.supabase.e2ee

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val provider get() = CryptographyProvider.Default

private const val DERIVE_INFO = "supabase-e2ee-v1"
private const val AES_KEY_BITS = 256

private suspend inline fun <T> cryptoResult(code: String, crossinline block: suspend () -> T): SupabaseResult<T> =
    try {
        SupabaseResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        SupabaseResult.Failure(SupabaseError(message = "E2EE $code failed: ${e.message}", code = code))
    }

/**
 * An ECDH (P-256) key pair for end-to-end encryption. The [publicKey] (raw bytes)
 * is safe to publish — store it in a Supabase table so peers can fetch it. The
 * private key stays inside this object; keep it on the device, never upload it.
 *
 * P-256 (rather than X25519) matches the cryptography-kotlin secure-messaging
 * recipe and is supported by every provider, including WebCrypto on `wasmJs`.
 */
public class E2eeKeyPair internal constructor(
    public val publicKey: ByteArray,
    internal val privateKey: ECDH.PrivateKey,
)

/**
 * A symmetric AES-256-GCM session derived from an ECDH key agreement. Encrypts and
 * decrypts **any data** — raw [ByteArray], [String], or any `@Serializable` value
 * via the [encryptValue]/[decryptValue] extensions. The relay/server only ever
 * sees the returned ciphertext (the GCM nonce is embedded in it).
 */
public class E2eeSession internal constructor(
    @PublishedApi internal val key: AES.GCM.Key,
) {
    /** Encrypts arbitrary bytes; the nonce is embedded in the returned ciphertext. */
    public suspend fun encrypt(plaintext: ByteArray): SupabaseResult<ByteArray> =
        cryptoResult("encrypt") { key.cipher().encrypt(plaintext) }

    /** Decrypts bytes produced by [encrypt]. */
    public suspend fun decrypt(ciphertext: ByteArray): SupabaseResult<ByteArray> =
        cryptoResult("decrypt") { key.cipher().decrypt(ciphertext) }

    /** Encrypts a UTF-8 string. */
    public suspend fun encrypt(text: String): SupabaseResult<ByteArray> =
        encrypt(text.encodeToByteArray())

    /** Decrypts ciphertext back into a UTF-8 string. */
    public suspend fun decryptToString(ciphertext: ByteArray): SupabaseResult<String> =
        cryptoResult("decrypt") { key.cipher().decrypt(ciphertext).decodeToString() }
}

/** Generates a fresh ECDH P-256 key pair; the public key is raw-encoded for publishing. */
public suspend fun generateE2eeKeyPair(): SupabaseResult<E2eeKeyPair> =
    cryptoResult("keygen") {
        val pair = provider.get(ECDH).keyPairGenerator(EC.Curve.P256).generateKey()
        E2eeKeyPair(
            publicKey = pair.publicKey.encodeToByteArray(EC.PublicKey.Format.RAW),
            privateKey = pair.privateKey,
        )
    }

/**
 * Derives a shared [E2eeSession] from this key pair and a peer's raw public key,
 * following the cryptography-kotlin secure-messaging recipe: ECDH → HKDF-SHA256 →
 * AES-256-GCM (the raw shared secret is never used as a key directly). Both sides
 * derive the **same** key locally; the server never sees it. Pairing two devices?
 * Each calls this with the other's published public key.
 */
public suspend fun E2eeKeyPair.deriveSession(peerPublicKey: ByteArray): SupabaseResult<E2eeSession> =
    cryptoResult("derive") {
        val peer =
            provider.get(ECDH)
                .publicKeyDecoder(EC.Curve.P256)
                .decodeFromByteArray(EC.PublicKey.Format.RAW, peerPublicKey)
        val shared = privateKey.sharedSecretGenerator().generateSharedSecretToByteArray(peer)
        val keyBytes =
            provider.get(HKDF)
                .secretDerivation(SHA256, AES_KEY_BITS.bits, ByteArray(0), DERIVE_INFO.encodeToByteArray())
                .deriveSecretToByteArray(shared)
        val aesKey =
            provider.get(AES.GCM)
                .keyDecoder()
                .decodeFromByteArray(AES.Key.Format.RAW, keyBytes)
        E2eeSession(aesKey)
    }

/** Encrypts any `@Serializable` [value] (encoded as JSON, then ciphertext). */
public suspend inline fun <reified T> E2eeSession.encryptValue(value: T, json: Json = Json): SupabaseResult<ByteArray> =
    encrypt(json.encodeToString(value))

/** Decrypts and decodes ciphertext produced by [encryptValue] back into [T]. */
public suspend inline fun <reified T> E2eeSession.decryptValue(ciphertext: ByteArray, json: Json = Json): SupabaseResult<T> {
    val text =
        when (val r = decryptToString(ciphertext)) {
            is SupabaseResult.Success -> r.value
            is SupabaseResult.Failure -> return r
        }
    return try {
        SupabaseResult.Success(json.decodeFromString<T>(text))
    } catch (e: Exception) {
        SupabaseResult.Failure(SupabaseError(message = "E2EE decode failed: ${e.message}", code = "decode"))
    }
}
