# supabase-auth-google

Native Google sign-in, wired into `supabase-auth` as a `NativeAuthProvider`. Backed by the Android Credential Manager — **the `googleAuthProvider(...)` factory is Android-only** (it lives in `androidMain` and needs a `Context`). The shared `GoogleSignInConfig` is in common code so you can declare it in shared source, but you can only construct the provider on Android.

**Coordinate:** `io.github.androidpoet:supabase-auth-google`

```kotlin
// Android only
import io.github.androidpoet.supabase.auth.google.GoogleSignInConfig
import io.github.androidpoet.supabase.auth.google.googleAuthProvider
import io.github.androidpoet.supabase.auth.native.signInWith

val provider = googleAuthProvider(
    context = activity,
    config = GoogleSignInConfig(
        serverClientId = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com",
        nonce = freshNonce, // generate a NEW random nonce per sign-in
    ),
)

auth.signInWith(provider) // auth from supabase-auth
    .onSuccess { session -> println("signed in: ${session.user.id}") }
```

**Targets:** the artifact publishes for Android, JVM, iOS, macOS and WasmJs, but the `googleAuthProvider` factory is implemented for **Android only**.

**Note:** Pass a **fresh, random nonce on every sign-in**. The config's raw nonce is forwarded to Supabase for validation; reusing one defeats replay protection.
