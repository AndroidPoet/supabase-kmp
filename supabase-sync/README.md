# supabase-sync

Offline-first sync between **Supabase** and a local database for Kotlin Multiplatform. This module is the `RemoteSource` that drives the engine against Supabase: incremental **pull** and bulk **push** over PostgREST (`supabase-database`) plus a live delta stream over **Realtime** (`supabase-realtime`). Pair it with `supabase-sync-sqldelight` for the on-device store and `supabase-sync-core` for the engine.

**Coordinate:** `io.github.androidpoet:supabase-sync` *(unreleased)*

```kotlin
import io.github.androidpoet.supabase.sync.SyncEngine
import io.github.androidpoet.supabase.sync.remote.SupabaseRemoteSource
import io.github.androidpoet.supabase.sync.remote.SyncColumns

// 1. Build the Supabase clients (share one auth token so RLS applies to reads + realtime).
val database = createDatabaseClient(client)
val realtime = createRealtimeClient(client)

// 2. The remote half, over PostgREST + Realtime.
val remote = SupabaseRemoteSource(
    database = database,
    realtime = realtime,
    columns = SyncColumns(id = "id", updatedAt = "updated_at", deleted = "deleted"),
)

// 3. The local half (SQLDelight) + the engine.
val local = openOfflineSyncStore(driver, adapters = supabaseAdapters(driver))
val engine = SyncEngine(local, remote)

engine.sync("notes")                         // pull changed rows → merge → push the outbox
engine.observe("notes").collect { /* … */ }  // live Realtime deltas, merged into local
```

### What each operation does
- **pull** — fetches rows whose `(updated_at, id)` is greater than the stored cursor (composite keyset, so rows sharing a timestamp are never skipped), ordered ascending and capped at `pageSize`.
- **push** — bulk upsert with `on_conflict` on the id column; deletes are **soft** (`deleted = true`), so an incremental pull still sees them.
- **changes** — a cold `Flow` of `postgres_changes`; INSERT/UPDATE carry the new row, DELETE becomes a tombstone.

### Required table shape
Every synced table must expose three columns (names configurable via `SyncColumns`):

| column | type | purpose |
|---|---|---|
| `id` | text / uuid | primary key, used for `on_conflict` |
| `updated_at` | **`bigint` (epoch millis)** | last-write-wins + the pull cursor — **not** `timestamptz`, so it compares against the engine's `Long` cursor |
| `deleted` | `boolean` default `false` | soft-delete tombstone |

Add RLS policies that let the same token both **read** the rows and **subscribe** to them via Realtime.

**Targets:** JVM, iOS, macOS.

**Note:** A push is all-or-nothing per request — on success every id is accepted; a transport/server error throws, leaving the outbox intact for the next `sync()`. The JSON↔`Record` mapping is pure and unit-tested (`MappingTest`); the live PostgREST/Realtime path is exercised by the e2e harness.
