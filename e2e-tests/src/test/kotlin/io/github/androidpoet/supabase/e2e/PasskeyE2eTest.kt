package io.github.androidpoet.supabase.e2e

import io.github.androidpoet.supabase.auth.createAuthClient
import io.github.androidpoet.supabase.auth.passkey.PasskeyAuthenticator
import io.github.androidpoet.supabase.auth.passkey.registerPasskey
import io.github.androidpoet.supabase.auth.passkey.signInWithPasskey
import io.github.androidpoet.supabase.client.Supabase
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Live end-to-end test of the Supabase passkey integration against a real
 * project (set SUPABASE_E2E_URL / SUPABASE_E2E_ANON_KEY).
 *
 * It runs the full ceremony through supabase-kmp's own helpers
 * ([registerPasskey] / [signInWithPasskey]) — the same code path a real app
 * uses — but substitutes a **software WebAuthn authenticator** for the device's
 * native one so it can run headless and use the HTTPS origin the project is
 * configured with. The native ceremony (passkeys-kmp) is verified separately on
 * real devices; this proves the Supabase HTTP layer + adapter contract.
 */
class PasskeyE2eTest {
    @Test
    fun test_passkey_registerThenSignIn_returnsSessionForSameUser() =
        runTest {
            val config = localSupabaseConfig()
            val client = Supabase.create(projectUrl = config.apiUrl, apiKey = config.anonKey)
            val auth = createAuthClient(client)

            // A permanent user with a session is required to register a passkey
            // (anonymous users are rejected with no_authorization).
            val email = "passkey-e2e-${java.util.UUID.randomUUID()}@example.com"
            val password = "Pk!${java.util.UUID.randomUUID()}"
            val signUp = auth.signUpWithEmail(email = email, password = password).unwrap("signUpWithEmail")
            println("[passkey-e2e] user = ${signUp.user.id}")

            // Must match an origin configured on the e2e project's WebAuthn
            // relying party. Defaults to the loopback origin a local stack uses.
            val origin = System.getenv("SUPABASE_E2E_PASSKEY_ORIGIN") ?: "http://localhost:8080"
            val authenticator = SoftwarePasskeyAuthenticator(origin = origin)

            val meta = auth.registerPasskey(signUp.accessToken, authenticator).unwrap("registerPasskey")
            println("[passkey-e2e] registered passkey id=${meta.id}")

            // Sessionless sign-in via the discoverable credential just registered.
            val session = auth.signInWithPasskey(authenticator).unwrap("signInWithPasskey")
            println("[passkey-e2e] signed in via passkey, user=${session.user.id}")

            client.close()

            assertTrue(session.accessToken.isNotBlank(), "passkey sign-in returned a blank access token")
            assertTrue(session.user.id == signUp.user.id, "passkey sign-in resolved a different user")
        }
}

/**
 * A minimal ES256 (P-256) WebAuthn authenticator implemented in pure JVM crypto.
 * Holds one credential in memory between [createCredential] and [getCredential].
 */
private class SoftwarePasskeyAuthenticator(
    private val origin: String,
) : PasskeyAuthenticator {
    private val keyPair: KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val credentialId: ByteArray = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
    private var userHandle: String = ""

    override suspend fun createCredential(options: JsonObject): SupabaseResult<JsonObject> {
        val opts = options["publicKey"]?.jsonObject ?: options
        val challenge = opts.getValue("challenge").jsonPrimitive.content
        val rpId = opts.getValue("rp").jsonObject.getValue("id").jsonPrimitive.content
        userHandle = opts.getValue("user").jsonObject.getValue("id").jsonPrimitive.content

        val clientDataJson = clientData("webauthn.create", challenge)
        val authData = authenticatorData(rpId, attested = true)
        val attestationObject =
            Cbor.map(
                listOf(
                    Cbor.text("fmt") to Cbor.text("none"),
                    Cbor.text("attStmt") to Cbor.map(emptyList()),
                    Cbor.text("authData") to Cbor.bytes(authData),
                ),
            )
        val credential =
            buildJsonObject {
                put("id", b64url(credentialId))
                put("rawId", b64url(credentialId))
                put("type", "public-key")
                put("authenticatorAttachment", "platform")
                put(
                    "response",
                    buildJsonObject {
                        put("clientDataJSON", b64url(clientDataJson))
                        put("attestationObject", b64url(attestationObject))
                    },
                )
            }
        return SupabaseResult.Success(credential)
    }

    override suspend fun getCredential(options: JsonObject): SupabaseResult<JsonObject> {
        val opts = options["publicKey"]?.jsonObject ?: options
        val challenge = opts.getValue("challenge").jsonPrimitive.content
        val rpId = (opts["rpId"] ?: opts["rp"]?.jsonObject?.get("id"))!!.jsonPrimitive.content

        val clientDataJson = clientData("webauthn.get", challenge)
        val authData = authenticatorData(rpId, attested = false)
        val signed = authData + sha256(clientDataJson)
        val signature =
            Signature.getInstance("SHA256withECDSA").run {
                initSign(keyPair.private)
                update(signed)
                sign()
            }
        val credential =
            buildJsonObject {
                put("id", b64url(credentialId))
                put("rawId", b64url(credentialId))
                put("type", "public-key")
                put("authenticatorAttachment", "platform")
                put(
                    "response",
                    buildJsonObject {
                        put("clientDataJSON", b64url(clientDataJson))
                        put("authenticatorData", b64url(authData))
                        put("signature", b64url(signature))
                        put("userHandle", userHandle)
                    },
                )
            }
        return SupabaseResult.Success(credential)
    }

    private fun clientData(type: String, challenge: String): ByteArray =
        """{"type":"$type","challenge":"$challenge","origin":"$origin","crossOrigin":false}""".toByteArray()

    private fun authenticatorData(rpId: String, attested: Boolean): ByteArray {
        val rpIdHash = sha256(rpId.toByteArray())
        val flags = if (attested) 0x45 else 0x05 // UP|UV(|AT)
        val signCount = byteArrayOf(0, 0, 0, 1)
        var data = rpIdHash + byteArrayOf(flags.toByte()) + signCount
        if (attested) {
            val aaguid = ByteArray(16)
            val credLen = byteArrayOf((credentialId.size shr 8).toByte(), credentialId.size.toByte())
            data += aaguid + credLen + credentialId + coseKey()
        }
        return data
    }

    private fun coseKey(): ByteArray {
        val pub = keyPair.public as ECPublicKey
        val x = fixed(pub.w.affineX, 32)
        val y = fixed(pub.w.affineY, 32)
        return Cbor.map(
            listOf(
                Cbor.int(1) to Cbor.int(2), // kty: EC2
                Cbor.int(3) to Cbor.int(-7), // alg: ES256
                Cbor.int(-1) to Cbor.int(1), // crv: P-256
                Cbor.int(-2) to Cbor.bytes(x),
                Cbor.int(-3) to Cbor.bytes(y),
            ),
        )
    }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)

    private fun fixed(v: BigInteger, len: Int): ByteArray {
        val raw = v.toByteArray()
        val out = ByteArray(len)
        val src = if (raw.size > len) raw.copyOfRange(raw.size - len, raw.size) else raw
        System.arraycopy(src, 0, out, len - src.size, src.size)
        return out
    }

    private fun b64url(b: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
}

/** Minimal CBOR encoder — just the structures a WebAuthn attestation needs. */
private object Cbor {
    fun int(v: Int): ByteArray = if (v >= 0) head(0, v.toLong()) else head(1, (-1 - v).toLong())

    fun bytes(b: ByteArray): ByteArray = head(2, b.size.toLong()) + b

    fun text(s: String): ByteArray {
        val b = s.toByteArray()
        return head(3, b.size.toLong()) + b
    }

    fun map(pairs: List<Pair<ByteArray, ByteArray>>): ByteArray {
        var r = head(5, pairs.size.toLong())
        pairs.forEach { r += it.first + it.second }
        return r
    }

    private fun head(major: Int, len: Long): ByteArray {
        val m = major shl 5
        return when {
            len < 24 -> byteArrayOf((m or len.toInt()).toByte())
            len < 256 -> byteArrayOf((m or 24).toByte(), len.toByte())
            len < 65536 -> byteArrayOf((m or 25).toByte(), (len shr 8).toByte(), len.toByte())
            else ->
                byteArrayOf(
                    (m or 26).toByte(),
                    (len shr 24).toByte(), (len shr 16).toByte(), (len shr 8).toByte(), len.toByte(),
                )
        }
    }
}
