# API Design Principles

The north star for every public-API decision in this SDK. Read this before adding or
changing anything in a published module's surface (anything visible in an `api/*.api`
dump). When a change is ambiguous, the identity below is the tie-breaker.

## Identity — what this SDK *is*

A **thin, explicit, Result-first** Supabase client for Kotlin Multiplatform. We are
deliberately **unlike** the established Kotlin Supabase SDK, and the difference is
**semantic, not syntactic**:

| Principle | What it means | What we are NOT |
|---|---|---|
| **Result-first** | Every fallible call returns `SupabaseResult<T>`, never throws. | No `RestException`-style throwing APIs. |
| **Explicit / no magic** | Modules are constructed with `create<X>Client(...)` factories. Typed decoding is opt-in (`selectTyped<T>`). | No plugin-install DSL on a god-client; no reflection-y type registration. |
| **Thin over REST** | One public call ≈ one HTTP request. The mapping to PostgREST/GoTrue is honest and visible. | No long-lived fluent chains (`from().select().eq().execute()`) that hide the request boundary. |
| **Type-safe where it counts** | `Column<T>` value-class tokens make `name eq 5` a compile error. | No stringly-typed filter keys as the primary path (`raw(...)` is the documented escape hatch). |
| **Minimal deps** | Don't leak third-party types (Ktor, kotlinx-serialization) into the surface unless they're a deliberate, documented power-user seam. | No transitive types in signatures for convenience. |

> The filter **DSL block** (`select(...) { where { col eq x } }`) is the idiomatic Kotlin
> type-safe-builder pattern (same as `buildList {}`, Ktor, Exposed). Using it is **not**
> imitation — our identity lives in the semantics above, not the filter syntax. Keep the
> DSL; never replace it with a fluent chain.

## Design rules (apply on every API change)

1. **Surface failure by operation kind — don't reflexively wrap everything in a Result.**
   This is the rule industry research corrected us on: no shipping Kotlin SDK (supabase-kt,
   Firebase, Apollo, Ktor, AWS, SqlDelight) wraps streaming or local-DB ops in a result type,
   because a `Result<Channel>` on `subscribe()` describes only the first attempt and goes stale
   on the mid-stream disconnect that actually matters. So:
   - **Request/response (HTTP: postgrest/auth/storage/functions)** → return `SupabaseResult<T>`.
     This is our deliberate differentiator from the throwing established SDK, and the one place
     a per-call result genuinely fits. (`kotlin.Result` is officially *not* for domain errors —
     KEEP — so our own sealed `SupabaseResult` carrying `SupabaseError` is the sanctioned shape.)
   - **Connection / streaming (realtime)** → surface failure **in-band**: a `status: StateFlow`
     and sealed events in the `Flow`, *not* a `Result` on `subscribe()`. Prefer a cold
     `callbackFlow { … awaitClose { unsubscribe() } }`; if a stream is hot (`replay = 0`,
     lifecycle owned by `subscribe`/`unsubscribe`), the KDoc must say so — never mislabel it cold.
   - **Local-DB / cache writes (sync)** → let them **throw** (SqlDelight-native) and surface the
     pipeline outcome as a `status: StateFlow<SyncStatus>` — mirroring Firebase's offline model.
   The discoverable, short-named method is always the safe one; a throwing variant on an HTTP
   call, if needed, is the explicit `…OrThrow`.
2. **The default name returns the rich/safe type.** Don't ship `foo()` (lossy/throwing) next
   to `fooWithResult()` (rich) — fold it into one. Avoid `WithResult`/`Unit`/`OrThrow` suffix
   sprawl: pick one axis (a `returning`/`format` param) over parallel methods.
3. **Model closed sets as `enum`/`sealed`, never `String`.** Providers, channels, formats,
   statuses, error categories. A `String` that has a fixed value set is a bug.
4. **No secrets in `data class`.** Auto-`toString()` on a `data class` prints fields — never
   put tokens/keys/secrets in one (`Session`, OAuth client secrets, config). Use a plain
   `class` with a redacting `toString()`. Also: don't make a `data class` whose `equals`/`copy`
   are meaningless (e.g. lambda fields) — use a plain class.
5. **One construction idiom:** `create<X>Client(client, …)` factories (+ a `getX()` accessor
   where the module attaches to `SupabaseClient`). New modules expose factories, not bare
   public constructors.
6. **One verb per concept SDK-wide:** `get` (read one) · `list` (enumerate) · `create`/`insert`
   (by semantics) · `update` · `delete` · `upsert`. Don't introduce `fetch`/`retrieve`/`load`/
   `remove` synonyms. Keep an argument's position consistent across methods (e.g. `accessToken`
   always first).
7. **Coroutines:** no hardcoded `Dispatchers.*` in the public surface; don't take or store a
   root `CoroutineScope` you never cancel (prefer `channelFlow { awaitClose { } }`); always
   re-throw `CancellationException` before a catch.
8. **Streaming, not whole-buffer, for IO that can be large.** Storage/transfer APIs take/return
   `kotlinx.io` `Source`/`RawSink` (or `Flow<ByteArray>`), not just an in-memory `ByteArray`.
9. **Flows: cold vs hot must match the KDoc.** If it's a hot `SharedFlow`/`replay=0`, say so and
   say who owns the lifecycle. Expose `StateFlow`, never `MutableStateFlow`. A `Flow`-returning
   function must not throw eagerly — defer into `flow { }`.
10. **Read-only collections in signatures** (`List`/`Map`/`Set`, not the `Mutable*` variants).
11. **Don't leak internals.** Wire DTOs, generated DB types, and impl helpers are `internal`.
    `@PublishedApi internal` is only for inline-function support. `const` in a `private
    companion` still leaks as a public static field — make it a non-`const` `private val`.
12. **`data class` in a public API is an ABI hazard** (`copy`/`componentN` break when a field is
    added). Use them for genuinely stable value types; before tagging 1.0, freeze the field set
    or switch config-style holders to a plain class + named-arg constructor.
13. **Typed ids:** if a `value class` id (`UserId`, `BucketId`, …) exists, use it at the boundary
    — don't ship unused id scaffolding alongside raw-`String` parameters. Either adopt or delete.

## Pre-1.0 posture

Breaking changes are **free now and expensive after 1.0** — so make the *right* surface
decision now rather than the cheapest. A rename/return-type change that aligns with the rules
above is worth doing before the tag; an additive convenience can wait for 1.x.

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the mechanics (explicitApi, BCV `apiDump`,
detekt, coverage gate) that enforce the surface.

## 1.0 stabilization ledger

The outcome of the full-SDK audit (6 surface auditors) reconciled against industry research
(Kotlin/AndroidX guidelines, Google AIP, and the conventions of supabase-kt, Firebase, Apollo,
Ktor, AWS, SqlDelight, jOOQ, Exposed). Findings were **source-verified** — a `.api` dump hides
`toString`/`equals` overrides, KDoc, and deprecations, so several dump-based "P0s" were already
handled in source.

**Done (stabilized):**
- Credential `toString()` redaction — already present on `Session` / `OAuthClient` (false-positive P0).
- `ErrorResponse` (wire-only) → `internal`; deleted unused typed-id value classes (dead public API).
- `SupabaseConfig` → plain `class` (function-typed fields; no meaningful `copy`/`equals`).
- `MAX_PULL_PAGES` / `DEFAULT_PAGE_SIZE` no longer leak as public static fields.
- `asFlow()` relabeled hot (was mislabeled "Cold"); `streamLines` / `invokeSSE` defaults defer
  their throw into the cold `flow { }` instead of throwing eagerly.
- **Database query response shape unified into `ResponseFormat`** — `select` / `rpc` / `rpcGet` /
  `selectRange` took 4 mutually-exclusive `head`/`single`/`csv`/`geojson` booleans (illegal combos
  caught at runtime by `require()`); now a single `format: ResponseFormat` enum makes the illegal
  combinations unrepresentable. The thin wrappers (`selectCsv`/`selectHead`/`rpcCsv`/`rpcGetSingleTyped`/…)
  just pin a `format`. The redundant `selectTyped(single = …)` flag was dropped (the typed terminal
  `selectSingleTyped` already covers the single-row case). The one orthogonal modifier left,
  `stripNulls`, is still rejected at runtime against CSV/GEOJSON (a format-vs-modifier guard, not a
  format-vs-format one).

**Won't-fix (industry says current design is correct):**
- Realtime + sync are **not** converted to `SupabaseResult`. Reactive in-band (status `StateFlow`
  + sealed events) and throw-plus-status-Flow are the idiomatic, more-correct surfaces — see Rule 1.
- `SupabaseResult` stays (not `kotlin.Result`).

**Do before 1.0 (breaking — free now, expensive later):**
- ~~**Database query surface (format enum):**~~ **DONE** — `select`/`rpc`/`rpcGet`/`selectRange`
  now take one `ResponseFormat` enum instead of the exclusive booleans; the *typed* terminals stay
  separate (cardinality changes the return type). See the Done section above.
- ~~**Database method sprawl:**~~ **DONE** — the `rpcGet` typed terminals (`rpcGetTyped`/`…Unit`/
  `…ListTyped`/`…SingleTyped`/`…MaybeSingleTyped`/`…Csv`/`…Head`) each dropped their redundant
  `List<Pair>` convenience overload and standardized on one `Map<String,String> = emptyMap()` form
  (RPC arguments are *named*, so they are Map-shaped; the pair-list seam stays on the raw
  `DatabaseClient.rpcGet` interface method for the rare ordered case). The `Request`-object typed
  overloads are unchanged.
- ~~**Naming sweep:**~~ **DONE** — landed across modules:
  - *Closed sets → enums:* `MessagingChannel` (auth phone channel) and `HttpLogLevel` (client config)
    replace the stringly-typed values; Ktor's `LogLevel` no longer leaks through config.
  - *Wire DTOs internalized:* ~46 request/response data classes in auth + storage are now `internal`.
  - *One verb / consistent nouns:* auth `fetchJwks`→`getJwks`, `retrieveSsoUrl`→`getSsoUrl`;
    auth-admin `auditLogEvents`→`listAuditLogEvents`, `signOut(jwt=)`→`signOut(accessToken=)`;
    storage `emptyBucket`→`clearBucket`, Iceberg `load*`→`get*` / `drop*`→`delete*`, duplicate
    `commitTable` folded into `updateTable`; realtime unified on the **Subscription** noun
    (`removeChannel*`→`removeSubscription*`, `removeAllChannels`→`removeAllSubscriptions`) with a
    `get*` prefix for snapshots (`activeChannels`→`getActiveChannelNames`,
    `activeChannelDetails`→`getActiveChannels`).
  - *`accessToken` position consistent:* MFA methods take `accessToken` first.
  - *Result-shaping twins collapsed (Rule 2 — base name returns the rich/safe type):*
    storage `remove`+`removeWithResult`→`deleteObjects` (rich `List<FileObject>`); auth
    `verifyOtp`/`verifyOtpWithTokenHash` (and their shorthands + `*AndSaveSession`) now return
    `OtpVerifyResult`, deleting every `*WithResult` twin; functions `invokeUnit`/`invokeWithBodyUnit`
    dropped (`invoke`/`invokeWithBody` already return a branchable `SupabaseResult`).
  - *Factory/accessor parity:* auth-admin `authAdmin(key)`→`createAuthAdminClient(client, key)`;
    added `SupabaseClient.functions` accessor (mirrors `.auth`/`.database`/`.storage`); sync gained
    `createSyncEngine` / `createSupabaseRemoteSource` factories. (Realtime stays factory-only by
    design — it owns a live WebSocket, so a fresh-per-access property would drop subscriptions.)
  - *SQLDelight leakage closed:* `SqlDelightLocalStore`'s generated-type constructor is `internal`
    (consumers use the `SqlDriver` ctor / `openOfflineSyncStore`) and the generated `…sync.store.db`
    package — including the `Cursor` that collided with the hand-written `sync.Cursor` — is excluded
    from the tracked ABI.

**Decide before 1.0 (additive, can land in 1.x but design now):**
- Storage streaming: add `kotlinx.io` `Source`/`Sink` (file-backed) overloads so large
  upload/download isn't forced through an in-memory `ByteArray`.
