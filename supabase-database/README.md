# supabase-database

PostgREST access: `select`, `insert`, `update`, `delete`, `upsert`, count/range reads, and RPC (`rpc` / `rpcGet`), all driven by the type-safe filter DSL (`eq`, `neq`, `gt`, `like`, `ilike`, `in`, `is`, `textSearch`, `contains`, ordering, limits). Each call returns a `SupabaseResult`, so errors carry a `category` instead of throwing.

**Coordinate:** `io.github.androidpoet:supabase-database`

```kotlin
import io.github.androidpoet.supabase.database.createDatabaseClient

val database = createDatabaseClient(client) // client from supabase-client

database.select(table = "todos") {
    eq("done", "false")
    order("created_at", ascending = false)
    limit(25)
}.onSuccess { json -> println(json) }
 .onFailure { error -> println("query failed: ${error.message}") }
```

`select` returns the raw response body as a `SupabaseResult<String>`; decode it with `kotlinx.serialization` (typed `selectTyped` / `insertTyped` extensions are available for `@Serializable` models).

**Targets:** Android, JVM, iOS, macOS, tvOS, watchOS, Linux, Windows, WasmJs.

**Note:** Filters are applied inside the trailing `filters { ... }` lambda; an empty lambda fetches all rows (subject to your RLS policies).
