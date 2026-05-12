# Configuration Reference

## SupabaseConfig

Key fields:

- `logging`: enable/disable Ktor logging
- `logLevel`: Ktor log level
- `defaultHeaders`: headers applied to all requests

## SessionConfig

Key fields control:

- auto-refresh enablement
- early refresh window
- refresh scheduling behavior

## RealtimeConfig

Controls reconnect/backoff timings and heartbeat behavior.
