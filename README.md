<p align="center">
  <img src="art/logo.png" width="720" alt="Supabase KMP Logo">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.1.10-blue.svg?logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/Ktor-3.1.1-blue.svg" alt="Ktor">
  <img src="https://img.shields.io/badge/Platform-Android%20%7C%20iOS%20%7C%20macOS%20%7C%20tvOS%20%7C%20watchOS%20%7C%20JVM%20%7C%20Linux%20%7C%20Windows%20%7C%20WasmJs-green.svg" alt="Platforms">
  <img src="https://img.shields.io/maven-central/v/io.github.androidpoet/supabase-core?color=blue&label=Maven%20Central" alt="Maven Central">
  <img src="https://img.shields.io/badge/License-MIT-orange.svg" alt="License">
</p>

# Supabase KMP

Kotlin Multiplatform SDK for [Supabase](https://supabase.com) — type-safe, coroutine-first, modular client for every platform Kotlin runs on.

## Features

- **Type-safe Result monad** — `SupabaseResult<T>` with `map`, `flatMap`, `recover` — no exceptions leak to callers
- **Value class IDs** — `UserId`, `BucketId`, `SessionId`, `ChannelId` prevent mixups at compile time
- **PostgREST filter DSL** — `eq`, `neq`, `gt`, `like`, `ilike`, `in`, `is`, `textSearch`, `contains`, and more
- **OAuth (17 providers) + MFA** — TOTP and phone-based multi-factor auth with CSPRNG-backed PKCE
- **Session management** — Single-flight auto-refresh, pluggable persistence (`SessionStorage`), `SessionState` via `StateFlow`
- **Realtime WebSocket** — Phoenix protocol with auto-reconnection, exponential backoff, presence, offline send buffering
- **Secure by default** — Credential headers redacted from logs, smart retries (`429`/`5xx` + `Retry-After`)
- **Edge Functions** — Invoke Supabase Edge Functions with typed request/response
- **Manual composition** — Explicit factory helpers for each feature module
- **15 platform targets** — Android, iOS, macOS, tvOS, watchOS, JVM, Linux, Windows, and WasmJs

## Setup

Add the dependencies you need to your `build.gradle.kts`:

```kotlin
[versions]
supabase-kmp = "0.3.5"

[libraries]
supabase-core = { module = "io.github.androidpoet:supabase-core", version.ref = "supabase-kmp" }
supabase-client = { module = "io.github.androidpoet:supabase-client", version.ref = "supabase-kmp" }
supabase-auth = { module = "io.github.androidpoet:supabase-auth", version.ref = "supabase-kmp" }
supabase-auth-admin = { module = "io.github.androidpoet:supabase-auth-admin", version.ref = "supabase-kmp" }
supabase-auth-google = { module = "io.github.androidpoet:supabase-auth-google", version.ref = "supabase-kmp" }
supabase-auth-apple = { module = "io.github.androidpoet:supabase-auth-apple", version.ref = "supabase-kmp" }
supabase-database = { module = "io.github.androidpoet:supabase-database", version.ref = "supabase-kmp" }
supabase-storage = { module = "io.github.androidpoet:supabase-storage", version.ref = "supabase-kmp" }
supabase-realtime = { module = "io.github.androidpoet:supabase-realtime", version.ref = "supabase-kmp" }
supabase-functions = { module = "io.github.androidpoet:supabase-functions", version.ref = "supabase-kmp" }
```

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.supabase.client)
            implementation(libs.supabase.auth)
            // Service-role/admin APIs only. Do not use in anon-key mobile clients.
            implementation(libs.supabase.auth.admin)
            implementation(libs.supabase.database)
            implementation(libs.supabase.storage)
            implementation(libs.supabase.realtime)
            implementation(libs.supabase.functions)
        }
    }
}
```

## Usage

### Create a Client

```kotlin
val client = Supabase.create(
    projectUrl = "https://your-project.supabase.co",
    apiKey = "your-anon-key",
) {
    logging = true
}

val auth = createAuthClient(client)
val database = createDatabaseClient(client)
val storage = createStorageClient(client)
val realtime = createRealtimeClient(client)
val functions = createFunctionsClient(client)
```

### Database — PostgREST with Filter DSL

```kotlin
@Serializable
data class Todo(val id: String, val title: String, val done: Boolean)

@Serializable
data class TodoPatch(val done: Boolean)

@Serializable
data class DashboardStatsRequest(val user_id: String)

@Serializable
data class DashboardStats(val total: Int)

val todos: SupabaseResult<List<Todo>> = database.selectTyped<Todo>(
    table = "todos",
) {
    eq("done", "false")
    gt("priority", "3")
    order("created_at", ascending = false)
    limit(25)
}

todos.onSuccess { items ->
    println("Got ${items.size} todos")
}.onFailure { error ->
    println("Error: ${error.message}")
}

val result = database.insertTyped(
    table = "todos",
    value = Todo(id = "1", title = "Ship it", done = false),
)

database.updateTyped(
    table = "todos",
    value = TodoPatch(done = true),
) {
    eq("id", "1")
}

val stats: SupabaseResult<DashboardStats> = database.rpcTyped<DashboardStatsRequest, DashboardStats>(
    function = "get_dashboard_stats",
    request = DashboardStatsRequest(user_id = "123"),
)
```

### Auth — Sign In with Session Management

```kotlin
val auth = createAuthClient(client)
val sessionManager = createSessionManager(authClient = auth, supabaseClient = client)

auth.signUpWithEmail(
    email = "user@example.com",
    password = "secure-password",
).onSuccess { session ->
    sessionManager.saveSession(session)
}

auth.signInWithEmail(
    email = "user@example.com",
    password = "secure-password",
).onSuccess { session ->
    sessionManager.saveSession(session)
}

val oauthUrl = auth.getOAuthSignInUrl(
    provider = OAuthProvider.GOOGLE,
    redirectTo = "myapp://callback",
)

val pkce = auth.generatePkceParams()
auth.exchangeCodeForSession(authCode = "code-from-callback", codeVerifier = pkce.codeVerifier)

val accessToken = sessionManager.accessToken!!
auth.mfaEnroll(factorType = MfaFactorType.TOTP, accessToken = accessToken).onSuccess { factor ->
    auth.mfaVerify(
        factorId = factor.id,
        challengeId = "challenge-id",
        code = "123456",
        accessToken = accessToken,
    )
}

sessionManager.sessionState.collect { state ->
    when (state) {
        is SessionState.Authenticated -> println("User: ${state.session.user.id}")
        is SessionState.Expired -> println("Session expired, refreshing...")
        SessionState.NotAuthenticated -> println("Signed out")
        SessionState.Loading -> println("Loading...")
    }
}
```

### Persisting Sessions

The default `InMemorySessionStorage` keeps the session only for the process
lifetime. To keep users signed in across restarts, back `KeyValueSessionStorage`
with your platform's secure store — Keychain (Apple), EncryptedSharedPreferences /
DataStore (Android), `localStorage` (Web), a file (desktop). You only implement
three string operations; serialization is handled for you:

```kotlin
class KeychainStore : KeyValueStore {
    override suspend fun get(key: String): String? = /* read */
    override suspend fun set(key: String, value: String) { /* write */ }
    override suspend fun remove(key: String) { /* delete */ }
}

val sessionManager = createSessionManager(
    authClient = auth,
    supabaseClient = client,
    config = SessionConfig(storage = KeyValueSessionStorage(KeychainStore())),
)

// On launch: load the stored session and refresh it in one call.
sessionManager.restoreSession()
```

> [!NOTE]
> `supabase-auth-admin` uses the service-role key — use it only in trusted
> server-side contexts, never in an anon-key client app.

### Native Sign-In (Google & Apple)

Every native sign-in produces an OIDC ID token, which `signInWithIdToken`
already exchanges for a session. The optional `supabase-auth-google` and
`supabase-auth-apple` modules wrap the platform UIs and hand you a
`NativeAuthProvider`; pass it to `auth.signInWith(...)`. The core never depends
on a provider SDK you didn't add — implement `NativeAuthProvider` yourself for
any other provider or platform.

```kotlin
// Android — Google via Credential Manager (androidMain):
val provider = googleAuthProvider(
    context = activity,
    config = GoogleSignInConfig(serverClientId = "<web-client-id>", nonce = rawNonce),
)
val session = auth.signInWith(provider) // → SupabaseResult<Session>

// Apple platforms — Sign in with Apple via AuthenticationServices (iosMain/macosMain):
val session = auth.signInWith(appleAuthProvider(AppleSignInConfig(nonce = rawNonce)))
```

> [!NOTE]
> The provider factories are platform-specific (they need an Android `Context`
> or an Apple presentation anchor), so construct them in platform source sets.
> On platforms without a bundled provider, use the redirect flow
> (`signInWithOAuth`) or supply your own `NativeAuthProvider`.

### Storage — File Upload & Download

```kotlin
val storage = createStorageClient(client)

storage.upload(
    bucket = "avatars",
    path = "user123/avatar.png",
    data = imageBytes,
    contentType = "image/png",
).onSuccess { key ->
    println("Uploaded: $key")
}

storage.createSignedUrl(
    bucket = "avatars",
    path = "user123/avatar.png",
    expiresIn = 3600,
).onSuccess { url ->
    println("Signed URL: $url")
}

val publicUrl = storage.getPublicUrl(bucket = "avatars", path = "user123/avatar.png")

storage.list(bucket = "avatars", prefix = "user123/").onSuccess { files ->
    files.forEach { println(it.name) }
}
```

### Realtime — WebSocket Subscriptions

```kotlin
val realtime = createRealtimeClient(client)

realtime.connect()
realtime.connectionState.collect { state ->
    when (state) {
        is ConnectionState.Connected -> println("Connected")
        is ConnectionState.Reconnecting -> println("Reconnecting attempt ${state.attempt}...")
        is ConnectionState.Failed -> println("Failed: ${state.reason}")
        else -> {}
    }
}

val subscription = realtime.channel("todos")
    .onPostgresChange(table = "todos", event = PostgresChangeEvent.INSERT) { record ->
        println("New todo: $record")
    }
    .onPostgresChange(table = "todos", event = PostgresChangeEvent.DELETE) { record ->
        println("Deleted: $record")
    }
    .subscribe()

realtime.channel("room:lobby")
    .onPresence { state ->
        println("Online users: ${state.size}")
    }
    .subscribe()

subscription.broadcast(event = "cursor", payload = buildJsonObject {
    put("x", 100)
    put("y", 200)
})

subscription.unsubscribe()
realtime.disconnect()
```

### Edge Functions

```kotlin
val functions = createFunctionsClient(client)

functions.invoke(
    functionName = "hello-world",
    body = """{"name": "Kotlin"}""",
).onSuccess { data ->
    println("Response: $data")
}

functions.invokeTyped<WelcomeResponse>(
    functionName = "hello-world",
    body = """{"name": "Kotlin"}""",
).onSuccess { response ->
    println("Message: ${response.message}")
}

functions.invokeWithBody(
    functionName = "process-image",
    body = imageBytes,
    contentType = "image/png",
)
```

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                              Your App                               │
├──────────┬───────────┬───────────┬───────────┬───────────┬─────────┤
│ supabase │ supabase  │ supabase  │ supabase  │ supabase  │supabase │
│ auth     │ database  │ storage   │ realtime  │ functions │ client  │
│          │           │           │           │           │         │
│ OAuth    │ PostgREST │ Buckets   │ WebSocket │ Invoke    │ HTTP    │
│ MFA/PKCE │ Filter DSL│ Upload    │ Phoenix   │ Typed Req │ Auth    │
│ Session  │ RPC       │ SignedURL │ Presence  │ Response  │ Factory │
│ Manager  │ Typed Ext │ Public URL│ Reconnect │ Binary    │ Config  │
├──────────┴───────────┴───────────┼───────────┤           │         │
│                                   │   Ktor    │           │         │
│                                   │  Engines  │           │         │
├───────────────────────────────────┴───────────┤           │         │
│                 supabase-core                  │           │         │
│  SupabaseResult · Value IDs · Filter DSL      │           │         │
│  Error Types · Response Models                │           │         │
└────────────────────────────────────────────────┴───────────┴─────────┘
```

## Modules

| Module | Artifact | Description |
|--------|----------|-------------|
| **supabase-core** | `io.github.androidpoet:supabase-core` | Result monad, error types, value class IDs, filter DSL |
| **supabase-client** | `io.github.androidpoet:supabase-client` | HTTP transport, platform engines, auth state, factory wiring |
| **supabase-auth** | `io.github.androidpoet:supabase-auth` | Email, phone, OTP, OAuth (17 providers), MFA, PKCE, session management |
| **supabase-auth-google** | `io.github.androidpoet:supabase-auth-google` | Native Google sign-in (Android Credential Manager) → `NativeAuthProvider` |
| **supabase-auth-apple** | `io.github.androidpoet:supabase-auth-apple` | Native Sign in with Apple (AuthenticationServices) → `NativeAuthProvider` |
| **supabase-database** | `io.github.androidpoet:supabase-database` | PostgREST CRUD, RPC, typed filter extensions |
| **supabase-storage** | `io.github.androidpoet:supabase-storage` | Bucket CRUD, file upload/download, signed & public URLs |
| **supabase-realtime** | `io.github.androidpoet:supabase-realtime` | WebSocket (Phoenix protocol), auto-reconnect, broadcast, presence |
| **supabase-functions** | `io.github.androidpoet:supabase-functions` | Edge function invocation with typed responses |

## Targets

| Platform | Target | Ktor Engine |
|----------|--------|-------------|
| Android | `androidTarget()` | OkHttp |
| JVM | `jvm()` | OkHttp |
| iOS | `iosX64()` `iosArm64()` `iosSimulatorArm64()` | Darwin |
| macOS | `macosX64()` `macosArm64()` | Darwin |
| tvOS | `tvosX64()` `tvosArm64()` `tvosSimulatorArm64()` | Darwin |
| watchOS | `watchosX64()` `watchosArm64()` `watchosSimulatorArm64()` | Darwin |
| Linux | `linuxX64()` | CIO |
| Windows | `mingwX64()` | CIO |
| Web | `wasmJs()` | Js |

## Why supabase-kmp?

| | supabase-kmp | supabase-kt (official) |
|--|--|--|
| **Codebase** | ~3K LOC | ~26K LOC |
| **Error handling** | `SupabaseResult<T>` monad | Thrown exceptions |
| **Type safety** | Value class IDs | String IDs |
| **Dependencies** | 3 core | 7+ |
| **Session mgmt** | `SessionManager` + `StateFlow` | Built-in (heavier) |
| **Reconnection** | Exponential backoff | Exponential backoff |
| **Targets** | 15 | 15+ |

## Tech Stack

| Layer | Library |
|-------|---------|
| Language | [Kotlin 2.1.10](https://kotlinlang.org/) |
| Networking | [Ktor 3.1.1](https://ktor.io/) |
| Serialization | [kotlinx.serialization 1.8.0](https://github.com/Kotlin/kotlinx.serialization) |
| Coroutines | [kotlinx.coroutines 1.10.1](https://github.com/Kotlin/kotlinx.coroutines) |
| Publishing | [vanniktech maven-publish 0.30.0](https://github.com/vanniktech/gradle-maven-publish-plugin) |

## Build

```bash
# Compile a single target (swap in IosArm64, LinuxX64, WasmJs, MingwX64, …)
./gradlew compileKotlinJvm

# Run JVM unit tests
./gradlew jvmTest

# Full build (all platforms)
./gradlew build --no-configuration-cache

# Publish to Maven Central (CI only, auto-release)
./gradlew publishAndReleaseToMavenCentral --no-configuration-cache
```

### Quality gates

These run in CI on every PR; run them locally before pushing:

```bash
./gradlew detekt        # static analysis (config/detekt/detekt.yml)
./gradlew apiCheck      # fails on unintended public/binary API changes
./gradlew jvmTest koverHtmlReport   # tests + coverage report
./gradlew dokkaHtmlMultiModule      # API reference docs from KDoc
```

If you intentionally change the public API, regenerate the dumps with
`./gradlew apiDump` and commit them. See [`CONTRIBUTING.md`](CONTRIBUTING.md) for
the full workflow.

## Documentation

- Release notes: [`CHANGELOG.md`](CHANGELOG.md)
- Contributing: [`CONTRIBUTING.md`](CONTRIBUTING.md)
- Security policy: [`SECURITY.md`](SECURITY.md)
- GitBook-ready pages: [`docs/`](docs/)
- GitBook config: [`.gitbook.yaml`](.gitbook.yaml)
- Publishing guide: [`docs/guides/gitbook-publishing.md`](docs/guides/gitbook-publishing.md)
- MkDocs config: [`mkdocs.yml`](mkdocs.yml)
- MkDocs deps: [`requirements-docs.txt`](requirements-docs.txt)

### Build Docs Locally (MkDocs)

```bash
python -m pip install -r requirements-docs.txt
mkdocs serve
```

### Publish Docs to GitHub Pages (MkDocs)

- Automated via GitHub Actions workflow:
  - [`.github/workflows/docs-mkdocs.yml`](.github/workflows/docs-mkdocs.yml)
- Manual publish:

```bash
mkdocs gh-deploy --force
```

## License

```
MIT License

Copyright (c) 2026 Ranbir Singh

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
