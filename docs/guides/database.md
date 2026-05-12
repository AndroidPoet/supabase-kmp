# Database Guide

`DatabaseClient` wraps PostgREST.

## Core operations

- `select`
- `insert`
- `update`
- `delete`
- `rpc`

## Typed helpers

Use extension helpers for serialization-safe usage:

- `selectTyped<T>`
- `insertTyped<T>`
- `updateTyped<T>`
- `rpcTyped<T>`

## Filters DSL

```kotlin
val users = database.selectTyped<User>("users") {
    eq("active", "true")
    order("created_at", ascending = false)
    limit(50)
}
```
