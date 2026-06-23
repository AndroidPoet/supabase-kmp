# supabase-sync-core

The transport-agnostic offline-first **sync engine**. It runs one pull → merge → push cycle per table and talks only to two interfaces — a `LocalStore` (your device cache) and a `RemoteSource` (the server) — so the same logic drives Supabase today and anything else tomorrow. This module has **no Supabase or database dependency** (just coroutines + serialization).

**Coordinate:** `io.github.androidpoet:supabase-sync-core` *(unreleased — part of the `supabase-sync` family)*

Key types:

- `SyncEngine(local, remote, resolvers)` — `suspend fun sync(table)` (pull, resolve conflicts, push the outbox) and `fun observe(table): Flow<RemoteChange>` (live deltas, merged through the same resolver).
- `Record(id, updatedAt, deleted, fields)` — the transport-neutral row; `updatedAt` is the server-set time (last-write-wins compares it), `deleted` is a soft-delete tombstone.
- `Cursor(updatedAt, id)` — composite keyset resume position for incremental pulls.
- `ConflictResolver` / `LastWriteWins` / `ResolverRegistry` — per-table conflict policy.
- `LocalStore` + `RemoteSource` — the two seams (implemented by `supabase-sync-sqldelight` and `supabase-sync`).
- `TableAdapter` — makes your own typed table the source of truth for a synced table.

```kotlin
val engine = SyncEngine(local, remote, ResolverRegistry().register("notes", myMerger))

val result = engine.sync("notes")            // pull → resolve → push outbox
engine.observe("notes").collect { /* live remote changes, already merged locally */ }
```

**Targets:** JVM, iOS, macOS.

**Note:** The engine is stateless and side-effect-free beyond the two interfaces — unit-test it with in-memory fakes, no network or database required.
