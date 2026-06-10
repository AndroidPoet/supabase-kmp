# Contributing to Supabase KMP

Thanks for your interest in improving Supabase KMP! This page covers the
mechanics; see [docs/guides/contributing.md](docs/guides/contributing.md) for
the deeper API-design conventions.

## Prerequisites

- JDK 17
- Android SDK (set `sdk.dir` in `local.properties`, or `ANDROID_HOME`)
- Xcode (only for building/running the Apple targets)

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

If you add code that trips a new detekt rule you cannot reasonably fix, refresh
the baseline with `./gradlew detektBaseline` — but prefer fixing the finding.

## Compiling other targets

```bash
./gradlew compileKotlinIosArm64 compileKotlinLinuxX64 compileKotlinWasmJs
```

## Pull requests

- Keep changes focused; one logical change per PR.
- Add or update tests for behavioral changes (`src/commonTest`).
- Update `CHANGELOG.md` under an "Unreleased" heading.
- Public API additions need KDoc — Dokka builds the reference site from it.

## Releasing (maintainers)

1. Bump `VERSION` in `buildSrc/src/main/kotlin/io/github/androidpoet/supabase/Configuration.kt`.
2. Update `CHANGELOG.md`.
3. Publish a GitHub Release — the `Publish` workflow pushes to Maven Central.
