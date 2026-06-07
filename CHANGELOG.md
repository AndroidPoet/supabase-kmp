# Changelog

## 0.3.2

- Added typed Iceberg REST catalog models and typed Analytics Catalog methods while keeping raw JSON methods available.
- Added Realtime debug state, debug events, and manual heartbeat sending for transport diagnostics.
- Verified Storage and Realtime common metadata, JVM, Wasm test compilation, and Android unit tests.

## 0.3.1

- Added Supabase JavaScript SDK parity coverage tracking.
- Added `supabase-auth-admin` for service-role auth admin APIs.
- Expanded Auth coverage with OAuth, Web3, passkey, MFA, lifecycle, and auth-state helpers.
- Expanded Database/PostgREST coverage with advanced filters, request options, custom headers, retry, and explain support.
- Expanded Storage coverage with vector buckets, analytics buckets, and Iceberg REST catalog helpers.
- Expanded Realtime coverage with connection-state helpers and raw channel send support.
- Added client custom Ktor engine injection, global headers verification, and cancellation parity docs.
- Added local Supabase config, e2e test scaffolding, chat sample diagnostics, and desktop demo sample.
