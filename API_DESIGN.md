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
- `asFlow()` relabeled hot (was mislabeled "Cold"); `streamLines` / `invokeSse` defaults defer
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
- **Sync `PullResult`/`PushResult`/`SyncResult` keep the `Result` suffix.** They are plain
  operation-summary data classes (counts, accepted/rejected ids, next cursor), the idiomatic name
  for "the result of an operation" (cf. WorkManager). They never appear *inside* a `SupabaseResult`,
  so there's no monad confusion at a call site; renaming to `*Summary`/`*Outcome` is churn without a
  clear ergonomic gain. (`PullProgress` already uses a non-`Result` name where it read better.)
- **Paging width split (`Long` in the store layer, `Int pageSize` in `supabase-sync-paging`) is
  intentional** — the AndroidX Paging 3 API is `Int`-based, while the low-level `LocalStore`/
  `TableAdapter` use `Long` for large offsets. Each matches its layer's convention.

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

- ~~**`selectRange` raw `Pair` return:**~~ **DONE** — `selectRange` returned
  `SupabaseResult<Pair<String, PostgrestRange>>` (callers had to remember `.first`/`.second`); it now
  returns a named `PostgrestRawPage(body, count, range)`, the raw-string analogue of `PostgrestPage<T>`.

**Decide before 1.0 (additive, can land in 1.x but design now):**
- Storage streaming: add `kotlinx.io` `Source`/`Sink` (file-backed) overloads so large
  upload/download isn't forced through an in-memory `ByteArray`. A bytes-returning functions
  response accessor (`invokeForBytes`) is the same shape of additive gap — both are purely new
  surface, safe to land post-1.0.

## Final naming audit (last pass before the freeze)

A second, naming-only sweep across **every** module's `.klib.api` (7 parallel auditors +
a cross-module acronym/factory-verb/enum-casing analysis), judged against the Kotlin/JetBrains
API guidelines. Verdict: the surface was already in good shape — all auditors reported "no
blockers." A handful of genuine, contained violations of the SDK's **own** conventions were
fixed; the rest are deliberate, recorded decisions.

**Fixed (the SDK's own convention was violated, and the change was contained):**
- `invokeSSE`→`invokeSse` — acronyms are word-cased in every other identifier (`Url`, `Otp`,
  `Jwt`, `Csv`, `GeoJson`); this was the single all-caps outlier in the whole SDK.
- `Paginator.endReached`→`isEndReached` — booleans read as assertions; its sibling is `isLoading`.
- `selectWithCount`→`selectWithCountTyped` — it decodes into `T`, so it joins the `*Typed` family.
- `RealtimeChannelBuilder.setPrivate`→`configurePrivate` — matches the builder's `configure*` group.
- storage `createUploadSignedUrl(WithPath)`→`createSignedUploadUrl(WithPath)` — `Signed`+direction
  order, matching `createSignedUrl` / `getSignedDownloadUrl`.
- `VectorDistanceMetric.DOTPRODUCT`→`DOT_PRODUCT`; auth-admin OIDC `jwksUri`→`jwksUrl`
  (both keep their wire value via `@SerialName`).
- Removed two exact duplicates: core `toResultFlow()` (≡ `asFlow()`) and realtime `statusFlow()`
  (≡ the `status` property).

**Deliberate — kept by design (recorded so they aren't "fixed" later):**
- **Enum-entry casing is intentionally mixed.** Wire-mapped / option / SQL-keyword enums use
  `UPPER_SNAKE` (`Order.ASC`, `ResponseFormat.GEOJSON`, every `@SerialName`-backed enum); the two
  pure-domain category sets — `SupabaseErrorCategory` and `TextSearchType` — use `PascalCase`
  because they read as a closed set of named conditions at a `when`/`onFailureCategory` call site.
  Both styles are sanctioned by the Kotlin style guide; the split is by role, not by accident.
- **Filter-operator vocabulary** (`eq`/`neq` abbreviated, `greater`/`greaterEq` spelled, range
  ops `rangeGt`/`rangeGte`) is kept — the query DSL was reviewed and blessed in the main audit;
  the equality/ordering shorthands match what query-DSL users reach for. Not reopened.
- **Factory verbs differ by what they return**, on purpose: `Supabase.create` (root builder),
  `createXClient` (feature clients), `googleAuthProvider`/`appleAuthProvider` (return a provider
  *descriptor* you hand to config, not a client), `openOfflineSyncStore` (opens a DB-backed
  resource).
- **`rpcTyped`=single vs `selectTyped`=list** is intentional: an RPC returns one value by nature,
  a `select` returns rows. `rpcListTyped` / `rpcSingleTyped` make the other cardinalities explicit.
- **Pseudo-namespaced auth methods** (`mfa*`/`oauth*`/`passkey*`) read noun-first by design — a
  flattened stand-in for sub-clients; admin stays verb-first CRUD. `getUserById`/`updateUserById`
  keep `ById` because the non-admin `getUser` already means "by access token."
- **`*OrThrow` / `*WithResult` / `*WithAck` suffixes** are the deliberate explicit-exception /
  await-confirmation escape hatches on top of the Result-first defaults — kept, not collapsed.

**Deferred (real but not worth pre-freeze churn; the sync surface isn't published in 1.0):**
- `sync-core` `Record`/`Cursor` → `SyncRecord`/`SyncCursor` (collide with `java.lang.Record`) —
  ~285 references across the sync tree; the sync modules' publishing is deferred, so this can ride
  the same train as their first publish.
- `RealtimeSubscription.channel: String` → `channelName` (a name-trap, but `.channel` is too
  ambiguous to rename mechanically without risk) and storage `ObjectListV2Result`→`*Response`
  (suffix polish, 69 references) — both additive-safe to revisit.
