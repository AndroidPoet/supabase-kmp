package io.github.androidpoet.supabase.storage
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.storage.models.AnalyticsBucket
import io.github.androidpoet.supabase.storage.models.Bucket
import io.github.androidpoet.supabase.storage.models.FileObject
import io.github.androidpoet.supabase.storage.models.IcebergCatalogConfig
import io.github.androidpoet.supabase.storage.models.IcebergCreateNamespaceRequest
import io.github.androidpoet.supabase.storage.models.IcebergNamespaceListResponse
import io.github.androidpoet.supabase.storage.models.IcebergNamespaceMetadata
import io.github.androidpoet.supabase.storage.models.IcebergTableCommitRequest
import io.github.androidpoet.supabase.storage.models.IcebergTableCreateRequest
import io.github.androidpoet.supabase.storage.models.IcebergTableIdentifier
import io.github.androidpoet.supabase.storage.models.IcebergTableListResponse
import io.github.androidpoet.supabase.storage.models.IcebergTableMetadataResponse
import io.github.androidpoet.supabase.storage.models.IcebergTableRegisterRequest
import io.github.androidpoet.supabase.storage.models.IcebergUpdateNamespacePropertiesRequest
import io.github.androidpoet.supabase.storage.models.IcebergUpdateNamespacePropertiesResponse
import io.github.androidpoet.supabase.storage.models.ObjectListV2Result
import io.github.androidpoet.supabase.storage.models.VectorBucket
import io.github.androidpoet.supabase.storage.models.VectorBucketListResponse
import io.github.androidpoet.supabase.storage.models.VectorData
import io.github.androidpoet.supabase.storage.models.VectorDataType
import io.github.androidpoet.supabase.storage.models.VectorDistanceMetric
import io.github.androidpoet.supabase.storage.models.VectorIndex
import io.github.androidpoet.supabase.storage.models.VectorIndexListResponse
import io.github.androidpoet.supabase.storage.models.VectorListResponse
import io.github.androidpoet.supabase.storage.models.VectorMatch
import io.github.androidpoet.supabase.storage.models.VectorMetadataConfiguration
import io.github.androidpoet.supabase.storage.models.VectorObject
import io.github.androidpoet.supabase.storage.models.VectorQueryResponse
import kotlinx.serialization.json.JsonObject

public interface AnalyticsCatalogClient {
    public suspend fun loadConfig(): SupabaseResult<JsonObject>

    public suspend fun loadConfigTyped(): SupabaseResult<IcebergCatalogConfig>

    public suspend fun listNamespaces(
        parent: List<String>? = null,
        pageToken: String? = null,
        pageSize: Int? = null,
    ): SupabaseResult<JsonObject>

    public suspend fun listNamespacesTyped(
        parent: List<String>? = null,
        pageToken: String? = null,
        pageSize: Int? = null,
    ): SupabaseResult<IcebergNamespaceListResponse>

    public suspend fun createNamespace(
        namespace: List<String>,
        properties: Map<String, String> = emptyMap(),
    ): SupabaseResult<JsonObject>

    public suspend fun createNamespaceTyped(
        request: IcebergCreateNamespaceRequest,
    ): SupabaseResult<IcebergNamespaceMetadata>

    public suspend fun dropNamespace(namespace: List<String>): SupabaseResult<Unit>

    public suspend fun loadNamespaceMetadata(namespace: List<String>): SupabaseResult<JsonObject>

    public suspend fun loadNamespaceMetadataTyped(namespace: List<String>): SupabaseResult<IcebergNamespaceMetadata>

    public suspend fun updateNamespaceProperties(
        namespace: List<String>,
        removals: List<String>? = null,
        updates: Map<String, String>? = null,
    ): SupabaseResult<JsonObject>

    public suspend fun updateNamespacePropertiesTyped(
        namespace: List<String>,
        request: IcebergUpdateNamespacePropertiesRequest,
    ): SupabaseResult<IcebergUpdateNamespacePropertiesResponse>

    public suspend fun listTables(
        namespace: List<String>,
        pageToken: String? = null,
        pageSize: Int? = null,
    ): SupabaseResult<JsonObject>

    public suspend fun listTablesTyped(
        namespace: List<String>,
        pageToken: String? = null,
        pageSize: Int? = null,
    ): SupabaseResult<IcebergTableListResponse>

    public suspend fun createTable(
        namespace: List<String>,
        request: JsonObject,
    ): SupabaseResult<JsonObject>

    public suspend fun createTableTyped(
        namespace: List<String>,
        request: IcebergTableCreateRequest,
    ): SupabaseResult<IcebergTableMetadataResponse>

    public suspend fun updateTable(
        namespace: List<String>,
        name: String,
        request: JsonObject,
    ): SupabaseResult<JsonObject>

    public suspend fun commitTableTyped(
        namespace: List<String>,
        name: String,
        request: IcebergTableCommitRequest,
    ): SupabaseResult<IcebergTableMetadataResponse>

    public suspend fun commitTable(
        namespace: List<String>,
        name: String,
        request: JsonObject,
    ): SupabaseResult<JsonObject>

    public suspend fun dropTable(
        namespace: List<String>,
        name: String,
        purge: Boolean = false,
    ): SupabaseResult<Unit>

    public suspend fun loadTable(
        namespace: List<String>,
        name: String,
        snapshots: String? = null,
    ): SupabaseResult<JsonObject>

    public suspend fun loadTableTyped(
        namespace: List<String>,
        name: String,
        snapshots: String? = null,
    ): SupabaseResult<IcebergTableMetadataResponse>

    public suspend fun registerTable(
        namespace: List<String>,
        request: JsonObject,
    ): SupabaseResult<JsonObject>

    public suspend fun registerTableTyped(
        namespace: List<String>,
        request: IcebergTableRegisterRequest,
    ): SupabaseResult<IcebergTableMetadataResponse>

    public suspend fun renameTable(request: JsonObject): SupabaseResult<Unit>

    public suspend fun renameTableTyped(
        source: IcebergTableIdentifier,
        destination: IcebergTableIdentifier,
    ): SupabaseResult<Unit>
}

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

    /**
     * Creates a resumable (TUS) upload handle. The upload is not started until
     * [ResumableUpload.await] is called. Pass [uploadUrl] (a previously captured
     * [ResumableUpload.uploadUrl]) to resume an interrupted upload instead of
     * starting a new one.
     */
    public fun createResumableUpload(
        bucket: String,
        path: String,
        data: ByteArray,
        contentType: String = "application/octet-stream",
        upsert: Boolean = false,
        cacheControl: Int? = null,
        chunkSize: Int = RESUMABLE_DEFAULT_CHUNK_SIZE,
        uploadUrl: String? = null,
    ): ResumableUpload

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

    public suspend fun listV2(
        bucket: String,
        prefix: String? = null,
        cursor: String? = null,
        limit: Int? = null,
        withDelimiter: Boolean? = null,
        sortBy: String? = null,
        sortOrder: SortOrder = SortOrder.ASC,
    ): SupabaseResult<ObjectListV2Result>

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

    public suspend fun createAnalyticsBucket(name: String): SupabaseResult<AnalyticsBucket>

    public suspend fun listAnalyticsBuckets(
        limit: Int? = null,
        offset: Int? = null,
        sortColumn: String? = null,
        sortOrder: SortOrder? = null,
        search: String? = null,
    ): SupabaseResult<List<AnalyticsBucket>>

    public suspend fun deleteAnalyticsBucket(name: String): SupabaseResult<Unit>

    public fun analyticsCatalog(bucketName: String): AnalyticsCatalogClient

    public suspend fun createVectorBucket(vectorBucketName: String): SupabaseResult<Unit>

    public suspend fun getVectorBucket(vectorBucketName: String): SupabaseResult<VectorBucket>

    public suspend fun listVectorBuckets(
        prefix: String? = null,
        maxResults: Int? = null,
        nextToken: String? = null,
    ): SupabaseResult<VectorBucketListResponse>

    public suspend fun deleteVectorBucket(vectorBucketName: String): SupabaseResult<Unit>

    public suspend fun createVectorIndex(
        vectorBucketName: String,
        indexName: String,
        dataType: VectorDataType,
        dimension: Int,
        distanceMetric: VectorDistanceMetric,
        metadataConfiguration: VectorMetadataConfiguration? = null,
    ): SupabaseResult<Unit>

    public suspend fun getVectorIndex(
        vectorBucketName: String,
        indexName: String,
    ): SupabaseResult<VectorIndex>

    public suspend fun listVectorIndexes(
        vectorBucketName: String,
        prefix: String? = null,
        maxResults: Int? = null,
        nextToken: String? = null,
    ): SupabaseResult<VectorIndexListResponse>

    public suspend fun deleteVectorIndex(
        vectorBucketName: String,
        indexName: String,
    ): SupabaseResult<Unit>

    public suspend fun putVectors(
        vectorBucketName: String,
        indexName: String,
        vectors: List<VectorObject>,
    ): SupabaseResult<Unit>

    public suspend fun getVectors(
        vectorBucketName: String,
        indexName: String,
        keys: List<String>,
        returnData: Boolean? = null,
        returnMetadata: Boolean? = null,
    ): SupabaseResult<List<VectorMatch>>

    public suspend fun listVectors(
        vectorBucketName: String,
        indexName: String,
        maxResults: Int? = null,
        nextToken: String? = null,
        returnData: Boolean? = null,
        returnMetadata: Boolean? = null,
        segmentCount: Int? = null,
        segmentIndex: Int? = null,
    ): SupabaseResult<VectorListResponse>

    public suspend fun queryVectors(
        vectorBucketName: String,
        indexName: String,
        queryVector: VectorData,
        topK: Int? = null,
        filter: JsonObject? = null,
        returnDistance: Boolean? = null,
        returnMetadata: Boolean? = null,
    ): SupabaseResult<VectorQueryResponse>

    public suspend fun deleteVectors(
        vectorBucketName: String,
        indexName: String,
        keys: List<String>,
    ): SupabaseResult<Unit>
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
