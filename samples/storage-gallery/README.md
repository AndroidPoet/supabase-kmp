# Storage Gallery

Pick a photo → upload to Supabase Storage → list, preview, share, delete.
Thumbnails are decoded from the downloaded bytes, so there are **no extra image
dependencies** — it's pure supabase-kmp storage.

## What it shows

- `upload` an image from the system photo picker
- `list` objects in a bucket
- `downloadBytes` + render a preview
- `createSignedUrl` (private) and `getPublicUrl` (public) for sharing
- `remove` an object

## Configure

1. Create a bucket called `gallery` and apply `supabase/policies.sql` (it
   creates the bucket and grants the anon role demo access — see the warning in
   that file; tighten before production).
2. Set keys in `~/.gradle/gradle.properties` (or a project `gradle.properties`):

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key
SUPABASE_STORAGE_BUCKET=gallery
```

(On the Android emulator against a local stack, use `http://10.0.2.2:<port>`.)

## Run

```bash
./gradlew :samples:storage-gallery:installDebug
```

No config set? The app shows a `MissingConfigScreen` instead of crashing.
