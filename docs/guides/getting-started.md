# Getting Started

## Prerequisites

- Kotlin Multiplatform project
- Supabase project URL and anon key
- Coroutines + serialization in common source sets

## Install dependencies

```kotlin
commonMain.dependencies {
    implementation(libs.supabase.client)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.database)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.functions)
}
```

## Create a client

```kotlin
val client = Supabase.create(
    projectUrl = "https://your-project.supabase.co",
    apiKey = "your-anon-key",
)
```

## Add feature clients with Koin

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

## Next steps

- Implement sign-in with [Authentication Guide](authentication.md)
- Read data with [Database Guide](database.md)
- Upload files with [Storage Guide](storage.md)
