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
 * Exports this pair's **private** key as PKCS#8 DER bytes so a device can persist
 * its identity across launches (store these in your platform's secure storage —
 * Keychain/Keystore/etc.). Restore later with [importE2eeKeyPair], pairing them
 * with [E2eeKeyPair.publicKey]. DER (PKCS#8) is the format that round-trips across
 * JDK / Apple / OpenSSL / WebCrypto, so it works on every target including
 * `wasmJs`. These bytes are secret — never log or upload them; only [publicKey]
 * is safe to publish.
 */
public suspend fun E2eeKeyPair.exportPrivateKey(): SupabaseResult<ByteArray> =
    cryptoResult("export") { privateKey.encodeToByteArray(EC.PrivateKey.Format.DER) }

/**
 * Restores an [E2eeKeyPair] from a [privateKeyDer] (PKCS#8 DER produced by
 * [exportPrivateKey]) and its matching raw [publicKey] (as published / from
 * [E2eeKeyPair.publicKey]). This is the missing half of **at-rest, single-user**
 * encryption: a device generates a pair once, persists it, and re-imports it on
 * the next launch to re-derive the same session and decrypt its own history.
 */
public suspend fun importE2eeKeyPair(
    privateKeyDer: ByteArray,
    publicKey: ByteArray,
): SupabaseResult<E2eeKeyPair> =
    cryptoResult("import") {
        val priv =
            provider
                .get(ECDH)
                .privateKeyDecoder(EC.Curve.P256)
                .decodeFromByteArray(EC.PrivateKey.Format.DER, privateKeyDer)
        E2eeKeyPair(publicKey = publicKey, privateKey = priv)
    }

/**
 * Derives a session keyed to **this device's own pair** — ECDH against its own
 * public key, a stable deterministic self-key for single-user at-rest encryption
 * (encrypt your own notes/files; the server only ever sees ciphertext). Persist
 * the pair with [exportPrivateKey]/[importE2eeKeyPair] so the same key returns on
 * the next launch. For peer-to-peer messaging, use [deriveSession] with the
 * peer's public key instead.
 */
public suspend fun E2eeKeyPair.deriveSelfSession(): SupabaseResult<E2eeSession> =
    deriveSession(publicKey)

/**
 * Derives a shared [E2eeSession] from this key pair and a peer's raw public key,
 * following the cryptography-kotlin secure-messaging recipe: ECDH → HKDF-SHA256 →
 * AES-256-GCM (the raw shared secret is never used as a key directly). Both sides
 * derive the **same** key locally; the server never sees it. Pairing two devices?
 * Each calls this with the other's published public key.
 *
 * **Security — authenticate the peer key.** This is raw ECDH: it does not verify that
 * [peerPublicKey] actually belongs to the intended peer. An attacker who can substitute
 * the published key (e.g. by tampering with the table you fetch it from) can mount a
 * man-in-the-middle attack. Distribute public keys over a trusted channel or verify their
 * fingerprints out-of-band. (The [deriveSelfSession] path has no peer and is unaffected.)
 */
public suspend fun E2eeKeyPair.deriveSession(peerPublicKey: ByteArray): SupabaseResult<E2eeSession> =
    cryptoResult("derive") {
        val peer =
            provider
                .get(ECDH)
                .publicKeyDecoder(EC.Curve.P256)
                .decodeFromByteArray(EC.PublicKey.Format.RAW, peerPublicKey)
        val shared = privateKey.sharedSecretGenerator().generateSharedSecretToByteArray(peer)
        val keyBytes =
            provider
                .get(HKDF)
                .secretDerivation(SHA256, AES_KEY_BITS.bits, ByteArray(0), DERIVE_INFO.encodeToByteArray())
                .deriveSecretToByteArray(shared)
        val aesKey =
            provider
                .get(AES.GCM)
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
