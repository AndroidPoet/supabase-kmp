# API Overview

## Primary entry points

- `Supabase.create(...)`
- `createAuthClient(...)`
- `createDatabaseClient(...)`
- `createStorageClient(...)`
- `createRealtimeClient(...)`
- `createFunctionsClient(...)`

## Create client example

```kotlin
val client = Supabase.create(
    projectUrl = "https://your-project.supabase.co",
    apiKey = "your-anon-key",
)
```

## Feature clients

- `AuthClient`
- `DatabaseClient`
- `StorageClient`
- `RealtimeClient`
- `FunctionsClient`

## Manual wiring example

```kotlin
val auth = createAuthClient(client)
val database = createDatabaseClient(client)
val storage = createStorageClient(client)
val realtime = createRealtimeClient(client)
val functions = createFunctionsClient(client)
```

## Common API contract

- All async results use `SupabaseResult<T>`
- Public models are serialization-ready
- Feature modules remain independent and composable
