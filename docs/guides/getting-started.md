# Getting Started

## Install

Add modules in `commonMain`:

```kotlin
implementation(libs.supabase.client)
implementation(libs.supabase.auth)
implementation(libs.supabase.database)
implementation(libs.supabase.storage)
implementation(libs.supabase.realtime)
implementation(libs.supabase.functions)
```

## Create a client

```kotlin
val client = Supabase.create(
    projectUrl = "https://your-project.supabase.co",
    apiKey = "your-anon-key",
)
```

## Dependency Injection

Use `supabaseModule(...)` plus feature modules (`authModule`, `databaseModule`, etc.) when using Koin.
