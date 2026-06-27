package io.github.androidpoet.supabase.e2ee

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256

private const val SAFETY_NUMBER_ITERATIONS = 5200
private const val SAFETY_NUMBER_GROUPS = 6

/**
 * Computes a deterministic, order-independent **safety number** for a pair of
 * public keys, so two users can compare it out of band (read it aloud / show a
 * QR) and confirm there is no man-in-the-middle.
 *
 * Because `supabase-e2ee` derives session keys from raw ECDH, the only thing
 * standing between you and a server that swaps in its own public key is this
 * comparison. Both sides pass the same two keys (in either order) and get the
 * **same** number; sort-independence is why it matches on both devices.
 *
 * The result is [SAFETY_NUMBER_GROUPS] space-separated 5-digit groups, derived
 * from [SAFETY_NUMBER_ITERATIONS] SHA-256 iterations (the same hardening idea as
 * Signal's fingerprint).
 */
public suspend fun safetyNumber(
    localPublicKey: ByteArray,
    peerPublicKey: ByteArray,
): String {
    val ordered =
        if (compareBytes(localPublicKey, peerPublicKey) <= 0) {
            localPublicKey + peerPublicKey
        } else {
            peerPublicKey + localPublicKey
        }
    val hasher = CryptographyProvider.Default.get(SHA256).hasher()
    var digest = hasher.hash(ordered)
    repeat(SAFETY_NUMBER_ITERATIONS - 1) {
        digest = hasher.hash(digest + ordered)
    }
    return buildString {
        for (group in 0 until SAFETY_NUMBER_GROUPS) {
            if (group > 0) append(' ')
            append(fiveDigits(digest, group * 5))
        }
    }
}

private fun fiveDigits(hash: ByteArray, offset: Int): String {
    var acc = 0L
    for (i in 0 until 5) {
        acc = (acc shl 8) or (hash[offset + i].toLong() and 0xFF)
    }
    return (acc % 100_000).toString().padStart(5, '0')
}

private fun compareBytes(a: ByteArray, b: ByteArray): Int {
    val min = minOf(a.size, b.size)
    for (i in 0 until min) {
        val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
        if (diff != 0) return diff
    }
    return a.size - b.size
}
