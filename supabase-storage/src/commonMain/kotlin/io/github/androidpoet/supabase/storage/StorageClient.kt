package io.github.androidpoet.supabase.storage
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.storage.models.Bucket
import io.github.androidpoet.supabase.storage.models.FileObject
public interface StorageClient {
    public suspend fun listBuckets(): SupabaseResult<List<Bucket>>
    public suspend fun getBucket(id: String): SupabaseResult<Bucket>
    public suspend fun createBucket(
        id: String,
        name: String,
        public: Boolean = false,
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
    ): SupabaseResult<String>
    public suspend fun download(bucket: String, path: String): SupabaseResult<String>
    public suspend fun list(
        bucket: String,
        prefix: String = "",
        limit: Int = 100,
        offset: Int = 0,
        sortBy: String? = null,
    ): SupabaseResult<List<FileObject>>
    public suspend fun move(bucket: String, fromPath: String, toPath: String): SupabaseResult<Unit>
    public suspend fun remove(bucket: String, paths: List<String>): SupabaseResult<Unit>
    public suspend fun createSignedUrl(
        bucket: String,
        path: String,
        expiresIn: Long,
    ): SupabaseResult<String>
    public fun getPublicUrl(bucket: String, path: String): String
}
