# supabase-auth

GoTrue authentication: email/password, phone, OTP, anonymous, ID-token, OAuth (17 providers), Web3, MFA (TOTP + phone), PKCE, and session management with single-flight auto-refresh and pluggable `SessionStorage`. Native sign-in providers (Google, Apple) and passkeys plug in through this module's `NativeAuthProvider` / `PasskeyAuthenticator` seams (see the `supabase-auth-google`, `supabase-auth-apple`, `supabase-auth-passkey` add-ons).

**Coordinate:** `io.github.androidpoet:supabase-auth`

```kotlin
import io.github.androidpoet.supabase.auth.createAuthClient

val auth = createAuthClient(client) // client from supabase-client

auth.signInWithEmail(email = "user@example.com", password = "hunter2")
    .onSuccess { session -> println("signed in: ${session.user.id}") }
    .onFailure { error -> println("auth failed: ${error.message}") }
```

Sign-up/in returns `SupabaseResult<Session>`. For native providers, run `auth.signInWith(provider)` (or `signInWithAndSaveSession(sessionManager, provider)`); for passkeys, `auth.signInWithPasskey(authenticator)`.

**Targets:** Android, JVM, iOS, macOS, tvOS, watchOS, Linux, Windows, WasmJs.

**Note:** Persist sessions with a `SessionManager` (`createSessionManager(...)`) — session storage is bring-your-own and pluggable; no platform keystore is bundled.
