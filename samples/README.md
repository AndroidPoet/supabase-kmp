# Samples

Plug-and-play starters for supabase-kmp. Each is a focused, idiomatic app you can
copy as the seed of a real project — not a kitchen-sink demo. The contract is the
same for all of them:

1. Paste `SUPABASE_URL` + `SUPABASE_ANON_KEY` into `~/.gradle/gradle.properties`
   (or a project `gradle.properties`).
2. Apply the bundled SQL (if the template ships any).
3. Run one command.
4. No keys set? The app shows a `MissingConfigScreen` instead of crashing.

## Templates

| Template | Focus | SDK surface | Run |
|---|---|---|---|
| [auth-starter](auth-starter) | The login screen every app needs | `supabase-auth` | `:samples:auth-starter:installDebug` |
| [todo-crud](todo-crud) | Typed create/read/update/delete | `supabase-database` | `:samples:todo-crud:installDebug` |
| [storage-gallery](storage-gallery) | Pick → upload → list → share → delete | `supabase-storage` | `:samples:storage-gallery:installDebug` |
| [chat-compose](chat-compose) | Realtime chat (kitchen-sink demo) | auth + db + realtime + storage + functions | `:samples:chat-compose:installDebug` |
| [passkey-web](passkey-web) | Passkey ceremony in the browser | `supabase-auth-passkey` (Wasm) | `:samples:passkey-web:wasmJsBrowserRun` |

The `auth-starter`, `todo-crud`, and `storage-gallery` templates are deliberately
small and single-purpose. `chat-compose` is the broad demo that exercises every
module at once.

> These are Android (Compose) + Wasm samples today. A Compose Multiplatform
> shared-UI variant (Android + iOS + Desktop from one codebase) is a planned
> upgrade — all SDK modules already publish those targets.
