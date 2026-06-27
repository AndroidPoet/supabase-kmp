# supabase-e2ee

Optional client-side end-to-end encryption. Derive an AES-256-GCM key on-device via ECDH (P-256) → HKDF-SHA256 so Supabase only ever stores ciphertext — the server never sees the key. Backed by `cryptography-kotlin` (WebCrypto on `wasmJs`), so it works on every target.

**Coordinate:** `io.github.androidpoet:supabase-e2ee`

```kotlin
import io.github.androidpoet.supabase.e2ee.generateE2eeKeyPair
import io.github.androidpoet.supabase.e2ee.deriveSession

// Each side generates a key pair and publishes its raw public key.
val keyPair = (generateE2eeKeyPair() as SupabaseResult.Success).value
// store keyPair.publicKey in a Supabase table; keep the private key on-device

// Peer-to-peer: derive the SAME shared session from the peer's public key.
val session = (keyPair.deriveSession(peerPublicKey) as SupabaseResult.Success).value

val ciphertext = session.encrypt("secret message")          // SupabaseResult<ByteArray>
val plaintext = session.decryptToString(/* ciphertext */)   // SupabaseResult<String>
```

Use `encryptValue<T>` / `decryptValue<T>` to round-trip any `@Serializable` value through JSON + ciphertext. The GCM nonce is embedded in the returned bytes.

### At-rest, single-user encryption

For encrypting **your own** data (notes, files) rather than messaging a peer, derive a deterministic self-key and persist the pair across launches:

```kotlin
import io.github.androidpoet.supabase.e2ee.exportPrivateKey
import io.github.androidpoet.supabase.e2ee.importE2eeKeyPair
import io.github.androidpoet.supabase.e2ee.deriveSelfSession

// First run: generate once, then persist.
val pair = (generateE2eeKeyPair() as SupabaseResult.Success).value
val privateDer = (pair.exportPrivateKey() as SupabaseResult.Success).value  // PKCS#8 DER
// store privateDer in your platform secure storage; keep pair.publicKey alongside it.

// Next launch: restore and decrypt your own history.
val restored = (importE2eeKeyPair(privateDer, savedPublicKey) as SupabaseResult.Success).value
val session = (restored.deriveSelfSession() as SupabaseResult.Success).value  // stable self-key
```

`deriveSelfSession()` keys against the pair's own public key, so the same pair always yields the same AES key — persist it and your ciphertext stays readable across launches. The private key (DER) is secret; store it in Keychain/Keystore (bring-your-own), never upload it.

**Targets:** Android, JVM, iOS, macOS, tvOS, watchOS, Linux, Windows, WasmJs.

**Note:** `publicKey` is raw-encoded and safe to publish; the private key stays inside `E2eeKeyPair` — never upload it. P-256 (not X25519) is used so every provider, including browser WebCrypto, is supported.

---

## Verified encrypted chat: KeyDirectory + EncryptedRoom

The crypto box above gives you encrypt/decrypt; this layer adds the plumbing for
a real plug-and-play encrypted chat: publishing/fetching public keys,
**verifying** them against tampering, and a live encrypted room over
`supabase-realtime`.

Apply the migration `supabase/migrations/20260628_add_e2ee_tables.sql`
(`device_keys` + `e2ee_messages`, both RLS-guarded).

```kotlin
val room = openEncryptedRoom(
    database = createDatabaseClient(supabase),
    realtime = createRealtimeClient(supabase, RealtimeConfig()),
    keyDirectory = SupabaseKeyDirectory(createDatabaseClient(supabase)),
    myKeyPair = keyPair,
    myUserId = myId,
    peerUserId = peerId,
    roomId = roomId,
    // trustStore = persist your own for durable verifications
    // requireVerified = true  (default — strict)
).getOrThrow()

// 1) Verify the peer OUT OF BAND (read the number aloud / compare a QR), then:
val number = room.safetyNumber()      // identical on both devices
if (userConfirmedItMatches) room.markVerified()

// 2) Send — Failure(E2eeErrorCodes.UNVERIFIED) if not verified in strict mode.
room.send("hello, end-to-end 🔐")

// 3) Read — history() + live messages(), both decrypt automatically.
val past = room.history().getOrThrow()
room.messages().collect { msg -> println(msg.plaintext ?: "🔒 (cannot decrypt)") }
```

Because the shared key is symmetric, **both peers decrypt the same rows** —
including their own sent messages (no encrypt-to-self workaround needed).

### Security model

The server is treated as **untrusted**. This module guarantees Supabase only
ever stores ciphertext, and hardens the two classic weak points:

- **MITM → safety numbers.** Key distribution flows through the server, so raw
  ECDH alone is man-in-the-middle-able. `safetyNumber()` returns the *same*
  number on both sides; comparing it out of band and calling `markVerified()` is
  what authenticates the peer. Strict mode blocks `send` until then.
- **Key-change rejection.** A peer's key changing after you trusted it (a
  tampered directory) is rejected as `E2eeErrorCodes.IDENTITY_CHANGED` until you
  re-verify (`TrustStore.remove` then re-open).

#### ⚠️ Honest caveat — no forward secrecy

The shared key is **static**. If a private key is ever extracted, an attacker
can decrypt **all** past and future messages. That's sufficient for "the server
can't read it" (the dominant commercial threat), but it is **not** Signal-grade
— do **not** advertise forward secrecy. For a Double Ratchet you need a real
libsignal binding (which is **AGPL**).

### API surface (chat plumbing)

| Symbol | Purpose |
|---|---|
| `safetyNumber(localPub, peerPub)` | out-of-band verification number (same on both sides) |
| `TrustStore` / `InMemoryTrustStore` / `TrustLevel` / `TrustEntry` | per-peer trust ledger (BYO persistence) |
| `KeyDirectory` / `SupabaseKeyDirectory` / `InMemoryKeyDirectory` | publish / fetch public keys |
| `openEncryptedRoom(...)` → `EncryptedRoom` | verify-first encrypted chat room |
| `EncryptedRoom.safetyNumber` / `markVerified` / `isVerified` / `send` / `history` / `messages` | room operations |
| `DecryptedMessage` / `E2eeErrorCodes` | decrypted result / error codes |

Bring your own persistence for `TrustStore` and key storage — this module never
bundles a platform secure-storage dependency.
