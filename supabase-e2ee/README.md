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
