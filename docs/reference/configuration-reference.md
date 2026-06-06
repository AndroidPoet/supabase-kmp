# Configuration Reference

## SupabaseConfig

Key fields:

- `logging`: enable/disable Ktor logging
- `logLevel`: Ktor log level
- `headers`: headers applied to all requests

## SessionConfig

Key fields control:

- auto-refresh enablement
- early refresh window
- refresh scheduling behavior
- `storage`: pluggable `SessionStorage` for platform-specific persistence

`SessionStorage` is the Kotlin equivalent of JavaScript/React Native custom storage options. Supply an implementation backed by Android DataStore, iOS Keychain, encrypted storage, or another platform store:

```kotlin
val sessionManager = createSessionManager(
    authClient = auth,
    supabaseClient = client,
    config = SessionConfig(storage = mySessionStorage),
)
```

JavaScript `AbortSignal` maps to structured coroutine cancellation in Kotlin. Cancel the coroutine running a SDK call and the transport propagates `CancellationException` instead of converting it into `SupabaseError`:

```kotlin
val job = scope.launch {
    database.select("messages")
}

job.cancel()
```

## RealtimeConfig

Controls reconnect/backoff timings and heartbeat behavior.
