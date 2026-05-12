# Realtime Guide

`RealtimeClient` manages websocket lifecycle and channels.

## Workflow

1. `connect()`
2. create channel via `channel("topic")`
3. register callbacks (`onPostgresChange`, `onPresence`, `onBroadcast`)
4. `subscribe()`
5. `unsubscribe()` and `disconnect()` on cleanup

## Basic subscription example

```kotlin
realtime.connect()

val sub = realtime.channel("todos")
    .onPostgresChange(table = "todos", event = PostgresChangeEvent.INSERT) { record ->
        println("Inserted: $record")
    }
    .onBroadcast("cursor") { payload ->
        println("Broadcast payload: $payload")
    }
    .subscribe()
```

## Presence + broadcast example

```kotlin
sub.track(buildJsonObject {
    put("userId", "u1")
    put("name", "Ranbir")
})

sub.broadcast(
    event = "cursor",
    payload = buildJsonObject {
        put("x", 120)
        put("y", 340)
    },
)
```

## Connection model

Observe `connectionState`:

- `Disconnected`
- `Connecting`
- `Connected`
- `Reconnecting`
- `Failed`

Reconnect uses backoff and re-joins active channels.
