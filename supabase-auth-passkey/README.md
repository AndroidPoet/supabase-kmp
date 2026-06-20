# supabase-auth-passkey

Passkey / WebAuthn support for `supabase-auth`. The HTTP ceremony (start/verify registration and authentication) lives in `supabase-auth`; this module supplies the device-side `PasskeyAuthenticator` driver that runs the actual native ceremony, plus the `AuthClient.registerPasskey` / `AuthClient.signInWithPasskey` extensions that stitch the steps together.

**Coordinate:** `io.github.androidpoet:supabase-auth-passkey`

```kotlin
import io.github.androidpoet.supabase.auth.passkey.PasskeysKmpAuthenticator
import io.github.androidpoet.supabase.auth.passkey.signInWithPasskey

// Wrap a passkeys-kmp PasskeyClient for the current platform.
val authenticator = PasskeysKmpAuthenticator(passkeyClient)

auth.signInWithPasskey(authenticator) // auth from supabase-auth
    .onSuccess { session -> println("signed in: ${session.user.id}") }
    .onFailure { error -> println("passkey failed (${error.code}): ${error.message}") }
```

Register a new passkey for a signed-in user with `auth.registerPasskey(accessToken, authenticator)`. `PasskeysKmpAuthenticator` drives the native ceremony on every Supabase target via `passkeys-kmp`; on Android you can alternatively use the bundled `CredentialManagerPasskeyAuthenticator`, or supply your own `PasskeyAuthenticator`.

**Targets:** Android, JVM, iOS, macOS, WasmJs (the platforms passkeys-kmp drives natively).

**Note:** Failures surface stable `error.code`s (`passkey_cancelled`, `passkey_no_credentials`, `passkey_unsupported`, `passkey_ceremony_failed`) so you can branch on user cancellation vs. unsupported device regardless of the authenticator in use.
