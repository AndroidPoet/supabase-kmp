package io.github.androidpoet.supabase.storage

/**
 * Centralized Supabase Storage endpoint paths.
 *
 * The API version and the base segments are factored out so the version lives in
 * exactly one place and the route prefixes are composed from it, rather than
 * repeating `/storage/v1/...` literals throughout [StorageClientImpl].
 *
 * Constants are full path prefixes; dynamic segments (bucket, object reference,
 * RPC name, …) are appended at the call site, e.g.
 * `"${StoragePaths.OBJECT_AUTHENTICATED}/${objectRef(bucket, path)}"`.
 */
internal object StoragePaths {
    const val API_VERSION: String = "v1"
    const val BASE: String = "/storage/" + API_VERSION

    // Buckets
    const val BUCKET: String = BASE + "/bucket"

    // Objects
    const val OBJECT: String = BASE + "/object"
    const val OBJECT_AUTHENTICATED: String = OBJECT + "/authenticated"
    const val OBJECT_PUBLIC: String = OBJECT + "/public"
    const val OBJECT_INFO: String = OBJECT + "/info"
    const val OBJECT_INFO_PUBLIC: String = OBJECT + "/info/public"
    const val OBJECT_SIGN: String = OBJECT + "/sign"
    const val OBJECT_UPLOAD_SIGN: String = OBJECT + "/upload/sign"
    const val OBJECT_LIST: String = OBJECT + "/list"
    const val OBJECT_LIST_V2: String = OBJECT + "/list-v2"
    const val OBJECT_REMOVE: String = OBJECT + "/remove"
    const val OBJECT_MOVE: String = OBJECT + "/move"
    const val OBJECT_COPY: String = OBJECT + "/copy"

    // Image render/transform
    const val RENDER_IMAGE: String = BASE + "/render/image"
    const val RENDER_IMAGE_AUTHENTICATED: String = RENDER_IMAGE + "/authenticated"
    const val RENDER_IMAGE_PUBLIC: String = RENDER_IMAGE + "/public"

    // Resumable (TUS) uploads
    const val RESUMABLE_UPLOAD: String = BASE + "/upload/resumable"

    // Analytics (Iceberg) and S3 Vectors
    const val ICEBERG: String = BASE + "/iceberg"
    const val VECTOR: String = BASE + "/vector"
}
