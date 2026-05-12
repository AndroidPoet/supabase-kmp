# Realtime Guide

`RealtimeClient` manages websocket lifecycle and channels.

## Workflow

1. `connect()`
2. create channel via `channel("topic")`
3. register callbacks (`onPostgresChange`, `onPresence`, `onBroadcast`)
4. `subscribe()`
5. `unsubscribe()` and `disconnect()` on cleanup

## Connection model

Observe `connectionState`:

- `Disconnected`
- `Connecting`
- `Connected`
- `Reconnecting`
- `Failed`

Reconnect uses backoff and re-joins active channels.
