# Error Handling

All public client calls return `SupabaseResult<T>`.

## Why this model

- Keeps flow explicit in async code
- Avoids hidden exception paths
- Makes retry/fallback logic composable

## Main types

- `SupabaseResult.Success<T>(value)`
- `SupabaseResult.Failure(error)`
- `SupabaseError` with category + code

## Recommended usage

```kotlin
val result = database.select(table = "todos")

result
  .map { response -> response.data }
  .onFailure { error -> logger.error(error.message) }
```

Use category-aware handlers for user-safe messaging and retry behavior.
