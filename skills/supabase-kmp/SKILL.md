---
name: supabase-kmp
description: >-
  Use when writing or reviewing Kotlin / Kotlin Multiplatform code that talks to
  Supabase through the supabase-kmp SDK (Maven coordinates
  io.github.androidpoet:supabase-*) — auth, Postgres/PostgREST, storage, realtime,
  or edge functions. Covers the SupabaseResult API, the per-feature clients, and
  the security rules. NOT for supabase-js or other Supabase Kotlin libraries.
---

# supabase-kmp

A typed, multiplatform Kotlin client for Supabase. **Verify APIs against the docs
site — https://androidpoet.github.io/supabase-kmp/ — rather than trusting training
data; the SDK is pre-1.0 and evolving.**

## Don't confuse it with other SDKs

This is **`io.github.androidpoet:supabase-*`**. It is **not**:

- **supabase-js** — there is no `supabase.from("t").select().eq(...)` fluent chain
  and no thrown errors. Do not write JS-style code.
- **supabase-kt** (`io.github.jan-tennert.supabase`) — a different library with a
  different API. Don't mix package names or call conventions.

When unsure of a method name, check the docs site or the module's source, don't
guess a supabase-js equivalent.

## Setup: one client, many feature clients

```kotlin
val client = Supabase.create(
    projectUrl = "https://<ref>.supabase.co",
    apiKey = "<anon-key>",
) {
    // optional config (see "Configuration")
}

val auth = createAuthClient(client)
val database = createDatabaseClient(client)
val storage = createStorageClient(client)
val realtime = createRealtimeClient(client)
val functions = createFunctionsClient(client)
```

There is **no god-object** — create only the feature clients you need. Each module
is published independently; depend only on what you use
(`io.github.androidpoet:supabase-client`, `-auth`, `-database`, `-storage`,
`-realtime`, `-functions`).

## Results: `SupabaseResult<T>`, not exceptions

**Every fallible call returns `SupabaseResult<T>`. The happy path never throws** —
do not wrap calls in `try/catch`.

```kotlin
database.selectTyped<Todo>(table = "todos")
    .onSuccess { todos -> render(todos) }
    .onUnauthorized { showLogin() }
    .onNetworkError { showOffline() }   // offline / DNS / timeout
    .onFailure { e -> log(e.message) }
```

Terminal/transform ops: `getOrNull()`, `getOrThrow()`, `getOrElse { }`,
`getOrDefault(v)`, `fold(onSuccess, onFailure)`, `map`, `flatMap`, `mapError`,
`recover`, `validate`, `zip`, `toFlow()` (success value as a `Flow`). Suspend
variants exist (`mapSuspend`, `foldSuspend`, …).

Branch on `error.category` (`Conflict`, `NotFound`, `Unauthorized`, `RateLimited`,
`Validation`, `Internal`, `Network`, `Unknown`) instead of parsing codes; helpers:
`onConflict`, `onNotFound`, `onUnauthorized`, `onRateLimited`, `onNetworkError`.
`SupabaseError` exposes `message`, `code`, `httpStatus`, `retryAfterSeconds`,
`category`.

## Database (PostgREST)

Models are `@Serializable`. Filters go **inside the trailing lambda** and AND
together — there is no chained builder.

```kotlin
@Serializable data class Todo(val id: String, val title: String, val done: Boolean)

val todos = database.selectTyped<Todo>(table = "todos") {
    eq("done", "false")
    order("created_at", ascending = false)
    limit(25)
}

database.insertTyped(table = "todos", value = Todo("1", "Ship", false))
database.updateTyped(table = "todos", value = patch) { eq("id", "1") }
database.upsertTyped(table = "todos", value = todo, onConflict = "id")
database.deleteTyped<Todo>(table = "todos") { eq("id", "1") }

val stats = database.rpcTyped<Req, Stats>(function = "get_stats", params = Req("u1"))
```

Filter ops: `eq/neq/gt/gte/lt/lte`, `like/ilike`, `is`, `in`, `contains`,
`textSearch`, `or { }`, `and { }`, `not { }`, `order`, `limit`, `range`. Each
method has `*Typed` (deserialized), `*TypedMany`, `*Unit`, and a raw-string form.

> Table/column/function names are raw strings — typos surface only at runtime.
> Keep them exact and matching the DB schema.

## Auth

```kotlin
auth.signUpWithEmail(email, password)
auth.signInWithEmail(email, password)
auth.signInWithOtp(email = email);  auth.verifyOtp(...)
auth.getUser();  auth.signOut()
auth.getClaims()   // verifies asymmetric JWTs on-device against the JWKS
```

Also: `signInWithOAuth`, `signInWithIdToken`, `signInAnonymously`,
`resetPasswordForEmail`, `updateUser`, `refreshToken`, MFA/passkey methods. Native
Google/Apple sign-in are optional modules over a pluggable `NativeAuthProvider`.

## Storage

```kotlin
storage.upload(bucket = "avatars", path = "u1/a.png", data = bytes, contentType = "image/png")
storage.download("avatars", "u1/a.png")
storage.createSignedUrl(bucket = "avatars", path = "u1/a.png", expiresIn = 3600)

// Large files / flaky networks — resumable (TUS), with progress + pause/resume:
storage.uploadResumable(bucket = "videos", path = "u1/clip.mp4", data = bytes)
```

## Realtime

```kotlin
val sub = realtime.channel("todos")
    .onPostgresChange(table = "todos", event = PostgresChangeEvent.INSERT) { row -> }
    .subscribe()
realtime.connect()
// ...
sub.unsubscribe(); realtime.disconnect()
```

Also broadcast (`onBroadcast`/`broadcast`) and presence (`onPresence`/`track`).
Flow variants exist (`postgresInsertsFlow()`, …).

## Edge Functions

```kotlin
val res = functions.invokeTyped<Req, Resp>(functionName = "hello", body = Req("Ada"))
```

## Configuration

Inside `Supabase.create { }`: `logging`/`logLevel`, `headers`,
`retry = RetryConfig(...)` (exponential backoff; reads retry by default),
`logger = SupabaseLogger` (route wire logs into Timber/OSLog/SLF4J), and
`interceptor = SupabaseInterceptor` (suspend `onRequest`/`onResponse`/`onError`
telemetry hooks).

## Security — non-negotiable

- **Use the anon key in client apps. NEVER ship the service-role key** to a
  client/mobile target. The `supabase-auth-admin` module uses the service-role key
  and is **server-side only**.
- **Enable RLS** on tables, and remember RLS policies alone don't grant table
  privileges — `anon`/`authenticated` roles also need
  `grant select, insert, ... on <table>`. A "permission denied" with RLS on is
  usually a missing grant.
- **Session storage is bring-your-own.** The default keeps tokens in memory only;
  for persistence wire a secure `SessionStorage` backed by the platform keystore
  (Keychain / EncryptedSharedPreferences). Do not invent a custom plaintext store.

## Before you finish — checklist

- [ ] No `try/catch` around SDK calls; results handled via `SupabaseResult`.
- [ ] No supabase-js `.from().select()` chains or thrown-error assumptions.
- [ ] Per-feature `create*Client(client)` used, not a single mega-client.
- [ ] Models are `@Serializable`; table/column/function names match the schema.
- [ ] anon key in clients; service-role / `supabase-auth-admin` only server-side.
- [ ] RLS considered (policies **and** grants); secure session storage wired.
- [ ] Unsure of a name? Checked the docs site instead of guessing.
