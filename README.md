<p align="center">
  <img src="art/logo.png" width="720" alt="Supabase KMP Logo">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.4.0-blue.svg?logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/Ktor-3.5.0-blue.svg" alt="Ktor">
  <img src="https://img.shields.io/badge/Platform-Android%20%7C%20iOS%20%7C%20macOS%20%7C%20tvOS%20%7C%20watchOS%20%7C%20JVM%20%7C%20Linux%20%7C%20Windows%20%7C%20WasmJs-green.svg" alt="Platforms">
  <img src="https://img.shields.io/maven-central/v/io.github.androidpoet/supabase-core?color=blue&label=Maven%20Central" alt="Maven Central">
  <img src="https://img.shields.io/badge/License-MIT-orange.svg" alt="License">
</p>

# Supabase KMP

Kotlin Multiplatform SDK for [Supabase](https://supabase.com) — type-safe, coroutine-first, modular client for every platform Kotlin runs on.

### 📖 [Read the docs → androidpoet.github.io/supabase-kmp](https://androidpoet.github.io/supabase-kmp/)

Full guides for [Authentication](https://androidpoet.github.io/supabase-kmp/auth), [Database](https://androidpoet.github.io/supabase-kmp/database), [Storage](https://androidpoet.github.io/supabase-kmp/storage), [Realtime](https://androidpoet.github.io/supabase-kmp/realtime) and [Edge Functions](https://androidpoet.github.io/supabase-kmp/functions) live on the docs site. This README is the quick start.

## Features

- **Type-safe Result monad** — `SupabaseResult<T>` with `map`, `flatMap`, `recover` — no exceptions leak to callers
- **Value class IDs** — `UserId`, `BucketId`, `SessionId`, `ChannelId` prevent mixups at compile time
- **PostgREST filter DSL** — `eq`, `neq`, `gt`, `like`, `ilike`, `in`, `is`, `textSearch`, `contains`, and more
- **OAuth (17 providers) + MFA** — TOTP and phone-based multi-factor auth with CSPRNG-backed PKCE
- **Native passkeys** — cross-platform WebAuthn ceremony (Android, iOS, macOS, JVM, Wasm) behind a pluggable authenticator, or bring your own
- **Session management** — Single-flight auto-refresh, pluggable persistence (`SessionStorage`), `SessionState` via `StateFlow`
- **Realtime WebSocket** — Phoenix protocol with auto-reconnection, exponential backoff, presence, offline send buffering, binary broadcast (raw `ByteArray`)
- **Secure by default** — Credential headers redacted from logs, smart retries (`429`/`5xx` + `Retry-After`)
- **End-to-end encryption** — Optional `supabase-e2ee`: derive a shared AES-256-GCM key on-device (ECDH → HKDF) so Supabase only ever stores ciphertext
- **16 platform targets** — Android, iOS, macOS, tvOS, watchOS, JVM, Linux, Windows, and WasmJs

## Setup

> **Requirements:** the published Android/JVM artifacts are compiled for **Java 17**
> and ship inline functions, so consuming modules must build with **`jvmTarget = 17`**
> (Android `compileOptions`/`kotlinOptions` JVM 17, or JVM `jvmToolchain(17)`).
> Building at a lower target fails with *"cannot inline bytecode built with JVM
> target 17 into bytecode being built with JVM target 11"*.

Add the modules you need to your version catalog:

```toml
[versions]
supabase-kmp = "0.9.0"

[libraries]
supabase-client = { module = "io.github.androidpoet:supabase-client", version.ref = "supabase-kmp" }
supabase-auth = { module = "io.github.androidpoet:supabase-auth", version.ref = "supabase-kmp" }
supabase-database = { module = "io.github.androidpoet:supabase-database", version.ref = "supabase-kmp" }
supabase-storage = { module = "io.github.androidpoet:supabase-storage", version.ref = "supabase-kmp" }
supabase-realtime = { module = "io.github.androidpoet:supabase-realtime", version.ref = "supabase-kmp" }
supabase-functions = { module = "io.github.androidpoet:supabase-functions", version.ref = "supabase-kmp" }

# Optional add-ons — only add the ones you need
supabase-auth-google = { module = "io.github.androidpoet:supabase-auth-google", version.ref = "supabase-kmp" }
supabase-auth-apple = { module = "io.github.androidpoet:supabase-auth-apple", version.ref = "supabase-kmp" }
supabase-auth-passkey = { module = "io.github.androidpoet:supabase-auth-passkey", version.ref = "supabase-kmp" }
supabase-auth-admin = { module = "io.github.androidpoet:supabase-auth-admin", version.ref = "supabase-kmp" }
supabase-e2ee = { module = "io.github.androidpoet:supabase-e2ee", version.ref = "supabase-kmp" }
```

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.supabase.client)
            implementation(libs.supabase.auth)
            implementation(libs.supabase.database)
            implementation(libs.supabase.storage)
            implementation(libs.supabase.realtime)
            implementation(libs.supabase.functions)
        }
    }
}
```

> Only depend on what you use — each module is published independently. Optional add-ons: `supabase-auth-google`, `supabase-auth-apple`, `supabase-auth-passkey` (native sign-in), `supabase-auth-admin` (service-role; server-side only) and `supabase-e2ee` (client-side end-to-end encryption).

## Quick start

Everything starts from a `SupabaseClient`. Create one, then build the feature clients you need from it.

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

> Use the **anon** key in client apps — never the service-role key.

## Results, not exceptions

Every fallible call returns a `SupabaseResult<T>` — a sealed `Success`/`Failure` type. There are no exceptions to catch on the happy path; you branch with `onSuccess`/`onFailure` and transform with `map`/`flatMap`/`recover`.

```kotlin
@Serializable
data class Todo(val id: String, val title: String, val done: Boolean)

database.selectTyped<Todo>(table = "todos") {
    eq("done", "false")
    order("created_at", ascending = false)
    limit(25)
}.onSuccess { todos ->
    println("Got ${todos.size} todos")
}.onFailure { error ->
    println("Error: ${error.message}")
}
```

Errors carry a `category` (`Conflict`, `NotFound`, `Unauthorized`, `RateLimited`, `Validation`, `Internal`, `Network`, `Unknown`) so you can branch without parsing codes — plus chainable helpers like `onUnauthorized { }`, `onRateLimited { }` and `onNetworkError { }`. See [Results & Errors](https://androidpoet.github.io/supabase-kmp/results-and-errors) for the full surface.

## Modules

| Module | Artifact | Description |
|--------|----------|-------------|
| **supabase-core** | `io.github.androidpoet:supabase-core` | Result monad, error types, value class IDs, filter DSL |
| **supabase-client** | `io.github.androidpoet:supabase-client` | HTTP transport, platform engines, auth state, factory wiring |
| **supabase-auth** | `io.github.androidpoet:supabase-auth` | Email, phone, OTP, OAuth (17 providers), MFA, PKCE, session management, passkeys |
| **supabase-auth-google** | `io.github.androidpoet:supabase-auth-google` | Native Google sign-in (Android Credential Manager) |
| **supabase-auth-apple** | `io.github.androidpoet:supabase-auth-apple` | Native Sign in with Apple (AuthenticationServices) |
| **supabase-auth-passkey** | `io.github.androidpoet:supabase-auth-passkey` | Passkey/WebAuthn ceremony driver — native on Android, iOS, macOS, JVM & Wasm (or bring your own) |
| **supabase-auth-admin** | `io.github.androidpoet:supabase-auth-admin` | Service-role admin APIs (server-side only) |
| **supabase-database** | `io.github.androidpoet:supabase-database` | PostgREST CRUD, RPC, typed filter extensions |
| **supabase-storage** | `io.github.androidpoet:supabase-storage` | Bucket CRUD, file upload/download, signed & public URLs |
| **supabase-realtime** | `io.github.androidpoet:supabase-realtime` | WebSocket (Phoenix protocol), auto-reconnect, broadcast, presence |
| **supabase-functions** | `io.github.androidpoet:supabase-functions` | Edge function invocation with typed responses |
| **supabase-e2ee** | `io.github.androidpoet:supabase-e2ee` | Optional client-side E2E encryption (ECDH → HKDF → AES-256-GCM) |

## Targets

Android · JVM · iOS · macOS · tvOS · watchOS · Linux · Windows · WasmJs — 16 targets in total, on OkHttp (Android/JVM), Darwin (Apple), CIO (Linux/Windows) and Js (Web) Ktor engines.

## Documentation

- **Docs site:** <https://androidpoet.github.io/supabase-kmp/> (source in [`website/`](website/))
- Release notes: [`CHANGELOG.md`](CHANGELOG.md)
- Contributing: [`CONTRIBUTING.md`](CONTRIBUTING.md)
- Security policy: [`SECURITY.md`](SECURITY.md)
- AI Agent Skill (Claude Code / Cursor / Copilot): [`skills/`](skills/)

## Build

```bash
./gradlew compileKotlinJvm                 # compile a single target
./gradlew jvmTest                          # run JVM unit tests
./gradlew build --no-configuration-cache   # full build (all platforms)
```

Quality gates run in CI on every PR — run them locally before pushing:

```bash
./gradlew detekt                    # static analysis (config/detekt/detekt.yml)
./gradlew apiCheck                  # fails on unintended public/binary API changes
./gradlew jvmTest koverHtmlReport   # tests + coverage report
```

If you intentionally change the public API, regenerate the dumps with `./gradlew apiDump` and commit them. See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the full workflow.

## License

[MIT](LICENSE) © 2026 Ranbir Singh
