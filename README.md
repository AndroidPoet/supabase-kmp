<p align="center">
  <img src="art/logo.png" width="720" alt="Supabase KMP Logo">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.1.10-blue.svg?logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/Ktor-3.1.1-blue.svg" alt="Ktor">
  <img src="https://img.shields.io/badge/Platform-Android%20%7C%20iOS%20%7C%20macOS%20%7C%20tvOS%20%7C%20watchOS%20%7C%20JVM%20%7C%20Linux%20%7C%20Windows%20%7C%20WasmJs-green.svg" alt="Platforms">
  <img src="https://img.shields.io/badge/Maven%20Central-0.1.0-blue.svg" alt="Maven Central">
  <img src="https://img.shields.io/badge/License-MIT-orange.svg" alt="License">
</p>

# Supabase KMP

Kotlin Multiplatform SDK for [Supabase](https://supabase.com) — type-safe, coroutine-first, modular client for every platform Kotlin runs on.

## Features

- **Type-safe Result monad** — `SupabaseResult<T>` with `map`, `flatMap`, `recover` — no exceptions leak to callers
- **Value class IDs** — `UserId`, `BucketId`, `SessionId`, `ChannelId` prevent mixups at compile time
- **PostgREST filter DSL** — `eq`, `neq`, `gt`, `like`, `ilike`, `in`, `is`, `textSearch`, `contains`, and more
- **OAuth (17 providers) + MFA** — TOTP and phone-based multi-factor auth with PKCE flow support
- **Session management** — Auto-refresh, token persistence, `SessionState` observation via `StateFlow`
- **Realtime WebSocket** — Phoenix protocol with auto-reconnection, exponential backoff, presence
- **Edge Functions** — Invoke Supabase Edge Functions with typed request/response
- **Koin DI** — First-class dependency injection modules for every component
- **15 platform targets** — Android, iOS, macOS, tvOS, watchOS, JVM, Linux, Windows, and WasmJs

## Setup

Add the dependencies you need to your `build.gradle.kts`:

```kotlin
// Version catalog (gradle/libs.versions.toml)
[versions]
supabase-kmp = "0.1.0"

[libraries]
supabase-core = { module = "io.github.androidpoet:supabase-core", version.ref = "supabase-kmp" }
supabase-client = { module = "io.github.androidpoet:supabase-client", version.ref = "supabase-kmp" }
supabase-auth = { module = "io.github.androidpoet:supabase-auth", version.ref = "supabase-kmp" }
supabase-database = { module = "io.github.androidpoet:supabase-database", version.ref = "supabase-kmp" }
supabase-storage = { module = "io.github.androidpoet:supabase-storage", version.ref = "supabase-kmp" }
supabase-realtime = { module = "io.github.androidpoet:supabase-realtime", version.ref = "supabase-kmp" }
supabase-functions = { module = "io.github.androidpoet:supabase-functions", version.ref = "supabase-kmp" }
```

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.supabase.client)     // includes supabase-core
            implementation(libs.supabase.auth)       // optional
            implementation(libs.supabase.database)   // optional
            implementation(libs.supabase.storage)    // optional
            implementation(libs.supabase.realtime)   // optional
            implementation(libs.supabase.functions)  // optional
        }
    }
}
```

## Usage

### Create a Client

```kotlin
// Direct instantiation
val client = Supabase.create(
    projectUrl = "https://your-project.supabase.co",
    apiKey = "your-anon-key",
) {
    logging = true
}

// Or with Koin DI
startKoin {
    modules(
        supabaseModule(
            projectUrl = "https://your-project.supabase.co",
            apiKey = "your-anon-key",
            config = SupabaseConfig(),
        ),
        authModule(),
        databaseModule,
        storageModule,
        realtimeModule(),
        functionsModule,
    )
}

val auth: AuthClient by inject()
val database: DatabaseClient by inject()
```

### Database — PostgREST with Filter DSL

```kotlin
@Serializable
data class Todo(val id: String, val title: String, val done: Boolean)

// Select with filters
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

// Insert
val result = database.insertTyped(
    table = "todos",
    value = Todo(id = "1", title = "Ship it", done = false),
)

// Update with filters
database.update(table = "todos", body = """{"done": true}""") {
    eq("id", "1")
}

// RPC call
val stats = database.rpc("get_dashboard_stats", params = """{"user_id": "123"}""")
```

### Auth — Sign In with Session Management

```kotlin
val auth: AuthClient by inject()
val sessionManager: SessionManager by inject()

// Email/password sign-up
auth.signUpWithEmail(
    email = "user@example.com",
    password = "secure-password",
).onSuccess { session ->
    sessionManager.saveSession(session)
}

// Email/password sign-in
auth.signInWithEmail(
    email = "user@example.com",
    password = "secure-password",
).onSuccess { session ->
    sessionManager.saveSession(session)
}

// OAuth — get URL for the provider, open in browser
val oauthUrl = auth.getOAuthSignInUrl(
    provider = OAuthProvider.GOOGLE,
    redirectTo = "myapp://callback",
)

// After OAuth callback, exchange the code
val pkce = auth.generatePkceParams()
auth.exchangeCodeForSession(authCode = "code-from-callback", codeVerifier = pkce.codeVerifier)

// MFA enrollment
val accessToken = sessionManager.accessToken!!
auth.mfaEnroll(factorType = MfaFactorType.TOTP, accessToken = accessToken).onSuccess { factor ->
    // Show factor.totp?.qrCode to user, then verify:
    auth.mfaVerify(
        factorId = factor.id,
        challengeId = "challenge-id",
        code = "123456",
        accessToken = accessToken,
    )
}

// Observe session state
sessionManager.sessionState.collect { state ->
    when (state) {
        is SessionState.Authenticated -> println("User: ${state.session.user.id}")
        is SessionState.Expired -> println("Session expired, refreshing...")
        SessionState.NotAuthenticated -> println("Signed out")
        SessionState.Loading -> println("Loading...")
    }
}
```

### Storage — File Upload & Download

```kotlin
val storage: StorageClient by inject()

// Upload a file
storage.upload(
    bucket = "avatars",
    path = "user123/avatar.png",
    data = imageBytes,
    contentType = "image/png",
).onSuccess { key ->
    println("Uploaded: $key")
}

// Get a signed URL
storage.createSignedUrl(
    bucket = "avatars",
    path = "user123/avatar.png",
    expiresIn = 3600,
).onSuccess { url ->
    println("Signed URL: $url")
}

// Get public URL (no auth needed)
val publicUrl = storage.getPublicUrl(bucket = "avatars", path = "user123/avatar.png")

// List files
storage.list(bucket = "avatars", prefix = "user123/").onSuccess { files ->
    files.forEach { println(it.name) }
}
```

### Realtime — WebSocket Subscriptions

```kotlin
val realtime: RealtimeClient by inject()

// Connect and observe connection state
realtime.connect()
realtime.connectionState.collect { state ->
    when (state) {
        is ConnectionState.Connected -> println("Connected")
        is ConnectionState.Reconnecting -> println("Reconnecting attempt ${state.attempt}...")
        is ConnectionState.Failed -> println("Failed: ${state.reason}")
        else -> {}
    }
}

// Subscribe to table changes
val subscription = realtime.channel("todos")
    .onPostgresChange(table = "todos", event = PostgresChangeEvent.INSERT) { record ->
        println("New todo: $record")
    }
    .onPostgresChange(table = "todos", event = PostgresChangeEvent.DELETE) { record ->
        println("Deleted: $record")
    }
    .subscribe()

// Presence
realtime.channel("room:lobby")
    .onPresence { state ->
        println("Online users: ${state.size}")
    }
    .subscribe()

// Broadcast
subscription.broadcast(event = "cursor", payload = buildJsonObject {
    put("x", 100)
    put("y", 200)
})

// Unsubscribe
subscription.unsubscribe()
realtime.disconnect()
```

### Edge Functions

```kotlin
val functions: FunctionsClient by inject()

// Invoke a function
functions.invoke(
    functionName = "hello-world",
    body = """{"name": "Kotlin"}""",
).onSuccess { data ->
    println("Response: $data")
}

// Typed response
functions.invokeTyped<WelcomeResponse>(
    functionName = "hello-world",
    body = """{"name": "Kotlin"}""",
).onSuccess { response ->
    println("Message: ${response.message}")
}

// Binary body
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
│ Session  │ RPC       │ SignedURL │ Presence  │ Response  │ Koin DI │
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
| **supabase-client** | `io.github.androidpoet:supabase-client` | HTTP transport, platform engines, auth state, Koin module |
| **supabase-auth** | `io.github.androidpoet:supabase-auth` | Email, phone, OTP, OAuth (17 providers), MFA, PKCE, session management |
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
| **DI** | Koin modules | Manual composition |
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
| DI | [Koin 4.0.2](https://insert-koin.io/) |
| Publishing | [vanniktech maven-publish 0.30.0](https://github.com/vanniktech/gradle-maven-publish-plugin) |

## Build

```bash
# Compile all targets
./gradlew compileKotlinJvm

# Run tests
./gradlew jvmTest

# Full build (all platforms)
./gradlew build --no-configuration-cache

# Publish to Maven Central (CI only)
./gradlew publishAllPublicationsToMavenCentral --no-configuration-cache
```

## Documentation

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
