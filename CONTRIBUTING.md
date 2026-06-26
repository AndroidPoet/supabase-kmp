# Contributing to Supabase KMP

Thanks for your interest in improving Supabase KMP! This page covers the
mechanics. The user-facing docs live in [`website/`](website/) (Nextra) and are
published to <https://androidpoet.github.io/supabase-kmp/>.

## Prerequisites

- JDK 17
- Android SDK (set `sdk.dir` in `local.properties`, or `ANDROID_HOME`)
- Xcode (only for building/running the Apple targets)

## Git hooks (recommended)

Run once after cloning so the same gates CI enforces run automatically:

```bash
./gradlew installGitHooks
```

This enables a **pre-commit** hook (auto-formats staged Kotlin with `spotlessApply`)
and a **pre-push** hook (`detekt` + `apiCheck`; add `RUN_TESTS=1` for `jvmTest`).
Skip per-invocation with `git commit/push --no-verify`. See
[`.githooks/README.md`](.githooks/README.md).

## Local checks

Before opening a PR, run the same gates CI runs:

```bash
./gradlew detekt        # static analysis (uses config/detekt/detekt.yml)
./gradlew apiCheck      # fails if the public/binary API changed unexpectedly
./gradlew jvmTest       # unit tests on the JVM target
```

If you intentionally changed the public API, regenerate the dumps and commit
them:

```bash
./gradlew apiDump
```

**Before changing any public API, read [`API_DESIGN.md`](API_DESIGN.md)** — the SDK's
design principles (Result-first, explicit, thin-over-REST, type-safe) and the concrete
rules every surface change must follow. It is the tie-breaker when a change is ambiguous.

**Before changing any public API, read [`docs/API_DESIGN.md`](docs/API_DESIGN.md)** —
the SDK's design principles (Result-first, explicit, thin-over-REST, type-safe) and the
concrete rules every surface change must follow. It is the tie-breaker when a change is
ambiguous.

If you add code that trips a new detekt rule you cannot reasonably fix, refresh
the baseline with `./gradlew detektBaseline` — but prefer fixing the finding.

## Compiling other targets

```bash
./gradlew compileKotlinIosArm64 compileKotlinLinuxX64 compileKotlinWasmJs
```

## Concurrency & thread-safety rules (KMP)

This is multiplatform code that runs on multithreaded targets (JVM, Android,
Kotlin/Native). Follow these rules so shared mutable state is correct on every
platform, not just the JVM.

- **Use `kotlin.concurrent.Volatile`, never `kotlin.jvm.Volatile`.** The
  `kotlin.concurrent` annotation (Kotlin 1.9+) is the multiplatform one: it gives
  real volatile semantics on JVM and on Kotlin/Native (the default memory model
  since 1.7.20), and is a harmless no-op on the single-threaded JS/Wasm targets.
  The JVM-only annotation silently does nothing in `commonMain`.
- **`@Volatile` only guarantees _visibility_ and atomic _single_ read/write.** It
  does **not** make compound operations atomic. A `check-then-act`
  (`if (x == null) { x = ... }`) or `read-modify-write` (`count = count + 1`) on a
  `@Volatile` field is still a race. Use `@Volatile` only for a flag/reference
  that is independently set and read (e.g. a pinned auth token, an
  `intentionalDisconnect` flag).
- **For compound atomicity, use the right tool:**
  - `kotlinx.coroutines.sync.Mutex` (`withLock`) for suspend-friendly critical
    sections — e.g. single-flight connect, refresh dedup.
  - `kotlinx-atomicfu` (`atomic(...)`, `incrementAndGet`, `getAndSet`) for lock-free
    counters/refs — e.g. the realtime ref counter and pending-heartbeat ref.
  - `MutableStateFlow.update { }` (a CAS loop) instead of `value = value.copy(...)`
    when several coroutines mutate the same flow.
  - `kotlinx.atomicfu.locks.synchronized(lock)` for a plain non-suspending monitor,
    but **never call a `suspend` function while holding it** — snapshot under the
    lock, release, then suspend.
- **Don't reach for `java.util.concurrent`** (or any `java.*`) in `commonMain` — it
  doesn't exist on Native/Wasm. Use the coroutines/atomicfu primitives above.

## Pull requests

- Keep changes focused; one logical change per PR.
- Add or update tests for behavioral changes (`src/commonTest`).
- Update `CHANGELOG.md` under an "Unreleased" heading.
- Public API additions need KDoc — Dokka builds the reference site from it.

## Versioning

The library is in its **`0.x` pre-stable line** and is **not** at `1.0.0` yet. We
follow the `0.x` semver convention: until `1.0`, the public API is allowed to break.

What that means in practice:

- **Breaking changes bump the minor.** A release that changes or removes existing
  public API bumps the **minor** (`0.9.x` → `0.10.0`). Fixes and additive features
  ship as a **patch** within the current minor (`0.10.0` → `0.10.1`).
- **Prefer additive, but don't fear a break.** Reach first for backward-compatible
  changes (append optional parameters with defaults, add new declarations). When a
  clean redesign is worth more than source-compatibility — as with the `0.10.0`
  filter DSL — a breaking change is acceptable pre-1.0; just call it out in the
  `CHANGELOG` under `### Changed` / `### Removed` and regenerate the API baseline
  with `./gradlew apiDump` (CI gates on `apiCheck`).
- **One version, all modules.** Every published module shares the single `VERSION`
  constant and is released together at the same number.

When we're confident the API is stable, we'll revisit and cut `1.0.0` deliberately —
until then, additions land as patches and breaks bump the minor.

## Releasing (maintainers)

1. Bump `VERSION` in `buildSrc/src/main/kotlin/io/github/androidpoet/supabase/Configuration.kt`
   — patch for additive/fix releases, minor for breaking ones (see [Versioning](#versioning)).
2. Update `CHANGELOG.md` (add a dated section for the new version) and the version
   in the `README.md` setup snippet.
3. Run the release gates locally: `./gradlew apiCheck detekt jvmTest`.
4. Publish a GitHub Release tagged `vX.Y.Z` — the `Publish` workflow pushes all
   modules to Maven Central. Let one release finish before tagging the next so two
   publishes don't run at once.
