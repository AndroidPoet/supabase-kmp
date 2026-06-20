# supabase-realtime

Realtime over the Phoenix WebSocket protocol: channel subscriptions, Postgres change streams, broadcast (including raw `ByteArray` binary broadcast), and presence — with automatic reconnection, exponential backoff, and offline send buffering.

**Coordinate:** `io.github.androidpoet:supabase-realtime`

```kotlin
import io.github.androidpoet.supabase.realtime.createRealtimeClient

val realtime = createRealtimeClient(supabaseClient = client) // client from supabase-client

realtime.connect()
val channel = realtime.channel("room:lobby")
channel.subscribe()
// broadcast / presence / postgres-changes on `channel`
```

`createRealtimeClient` accepts an optional `RealtimeConfig` and an `engineFactory` override; both default sensibly per platform.

**Targets:** Android, JVM, iOS, macOS, tvOS, watchOS, Linux, Windows, WasmJs.

**Note:** The client keeps a single multiplexed WebSocket; reconnection and re-subscription are handled for you, so keep the `RealtimeClient` long-lived rather than recreating it per screen.
