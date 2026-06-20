# supabase-auth-apple

Native Sign in with Apple, wired into `supabase-auth` as a `NativeAuthProvider`. Backed by Apple's `AuthenticationServices` — **the `appleAuthProvider(...)` factory is Apple-only** (it lives in `appleMain`, i.e. iOS and macOS). The shared `AppleSignInConfig` is in common code, but the provider can only be constructed on Apple platforms.

**Coordinate:** `io.github.androidpoet:supabase-auth-apple`

```kotlin
// iOS / macOS only
import io.github.androidpoet.supabase.auth.apple.AppleSignInConfig
import io.github.androidpoet.supabase.auth.apple.appleAuthProvider
import io.github.androidpoet.supabase.auth.native.signInWith

val provider = appleAuthProvider(
    AppleSignInConfig(
        nonce = freshNonce,      // generate a NEW random nonce per sign-in
        requestEmail = true,
        requestFullName = true,
    ),
)

auth.signInWith(provider) // auth from supabase-auth
    .onSuccess { session -> println("signed in: ${session.user.id}") }
```

**Targets:** the artifact publishes for Android, JVM, iOS, macOS and WasmJs, but the `appleAuthProvider` factory is implemented for **iOS and macOS only**.

**Note:** Pass a **fresh, random nonce on every sign-in**. Its SHA-256 hash is sent to Apple and the raw value is returned to Supabase to validate the token's `nonce` claim; reusing a nonce will make validation fail or weaken replay protection.
