package io.github.androidpoet.supabase.e2ee

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How much a peer's public key is trusted. */
public enum class TrustLevel {
    /** The peer's key has never been seen. */
    UNKNOWN,

    /**
     * The key is known (recorded on first contact) but the user has **not**
     * confirmed the [safetyNumber] out of band. A network attacker could have
     * supplied it; treat messages as not-yet-authenticated.
     */
    UNVERIFIED,

    /** The user compared the [safetyNumber] out of band and confirmed it. */
    VERIFIED,
}

/** A recorded peer identity: the public key bytes and its [TrustLevel]. */
public class TrustEntry(
    /** The peer's public key. */
    public val publicKey: ByteArray,
    /** How much [publicKey] is trusted. */
    public val level: TrustLevel,
) {
    /** Returns a copy with a different [level]. */
    public fun withLevel(level: TrustLevel): TrustEntry =
        TrustEntry(publicKey = publicKey, level = level)
}

/**
 * Persists the per-peer trust ledger: which public key was seen for each user
 * and whether the user verified it.
 *
 * **Bring your own persistence.** Back this with secure storage so verifications
 * survive restarts; the default [InMemoryTrustStore] loses them. This module
 * never bundles a platform storage dependency.
 */
public interface TrustStore {
    /** Returns the entry for [userId], or `null` if the peer is unseen. */
    public suspend fun get(userId: String): TrustEntry?

    /** Stores or replaces the entry for [userId]. */
    public suspend fun put(userId: String, entry: TrustEntry)

    /** Forgets [userId] (used to accept a legitimate key change). */
    public suspend fun remove(userId: String)
}

/** A non-persistent [TrustStore] for tests and prototyping. */
public class InMemoryTrustStore : TrustStore {
    private val mutex = Mutex()
    private val entries = mutableMapOf<String, TrustEntry>()

    override suspend fun get(userId: String): TrustEntry? =
        mutex.withLock { entries[userId] }

    override suspend fun put(userId: String, entry: TrustEntry): Unit =
        mutex.withLock { entries[userId] = entry }

    override suspend fun remove(userId: String) {
        mutex.withLock { entries.remove(userId) }
    }
}
