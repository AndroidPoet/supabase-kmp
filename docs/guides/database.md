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

## Read with filter DSL

```kotlin
@Serializable
data class Todo(
    val id: String,
    val title: String,
    val done: Boolean,
)

val todos = database.selectTyped<Todo>(table = "todos") {
    eq("done", "false")
    order("created_at", ascending = false)
    limit(20)
}
```

## Insert and update

```kotlin
database.insertTyped(
    table = "todos",
    value = Todo(id = "1", title = "Ship docs", done = false),
)

database.update(
    table = "todos",
    body = """{"done":true}""",
) {
    eq("id", "1")
}
```

## RPC example

```kotlin
val stats = database.rpc(
    function = "get_dashboard_stats",
    params = """{"user_id":"123"}""",
)
```
