# Module Overview

## Core modules

- `supabase-core`: primitives shared by all modules
- `supabase-client`: network transport and base APIs

## Feature modules

- `supabase-auth`
- `supabase-database`
- `supabase-storage`
- `supabase-realtime`
- `supabase-functions`

## Selection guidance

Minimum dependency for simple REST-style calls:

- `supabase-client` (implicitly includes core primitives)

Add modules incrementally by feature needs.
