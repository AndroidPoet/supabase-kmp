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

## Production notes

- Prefer signed URLs for private assets
- Keep content types explicit
- Use `upsert=true` only when overwrite behavior is intended
