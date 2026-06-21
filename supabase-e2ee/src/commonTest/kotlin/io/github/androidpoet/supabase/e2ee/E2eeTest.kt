package io.github.androidpoet.supabase.e2ee

import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class E2eeTest {
    @Serializable
    private data class Message(
        val from: String,
        val body: String,
        val ts: Long,
    )

    private fun <T> SupabaseResult<T>.value(): T = (this as SupabaseResult.Success<T>).value

    @Test
    fun test_twoParties_deriveSameKey_andRoundTripAnyData() =
        runTest {
            // Alice and Bob each generate a keypair and publish their public key.
            val alice = generateE2eeKeyPair().value()
            val bob = generateE2eeKeyPair().value()

            // Each derives a session from the other's PUBLIC key — server never sees the key.
            val aliceSession = alice.deriveSession(bob.publicKey).value()
            val bobSession = bob.deriveSession(alice.publicKey).value()

            // 1) raw bytes
            val bytes = byteArrayOf(1, 2, 3, 4, 5, 0, -7, 42)
            val encBytes = aliceSession.encrypt(bytes).value()
            assertTrue(!encBytes.contentEquals(bytes)) // actually encrypted
            assertEquals(bytes.toList(), bobSession.decrypt(encBytes).value().toList())

            // 2) string
            val text = "ssh deploy@rayclip.io 🔐"
            val encText = aliceSession.encrypt(text).value()
            assertEquals(text, bobSession.decryptToString(encText).value())

            // 3) any @Serializable value
            val msg = Message(from = "alice", body = "hello bob", ts = 1_700_000_000)
            val encMsg = aliceSession.encryptValue(msg).value()
            assertEquals(msg, bobSession.decryptValue<Message>(encMsg).value())
        }

    @Test
    fun test_exportImport_roundTrips_andSelfSessionDecryptsAcrossRestart() =
        runTest {
            // Generate once, persist (export), then restore (import) — the "device
            // restart" path for at-rest single-user encryption.
            val original = generateE2eeKeyPair().value()
            val privateDer = original.exportPrivateKey().value()
            val restored = importE2eeKeyPair(privateDer, original.publicKey).value()

            // Both derive the SAME deterministic self-session key, so ciphertext
            // written before the restart decrypts after re-import.
            val cipher =
                original
                    .deriveSelfSession()
                    .value()
                    .encryptValue(Message("me", "secret note", 1))
                    .value()
            val plain =
                restored
                    .deriveSelfSession()
                    .value()
                    .decryptValue<Message>(cipher)
                    .value()
            assertEquals(Message("me", "secret note", 1), plain)
        }

    @Test
    fun test_tamperedCiphertext_failsAuthentication() =
        runTest {
            val alice = generateE2eeKeyPair().value()
            val bob = generateE2eeKeyPair().value()
            val aliceSession = alice.deriveSession(bob.publicKey).value()
            val bobSession = bob.deriveSession(alice.publicKey).value()

            val cipher = aliceSession.encrypt("integrity matters").value()
            // Flip a bit in the trailing GCM auth tag — AEAD must reject it.
            val tampered = cipher.copyOf()
            tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0x01).toByte()

            assertTrue(bobSession.decryptToString(tampered) is SupabaseResult.Failure)
            // The untampered ciphertext still decrypts, proving the tamper test is meaningful.
            assertEquals("integrity matters", bobSession.decryptToString(cipher).value())
        }

    @Test
    fun test_wrongPeerKey_cannotDecrypt() =
        runTest {
            val alice = generateE2eeKeyPair().value()
            val bob = generateE2eeKeyPair().value()
            val eve = generateE2eeKeyPair().value()

            val aliceToBob = alice.deriveSession(bob.publicKey).value()
            val eveSession = eve.deriveSession(alice.publicKey).value() // wrong pairing

            val cipher = aliceToBob.encrypt("secret").value()
            // Eve's derived key differs → decrypt fails (returned as Failure, not thrown).
            assertTrue(eveSession.decryptToString(cipher) is SupabaseResult.Failure)
        }
}
