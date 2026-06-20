# supabase-client

The transport core. `supabase-client` creates the `SupabaseClient` that every feature module (`auth`, `database`, `storage`, `realtime`, `functions`) is built from: it owns the Ktor HTTP layer, the platform engine selection, auth-state headers, retries, and logging. Start here — you always need this module.

**Coordinate:** `io.github.androidpoet:supabase-client`

```kotlin
import io.github.androidpoet.supabase.client.Supabase

val client = Supabase.create(
    projectUrl = "https://your-project.supabase.co",
    apiKey = "your-anon-key", // use the anon key in client apps — never the service-role key
) {
    logging = true
}
```

Pass `client` to the feature factories (`createAuthClient(client)`, `createDatabaseClient(client)`, etc.). The default `engineFactory` resolves per platform automatically, but you can override it via `Supabase.create(..., engineFactory = ...)`.

**Targets:** Android, JVM, iOS, macOS, tvOS, watchOS, Linux, Windows, WasmJs — on OkHttp (Android/JVM), Darwin (Apple), CIO (Linux/Windows) and the JS Ktor engine for the browser (`wasmJs`). The browser ships only as Kotlin/Wasm via `wasmJs`; there is no separate Kotlin/JS (`js()`) artifact.

**Note:** `projectUrl` must start with `http://` or `https://`; a single trailing slash is stripped for you.
