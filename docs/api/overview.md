# API Overview

## Primary entry points

- `Supabase.create(...)`
- `supabaseModule(...)`

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

## Koin wiring example

```kotlin
startKoin {
    modules(
        supabaseModule(projectUrl = "https://your-project.supabase.co", apiKey = "your-anon-key"),
        authModule(),
        databaseModule,
        storageModule,
        realtimeModule(),
        functionsModule,
    )
}
```

## Common API contract

- All async results use `SupabaseResult<T>`
- Public models are serialization-ready
- Feature modules remain independent and composable
