package io.github.androidpoet.supabase.storage

import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.serialization.json.JsonObject

/**
 * Appends a cache-busting query param ([name]=[nonce]) to a previously built
 * storage URL (public, authenticated, signed, or render). Useful to force a
 * CDN/browser to re-fetch an object that was overwritten at the same path.
 * Returns the URL unchanged when [nonce] is null/blank. The nonce is appended
 * verbatim, so pass a URL-safe value (e.g. a timestamp or version string).
 */
public fun withCacheNonce(url: String, nonce: String?, name: String = "cacheNonce"): String {
    if (nonce.isNullOrBlank()) return url
    val sep = if ('?' in url) '&' else '?'
    return "$url$sep$name=$nonce"
}

/**
 * Uploads [data] in resumable (TUS) chunks and suspends until it completes.
 * One-shot convenience over [StorageClient.createResumableUpload]; for progress
 * or pause/resume, use that directly and observe [ResumableUpload.progress].
 *
 * When [metadata] is provided, it is Base64-encoded and sent as the `metadata`
 * entry of the TUS `Upload-Metadata` header so the server stores it as the
 * object's user metadata (the same encoding as the non-resumable upload).
 */
public suspend fun StorageClient.uploadResumable(
    bucket: String,
    path: String,
    data: ByteArray,
    contentType: String = "application/octet-stream",
    upsert: Boolean = false,
    cacheControl: Int? = null,
    chunkSize: Int = RESUMABLE_DEFAULT_CHUNK_SIZE,
    metadata: JsonObject? = null,
): SupabaseResult<Unit> =
    createResumableUpload(
        bucket = bucket,
        path = path,
        data = data,
        contentType = contentType,
        upsert = upsert,
        cacheControl = cacheControl,
        chunkSize = chunkSize,
        metadata = metadata,
    ).await()

/**
 * Convenience over [StorageClient.createSignedUrl] that always sets `download = true`, producing a
 * signed URL that serves the object as an attachment ([fileName] suggests the saved name).
 */
public suspend fun StorageClient.createSignedDownloadUrl(
    bucket: String,
    path: String,
    expiresIn: Long,
    fileName: String? = null,
): SupabaseResult<String> =
    createSignedUrl(
        bucket = bucket,
        path = path,
        expiresIn = expiresIn,
        download = true,
        fileName = fileName,
    )

/** [createSignedDownloadUrl] that also bakes in an image [transform] (render endpoint). */
public suspend fun StorageClient.createSignedDownloadUrl(
    bucket: String,
    path: String,
    expiresIn: Long,
    fileName: String? = null,
    transform: ImageTransformOptions? = null,
): SupabaseResult<String> =
    createSignedUrl(
        bucket = bucket,
        path = path,
        expiresIn = expiresIn,
        download = true,
        fileName = fileName,
        transform = transform,
    )

/**
 * Batch [createSignedDownloadUrl]: signs every path in [paths] with `download = true`, returning
 * the attachment URLs in the same order. See [StorageClient.createSignedUrls].
 */
public suspend fun StorageClient.createSignedDownloadUrls(
    bucket: String,
    paths: List<String>,
    expiresIn: Long,
    fileName: String? = null,
): SupabaseResult<List<String>> =
    createSignedUrls(
        bucket = bucket,
        paths = paths,
        expiresIn = expiresIn,
        download = true,
        fileName = fileName,
    )

/** `vararg` overload of [createSignedDownloadUrls] (no attachment file name). */
public suspend fun StorageClient.createSignedDownloadUrls(
    bucket: String,
    expiresIn: Long,
    vararg paths: String,
): SupabaseResult<List<String>> =
    createSignedDownloadUrls(
        bucket = bucket,
        paths = paths.toList(),
        expiresIn = expiresIn,
        fileName = null,
    )

/** `vararg` overload of [createSignedDownloadUrls] with a suggested attachment [fileName]. */
public suspend fun StorageClient.createSignedDownloadUrls(
    bucket: String,
    expiresIn: Long,
    fileName: String?,
    vararg paths: String,
): SupabaseResult<List<String>> =
    createSignedDownloadUrls(
        bucket = bucket,
        paths = paths.toList(),
        expiresIn = expiresIn,
        fileName = fileName,
    )

/**
 * Convenience over [StorageClient.createSignedUrl] for a transformed image: signs an inline
 * (non-download) render URL applying [transform].
 */
public suspend fun StorageClient.createSignedRenderUrl(
    bucket: String,
    path: String,
    expiresIn: Long,
    transform: ImageTransformOptions,
): SupabaseResult<String> =
    createSignedUrl(
        bucket = bucket,
        path = path,
        expiresIn = expiresIn,
        download = false,
        fileName = null,
        transform = transform,
    )

/**
 * Batch [createSignedRenderUrl]: signs a render URL for each path in [paths] applying [transform].
 * Signs sequentially and short-circuits to [SupabaseResult.Failure] on the first failing path.
 */
public suspend fun StorageClient.createSignedRenderUrls(
    bucket: String,
    paths: List<String>,
    expiresIn: Long,
    transform: ImageTransformOptions,
): SupabaseResult<List<String>> {
    val urls = ArrayList<String>(paths.size)
    for (path in paths) {
        when (
            val signed =
                createSignedUrl(
                    bucket = bucket,
                    path = path,
                    expiresIn = expiresIn,
                    download = false,
                    transform = transform,
                )
        ) {
            is SupabaseResult.Failure -> return signed
            is SupabaseResult.Success -> urls += signed.value
        }
    }
    return SupabaseResult.Success(urls)
}

/** `vararg` overload of [createSignedRenderUrls]. */
public suspend fun StorageClient.createSignedRenderUrls(
    bucket: String,
    expiresIn: Long,
    transform: ImageTransformOptions,
    vararg paths: String,
): SupabaseResult<List<String>> =
    createSignedRenderUrls(
        bucket = bucket,
        paths = paths.toList(),
        expiresIn = expiresIn,
        transform = transform,
    )

/**
 * Like [createSignedDownloadUrls] but returns a path-to-URL [Map] instead of a positional list,
 * pairing each input path with its signed download URL.
 */
public suspend fun StorageClient.createSignedDownloadUrlsByPath(
    bucket: String,
    paths: List<String>,
    expiresIn: Long,
    fileName: String? = null,
): SupabaseResult<Map<String, String>> =
    when (
        val result =
            createSignedDownloadUrls(
                bucket = bucket,
                paths = paths,
                expiresIn = expiresIn,
                fileName = fileName,
            )
    ) {
        is SupabaseResult.Failure -> result
        is SupabaseResult.Success -> SupabaseResult.Success(paths.zip(result.value).toMap())
    }

/** `vararg` overload of [createSignedDownloadUrlsByPath] (no attachment file name). */
public suspend fun StorageClient.createSignedDownloadUrlsByPath(
    bucket: String,
    expiresIn: Long,
    vararg paths: String,
): SupabaseResult<Map<String, String>> =
    createSignedDownloadUrlsByPath(
        bucket = bucket,
        paths = paths.toList(),
        expiresIn = expiresIn,
        fileName = null,
    )

/** `vararg` overload of [createSignedDownloadUrlsByPath] with a suggested attachment [fileName]. */
public suspend fun StorageClient.createSignedDownloadUrlsByPath(
    bucket: String,
    expiresIn: Long,
    fileName: String?,
    vararg paths: String,
): SupabaseResult<Map<String, String>> =
    createSignedDownloadUrlsByPath(
        bucket = bucket,
        paths = paths.toList(),
        expiresIn = expiresIn,
        fileName = fileName,
    )

/**
 * Like [createSignedRenderUrls] but returns a path-to-URL [Map], pairing each input path with its
 * signed render URL.
 */
public suspend fun StorageClient.createSignedRenderUrlsByPath(
    bucket: String,
    paths: List<String>,
    expiresIn: Long,
    transform: ImageTransformOptions,
): SupabaseResult<Map<String, String>> =
    when (
        val result =
            createSignedRenderUrls(
                bucket = bucket,
                paths = paths,
                expiresIn = expiresIn,
                transform = transform,
            )
    ) {
        is SupabaseResult.Failure -> result
        is SupabaseResult.Success -> SupabaseResult.Success(paths.zip(result.value).toMap())
    }

/** `vararg` overload of [createSignedRenderUrlsByPath]. */
public suspend fun StorageClient.createSignedRenderUrlsByPath(
    bucket: String,
    expiresIn: Long,
    transform: ImageTransformOptions,
    vararg paths: String,
): SupabaseResult<Map<String, String>> =
    createSignedRenderUrlsByPath(
        bucket = bucket,
        paths = paths.toList(),
        expiresIn = expiresIn,
        transform = transform,
    )

/** `vararg` overload of [StorageClient.deleteObjects], returning the deleted objects. */
public suspend fun StorageClient.deleteObjects(
    bucket: String,
    vararg paths: String,
): SupabaseResult<List<io.github.androidpoet.supabase.storage.models.FileObject>> =
    deleteObjects(bucket = bucket, paths = paths.toList())

/**
 * Builds public URLs for many [paths] at once via [StorageClient.getPublicUrl], returning them in
 * order. Purely local; no network call. See [StorageClient.getPublicUrl] for the parameters.
 */
public fun StorageClient.getPublicUrls(
    bucket: String,
    paths: List<String>,
    download: Boolean = false,
    fileName: String? = null,
    transform: ImageTransformOptions? = null,
): List<String> =
    paths.map { path ->
        getPublicUrl(
            bucket = bucket,
            path = path,
            download = download,
            fileName = fileName,
            transform = transform,
        )
    }

/** `vararg` overload of [getPublicUrls] with default download/transform options. */
public fun StorageClient.getPublicUrls(
    bucket: String,
    vararg paths: String,
): List<String> = getPublicUrls(bucket = bucket, paths = paths.toList())

/** `vararg` overload of [getPublicUrls] with explicit download/transform options. */
public fun StorageClient.getPublicUrls(
    bucket: String,
    download: Boolean = false,
    fileName: String? = null,
    transform: ImageTransformOptions? = null,
    vararg paths: String,
): List<String> =
    getPublicUrls(
        bucket = bucket,
        paths = paths.toList(),
        download = download,
        fileName = fileName,
        transform = transform,
    )

/**
 * Like [getPublicUrls] but returns a path-to-URL [Map] instead of a positional list. Purely local.
 */
public fun StorageClient.getPublicUrlsByPath(
    bucket: String,
    paths: List<String>,
    download: Boolean = false,
    fileName: String? = null,
    transform: ImageTransformOptions? = null,
): Map<String, String> =
    paths.associateWith { path ->
        getPublicUrl(
            bucket = bucket,
            path = path,
            download = download,
            fileName = fileName,
            transform = transform,
        )
    }

/** `vararg` overload of [getPublicUrlsByPath] with default download/transform options. */
public fun StorageClient.getPublicUrlsByPath(
    bucket: String,
    vararg paths: String,
): Map<String, String> = getPublicUrlsByPath(bucket = bucket, paths = paths.toList())

/** `vararg` overload of [getPublicUrlsByPath] with explicit download/transform options. */
public fun StorageClient.getPublicUrlsByPath(
    bucket: String,
    download: Boolean = false,
    fileName: String? = null,
    transform: ImageTransformOptions? = null,
    vararg paths: String,
): Map<String, String> =
    getPublicUrlsByPath(
        bucket = bucket,
        paths = paths.toList(),
        download = download,
        fileName = fileName,
        transform = transform,
    )

/**
 * Builds authenticated URLs for many [paths] at once via [StorageClient.getAuthenticatedUrl],
 * returning them in order. Purely local; the URLs still require credentials when fetched.
 */
public fun StorageClient.getAuthenticatedUrls(
    bucket: String,
    paths: List<String>,
    download: Boolean = false,
    fileName: String? = null,
    transform: ImageTransformOptions? = null,
): List<String> =
    paths.map { path ->
        getAuthenticatedUrl(
            bucket = bucket,
            path = path,
            download = download,
            fileName = fileName,
            transform = transform,
        )
    }

/** `vararg` overload of [getAuthenticatedUrls] with default download/transform options. */
public fun StorageClient.getAuthenticatedUrls(
    bucket: String,
    vararg paths: String,
): List<String> = getAuthenticatedUrls(bucket = bucket, paths = paths.toList())

/** `vararg` overload of [getAuthenticatedUrls] with explicit download/transform options. */
public fun StorageClient.getAuthenticatedUrls(
    bucket: String,
    download: Boolean = false,
    fileName: String? = null,
    transform: ImageTransformOptions? = null,
    vararg paths: String,
): List<String> =
    getAuthenticatedUrls(
        bucket = bucket,
        paths = paths.toList(),
        download = download,
        fileName = fileName,
        transform = transform,
    )

/** Like [getAuthenticatedUrls] but returns a path-to-URL [Map] instead of a positional list. */
public fun StorageClient.getAuthenticatedUrlsByPath(
    bucket: String,
    paths: List<String>,
    download: Boolean = false,
    fileName: String? = null,
    transform: ImageTransformOptions? = null,
): Map<String, String> =
    paths.associateWith { path ->
        getAuthenticatedUrl(
            bucket = bucket,
            path = path,
            download = download,
            fileName = fileName,
            transform = transform,
        )
    }

/** `vararg` overload of [getAuthenticatedUrlsByPath] with default download/transform options. */
public fun StorageClient.getAuthenticatedUrlsByPath(
    bucket: String,
    vararg paths: String,
): Map<String, String> = getAuthenticatedUrlsByPath(bucket = bucket, paths = paths.toList())

/** `vararg` overload of [getAuthenticatedUrlsByPath] with explicit download/transform options. */
public fun StorageClient.getAuthenticatedUrlsByPath(
    bucket: String,
    download: Boolean = false,
    fileName: String? = null,
    transform: ImageTransformOptions? = null,
    vararg paths: String,
): Map<String, String> =
    getAuthenticatedUrlsByPath(
        bucket = bucket,
        paths = paths.toList(),
        download = download,
        fileName = fileName,
        transform = transform,
    )

/**
 * Builds public render/transform URLs for many [paths] via [StorageClient.getPublicRenderUrl],
 * returning them in order. Purely local; no network call.
 */
public fun StorageClient.getPublicRenderUrls(
    bucket: String,
    paths: List<String>,
    transform: ImageTransformOptions? = null,
): List<String> =
    paths.map { path ->
        getPublicRenderUrl(bucket = bucket, path = path, transform = transform)
    }

/** `vararg` overload of [getPublicRenderUrls]. */
public fun StorageClient.getPublicRenderUrls(
    bucket: String,
    transform: ImageTransformOptions? = null,
    vararg paths: String,
): List<String> = getPublicRenderUrls(bucket = bucket, paths = paths.toList(), transform = transform)

/** Like [getPublicRenderUrls] but returns a path-to-URL [Map] instead of a positional list. */
public fun StorageClient.getPublicRenderUrlsByPath(
    bucket: String,
    paths: List<String>,
    transform: ImageTransformOptions? = null,
): Map<String, String> =
    paths.associateWith { path ->
        getPublicRenderUrl(bucket = bucket, path = path, transform = transform)
    }

/** `vararg` overload of [getPublicRenderUrlsByPath]. */
public fun StorageClient.getPublicRenderUrlsByPath(
    bucket: String,
    transform: ImageTransformOptions? = null,
    vararg paths: String,
): Map<String, String> = getPublicRenderUrlsByPath(bucket = bucket, paths = paths.toList(), transform = transform)

/**
 * Builds authenticated render/transform URLs for many [paths] via
 * [StorageClient.getAuthenticatedRenderUrl], returning them in order. Purely local; the URLs still
 * require credentials when fetched.
 */
public fun StorageClient.getAuthenticatedRenderUrls(
    bucket: String,
    paths: List<String>,
    transform: ImageTransformOptions? = null,
): List<String> =
    paths.map { path ->
        getAuthenticatedRenderUrl(bucket = bucket, path = path, transform = transform)
    }

/** `vararg` overload of [getAuthenticatedRenderUrls]. */
public fun StorageClient.getAuthenticatedRenderUrls(
    bucket: String,
    transform: ImageTransformOptions? = null,
    vararg paths: String,
): List<String> = getAuthenticatedRenderUrls(bucket = bucket, paths = paths.toList(), transform = transform)

/** Like [getAuthenticatedRenderUrls] but returns a path-to-URL [Map] instead of a positional list. */
public fun StorageClient.getAuthenticatedRenderUrlsByPath(
    bucket: String,
    paths: List<String>,
    transform: ImageTransformOptions? = null,
): Map<String, String> =
    paths.associateWith { path ->
        getAuthenticatedRenderUrl(bucket = bucket, path = path, transform = transform)
    }

/** `vararg` overload of [getAuthenticatedRenderUrlsByPath]. */
public fun StorageClient.getAuthenticatedRenderUrlsByPath(
    bucket: String,
    transform: ImageTransformOptions? = null,
    vararg paths: String,
): Map<String, String> = getAuthenticatedRenderUrlsByPath(bucket = bucket, paths = paths.toList(), transform = transform)
