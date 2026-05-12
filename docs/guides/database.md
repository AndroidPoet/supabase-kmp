# Database Guide

`DatabaseClient` wraps PostgREST endpoints.

## Common operations

- `select(...)` and `selectTyped<T>(...)`
- `insert(...)` and `insertTyped<T>(...)`
- `update(...)` and `updateTyped<T>(...)`
- `delete(...)`
- `rpc(...)` and `rpcTyped<T>(...)`

## Filters DSL

Use `filters { ... }` or the lambda overloads to build PostgREST params.
