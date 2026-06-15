package io.github.androidpoet.supabase.storage

import io.github.androidpoet.supabase.core.result.SupabaseResult

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

public suspend fun StorageClient.remove(
    bucket: String,
    vararg paths: String,
): SupabaseResult<Unit> =
    remove(bucket = bucket, paths = paths.toList())

public suspend fun StorageClient.removeWithResult(
    bucket: String,
    vararg paths: String,
): SupabaseResult<List<io.github.androidpoet.supabase.storage.models.FileObject>> =
    removeWithResult(bucket = bucket, paths = paths.toList())

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

public fun StorageClient.getPublicUrls(
    bucket: String,
    vararg paths: String,
): List<String> = getPublicUrls(bucket = bucket, paths = paths.toList())

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

public fun StorageClient.getPublicUrlsByPath(
    bucket: String,
    vararg paths: String,
): Map<String, String> = getPublicUrlsByPath(bucket = bucket, paths = paths.toList())

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

public fun StorageClient.getAuthenticatedUrls(
    bucket: String,
    vararg paths: String,
): List<String> = getAuthenticatedUrls(bucket = bucket, paths = paths.toList())

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

public fun StorageClient.getAuthenticatedUrlsByPath(
    bucket: String,
    vararg paths: String,
): Map<String, String> = getAuthenticatedUrlsByPath(bucket = bucket, paths = paths.toList())

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

public fun StorageClient.getPublicRenderUrls(
    bucket: String,
    paths: List<String>,
    transform: ImageTransformOptions? = null,
): List<String> =
    paths.map { path ->
        getPublicRenderUrl(bucket = bucket, path = path, transform = transform)
    }

public fun StorageClient.getPublicRenderUrls(
    bucket: String,
    transform: ImageTransformOptions? = null,
    vararg paths: String,
): List<String> = getPublicRenderUrls(bucket = bucket, paths = paths.toList(), transform = transform)

public fun StorageClient.getPublicRenderUrlsByPath(
    bucket: String,
    paths: List<String>,
    transform: ImageTransformOptions? = null,
): Map<String, String> =
    paths.associateWith { path ->
        getPublicRenderUrl(bucket = bucket, path = path, transform = transform)
    }

public fun StorageClient.getPublicRenderUrlsByPath(
    bucket: String,
    transform: ImageTransformOptions? = null,
    vararg paths: String,
): Map<String, String> = getPublicRenderUrlsByPath(bucket = bucket, paths = paths.toList(), transform = transform)

public fun StorageClient.getAuthenticatedRenderUrls(
    bucket: String,
    paths: List<String>,
    transform: ImageTransformOptions? = null,
): List<String> =
    paths.map { path ->
        getAuthenticatedRenderUrl(bucket = bucket, path = path, transform = transform)
    }

public fun StorageClient.getAuthenticatedRenderUrls(
    bucket: String,
    transform: ImageTransformOptions? = null,
    vararg paths: String,
): List<String> = getAuthenticatedRenderUrls(bucket = bucket, paths = paths.toList(), transform = transform)

public fun StorageClient.getAuthenticatedRenderUrlsByPath(
    bucket: String,
    paths: List<String>,
    transform: ImageTransformOptions? = null,
): Map<String, String> =
    paths.associateWith { path ->
        getAuthenticatedRenderUrl(bucket = bucket, path = path, transform = transform)
    }

public fun StorageClient.getAuthenticatedRenderUrlsByPath(
    bucket: String,
    transform: ImageTransformOptions? = null,
    vararg paths: String,
): Map<String, String> = getAuthenticatedRenderUrlsByPath(bucket = bucket, paths = paths.toList(), transform = transform)
