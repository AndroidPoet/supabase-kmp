# supabase-storage

Supabase Storage: bucket CRUD, file upload/download (bytes or as base data URLs), resumable (TUS) uploads, and signed / public URL generation. All operations return a `SupabaseResult`.

**Coordinate:** `io.github.androidpoet:supabase-storage`

```kotlin
import io.github.androidpoet.supabase.storage.createStorageClient

val storage = createStorageClient(client) // client from supabase-client

storage.upload(
    bucket = "avatars",
    path = "user-123/photo.png",
    data = pngBytes,             // ByteArray
    contentType = "image/png",
    upsert = true,
).onSuccess { key -> println("uploaded: $key") }
 .onFailure { error -> println("upload failed: ${error.message}") }
```

Read back with `download(bucket, path)` / `downloadBytes(...)`, or use `createBucket(...)` and the signed/public URL helpers.

**Targets:** Android, JVM, iOS, macOS, tvOS, watchOS, Linux, Windows, WasmJs.

**Note:** Uploads take raw `ByteArray` data — read files yourself with your platform's file APIs; the SDK does not bundle filesystem access.
