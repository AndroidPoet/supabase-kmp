# Architecture

Supabase KMP is split into focused modules to keep the dependency graph clean and predictable.

## Module map

- `supabase-core`: result type, error model, filter DSL, typed IDs
- `supabase-client`: HTTP transport, base client, auth header management
- `supabase-auth`: GoTrue operations, PKCE, OAuth URL generation, MFA, session manager
- `supabase-database`: PostgREST CRUD and RPC
- `supabase-storage`: bucket/object operations and URL generation
- `supabase-realtime`: websocket channels, subscriptions, presence, reconnect logic
- `supabase-functions`: Edge Functions invocation

## Dependency direction

Feature modules depend on `supabase-client` + `supabase-core`.
No feature module should depend on another feature module's internals.

## Runtime flow

1. App creates `SupabaseClient`
2. Feature clients use shared transport
3. Feature clients return `SupabaseResult<T>`
4. App composes results with `map`/`flatMap`/`recover`

## DI strategy

Koin modules provide ready-to-wire defaults:

- `supabaseModule(...)`
- `authModule(...)`
- `databaseModule`
- `storageModule`
- `realtimeModule(...)`
- `functionsModule`
