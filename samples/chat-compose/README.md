# Chat Compose Sample

Jetchat-style realtime chat sample backed by Supabase.

## Features

- Compose chat UI with bubbles and message composer
- Realtime incoming messages via Supabase Realtime
- Infinite scroll pagination for message history
- Send messages to Postgres-backed `chat_messages` table
- Room switching and user-id simulation

## Configure

Set these in `~/.gradle/gradle.properties` (or project `gradle.properties`):

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key
```

## Run

```bash
./gradlew :samples:chat-compose:installDebug
```

## Backend expectation

The sample expects a table `chat_messages` with columns:

- `id` (uuid)
- `room_id` (text/uuid)
- `sender_id` (text/uuid)
- `body` (text)
- `created_at` (timestamptz)

Use the SQL and RLS blocks from docs page:
- `docs/realtime-chat-sample.md`
