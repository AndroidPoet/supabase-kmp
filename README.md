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

A lightweight, type-safe Kotlin Multiplatform SDK for [Supabase](https://supabase.com) — coroutine-first, modular, and built for every platform Kotlin runs on.

## Features

- **Type-safe Result monad** — `SupabaseResult<T>` with `map`, `flatMap`, `recover` — no exceptions leak to callers
- **Value class IDs** — `UserId`, `BucketId`, `SessionId`, `ChannelId` prevent mixups at compile time
- **PostgREST filter DSL** — `eq`, `neq`, `gt`, `gte`, `lt`, `lte`, `like`, `ilike`, `in`, `is`, `textSearch`, and more
- **OAuth (17 providers) + MFA** — TOTP and phone-based multi-factor auth with PKCE flow support
- **Session management** — Auto-refresh, token persistence, and session state observation
- **Realtime WebSocket** — Phoenix protocol with auto-reconnection, channel subscriptions, and presence
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

### Initialize the Client

```kotlin
// With Koin DI
startKoin {
    modules(
        supabaseModule(
            SupabaseConfig(
                url = "https://your-project.supabase.co",
                anonKey = "your-anon-key",
            )
        ),
        authModule,
        databaseModule,
        storageModule,
        realtimeModule,
        functionsModule,
    )
}

val auth: SupabaseAuth by inject()
val database: SupabaseDatabase by inject()
```

### Database — PostgREST with Filter DSL

```kotlin
@Serializable
data class Todo(val id: String, val title: String, val done: Boolean)

// Select with filters
val todos: SupabaseResult<List<Todo>> = database.from("todos")
    .select {
        eq("done", false)
        gt("priority", 3)
        order("created_at", ascending = false)
        limit(25)
    }

todos.onSuccess { items ->
    println("Got ${items.size} todos")
}.onFailure { error ->
    println("Error: ${error.message}")
}

// Insert
val result = database.from("todos")
    .insert(Todo(id = "1", title = "Ship it", done = false))

// Update with filters
database.from("todos")
    .update(mapOf("done" to true)) {
        eq("id", "1")
    }

// RPC call
val stats = database.rpc("get_dashboard_stats", args = mapOf("user_id" to userId))
```

### Auth — Sign In with Session Management

```kotlin
// Email/password sign-up
val session = auth.signUp(
    email = "user@example.com",
    password = "secure-password",
)

// Email/password sign-in
val session = auth.signInWithPassword(
    email = "user@example.com",
    password = "secure-password",
)

// OAuth (17 providers)
auth.signInWithOAuth(provider = OAuthProvider.GOOGLE)

// OTP (magic link)
auth.signInWithOtp(email = "user@example.com")

// MFA enrollment
val factor = auth.mfa.enroll(factorType = FactorType.TOTP)
auth.mfa.verify(factorId = factor.id, code = "123456")

// Observe session state
auth.sessionFlow.collect { session ->
    println("User: ${session?.user?.id}")
}
```

### Storage — File Upload & Download

```kotlin
val storage: SupabaseStorage by inject()

// Upload a file
val result = storage.from("avatars")
    .upload(path = "user123/avatar.png", data = imageBytes, contentType = "image/png")

// Get a signed URL
val url = storage.from("avatars")
    .createSignedUrl(path = "user123/avatar.png", expiresIn = 3600)

// Download
val bytes = storage.from("avatars")
    .download(path = "user123/avatar.png")

// List files in a bucket
val files = storage.from("avatars").list(path = "user123/")
```

### Realtime — WebSocket Subscriptions

```kotlin
val realtime: SupabaseRealtime by inject()

// Subscribe to table changes
realtime.channel("todos")
    .on("todos", event = RealtimeEvent.INSERT) { payload ->
        println("New todo: ${payload.new}")
    }
    .subscribe()

// Presence
realtime.channel("room:lobby")
    .onPresenceSync { state ->
        println("Online users: ${state.size}")
    }
    .subscribe()
```

### Edge Functions

```kotlin
val functions: SupabaseFunctions by inject()

val response = functions.invoke(
    functionName = "hello-world",
    body = mapOf("name" to "Kotlin"),
)

response.onSuccess { data ->
    println("Response: $data")
}
```

## Architecture

```
┌───────────────────────────────────────────────────────────────────────┐
│                             Your App                                  │
├──────────┬───────────┬───────────┬───────────┬───────────┬───────────┤
│ supabase-│ supabase- │ supabase- │ supabase- │ supabase- │ supabase- │
│ auth     │ database  │ storage   │ realtime  │ functions │ client    │
│          │           │           │           │           │           │
│ OAuth    │ PostgREST │ Buckets   │ WebSocket │ Invoke    │ HttpClient│
│ MFA/PKCE │ Filter DSL│ Upload    │ Phoenix   │ Typed Req │ Auth State│
│ Session  │ RPC       │ SignedURL │ Presence  │ Response  │ Koin DI   │
├──────────┴───────────┴───────────┴───────────┼───────────┤           │
│                                               │   Ktor    │           │
│                                               │  Engines  │           │
├───────────────────────────────────────────────┴───────────┤           │
│                      supabase-core                        │           │
│  SupabaseResult · Value IDs · Filter DSL · Error Types    │           │
└───────────────────────────────────────────────────────────┴───────────┘
```

## Modules

| Module | Artifact | Description |
|--------|----------|-------------|
| **supabase-core** | `io.github.androidpoet:supabase-core` | Result monad, error types, value class IDs, filter DSL |
| **supabase-client** | `io.github.androidpoet:supabase-client` | HTTP transport, platform engines, Koin module |
| **supabase-auth** | `io.github.androidpoet:supabase-auth` | Auth (email, phone, OTP, OAuth, MFA, PKCE), session management |
| **supabase-database** | `io.github.androidpoet:supabase-database` | PostgREST CRUD, RPC, typed filter extensions |
| **supabase-storage** | `io.github.androidpoet:supabase-storage` | Bucket management, file upload/download, signed URLs |
| **supabase-realtime** | `io.github.androidpoet:supabase-realtime` | WebSocket (Phoenix protocol), auto-reconnect, presence |
| **supabase-functions** | `io.github.androidpoet:supabase-functions` | Edge function invocation |

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
| Codebase | ~3K LOC | ~26K LOC |
| Error handling | Result monad | Exceptions |
| Type safety | Value class IDs | String IDs |
| DI | Koin modules | None |
| Dependencies | 3 core | 7+ |

## Tech Stack

| Layer | Library |
|-------|---------|
| Language | [Kotlin 2.1.10](https://kotlinlang.org/) |
| Networking | [Ktor 3.1.1](https://ktor.io/) |
| Serialization | [kotlinx.serialization 1.8.0](https://github.com/Kotlin/kotlinx.serialization) |
| Coroutines | [kotlinx.coroutines 1.10.1](https://github.com/Kotlin/kotlinx.coroutines) |
| Date/Time | [kotlinx-datetime 0.6.2](https://github.com/Kotlin/kotlinx-datetime) |
| DI | [Koin 4.0.2](https://insert-koin.io/) |
| Publishing | [vanniktech maven-publish 0.30.0](https://github.com/vanniktech/gradle-maven-publish-plugin) |

## Build

```bash
# Compile all targets
./gradlew compileKotlinJvm

# Run tests
./gradlew jvmTest

# Publish to Maven Central (CI only)
./gradlew publishAllPublicationsToMavenCentral --no-configuration-cache
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
