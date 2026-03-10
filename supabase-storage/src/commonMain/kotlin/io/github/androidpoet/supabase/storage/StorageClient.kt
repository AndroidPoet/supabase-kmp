package io.github.androidpoet.supabase.storage

import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.storage.models.Bucket
import io.github.androidpoet.supabase.storage.models.FileObject

/**
 * Client for Supabase Storage operations.
 *
 * Provides typed methods for bucket management, file upload/download,
 * listing, moving, removal, and URL generation against the Storage v1 API.
 */
public interface StorageClient {

    /** Lists all buckets in the project. */
    public suspend fun listBuckets(): SupabaseResult<List<Bucket>>

    /** Retrieves a single bucket by [id]. */
    public suspend fun getBucket(id: String): SupabaseResult<Bucket>

    /** Creates a new storage bucket. */
    public suspend fun createBucket(
        id: String,
        name: String,
        public: Boolean = false,
        fileSizeLimit: Long? = null,
        allowedMimeTypes: List<String>? = null,
    ): SupabaseResult<Bucket>

    /** Deletes the bucket with the given [id]. */
    public suspend fun deleteBucket(id: String): SupabaseResult<Unit>

    /** Empties all objects from the bucket with the given [id]. */
    public suspend fun emptyBucket(id: String): SupabaseResult<Unit>

    /** Uploads [data] to [path] within [bucket], returning the object key. */
    public suspend fun upload(
        bucket: String,
        path: String,
        data: ByteArray,
        contentType: String = "application/octet-stream",
        upsert: Boolean = false,
    ): SupabaseResult<String>

    /** Downloads the object at [path] within [bucket], returning the raw response body. */
    public suspend fun download(bucket: String, path: String): SupabaseResult<String>

    /** Lists objects in [bucket] matching [prefix]. */
    public suspend fun list(
        bucket: String,
        prefix: String = "",
        limit: Int = 100,
        offset: Int = 0,
        sortBy: String? = null,
    ): SupabaseResult<List<FileObject>>

    /** Moves an object from [fromPath] to [toPath] within [bucket]. */
    public suspend fun move(bucket: String, fromPath: String, toPath: String): SupabaseResult<Unit>

    /** Removes the objects at [paths] within [bucket]. */
    public suspend fun remove(bucket: String, paths: List<String>): SupabaseResult<Unit>

    /** Creates a signed URL for temporary access to [path] within [bucket]. */
    public suspend fun createSignedUrl(
        bucket: String,
        path: String,
        expiresIn: Long,
    ): SupabaseResult<String>

    /** Returns the public URL for [path] within [bucket]. */
    public fun getPublicUrl(bucket: String, path: String): String
}
