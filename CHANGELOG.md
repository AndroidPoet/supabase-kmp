# Changelog

## Unreleased

### Removed

- **`AdminUserAttributes.nonce`** — removed a non-functional field. GoTrue's admin
  create/update-user endpoint has no `nonce` parameter (it silently ignored the key), so setting
  it never did anything; `nonce` belongs to the self-service reauthentication flow, not admin.
  Keeping it implied admin-reauth support that doesn't exist.

### Added

- **`Session.expiresAt` / `MfaVerifyResponse.expiresAt`** — GoTrue's `AccessTokenResponse` includes
  an absolute `expires_at` (Unix seconds) alongside the relative `expires_in`; both models now carry
  it (nullable, for servers/responses that omit it). Prefer it over computing `now + expiresIn`, since
  it reflects the server's clock. Additive and backward-compatible.

### Fixed

- **Realtime presence no longer reports a false leave when a member is still connected.** Presence
  stored a single meta per key and removed the whole key on any `presence_diff` leave, so a member
  tracked from two connections (two tabs/devices, or a shared presence key) vanished from
  `presenceState()` as soon as one connection left. Presence now keeps the full Phoenix metas array
  per key, removes only the metas that actually left (matched by `phx_ref`), and drops the key only
  once its last meta is gone. The public single-meta `PresenceState` shape is unchanged.
- **Realtime `postgres_changes` no longer mis-routes when the server's echoed bindings diverge.** The
  join reply's bindings were paired to local callbacks purely by array index; a reordered, normalised,
  or rejected binding would silently route changes to the wrong callback. The echoed `event`/`schema`/
  `table`/`filter` are now verified against the local config before the server id is trusted (matching
  realtime-js), so a mismatched binding is skipped rather than mis-paired.
- **Storage `createSignedUrls` no longer fails the whole batch when one path is unsignable.** The
  batch endpoint returns `200` even on partial success, with `signedURL: null` and an `error` for
  paths it couldn't sign. The response item required a non-null `signedURL`, so a single missing
  object made the entire call fail to decode. `signedURL` is now nullable (and `error` is modelled);
  unsignable paths are skipped so the URLs that did sign are still returned.
- **Session auto-refresh now uses the server's absolute `expires_at` for opaque tokens.** When the
  access token isn't a decodable JWT, the refresh scheduler fell back to the relative `expires_in`
  captured at issue time — so after a restore the token looked valid long after it had actually
  expired, refreshing too late and yielding 401s. It now prefers JWT `exp` → `Session.expiresAt` →
  `expires_in`. JWT-token apps (the common case) are unaffected.
- **MFA challenge responses now decode.** `MfaChallengeResponse` required a non-null `factor_id`, but
  GoTrue's `POST /factors/{id}/challenge` returns only `id`, `type`, `expires_at` and `webauthn` — no
  `factor_id` — so `mfaChallenge()` threw a missing-field error on **every** call. The non-existent
  field is removed and the real `type` field added. Relatedly, `MfaVerifyRequest` no longer sends a
  `factor_id` in the body (GoTrue's `VerifyFactorParams` is `{challenge_id, code, webauthn}`; the
  factor is identified by the URL path).
- **Codegen fails fast on colliding column names instead of emitting uncompilable output.** Two
  columns in one table that normalise to the same Kotlin property (e.g. `user_id` and `userId`)
  produced a data class with duplicate properties that wouldn't compile. The generator now
  raises an actionable error naming both columns, matching the existing table- and enum-name
  collision checks.
- **Codegen fails fast on colliding enum labels instead of silently dropping one.** Two distinct
  Postgres enum labels that normalise to the same Kotlin constant (e.g. `active`/`Active`, or
  `in progress`/`in_progress`) were silently collapsed to one constant, so a row carrying the
  dropped label failed to deserialize at runtime. The generator now raises an actionable error.
- **`Paginator.refresh()` during an in-flight load no longer leaves the list empty.** Pull-to-
  refresh while a page was still loading reset the state and then called `loadNext()`, but that
  call no-opped against the stale load's `isLoading` flag and the stale result was discarded by
  the epoch check — so the list stayed empty and idle. `refresh()` now takes the loading flag in
  the same lock as the reset, and the flag is cleared under the mutex only by the load owning the
  current epoch (so a stale load can't clear a newer load's flag).
- **Multi-column `order()` no longer silently drops sort columns.** Each `order(...)` call
  emitted its own `order=` query parameter, but PostgREST expects multi-column ordering as a
  single comma-joined value (`order=a.desc,b.asc`) and honours only one of several repeated
  `order=` params — so `order("a"); order("b")` sorted by only one column. Successive `order`
  calls now accumulate into one parameter (per referenced table), matching PostgREST.
- **Edge Function SSE streaming: the last-event-id now persists across events.** The
  `invokeSSE` parser reset the id on every event, so a streamed event that omitted `id:`
  reported `null` instead of inheriting the most recent id (the browser
  `EventSource.lastEventId` behaviour). The id is now carried forward until the server sends
  a new one. As part of the same fix, a block carrying only a (now-persistent) id no longer
  dispatches a spurious empty event after a keep-alive; `data` events and `event:`-only
  terminal signals (e.g. `event: done`) are unaffected.
- **Custom request `Content-Type` is no longer overridden with `application/json`.** The
  transport unconditionally set `Content-Type: application/json` and then *appended* the
  caller's header; because Ktor's String-body transformer reads the first `Content-Type`
  value, a caller-supplied type was silently dropped. This broke every non-JSON write — a
  `text/csv` bulk insert (`insertCsv`-style) or CSV/scalar `rpc` was sent as JSON and rejected
  by PostgREST. The JSON default now applies only when the caller didn't set a `Content-Type`,
  so custom types are honoured (JSON requests are unchanged).
- **`rpc(head = true)` now selects a non-default schema correctly.** A head RPC is an HTTP
  POST, but it sent the schema in `Accept-Profile`, which PostgREST only reads on GET/HEAD —
  so a head-count RPC in a non-`public` schema resolved against the wrong schema. It now uses
  `Content-Profile` (and sends the body's `Content-Type`), matching the non-head RPC path.
- **Realtime binary broadcast now works against a live server.** The client connected with
  `vsn=1.0.0`, which the server routes to its stock V1 JSON serializer — that has no support
  for the binary broadcast frames (kinds 3/4), so the feature was inert end-to-end. The client
  now negotiates Realtime **Protocol 2.0.0** (`vsn=2.0.0`): text frames use the array form
  `[join_ref, ref, topic, event, payload]` (new internal `RealtimeMessageSerializer`) and the
  binary frames that carry binary broadcast share the same transport. No public API change.
- **Offline app launch no longer signs the user out.** When restoring a persisted session,
  a transient refresh failure (offline, 5xx, 429) dropped the session straight to `Expired`,
  because the keep-session guard only fired when a session was already in memory — during
  restore there isn't one yet. Restore now adopts the stored session (it stays
  `Authenticated`) and schedules a retry, matching the existing behaviour for a transient
  failure on an already-active session. The refresh token is still valid in this case, so
  the user keeps their session and recovers automatically when connectivity returns.
- **Apple first-sign-in name/email are now reachable through `signInWith`.** The native
  credential captures the `fullName`/`email` that Sign in with Apple returns only on the
  first authorization (and never in the ID token), but the `signInWith` /
  `signInWithAndSaveSession` convenience extensions discarded the credential and returned
  only the session — and the `id_token` grant can't carry name/email either, so the data was
  unrecoverable unless you abandoned the sugar and called `provider.signIn()` directly. Both
  extensions gain an optional `onCredential: (NativeAuthCredential) -> Unit` hook that fires
  with the raw credential on a successful native flow, so you can capture the name/email (e.g.
  to write to user metadata or a profile row). Additive and backward compatible.

## 0.9.1 — 2026-06-21

### Added

- **`supabase-e2ee` key persistence** — `E2eeKeyPair.exportPrivateKey()` (PKCS#8
  DER) and `importE2eeKeyPair(privateKeyDer, publicKey)` let a device persist its
  identity across launches, the missing half of at-rest single-user encryption.
  Plus `E2eeKeyPair.deriveSelfSession()` — a deterministic self-key (ECDH against
  the pair's own public key) for encrypting your own data, where the server only
  ever sees ciphertext. DER round-trips across JDK / Apple / OpenSSL / WebCrypto.
- **Secure nonce helper** — `generateNonce()` in `supabase-auth` draws a fresh,
  URL-safe, high-entropy nonce from the SDK's CSPRNG (the same source as PKCE
  verifiers, not `kotlin.random`). The native sign-in configs that ask for "a fresh
  random value per sign-in" (`GoogleSignInConfig.nonce`, `AppleSignInConfig.nonce`)
  now point at it so apps stop hand-rolling weak randomness.
- **Apple first-sign-in name/email** — `NativeAuthCredential` gains optional
  `fullName` and `email` fields, populated by the Apple provider from the
  `ASAuthorizationAppleIDCredential` that Apple returns only on the **first**
  authorization. Additive and defaulted, so other providers and call sites are
  unaffected; `toString()` keeps masking the credential.

### Changed

- **BREAKING (`supabase-auth-admin`): `SupabaseClient.authAdmin(serviceRoleKey)` no
  longer defaults the key to the anon `apiKey`.** The default meant `authAdmin()`
  silently built a non-functional admin client (every call 401s) and nudged
  developers toward embedding the real service-role key to "fix" it — a full RLS
  bypass if shipped in a client app. The key is now a required argument; pass the
  service-role key explicitly from a secret, server-side only.

### Fixed

- **`supabase-auth` no longer signs the user out on a transient refresh failure.**
  A token refresh that failed for a connectivity blip, a 5xx server hiccup or a
  rate limit (`429`) flipped `SessionState` to `Expired`, logging the user out even
  though the refresh token was still valid. The session now stays `Authenticated`
  on a transient failure (categories `Network` / `Internal` / `RateLimited`) and
  retries shortly; it expires only on a genuine invalidation (`invalid_grant` /
  reused or expired refresh token, surfaced as `Unauthorized` / `Validation`).

- **`supabase-auth-apple` reports user-cancellation distinctly.** A cancelled Apple
  sign-in (`ASAuthorizationError.canceled`) now returns `SupabaseError(code =
  "apple_sign_in_cancelled")` instead of a generic, code-less failure, so apps can
  suppress error UI on a deliberate dismiss — matching the Google provider's
  `*_cancelled` / `*_failed` vocabulary.

- **`selectMaybeSingleTyped` / `rpcMaybeSingleTyped` / `rpcGetMaybeSingleTyped` now
  return `Success(null)` for a missing row against a real server.** The no-row case
  was detected with `error.code == "406"`, but the transport reports PostgREST's
  single-object miss as `httpStatus = 406` / `code = "PGRST116"` / category
  `Validation` — so the check never matched and every "maybe" lookup of an absent
  row failed. Detection now keys on the real shape and distinguishes 0 rows (→
  `null`) from >1 rows (still a failure, per the `…Single` contract). The unit
  fakes were emitting a `code = "406"` a live server never sends; they now mirror
  the real error and a >1-rows regression test was added.
- **`SupabaseClient.postRaw` / `putRaw` no longer double-prefix an absolute URL.**
  They unconditionally prepended the project URL while their docs said the argument
  was absolute, so a full `https://…` (e.g. a signed upload URL) became
  `https://proj.supabase.cohttps://…`. They now share `rawRequest`'s path-vs-URL
  resolution: a path is prefixed, an `http(s)://` URL is used verbatim. Docs
  corrected; tests added.
- **Realtime `subscribe()` now opens the socket (documented lazy-connect).**
  Subscribing without a prior `connect()` sent a `phx_join` that — being a
  non-buffered control frame — was dropped on the dead socket, leaving the channel
  stuck in `SUBSCRIBING` until it timed out to `ERROR`. `subscribe()` now connects
  first when there is no live session, matching the KDoc.

### Docs

- **Versioning guide** — document the release policy in `CONTRIBUTING.md`: the
  library stays in the `0.9.x` line with patch-only bumps (`0.9.1`, `0.9.2`, …) and
  does not move to `1.0.0` yet, so every change stays backward-compatible.
- **README** — add copy-pasteable version-catalog coordinates for the optional
  add-on modules.
- **JVM 17 requirement** — README Setup now states that consuming modules must build
  with `jvmTarget = 17` (the published Android/JVM artifacts ship inline functions
  compiled for Java 17); building lower fails with an inline-bytecode error.

## 0.9.0 — 2026-06-20

### Added

- **Binary broadcast** — send and receive raw `ByteArray` payloads over Realtime
  broadcast as WebSocket binary frames, skipping the base64/JSON overhead of
  textual broadcast. New `RealtimeSubscription.broadcastBinary(event, ByteArray)`,
  `RealtimeEvent.BinaryBroadcast`, and the `binaryBroadcastFlow(event)` typed view.
  Ideal for sensor/telemetry streams, image frames, or encrypted bytes (pairs with
  `supabase-e2ee`). Additive; existing JSON broadcast is unchanged.

## 0.8.0 — 2026-06-20

### Added

- **Pagination** — a demand-driven `Paginator<T>` in `supabase-core` (exposes
  `items`/`isLoading`/`endReached`/`error` as `StateFlow`s, with `loadNext()` and
  `refresh()`), plus in-module factories: `DatabaseClient.paginator()`,
  `StorageClient.listPaginator()` and `AuthAdminClient.usersPaginator()`. No new
  dependencies; works on all targets. See the new **Pagination** docs page
  (includes an androidx Paging 3 recipe).
- **Plain reads** — no-`SupabaseResult` variants for the paginated endpoints that
  return the list directly and throw on failure: `selectTypedOrThrow`,
  `listOrThrow`, `listUsersOrThrow`.
- **Flow bridges** — `SupabaseResult.asFlow()`, `supabaseFlow { }` (keeps the
  Result) and `dataFlow { }` (plain values, throws) in `supabase-core`.
- **End-to-end encryption** — new optional `supabase-e2ee` module. Derive a shared
  AES-256-GCM session on-device via ECDH (P-256) → HKDF-SHA256 — the key never
  leaves the device and Supabase only stores ciphertext. Encrypts `ByteArray`,
  `String` or any `@Serializable` value (`generateE2eeKeyPair`, `deriveSession`,
  `encrypt`/`decrypt`, `encryptValue`/`decryptValue`). Result-first, all targets
  incl. Wasm. See the new **End-to-End Encryption** docs page.

## 0.7.0 — 2026-06-19

Hardening release driven by a full audit of `supabase-auth`/`supabase-auth-admin`
against the official Auth (GoTrue) OpenAPI spec, now pinned into the repo. Every
change is additive — no breaking changes.

### Added

- **Auth** — `channel` (`sms`/`whatsapp`) on `signUpWithPhone` and phone-change
  `updateUser`; `clientId`/`issuer` on `signInWithIdToken` (required to verify
  custom OIDC issuers); `inviteToken` on `signInWithOAuth`/`getOAuthSignInUrl`;
  `redirectTo` on `verifyOtp`; PKCE on `linkIdentity` and `retrieveSsoUrl`, plus
  `captchaToken` and a defaulted `skip_http_redirect=true` on SSO so the URL call
  returns JSON instead of a 303 redirect; `webauthn` verification on `mfaVerify`
  (new `MfaWebauthnVerification` model).
- **Auth Admin** — `auditLogEvents(page, perPage)` returning `AuditLogEntry`;
  `updateFactor(userId, factorId, friendlyName)` returning the full `MfaFactor`.

### Fixed

- **Realtime** — channels now rejoin on `phx_error`; hardened the
  connect/disconnect/reconnect lifecycle against races (mutex-guarded, intentional
  disconnect re-checked, URL api-key encoded).
- **Auth** — PKCE is now emitted on sign-up, magic-link and password-recovery
  flows; `resend` sends `redirect_to` as a query param (not a dead body field) and
  rejects OTP types `/resend` doesn't accept.
- **Core/Database** — error responses now carry the HTTP status into error
  categorization and parse leniently; corrected the PostgREST `EXPLAIN` plan media
  type.

### Tooling

- Pinned the official Auth OpenAPI spec (`contracts/auth.openapi.yaml`) with a
  full coverage matrix, a machine-readable coverage manifest, and a `spec-drift`
  CI workflow (coverage gate + weekly `oasdiff` upstream diff) so endpoint
  coverage can no longer drift silently.

## 0.6.0 — 2026-06-19

### Changed (breaking)

- **`MfaFactor.status` is now a typed `MfaFactorStatus` enum** (`VERIFIED`/
  `UNVERIFIED`) instead of a raw `String`. Code that read `status` as a `String`
  must switch to the enum. This is the only source-breaking change in the
  release; everything else is additive.

### Added

- **Auth** — `Session.providerToken`/`providerRefreshToken` (call third-party
  provider APIs after OAuth/native sign-in); `User` gains `isAnonymous`, `role`,
  `emailConfirmedAt`, `phoneConfirmedAt`, `confirmedAt`, `lastSignInAt`;
  `mfaGetAuthenticatorAssuranceLevels` returns current+next AAL; `emailRedirectTo`
  on `signUpWithEmail`; phone MFA delivery `channel` on `mfaChallenge`.
- **Storage** — optional object metadata (`x-metadata`, Base64 JSON) on
  `upload`/`update`/`uploadToSignedUrl`; `FileObject` widened with `size`,
  `contentType`, `etag`, `lastAccessedAt`, `cacheControl`; `path` on the
  signed-upload-url response; transformed-image `downloadBytes`/`downloadPublicBytes`
  overloads via the render endpoints.
- **Postgrest** — `match`/`imatch` POSIX-regex operators; typed
  `in(List<Number>)`/`in(List<Any>)` and `contains`/`containedBy`/`overlaps`
  list overloads (build the `{a,b,c}` literal); `OrderDirection`/`NullsPlacement`
  enums for `order()`.
- **Client** — opt-in `connectTimeoutMillis`/`socketTimeoutMillis`/
  `requestTimeoutMillis` (Ktor `HttpTimeout`, installed only when set so streams
  aren't capped); an explicit `httpClientConfig` raw-Ktor escape hatch; a suspend
  `accessTokenProvider` for third-party-auth JWTs.
- **Auth** — `captchaToken` on `signUpWithEmail`/`signUpWithPhone`/`signInWithEmail`/
  `signInWithPhone` (sent under `gotrue_meta_security`), so these flows work when
  bot/CAPTCHA protection is enabled; previously there was no way to pass the token.
- **Auth** — `signInWithOtp(data = …)` attaches `user_metadata` at magic-link/OTP
  signup time, matching the other sign-up entry points.
- **Postgrest** — `or`/`and` accept a `referencedTable` to scope a logical group to
  an embedded resource (e.g. `authors.or=(…)`), mirroring `order`/`limit`/`range`.
- **Realtime** — `InboundEventDropped` debug event (see backpressure fix below).
- **Passkeys** — the opt-in `supabase-auth-passkey` module now drives the native
  WebAuthn ceremony on all platforms; a dedicated docs page documents it.
- **Auth** — `getSettings()` (`GET /auth/v1/settings`, reports which providers and
  flags the project has enabled) and `getHealth()` (`GET /auth/v1/health`); both
  decode tolerantly so unknown/missing keys don't break the response.
- **Postgrest** — `TextSearchType.Raw` exposes the bare `fts` operator
  (`to_tsquery`); a standalone `offset()`; `ReturnOption.HEADERS_ONLY`
  (`Prefer: return=headers-only`, an empty body that keeps headers like
  `Location`); and `replace()` — `PUT`-based single-row upsert by primary key.
- **Realtime** — postgres-change events now carry `commitTimestamp`, `schema`, and
  `table`; broadcast events expose `replayed` (set when the server replays a
  message); and `RealtimeConfig.logLevel` forwards a `log_level` to the server.
- **Realtime** — `RealtimeSubscription.broadcastWithAck(event, payload, timeoutMillis)`
  sends a broadcast and suspends until the server acknowledges it. Result-first:
  returns `SupabaseResult.Failure` if the channel is not subscribed, the server
  rejects the push, the connection drops before the ack, or the ack does not arrive
  within `timeoutMillis`. The fire-and-forget `broadcast`/`send` path is unchanged.
- **Postgrest** — `insert`/`rpc` accept a `contentType` so callers can send non-JSON
  bodies (e.g. `text/csv` bulk inserts, scalar-typed RPC args); the default stays
  JSON. The raw `rpc` also accepts a `filters` block to filter/order/paginate the
  rows a set-returning function returns.
- **Core** — `SupabaseException` now carries an optional `cause`, so an underlying
  throwable that produced an error stays in the stack trace; `toException(cause)`
  threads it through.
- **Auth admin** — SAML SSO identity-provider management: `createSsoProvider`,
  `listSsoProvider`s, `getSsoProvider`, `updateSsoProvider`, `deleteSsoProvider`
  (the `/admin/sso/providers` family).
- **Functions** — `invoke`/`invokeWithBody` accept a `FunctionMethod`
  (`GET`/`POST`/`PUT`/`PATCH`/`DELETE`) for REST-style Edge Functions that branch on
  the request verb; the default stays `POST` and `GET` is sent without a body.
- **Storage** — `createResumableUpload`/`uploadResumable` accept object `metadata`,
  emitted as the TUS `Upload-Metadata` `metadata` entry with the same Base64-JSON
  encoding as the non-resumable `x-metadata` path (previously metadata was dropped
  on the resumable path).
- **Postgrest** — `isDistinct` filter operator (`IS DISTINCT FROM`, a null-aware
  inequality), completing the operator set.

### Fixed

- **Auth `updateUser`** — fixed a test that still asserted the old `PATCH` verb
  after the endpoint moved to `PUT`, so the change is now actually covered.
- **Client `Retry-After`** — the header is now parsed in both RFC 7231 forms
  (delta-seconds *and* HTTP-date); previously a date-form value was dropped and the
  server's backoff hint was lost.
- **Errors** — HTTP `406` (Not Acceptable) and `416` (Range Not Satisfiable) now map
  to the `Validation` category instead of falling through to `Unknown`.
- **Realtime** — channel-level `phx_close` and `system` close frames now move the
  channel to `UNSUBSCRIBED`/`ERROR` and surface a `SystemEvent`; previously they
  were silently ignored.

- **Storage signed-URL TTL** — the request body now sends camelCase `expiresIn`;
  it was `expires_in`, which the server ignored, so signed URLs silently used the
  server-default expiry.
- **Storage `remove`** — now `DELETE /object/{bucket}` with `{prefixes}`; it was
  `POST /object/remove/{bucket}`, a route that does not exist server-side (404 in
  production). **`uploadToSignedUrl`** now uses `PUT` (the upload/sign route is
  registered as `PUT`, not `POST`).
- **Postgrest bulk insert** — a multi-row insert whose rows carry different key
  sets now sends the column union as `columns=`; previously PostgREST inferred
  columns from the first row only and silently dropped the rest (data loss).
- **Auth** — current AAL is read from the JWT `aal` claim instead of being
  inferred as AAL2 from "any verified factor"; phone MFA challenge now sends a
  body so the delivery channel reaches the server.
- **Realtime** — only the join reply (`ref == joinRef`) drives subscription
  status, so a `phx_leave` ack no longer resurrects an unsubscribed channel; and
  inbound delivery is now non-blocking (`DROP_OLDEST`) so a stalled collector
  can't wedge the socket (heartbeats keep flowing), surfacing evictions as
  `InboundEventDropped`.
- **Native auth providers** — the Android passkey authenticator returns a
  `Failure` instead of throwing `ClassCastException`; the Apple provider can no
  longer double-resume its continuation; the Google provider propagates coroutine
  cancellation to the in-flight Credential Manager request.
- **Auth `updateUser`** — now sends `PUT /user` instead of `PATCH`; the server
  only registers `PUT` for that route, so profile/password/metadata updates did
  not reach the handler.
- **Auth magic-link redirect** — `signInWithOtp(emailRedirectTo = …)` now sends
  the redirect as the `redirect_to` query parameter; it was a request-body field
  the server ignores, so the magic link used the project default redirect.
- **Auth response decoding** — `Session.tokenType`/`MfaVerifyResponse.tokenType`
  default to `bearer`, so a response omitting `token_type` still decodes. Secret
  fields (access/refresh/provider tokens, TOTP secret, native credential tokens)
  are now redacted from `toString()` to keep credentials out of logs.
- **Postgrest logical groups** — nested `and`/`or` inside a group now render as
  `and(…)`/`or(…)` (no spurious dot) and a negated group renders as
  `not.and=(…)`/`not.or=(…)` (prefix on the key); both forms were malformed and
  rejected by the server. `range(from, to)` now requires `to >= from`.
- **Storage image transforms** — `getPublicUrl`/`getAuthenticatedUrl` with a
  `transform` now target the `/render/image/...` route; on the `/object/...`
  route the server ignores transform params and returns the original image.
  `download = true` without a filename now emits `download=` (empty value).
- **Functions region** — `FunctionRegion.ANY` no longer sends a literal
  `x-region: any` header (the header is omitted, letting the platform route).
- **Client transport** — a caller-supplied `Authorization` header is matched
  case-insensitively (no more duplicate header from a lowercased key); a throwing
  `accessTokenProvider` is returned as a `SupabaseResult.Failure` rather than
  thrown; a trailing slash on the project URL is trimmed (no `//` in paths).
- **Realtime** — a single malformed inbound frame is now dropped in isolation
  instead of tearing down the socket and forcing every channel to rejoin; a
  heartbeat reply only clears the liveness watchdog when its `ref` matches the
  outstanding heartbeat.
- **Auth admin** — `createUser`/`getUserById`/`updateUserById`/`inviteUserByEmail`
  now decode the bare `User` the server returns (a `{"user":…}` wrapper is still
  accepted). They previously required the wrapper and so failed on every valid
  `200`; the unit tests had masked this by mocking the wrapper shape.
- **Auth** — `resetPasswordForEmail` no longer sends a `create_user` field the
  `/recover` endpoint doesn't define; `verifyOtp` returns a clear failure on a
  no-session confirmation (e.g. email change) instead of an opaque decode error;
  `generateLink` no longer sends `redirect_to` in both the body and the query.
- **Enums from server responses** — MFA factor type/status, the admin OAuth-client
  enums, and the Storage vector data-type/distance-metric enums now tolerate an
  unknown server value (coerced to `UNKNOWN`) instead of failing the whole
  response decode, so a new server-side value can't break e.g. `listFactors()`.
- **Postgrest** — a `null` argument to a GET-based RPC is now omitted from the
  query string instead of being sent as the literal text `"null"`.
- **Realtime** — a handshake that times out during `connect()` no longer leaks the
  connection scope or rethrows a raw cancellation; it routes through the normal
  reconnect/`Failed` path.
- **Secret redaction** — `toString()` now masks secrets on the auth request bodies
  (passwords, id/refresh tokens) and on the admin client-secret/password carriers,
  extending the redaction already applied to `Session` and the MFA/native types.

### Added (supabase-kt parity audit)

- **Storage binary download** — `downloadBytes`/`downloadPublicBytes` return
  `SupabaseResult<ByteArray>`; `download` decoded the body as a UTF-8 `String`,
  corrupting images/PDFs. Also: extension→MIME inference when `contentType` is
  left at the octet-stream default, and a `withCacheNonce` URL cache-buster.
- **Postgrest count** — `selectCount` (HTTP `HEAD`), `selectRange`, and typed
  `selectWithCount<T>` parse the `Content-Range` header into a `PostgrestPage`/
  `PostgrestRange` (handles `0-9/27`, `*/27`, `*/*`), so the requested count is
  finally surfaced. Added the geojson+`stripNulls` mutual-exclusion guard.
- **Realtime** — HTTP `broadcast(...)` without a subscription (`/realtime/v1/api/broadcast`);
  presence `metas` are unwrapped to user state; `postgres_changes` route by the
  server-assigned binding `ids`; reconnect backoff gains optional jitter.
- **Edge Functions SSE** — `invokeSSE(...): Flow<FunctionServerSentEvent>` with
  per-event `decodeAs<T>()`, over a new `streamLines` transport primitive.
- **Auth** — single-flight, TTL-cached JWKS (`resolveSigningKey`); `getClaims`
  validates `exp`/`nbf` (with clock-skew leeway) and, opt-in, `iss`/`aud`;
  `generateOAuthState`/`verifyOAuthState` for OAuth CSRF protection.
- **HTTP retry jitter** — `RetryConfig.jitter` (default on) spreads reconnect
  storms; `Retry-After` precedence preserved.

### Fixed (audit)

- **`cacheControl` is now sent as the `Cache-Control: max-age=N` request header**
  on uploads (`upload`/`update`/`uploadToSignedUrl`). It was a `?cacheControl=`
  query param, which the storage server ignores — the requested TTL was silently
  dropped and objects were stored `no-cache`.
- **Realtime `connect()` is single-flighted** via a mutex, so concurrent callers
  can't open two sockets; **`reconnectAttempt` is now an atomic counter** (it was
  a `@Volatile var` incremented in the reconnect loop while being reset from
  `connect()`/`disconnect()` — a non-atomic read-modify-write race).

### Dependencies

- **Toolchain upgrade:** Kotlin `2.1.10` → `2.4.0`, Ktor `3.1.1` → `3.5.0`,
  coroutines `1.10.1` → `1.11.0`, serialization `1.8.0` → `1.11.0`,
  kotlinx-datetime `0.6.2` → `0.8.0`, atomicfu `0.27.0` → `0.33.0`,
  kotlinx-browser `0.3` → `0.5.0`. Also cryptography `0.4.0` → `0.6.0`,
  androidx-credentials `1.3.0` → `1.6.0`, googleid `1.1.1` → `1.2.0`,
  spotless `8.6.0` → `8.7.0`, vanniktech-publish `0.30.0` → `0.34.0` (held
  below `0.35.0`, which requires AGP 8.13+).
- datetime 0.8 moved `Clock`/`Instant` to `kotlin.time`; the two internal
  expiry checks now import `kotlin.time.Clock`.
- **ABI note (binary-compatibility):** recompiling on Kotlin 2.4 changes the
  generated bytecode shape — interface methods with default bodies are now real
  JVM default methods (the `$DefaultImpls` classes are gone) and default-argument
  `$default` synthetics are emitted for interface members; serialization 1.11
  adds `typeParametersSerializers()` to generated `$$serializer` classes. No
  public Kotlin API was removed; the `.api` baselines were regenerated to match.

### Added

- **`FunctionRegion` now covers every Supabase edge region** — added `ANY`,
  `US_WEST_2`, `CA_CENTRAL_1`, `SA_EAST_1`, `EU_WEST_2`, `EU_WEST_3`,
  `EU_CENTRAL_1`, `AP_SOUTH_1`, `AP_SOUTHEAST_2`, and `AP_NORTHEAST_2` to the
  previous five-region subset.
- **`OtpType.SIGNUP` and `OtpType.MAGIC_LINK`** for the token-hash verify flow
  (`verifyOtpWithTokenHash`), which previously couldn't express sign-up
  confirmation or magic-link tokens.
- **`ExplainFormat.XML` and `ExplainFormat.YAML`** — PostgREST `EXPLAIN` supports
  all four output formats.

### Added

- **Phone OTP delivery channel.** `signInWithOtp(..., channel = "whatsapp")` (and
  `sendPhoneOtp(phone, channel)`) now sends the GoTrue `channel` field, so WhatsApp
  OTP delivery is reachable. Defaults to the server's `sms`.

### Fixed

- **CAPTCHA tokens now reach GoTrue.** `signInWithIdToken`, `signInAnonymously`,
  `signInWithOtp`, `verifyOtp`/`verifyOtpWithTokenHash`, `resendEmailOtp`/
  `resendPhoneOtp`, and `resetPasswordForEmail` sent `captcha_token` as a
  **top-level** body field, which GoTrue ignores — it only reads the token nested
  under `gotrue_meta_security`. With CAPTCHA protection enabled every one of these
  calls was rejected. The token is now nested, matching the Web3 and passkey
  requests that already did this. (Public method signatures are unchanged — they
  still take `captchaToken: String?`; only the internal request DTOs changed, so
  the `.api` baseline was updated.)
- **Auto-refresh no longer leaks refresh coroutines under concurrency.** The
  scheduled `refreshJob` was a `@Volatile var` swapped with a non-atomic
  cancel-then-reassign; `scheduleRefresh` is reachable concurrently from
  `saveSession` and `startAutoRefresh`, so two interleaved swaps could orphan an
  uncancellable refresh coroutine that fires forever. It is now an atomic
  reference swapped with `getAndSet(...)?.cancel()`.
- **A malformed JWT `exp` can no longer trigger a refresh storm.** Computing the
  refresh delay multiplied the remaining seconds by 1000; an `exp` claim near
  `Long.MAX` overflowed to a negative value, collapsed to the 1 s floor, and
  busy-refreshed. The seconds are now clamped so the multiply can't overflow.
- **`createSignedUrl` now decodes the server response.** The single-object signed
  URL endpoint returns camelCase `signedURL` (same key the batch endpoint uses),
  but `SignedUrlResponse` was annotated `@SerialName("signed_url")` — so every
  `createSignedUrl` call failed to deserialize. (`createSignedUrls` was correct.)
- **Result-returning auth APIs no longer throw on bad input.** `signUpWithEmail`,
  `signUpWithPhone`, and `retrieveSsoUrl` used `require(...)`, throwing
  `IllegalArgumentException` instead of returning `SupabaseResult.Failure` like
  every other method — a caller doing `when (result)` crashed. They now return
  `Failure`.
- **`MfaListFactorsResponse` and `IcebergNamespaceMetadata` decode when fields are
  omitted.** Their `List` fields were required (no default), so a `/factors` or
  Iceberg metadata response that omitted an empty list threw `MissingFieldException`
  out of a `SupabaseResult`-returning call. The lists now default to empty.
- **Google native sign-in with a nonce now validates.** The Android Google
  provider sent the **raw** nonce to Google's `setNonce` *and* to Supabase, so
  Google embedded the raw value in the ID token's `nonce` claim while Supabase
  compared it against the SHA-256 hash of the raw nonce — every nonce sign-in
  failed. It now sends the hashed nonce to Google and the raw nonce to Supabase,
  matching the (already correct) Apple provider.
- **Realtime now bundles a WebSocket engine on every target.** `supabase-realtime`
  previously declared only `ktor-client-core` + `websockets` and built its
  `HttpClient` with no engine, so the realtime connection failed at runtime on
  Kotlin/Native (Apple, Linux, Windows) and Wasm — and relied on a transitively
  leaked engine on JVM. Each target now supplies a concrete WebSocket-capable
  engine (OkHttp on Android/JVM, Darwin on Apple, CIO on Linux/Windows, Js on
  Wasm), mirroring `supabase-client`.
- **Realtime no longer drops buffered messages on a failed flush.**
  `flushOutboundBuffer` cleared the buffer before confirming a live socket and
  before each send; a socket that was down (or died mid-flush) silently lost the
  buffered application messages. It now drains only with a live session and
  re-buffers any unsent remainder, in order, for the next reconnect.
- **Realtime won't reconnect after an intentional `disconnect()`.**
  `scheduleReconnect` now bails when `intentionalDisconnect` is set (the
  `attemptReconnect` failure path previously called it unguarded), and the
  backoff timer re-checks after its delay — closing a race where a `disconnect()`
  could be followed by a resurrected socket.
- **Realtime reconnect no longer leaks a `CoroutineScope` per attempt.** A single
  long-lived reconnect scope replaces the per-attempt `SupervisorJob` scope that
  was never cancelled; it's torn down in `close()`.
- **Realtime debug counters update atomically.** `recordOutbound/InboundMessage`
  used a read-modify-write on the debug `StateFlow` that could lose updates under
  concurrent sends; now uses an atomic `update {}`.

### Changed

- **`ResizeMode` / `SortOrder` now carry explicit wire values** instead of
  deriving them from `.name.lowercase()`, removing a latent serialization trap.
  No wire-format change.
- **`createRealtimeClient` gained an optional `engineFactory` parameter**
  (defaulting to the platform engine) so callers can supply their own Ktor
  engine — e.g. a mock in tests. Source-compatible for existing callers.
- **Log redaction now also covers `Cookie` / `Set-Cookie`**, so GoTrue session
  cookies never reach the debug log sink.
- **Realtime `Json` now matches the rest of the SDK** (`isLenient`,
  `explicitNulls = false`).

## 0.5.0

### Added

- **Passkeys (`supabase-auth-passkey`):** new module for Supabase's WebAuthn
  passkey authentication.
  - HTTP layer over the auth passkey endpoints — start/verify for both
    registration and discoverable-credential sign-in, plus list/rename/delete
    management.
  - High-level `AuthClient.registerPasskey(...)` / `signInWithPasskey(...)`
    helpers that run the full ceremony behind a single call.
  - `PasskeyAuthenticator` seam so the device ceremony is pluggable. Ships
    `PasskeysKmpAuthenticator`, backed by the cross-platform
    [`passkeys-kmp`](https://github.com/AndroidPoet/passkeys-kmp) `PasskeyClient`,
    which drives the **native** authenticator on Android, iOS, macOS, JVM, and
    the browser (Wasm) from one common API. Typed ceremony failures
    (cancelled / no-credential / unsupported) map to stable error codes.
- **Sample:** `samples/passkey-web` — a Kotlin/Wasm browser demo of the full
  register → sign-in round trip, secrets kept out of the build via a gitignored
  `config.js`.
- **Configurable retry:** `SupabaseConfigBuilder.retry` takes a `RetryConfig`
  (max attempts, exponential base/cap delays, retryable status set). Replaces the
  previously hardcoded transport retry constants; a `Retry-After` header still
  wins over the computed backoff.
- **Observability hooks** on `SupabaseConfig`:
  - `logger: SupabaseLogger?` — route HTTP wire logs into your own logging
    framework (Timber/OSLog/SLF4J) instead of Ktor's default sink.
  - `interceptor: SupabaseInterceptor?` — `onRequest` / `onResponse(status,
    durationMillis)` / `onError(error, durationMillis)` hooks fired around every
    request, for tracing/metrics. All `suspend`, all default no-ops.

## 0.4.0

### Added

- **Storage:** resumable (TUS) uploads via `createResumableUpload(...)`. The
  returned `ResumableUpload` exposes `uploadUrl`, a `progress: StateFlow`, and a
  cancellable `await()`; cancel to pause and resume later (even across app
  restarts) by persisting `uploadUrl`. Uploads in 6 MiB chunks with server-side
  offset tracking.
- **Client:** `SupabaseClient.rawRequest(method, url, body, contentType, headers)`
  for issuing raw, non-JSON requests through the configured client (powers the
  resumable upload protocol).
- **Errors:** network-aware error handling — `SupabaseError.httpStatus` and
  `retryAfterSeconds`, a `SupabaseErrorCategory.Network` category,
  `SupabaseErrorCategory.isRetryable`, `SupabaseError.isNetworkError()`, and an
  `onNetworkError { }` result handler. Transport now classifies timeouts and
  connection failures into `SupabaseErrorCodes.Client` codes so categorization
  works offline.
- **Result operators:** `validate(predicate, lazyError)` (turn a failing
  `Success` into a `Failure`), `flatMapError`/`flatMapErrorSuspend` (failure-side
  recovery chains), and `zip(other) { a, b -> … }` (combine two results).
- **Database:** GeoJSON responses via `selectGeoJson(...)` / the `geojson` flag
  on `select` (`Accept: application/geo+json`).
- **Realtime:** `RealtimeSubscription.presenceState()` for the current
  cumulative presence snapshot.
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

### Changed

- Internal: every module's endpoint URLs are now centralized — `StoragePaths`,
  `AuthPaths`, `AuthAdminPaths`, `DatabasePaths`, and `FunctionsPaths` factor the
  `v1` API version and route prefixes into one place per module instead of
  repeating `/storage/v1/...`, `/auth/v1/...`, `/rest/v1/...`, and
  `/functions/v1/...` string literals across the codebase. No public API change.

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
