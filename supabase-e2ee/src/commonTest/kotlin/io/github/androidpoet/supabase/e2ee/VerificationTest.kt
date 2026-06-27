package io.github.androidpoet.supabase.e2ee

import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VerificationTest {
    private fun <T> SupabaseResult<T>.value(): T = (this as SupabaseResult.Success<T>).value

    @Test
    fun test_safetyNumber_isIdenticalOnBothSides() =
        runTest {
            val alice = generateE2eeKeyPair().value()
            val bob = generateE2eeKeyPair().value()

            val fromAlice = safetyNumber(alice.publicKey, bob.publicKey)
            val fromBob = safetyNumber(bob.publicKey, alice.publicKey)

            assertEquals(fromAlice, fromBob) // order-independent → matches on both
            assertEquals(6, fromAlice.split(' ').size)
            assertTrue(fromAlice.replace(" ", "").all { it.isDigit() })
        }

    @Test
    fun test_safetyNumber_differsForDifferentPeers() =
        runTest {
            val alice = generateE2eeKeyPair().value()
            val bob = generateE2eeKeyPair().value()
            val eve = generateE2eeKeyPair().value()

            assertNotEquals(
                safetyNumber(alice.publicKey, bob.publicKey),
                safetyNumber(alice.publicKey, eve.publicKey),
            )
        }

    @Test
    fun test_trustStore_recordsAndUpgradesVerification() =
        runTest {
            val store = InMemoryTrustStore()
            val key = byteArrayOf(1, 2, 3)
            assertNull(store.get("bob"))

            store.put("bob", TrustEntry(key, TrustLevel.UNVERIFIED))
            assertEquals(TrustLevel.UNVERIFIED, store.get("bob")?.level)

            store.put("bob", store.get("bob")!!.withLevel(TrustLevel.VERIFIED))
            assertEquals(TrustLevel.VERIFIED, store.get("bob")?.level)

            store.remove("bob")
            assertNull(store.get("bob"))
        }

    @Test
    fun test_inMemoryKeyDirectory_publishThenFetch() =
        runTest {
            val directory = InMemoryKeyDirectory()
            val alice = generateE2eeKeyPair().value()

            assertNull(directory.fetch("alice").value())
            directory.publish("alice", alice.publicKey)

            val fetched = directory.fetch("alice").value()
            assertTrue(fetched != null && fetched.contentEquals(alice.publicKey))
        }

    @Test
    fun test_endToEnd_verifyThenEncryptDecrypt_viaDirectory() =
        runTest {
            // Mirrors what EncryptedRoom does, minus the DB/realtime IO: publish
            // keys, fetch the peer's, gate on verification, then round-trip.
            val directory = InMemoryKeyDirectory()
            val aliceTrust = InMemoryTrustStore()
            val alice = generateE2eeKeyPair().value()
            val bob = generateE2eeKeyPair().value()
            directory.publish("alice", alice.publicKey)
            directory.publish("bob", bob.publicKey)

            val bobKey = directory.fetch("bob").value()!!
            aliceTrust.put("bob", TrustEntry(bobKey, TrustLevel.UNVERIFIED))

            // Strict gate: not verified yet.
            assertNotEquals(TrustLevel.VERIFIED, aliceTrust.get("bob")?.level)

            // Compare safety numbers out of band, then verify.
            assertEquals(
                safetyNumber(alice.publicKey, bobKey),
                safetyNumber(bob.publicKey, alice.publicKey),
            )
            aliceTrust.put("bob", aliceTrust.get("bob")!!.withLevel(TrustLevel.VERIFIED))
            assertEquals(TrustLevel.VERIFIED, aliceTrust.get("bob")?.level)

            // Now encrypt → decrypt (shared key; both sides derive the same one).
            val aliceSession = alice.deriveSession(bobKey).value()
            val bobSession = bob.deriveSession(directory.fetch("alice").value()!!).value()
            val cipher = aliceSession.encrypt("verified hello 🔐").value()
            assertEquals("verified hello 🔐", bobSession.decryptToString(cipher).value())
        }
}
