# supabase-functions

Invoke Supabase Edge Functions. Call a function by name with an optional body, choose the HTTP method, pin a region, and get the response back as a `SupabaseResult`. A binary variant (`invokeWithBody`) lets you POST raw bytes (images, protobuf, etc.).

**Coordinate:** `io.github.androidpoet:supabase-functions`

```kotlin
import io.github.androidpoet.supabase.functions.createFunctionsClient

val functions = createFunctionsClient(client) // client from supabase-client

functions.invoke(
    functionName = "hello-world",
    body = """{"name":"Ada"}""",
).onSuccess { response -> println(response) }
 .onFailure { error -> println("invoke failed: ${error.message}") }
```

`invoke` returns the response body as a `SupabaseResult<String>`; defaults to `POST` (override via `method = FunctionMethod.GET`, etc.). Decode JSON responses with `kotlinx.serialization`.

**Targets:** Android, JVM, iOS, macOS, tvOS, watchOS, Linux, Windows, WasmJs.

**Note:** The auth/region headers are merged automatically; anything you pass in `headers` is layered on top.
