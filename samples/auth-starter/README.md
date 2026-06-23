# Auth Starter

The login screen every app needs first — drop in your keys and run.

## What it shows

- Email sign up / sign in
- Anonymous sign in
- Session restore on launch
- "Who am I" (current user), session refresh, JWT claim parsing
- Sign out

Every call returns a `SupabaseResult`, so the UI branches on `Success`/`Failure`
instead of catching exceptions. Session persistence is pluggable (BYO storage) —
this sample uses the default in-memory store; a real app injects its own
(DataStore / Keychain / etc.) when creating the session manager.

## Configure

In the local Supabase stack, or your hosted project, enable email + anonymous
sign-in, then set these in `~/.gradle/gradle.properties` (or a project
`gradle.properties`):

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key
```

(On the Android emulator against a local stack, use `http://10.0.2.2:<port>`.)

## Run

```bash
./gradlew :samples:auth-starter:installDebug
```

No config set? The app shows a `MissingConfigScreen` instead of crashing.
