# Tutorial: Build a Todo App

## Goal

Create a multiplatform todo flow with auth, database, and realtime updates.

## Steps

1. Sign in user and save session
2. Load initial todo list from `database.selectTyped<Todo>("todos")`
3. Subscribe to `INSERT` and `UPDATE` events on `todos`
4. Upload attachment files (optional) to Storage
5. Invoke Edge Function for batch actions

## Data model

```kotlin
@Serializable
data class Todo(
    val id: String,
    val title: String,
    val done: Boolean,
)
```

## Success criteria

- New todos appear across clients in realtime
- Session survives app restart
- Error states are surfaced from `SupabaseResult.Failure`
