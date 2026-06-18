package io.github.androidpoet.supabase.storage.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
public data class Bucket(
    val id: String,
    val name: String,
    val public: Boolean,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("file_size_limit") val fileSizeLimit: Long? = null,
    @SerialName("allowed_mime_types") val allowedMimeTypes: List<String>? = null,
)

@Serializable
public data class FileObject(
    val name: String,
    val id: String? = null,
    @SerialName("bucket_id") val bucketId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val metadata: JsonObject? = null,
)

@Serializable
public data class CreateBucketRequest(
    val id: String,
    val name: String,
    val public: Boolean = false,
    @SerialName("file_size_limit") val fileSizeLimit: Long? = null,
    @SerialName("allowed_mime_types") val allowedMimeTypes: List<String>? = null,
)

@Serializable
public data class SignedUrlRequest(
    // The storage server reads the request body as camelCase `expiresIn`; sending
    // snake_case `expires_in` left the TTL unset, so signed URLs used the server default.
    @SerialName("expiresIn") val expiresIn: Long,
    @SerialName("transform") val transform: SignedUrlTransformRequest? = null,
)

@Serializable
public data class SignedUrlTransformRequest(
    val width: Int? = null,
    val height: Int? = null,
    val resize: String? = null,
    val format: String? = null,
    val quality: Int? = null,
)

@Serializable
public data class SignedUrlResponse(
    // The storage server returns camelCase `signedURL` from POST /object/sign/:bucket/*
    // (same key the batch endpoint uses); decoding `signed_url` here failed every call.
    @SerialName("signedURL") val signedUrl: String,
)

@Serializable
public data class SignedUrlsRequest(
    @SerialName("paths") val paths: List<String>,
    @SerialName("expiresIn") val expiresIn: Long,
)

@Serializable
public data class SignedUrlItemResponse(
    @SerialName("path") val path: String,
    @SerialName("signedURL") val signedUrl: String,
)

@Serializable
public data class UploadSignedUrlResponse(
    @SerialName("url") val url: String,
    @SerialName("token") val token: String,
)

@Serializable
public data class UpdateBucketRequest(
    val id: String? = null,
    val name: String? = null,
    val public: Boolean? = null,
    @SerialName("file_size_limit") val fileSizeLimit: Long? = null,
    @SerialName("allowed_mime_types") val allowedMimeTypes: List<String>? = null,
)

@Serializable
public data class MoveRequest(
    val bucketId: String,
    val sourceKey: String,
    val destinationKey: String? = null,
    @SerialName("destinationBucket") val destinationBucketId: String? = null,
    @SerialName("copyMetadata") val copyMetadata: Boolean? = null,
)

@Serializable
public data class ObjectSortByRequest(
    val column: String,
    val order: String,
)

@Serializable
public data class ObjectListRequest(
    val prefix: String,
    val limit: Int,
    val offset: Int,
    @SerialName("sortBy") val sortBy: ObjectSortByRequest? = null,
    val search: String? = null,
)

@Serializable
public data class ObjectListV2Request(
    val prefix: String? = null,
    val cursor: String? = null,
    val limit: Int? = null,
    @SerialName("with_delimiter") val withDelimiter: Boolean? = null,
    @SerialName("sortBy") val sortBy: ObjectSortByRequest? = null,
)

@Serializable
public data class ObjectListV2Object(
    val name: String,
    val key: String? = null,
    val id: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("last_accessed_at") val lastAccessedAt: String,
    val metadata: JsonObject? = null,
)

@Serializable
public data class ObjectListV2Folder(
    val name: String,
    val key: String? = null,
)

@Serializable
public data class ObjectListV2Result(
    val hasNext: Boolean,
    val folders: List<ObjectListV2Folder>,
    val objects: List<ObjectListV2Object>,
    val nextCursor: String? = null,
)

@Serializable
public data class AnalyticsBucket(
    val name: String,
    val type: String,
    val format: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
public data class AnalyticsBucketCreateRequest(
    val name: String,
)

@Serializable
public data class AnalyticsBucketDeleteResponse(
    val message: String? = null,
)

@Serializable
public data class IcebergCatalogConfig(
    val defaults: Map<String, String> = emptyMap(),
    val overrides: Map<String, String> = emptyMap(),
)

@Serializable
public data class IcebergNamespaceListResponse(
    val namespaces: List<List<String>> = emptyList(),
    @SerialName("next-page-token") val nextPageToken: String? = null,
    @SerialName("nextPageToken") val nextPageTokenCamelCase: String? = null,
) {
    public val resolvedNextPageToken: String?
        get() = nextPageToken ?: nextPageTokenCamelCase
}

@Serializable
public data class IcebergCreateNamespaceRequest(
    val namespace: List<String>,
    val properties: Map<String, String> = emptyMap(),
)

@Serializable
public data class IcebergNamespaceMetadata(
    val namespace: List<String> = emptyList(),
    val properties: Map<String, String> = emptyMap(),
)

@Serializable
public data class IcebergUpdateNamespacePropertiesRequest(
    val removals: List<String>? = null,
    val updates: Map<String, String>? = null,
)

@Serializable
public data class IcebergUpdateNamespacePropertiesResponse(
    val removed: List<String> = emptyList(),
    val updated: List<String> = emptyList(),
    val missing: List<String> = emptyList(),
)

@Serializable
public data class IcebergTableIdentifier(
    val namespace: List<String>,
    val name: String,
)

@Serializable
public data class IcebergTableListResponse(
    val identifiers: List<IcebergTableIdentifier> = emptyList(),
    @SerialName("next-page-token") val nextPageToken: String? = null,
    @SerialName("nextPageToken") val nextPageTokenCamelCase: String? = null,
) {
    public val resolvedNextPageToken: String?
        get() = nextPageToken ?: nextPageTokenCamelCase
}

@Serializable
public data class IcebergTableCreateRequest(
    val name: String,
    val schema: JsonObject,
    val location: String? = null,
    val partitionSpec: JsonObject? = null,
    val writeOrder: JsonObject? = null,
    val properties: Map<String, String> = emptyMap(),
)

@Serializable
public data class IcebergTableCommitRequest(
    val requirements: List<JsonObject> = emptyList(),
    val updates: List<JsonObject> = emptyList(),
)

@Serializable
public data class IcebergTableRegisterRequest(
    val name: String,
    @SerialName("metadata-location") val metadataLocation: String,
)

@Serializable
public data class IcebergTableRenameRequest(
    val source: IcebergTableIdentifier,
    val destination: IcebergTableIdentifier,
)

@Serializable
public data class IcebergTableMetadataResponse(
    val name: String? = null,
    val metadata: JsonObject? = null,
    val config: Map<String, String> = emptyMap(),
    @SerialName("metadata-location") val metadataLocation: String? = null,
)

@Serializable
public data class VectorBucketCreateRequest(
    val vectorBucketName: String,
)

@Serializable
public data class VectorBucketRequest(
    val vectorBucketName: String,
)

@Serializable
public data class VectorBucket(
    val vectorBucketName: String,
    val creationTime: Long? = null,
    val encryptionConfiguration: JsonObject? = null,
)

@Serializable
public data class VectorBucketResponse(
    val vectorBucket: VectorBucket,
)

@Serializable
public data class VectorBucketListRequest(
    val prefix: String? = null,
    val maxResults: Int? = null,
    val nextToken: String? = null,
)

@Serializable
public data class VectorBucketListItem(
    val vectorBucketName: String,
)

@Serializable
public data class VectorBucketListResponse(
    val vectorBuckets: List<VectorBucketListItem>,
    val nextToken: String? = null,
)

@Serializable
public enum class VectorDataType {
    @SerialName("float32")
    FLOAT32,
}

@Serializable
public enum class VectorDistanceMetric {
    @SerialName("cosine")
    COSINE,

    @SerialName("euclidean")
    EUCLIDEAN,

    @SerialName("dotproduct")
    DOTPRODUCT,
}

@Serializable
public data class VectorMetadataConfiguration(
    val nonFilterableMetadataKeys: List<String>? = null,
)

@Serializable
public data class VectorIndexCreateRequest(
    val vectorBucketName: String,
    val indexName: String,
    val dataType: VectorDataType,
    val dimension: Int,
    val distanceMetric: VectorDistanceMetric,
    val metadataConfiguration: VectorMetadataConfiguration? = null,
)

@Serializable
public data class VectorIndexRequest(
    val vectorBucketName: String,
    val indexName: String,
)

@Serializable
public data class VectorIndex(
    val indexName: String,
    val vectorBucketName: String,
    val dataType: VectorDataType,
    val dimension: Int,
    val distanceMetric: VectorDistanceMetric,
    val metadataConfiguration: VectorMetadataConfiguration? = null,
    val creationTime: Long? = null,
)

@Serializable
public data class VectorIndexResponse(
    val index: VectorIndex,
)

@Serializable
public data class VectorIndexListRequest(
    val vectorBucketName: String,
    val prefix: String? = null,
    val maxResults: Int? = null,
    val nextToken: String? = null,
)

@Serializable
public data class VectorIndexListItem(
    val indexName: String,
)

@Serializable
public data class VectorIndexListResponse(
    val indexes: List<VectorIndexListItem>,
    val nextToken: String? = null,
)

@Serializable
public data class VectorData(
    val float32: List<Double>,
)

@Serializable
public data class VectorObject(
    val key: String,
    val data: VectorData,
    val metadata: JsonObject? = null,
)

@Serializable
public data class VectorMatch(
    val key: String,
    val data: VectorData? = null,
    val metadata: JsonObject? = null,
    val distance: Double? = null,
)

@Serializable
public data class VectorPutRequest(
    val vectorBucketName: String,
    val indexName: String,
    val vectors: List<VectorObject>,
)

@Serializable
public data class VectorGetRequest(
    val vectorBucketName: String,
    val indexName: String,
    val keys: List<String>,
    val returnData: Boolean? = null,
    val returnMetadata: Boolean? = null,
)

@Serializable
public data class VectorDeleteRequest(
    val vectorBucketName: String,
    val indexName: String,
    val keys: List<String>,
)

@Serializable
public data class VectorListRequest(
    val vectorBucketName: String,
    val indexName: String,
    val maxResults: Int? = null,
    val nextToken: String? = null,
    val returnData: Boolean? = null,
    val returnMetadata: Boolean? = null,
    val segmentCount: Int? = null,
    val segmentIndex: Int? = null,
)

@Serializable
public data class VectorListResponse(
    val vectors: List<VectorMatch>,
    val nextToken: String? = null,
)

@Serializable
public data class VectorQueryRequest(
    val vectorBucketName: String,
    val indexName: String,
    val queryVector: VectorData,
    val topK: Int? = null,
    val filter: JsonObject? = null,
    val returnDistance: Boolean? = null,
    val returnMetadata: Boolean? = null,
)

@Serializable
public data class VectorQueryResponse(
    val vectors: List<VectorMatch>,
    val distanceMetric: VectorDistanceMetric? = null,
)
