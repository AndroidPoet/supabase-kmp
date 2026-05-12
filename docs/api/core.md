# Core API

## Types

- `SupabaseResult<T>` and helper extensions
- `SupabaseError` and `SupabaseErrorCategory`
- `FilterBuilder` and `filters { ... }`
- Typed IDs (`UserId`, `SessionId`, `BucketId`, `ChannelId`, `ProjectUrl`)

## DSL notes

The filter DSL maps to PostgREST query operators and ordering options.
