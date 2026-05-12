# API Overview

## Primary entry points

- `Supabase.create(...)`
- `supabaseModule(...)`

## Feature clients

- `AuthClient`
- `DatabaseClient`
- `StorageClient`
- `RealtimeClient`
- `FunctionsClient`

## Common API contract

- All async results use `SupabaseResult<T>`
- Public models are serialization-ready
