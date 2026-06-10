# Changelog

## 0.3.5

### Security

- PKCE code verifiers are now generated from a cryptographically secure RNG
  (`CryptographyRandom`) instead of `kotlin.random.Random`.
- HTTP logging now redacts the `Authorization` and `apikey` headers, so enabling
  `logging = true` no longer leaks anon/service-role keys or bearer tokens.

### Fixed

- **Functions:** `invokeWithBody` no longer double-prepends the project URL
  (it passed an absolute URL into `postRaw`, which prepends `projectUrl` again).
- **Storage:** bucket ids, object paths, and signed-URL tokens are now
  URL-encoded; object keys containing spaces / `#` / `?` / `+` no longer corrupt
  the request URL or break routing.
- **Realtime:** added `RealtimeClient.close()` to release the underlying Ktor
  engine (the WebSocket `HttpClient` was never closed); the connection handshake
  now honors `connectionTimeoutMs`; Phoenix `ref` generation uses an atomic
  counter (kotlinx-atomicfu) so concurrent sends can't collide.
- **Realtime reliability:** `scheduleReconnect` cancels any in-flight reconnect
  so overlapping triggers can't spawn parallel loops; a missed heartbeat reply
  now tears the socket down and reconnects instead of leaving a zombie
  connection; `phx_join` has a watchdog that marks the subscription `ERROR` if no
  reply arrives within `connectionTimeoutMs`; `activeSubscriptions` is guarded by
  a lock so the non-suspend getters are thread-safe.
- Auth `signUpWithEmail` / `signUpWithPhone` validate that the identifier is
  non-blank.
- Session refresh is de-duplicated: concurrent refreshes now share a single
  in-flight request instead of each spending the rotating refresh token (which
  caused spurious logouts).
- Retries now cover 429/500/502/504 (in addition to 503/520) and honor
  `Retry-After`.
- PostgREST filter values containing structural characters (`, ( ) "`) are
  quoted/escaped, fixing `in(...)` lists and `or`/`and` groups.
- Realtime: outbound application messages sent while disconnected are buffered
  and replayed after rejoin instead of being silently dropped; presence
  callbacks now receive the full cumulative state on diffs.
- Cross-thread visibility hardening for the access token, session refresh state,
  and realtime connection fields (`@Volatile`).

### Added

- `SupabaseClient` now implements `AutoCloseable`.
- `KeyValueStore` + `KeyValueSessionStorage` for easy persistent, serialized
  session storage backed by the platform keystore.
- Input validation on `Supabase.create` (non-blank api key, http(s) project URL).
- Tooling: detekt, binary-compatibility-validator (JVM + Android + native klib
  ABI dumps), Kover coverage, Dokka API docs, a multiplatform CI matrix,
  Dependabot, and CONTRIBUTING/SECURITY/CODE_OF_CONDUCT.
- Internal: shared `urlEncode` in `supabase-core` (deduplicated from auth /
  auth-admin); Functions now uses the live session token (falls back to the
  client's current token instead of only a pinned one).

## 0.3.2

- Added typed Iceberg REST catalog models and typed Analytics Catalog methods while keeping raw JSON methods available.
- Added Realtime debug state, debug events, and manual heartbeat sending for transport diagnostics.
- Verified Storage and Realtime common metadata, JVM, Wasm test compilation, and Android unit tests.

## 0.3.1

- Added Supabase JavaScript SDK parity coverage tracking.
- Added `supabase-auth-admin` for service-role auth admin APIs.
- Expanded Auth coverage with OAuth, Web3, passkey, MFA, lifecycle, and auth-state helpers.
- Expanded Database/PostgREST coverage with advanced filters, request options, custom headers, retry, and explain support.
- Expanded Storage coverage with vector buckets, analytics buckets, and Iceberg REST catalog helpers.
- Expanded Realtime coverage with connection-state helpers and raw channel send support.
- Added client custom Ktor engine injection, global headers verification, and cancellation parity docs.
- Added local Supabase config, e2e test scaffolding, chat sample diagnostics, and desktop demo sample.
