# supabase-sync-sqldelight

The **local store** for the sync engine, backed by [SQLDelight](https://sqldelight.github.io/). This is the module that **saves data on the device**: synced rows are written to a local database, reads come from local (instant, offline-capable), and local writes are queued in an outbox until they can be pushed.

**Coordinate:** `io.github.androidpoet:supabase-sync-sqldelight` *(unreleased — part of the `supabase-sync` family)*

Synced data lives in **your own typed tables** — the ones you already query — not a parallel cache. Each table you want as the source of truth registers a `TableAdapter`; tables without one fall back to a generic blob cache (zero-config). The store keeps only bookkeeping next to your data: an **outbox** of pending local writes, a per-table pull **cursor**, and a sidecar holding the `updatedAt` + tombstone (so your table schema stays a clean 1:1 mirror of the remote, with no sync columns leaking in).

```kotlin
// Bring your own SqlDriver (the standard SQLDelight way) — Android, iOS/macOS native, JVM, etc.
val local = openOfflineSyncStore(driver, adapters = supabaseAdapters(driver))

// reads are local + offline; writes go to the outbox, then push on the next sync()
local.enqueue("notes", PendingChange(record, ChangeKind.UPSERT))
val page = local.page("notes", limit = 25, offset = 0)   // Page(items, total, hasMore)
```

Pagination comes in three shapes for both the typed and blob paths: `page(limit, offset)` → `Page<Record>`, keyset `pageAfter(afterId, limit)`, and `count(table)`.

**Targets:** JVM, iOS, macOS.

**Note:** `upsert` is monotonic — it never regresses a row to an older `updatedAt`, which guards against out-of-order realtime/retry. The codegen (`supabase-codegen --adapters`) emits the `supabaseAdapters(driver)` factory of `TableAdapter`s for you.
