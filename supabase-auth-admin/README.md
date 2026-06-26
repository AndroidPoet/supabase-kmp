# supabase-auth-admin

Service-role admin APIs for GoTrue: create/update/delete users, list users (with paging), invite by email, generate links, manage MFA factors, OAuth clients, and custom providers.

## ⚠️ SECURITY — server-side only

**This module uses the Supabase service-role key, which bypasses Row Level Security (RLS) and can read or modify any data in your project. Never ship it — or the service-role key — in a client app** (mobile, desktop, or browser). Use it only from a trusted server / backend you control. `createAuthAdminClient(client, serviceRoleKey)` takes the key explicitly so the privileged credential is obvious at the call site; do not reuse your anon key here.

**Coordinate:** `io.github.androidpoet:supabase-auth-admin`

```kotlin
import io.github.androidpoet.supabase.auth.admin.createAuthAdminClient

// `client` should be configured with your service-role key, on a server only.
val admin = createAuthAdminClient(client, serviceRoleKey = System.getenv("SUPABASE_SERVICE_ROLE_KEY"))

admin.listUsers(page = 1, perPage = 50)
    .onSuccess { page -> println("users: ${page.users.size}") }
    .onFailure { error -> println("admin call failed: ${error.message}") }
```

**Targets:** Android, JVM, iOS, macOS, tvOS, watchOS, Linux, Windows, WasmJs (the artifact is multiplatform, but it should only run in a trusted server context).

**Note:** Convenience helpers `listUsersOrThrow(...)` and `usersPaginator(...)` are provided for iterating large user lists.
