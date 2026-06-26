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

#### `updated_at` must be set by the server, not the client

The pull cursor and last-write-wins both order by `updated_at`, so it has to come from **one clock** — the database's. If each device stamps its own `updated_at`, a skewed client clock can write a row with a timestamp that has *already* been scanned past, and that edit is then never pulled again (silent data loss), or a stale write can win LWW. Stamp it in a trigger so client clocks never decide ordering:

```sql
create or replace function set_updated_at() returns trigger as $$
begin
  new.updated_at := (extract(epoch from clock_timestamp()) * 1000)::bigint;
  return new;
end;
$$ language plpgsql;

create trigger notes_set_updated_at
  before insert or update on notes
  for each row execute function set_updated_at();
```

The client still sends an `updated_at` (it drives the local optimistic order), but the trigger overwrites it on the server — that server value is what every device pulls and resolves against.

### Correctness model

Sync is **last-write-wins per row**, ordered by the server's `updated_at` (see above), with a composite `(updated_at, id)` keyset cursor so rows sharing a timestamp are never skipped. Local edits go through the outbox (`enqueue`), which writes the row optimistically *and* queues it in one transaction; the outbox keeps one entry per id, and push is an idempotent `on_conflict` upsert, so an at-least-once retry re-applies harmlessly. Deletes are tombstones carried in the change feed (never hard deletes), so an offline client still learns of them and won't resurrect the row.

Because resolution is whole-row LWW on an absolute value, **don't model fields as relative deltas** (`count = count + 1`): an idempotent retry would be safe but two concurrent edits would lose one increment. Do counters server-side (an atomic SQL `update` or an RPC).

### Known limitations

These are deliberate trade-offs for a thin, schema-driven engine — reach for a heavier framework if your data needs them:

- **Push is all-or-nothing.** One permanently-rejected row (e.g. an RLS denial) keeps the whole table's outbox from draining — there's no dead-letter / skip yet. A transport error simply retries next `sync()`.
- **No stale-cursor reset.** If the server prunes history below a client's cursor, there's no automatic full-resync; the cursor here is a row timestamp, not a compacted log offset, so this only matters if you GC `updated_at` history.
- **Whole-row merge, not per-field.** A remote update replaces the whole row; a concurrent local edit to a *different* field on the same row is clobbered by the newer write.
- **Tombstones aren't GC'd.** Soft-deleted rows accumulate; prune them server-side only once every client has synced past the delete (otherwise a long-offline client resurrects the row).

**Targets:** JVM, iOS, macOS.

**Note:** A push is all-or-nothing per request — on success every id is accepted; a transport/server error throws, leaving the outbox intact for the next `sync()`. The JSON↔`Record` mapping is pure and unit-tested (`MappingTest`); the live PostgREST/Realtime path is exercised by the e2e harness.
