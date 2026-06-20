# supabase-core

Foundational types shared by every other Supabase KMP module: the `SupabaseResult<T>` Result monad (`Success`/`Failure`), the `SupabaseError` model with its `SupabaseErrorCategory` (`Conflict`, `NotFound`, `Unauthorized`, `RateLimited`, `Validation`, `Internal`, `Network`, `Unknown`), value-class IDs, and the PostgREST filter DSL primitives. You rarely depend on this directly — it comes in transitively with `supabase-client` — but it is published independently so the Result/error surface can be referenced on its own.

**Coordinate:** `io.github.androidpoet:supabase-core`

```kotlin
import io.github.androidpoet.supabase.core.result.SupabaseResult

fun describe(result: SupabaseResult<String>): String =
    result.fold(
        onSuccess = { value -> "ok: $value" },
        onFailure = { error -> "error (${error.category}): ${error.message}" },
    )
```

Transform with `map` / `flatMap` / `recover`, branch with `onSuccess` / `onFailure`, and read failures via `error.category`.

**Targets:** Android, JVM, iOS, macOS, tvOS, watchOS, Linux, Windows, WasmJs.

**Note:** This is a pure-Kotlin module with no HTTP or platform dependencies; the engine wiring lives in `supabase-client`.
