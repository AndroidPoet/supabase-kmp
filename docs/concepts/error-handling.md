# Error Handling

All feature clients return `SupabaseResult<T>`.

## Pattern

- `Success(value)` when the request succeeds
- `Failure(error)` when API/transport/serialization fails

## Recommended usage

- Use `map`, `flatMap`, and `recover` for composition
- Use `onFailureCategory` for category-specific handling
- Convert to throwable only at app boundaries
