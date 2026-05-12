# Architecture

## Modules

- `supabase-core`: errors, result type, filters DSL, shared primitives
- `supabase-client`: transport/client bootstrap and config
- `supabase-auth`: GoTrue auth + session management
- `supabase-database`: PostgREST operations
- `supabase-storage`: Storage v1 operations
- `supabase-realtime`: websocket subscriptions and events
- `supabase-functions`: Edge Functions invocation

## Design principles

- Small focused modules
- No exception-first API surface
- Coroutine-first and multiplatform-safe abstractions
