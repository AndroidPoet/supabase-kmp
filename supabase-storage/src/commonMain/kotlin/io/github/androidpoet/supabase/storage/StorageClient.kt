package io.github.androidpoet.supabase.storage
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.storage.models.Bucket
import io.github.androidpoet.supabase.storage.models.FileObject
public interface StorageClient {
    public suspend fun listBuckets(
        limit: Int? = null,
        offset: Int? = null,
        sortColumn: String? = null,
        sortOrder: SortOrder? = null,
        search: String? = null,
    ): SupabaseResult<List<Bucket>>
    public suspend fun getBucket(id: String): SupabaseResult<Bucket>
    public suspend fun createBucket(
        id: String,
        name: String,
        public: Boolean = false,
        fileSizeLimit: Long? = null,
        allowedMimeTypes: List<String>? = null,
    ): SupabaseResult<Bucket>
    public suspend fun updateBucket(
        id: String,
        name: String? = null,
        public: Boolean? = null,
        fileSizeLimit: Long? = null,
        allowedMimeTypes: List<String>? = null,
    ): SupabaseResult<Bucket>
    public suspend fun deleteBucket(id: String): SupabaseResult<Unit>
    public suspend fun emptyBucket(id: String): SupabaseResult<Unit>
    public suspend fun upload(
        bucket: String,
        path: String,
        data: ByteArray,
        contentType: String = "application/octet-stream",
        upsert: Boolean = false,
        cacheControl: Int? = null,
    ): SupabaseResult<String>
    public suspend fun update(
        bucket: String,
        path: String,
        data: ByteArray,
        contentType: String = "application/octet-stream",
        upsert: Boolean = false,
        cacheControl: Int? = null,
    ): SupabaseResult<String>
    public suspend fun download(bucket: String, path: String): SupabaseResult<String>
    public suspend fun downloadPublic(bucket: String, path: String): SupabaseResult<String>
    public suspend fun download(
        bucket: String,
        path: String,
        download: Boolean = true,
        fileName: String? = null,
    ): SupabaseResult<String>
    public suspend fun downloadPublic(
        bucket: String,
        path: String,
        download: Boolean = true,
        fileName: String? = null,
    ): SupabaseResult<String>
    public suspend fun info(bucket: String, path: String): SupabaseResult<FileObject>
    public suspend fun infoPublic(bucket: String, path: String): SupabaseResult<FileObject>
    public suspend fun exists(bucket: String, path: String): SupabaseResult<Boolean>
    public suspend fun existsPublic(bucket: String, path: String): SupabaseResult<Boolean>
    public suspend fun list(
        bucket: String,
        prefix: String = "",
        limit: Int = 100,
        offset: Int = 0,
        sortBy: String? = null,
        sortOrder: SortOrder = SortOrder.ASC,
        search: String? = null,
    ): SupabaseResult<List<FileObject>>
    public suspend fun move(
        bucket: String,
        fromPath: String,
        toPath: String,
        destinationBucket: String? = null,
    ): SupabaseResult<Unit>
    public suspend fun deleteObject(
        bucket: String,
        path: String,
    ): SupabaseResult<Unit>
    public suspend fun copy(
        bucket: String,
        fromPath: String,
        toPath: String,
        destinationBucket: String? = null,
        copyMetadata: Boolean? = null,
    ): SupabaseResult<Unit>
    public suspend fun remove(bucket: String, paths: List<String>): SupabaseResult<Unit>
    public suspend fun removeWithResult(bucket: String, paths: List<String>): SupabaseResult<List<FileObject>>
    public suspend fun createSignedUrl(
        bucket: String,
        path: String,
        expiresIn: Long,
        download: Boolean = false,
        fileName: String? = null,
        transform: ImageTransformOptions? = null,
    ): SupabaseResult<String>
    public suspend fun createSignedUrls(
        bucket: String,
        paths: List<String>,
        expiresIn: Long,
        download: Boolean = false,
        fileName: String? = null,
    ): SupabaseResult<List<String>>
    public suspend fun createUploadSignedUrl(
        bucket: String,
        path: String,
        upsert: Boolean = false,
    ): SupabaseResult<String>
    public suspend fun createUploadSignedUrlWithPath(
        bucket: String,
        path: String,
        upsert: Boolean = false,
    ): SupabaseResult<UploadSignedUrl>
    public fun getSignedDownloadUrl(
        bucket: String,
        path: String,
        token: String,
        download: Boolean = false,
        fileName: String? = null,
    ): String
    public suspend fun uploadToSignedUrl(
        bucket: String,
        path: String,
        token: String,
        data: ByteArray,
        contentType: String = "application/octet-stream",
        upsert: Boolean = false,
        cacheControl: Int? = null,
    ): SupabaseResult<String>
    public fun getPublicUrl(
        bucket: String,
        path: String,
        transform: ImageTransformOptions? = null,
    ): String
    public fun getAuthenticatedUrl(
        bucket: String,
        path: String,
        transform: ImageTransformOptions? = null,
    ): String
    public fun getPublicUrl(
        bucket: String,
        path: String,
        download: Boolean = false,
        fileName: String? = null,
        transform: ImageTransformOptions? = null,
    ): String
    public fun getAuthenticatedUrl(
        bucket: String,
        path: String,
        download: Boolean = false,
        fileName: String? = null,
        transform: ImageTransformOptions? = null,
    ): String
    public fun getPublicRenderUrl(
        bucket: String,
        path: String,
        transform: ImageTransformOptions? = null,
    ): String
    public fun getAuthenticatedRenderUrl(
        bucket: String,
        path: String,
        transform: ImageTransformOptions? = null,
    ): String
}

public data class UploadSignedUrl(
    public val url: String,
    public val token: String,
)

public data class ImageTransformOptions(
    public val width: Int? = null,
    public val height: Int? = null,
    public val resize: ResizeMode? = null,
    public val format: String? = null,
    public val quality: Int? = null,
)

public enum class ResizeMode {
    COVER,
    CONTAIN,
    FILL,
}

public enum class SortOrder {
    ASC,
    DESC,
}
