# Storage Guide

## Buckets

- `listBuckets`
- `getBucket`
- `createBucket`
- `emptyBucket`
- `deleteBucket`

## Objects

- `upload`
- `download`
- `list`
- `move`
- `remove`
- `createSignedUrl`
- `getPublicUrl`

## Upload and list example

```kotlin
val upload = storage.upload(
    bucket = "avatars",
    path = "users/u1/profile.png",
    data = imageBytes,
    contentType = "image/png",
    upsert = true,
)

upload.onSuccess { key ->
    println("Uploaded: $key")
}

val files = storage.list(bucket = "avatars", prefix = "users/u1/")
```

## Signed and public URLs

```kotlin
val signed = storage.createSignedUrl(
    bucket = "avatars",
    path = "users/u1/profile.png",
    expiresIn = 3600,
)

val publicUrl = storage.getPublicUrl(
    bucket = "public-assets",
    path = "logos/logo.png",
)
```

## Production notes

- Prefer signed URLs for private assets
- Keep content types explicit
- Use `upsert=true` only when overwrite behavior is intended
