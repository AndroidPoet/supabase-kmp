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

1. **Return `SupabaseResult<T>` for anything fallible.** The one carve-out: a layer that
   *feeds* a `Flow`/Paging source may stay throw-based (Flows need exceptions) — but say so
   in KDoc. The discoverable, short-named method is always the safe one; a throwing variant,
   if needed, is the explicit `…OrThrow`.
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
