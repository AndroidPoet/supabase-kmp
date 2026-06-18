package io.github.androidpoet.supabase.storage.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A storage bucket as returned by the bucket endpoints.
 *
 * @property id the bucket's stable identifier and URL segment.
 * @property name human-readable bucket name.
 * @property public whether objects are readable via the public endpoint without a token.
 * @property createdAt creation timestamp, when reported by the server.
 * @property updatedAt last-update timestamp, when reported.
 * @property fileSizeLimit per-object size cap in bytes, when set.
 * @property allowedMimeTypes whitelist of accepted content types, when set; null allows any.
 */
@Serializable
public data class Bucket(
    val id: String,
    val name: String,
    val public: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("file_size_limit") val fileSizeLimit: Long? = null,
    @SerialName("allowed_mime_types") val allowedMimeTypes: List<String>? = null,
)

/**
 * Represents a file or folder entry returned by the storage server.
 *
 * For folders, every field except [name] is typically `null`. The trailing
 * fields ([size], [contentType], [etag], [lastAccessedAt], [cacheControl]) are
 * populated by object-info and v2 listing endpoints; older list responses omit
 * them, so they default to `null`.
 *
 * @property name The object name (or folder name).
 * @property id The object id, when present.
 * @property bucketId The id of the owning bucket, when present.
 * @property createdAt The creation timestamp, when present.
 * @property updatedAt The last-update timestamp, when present.
 * @property metadata Free-form server metadata, when present.
 * @property size The object size in bytes, when reported by the server.
 * @property contentType The MIME type the server stored for the object, when reported.
 * @property etag The entity tag the server assigned to the object, when reported.
 * @property lastAccessedAt The last-access timestamp, when reported.
 * @property cacheControl The cache-control directive the server stored, when reported.
 */
@Serializable
public data class FileObject(
    val name: String,
    val id: String? = null,
    @SerialName("bucket_id") val bucketId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val metadata: JsonObject? = null,
    val size: Long? = null,
    @SerialName("content_type") val contentType: String? = null,
    val etag: String? = null,
    @SerialName("last_accessed_at") val lastAccessedAt: String? = null,
    @SerialName("cache_control") val cacheControl: String? = null,
)

/** Request body for creating a bucket; see [io.github.androidpoet.supabase.storage.StorageClient.createBucket] for field semantics. */
@Serializable
public data class CreateBucketRequest(
    val id: String,
    val name: String,
    val public: Boolean = false,
    @SerialName("file_size_limit") val fileSizeLimit: Long? = null,
    @SerialName("allowed_mime_types") val allowedMimeTypes: List<String>? = null,
)

/** Request body for signing a single object URL: TTL plus optional image transform. */
@Serializable
public data class SignedUrlRequest(
    // The storage server reads the request body as camelCase `expiresIn`; sending
    // snake_case `expires_in` left the TTL unset, so signed URLs used the server default.
    @SerialName("expiresIn") val expiresIn: Long,
    @SerialName("transform") val transform: SignedUrlTransformRequest? = null,
)

/**
 * Wire form of an image transform sent in a signed-URL request. The on-the-wire counterpart of
 * [io.github.androidpoet.supabase.storage.ImageTransformOptions], with [resize] as the raw string.
 */
@Serializable
public data class SignedUrlTransformRequest(
    val width: Int? = null,
    val height: Int? = null,
    val resize: String? = null,
    val format: String? = null,
    val quality: Int? = null,
)

/** Response carrying a single signed object URL. */
@Serializable
public data class SignedUrlResponse(
    // The storage server returns camelCase `signedURL` from POST /object/sign/:bucket/*
    // (same key the batch endpoint uses); decoding `signed_url` here failed every call.
    @SerialName("signedURL") val signedUrl: String,
)

/** Request body for batch-signing object URLs: the paths to sign and their shared TTL. */
@Serializable
public data class SignedUrlsRequest(
    @SerialName("paths") val paths: List<String>,
    @SerialName("expiresIn") val expiresIn: Long,
)

/** One entry of a batch signed-URL response: the requested [path] and its [signedUrl]. */
@Serializable
public data class SignedUrlItemResponse(
    @SerialName("path") val path: String,
    @SerialName("signedURL") val signedUrl: String,
)

/**
 * Response to a create-upload-signed-URL request: the upload [url], the [token] authorizing it,
 * and the server-bound object [path] when returned.
 */
@Serializable
public data class UploadSignedUrlResponse(
    @SerialName("url") val url: String,
    @SerialName("token") val token: String,
    @SerialName("path") val path: String? = null,
)

/**
 * Request body for updating a bucket. Only non-null fields are applied; see [Bucket] for the
 * meaning of each attribute.
 */
@Serializable
public data class UpdateBucketRequest(
    val id: String? = null,
    val name: String? = null,
    val public: Boolean? = null,
    @SerialName("file_size_limit") val fileSizeLimit: Long? = null,
    @SerialName("allowed_mime_types") val allowedMimeTypes: List<String>? = null,
)

/**
 * Request body shared by the object move and copy endpoints.
 *
 * @property bucketId source bucket id.
 * @property sourceKey object key to move/copy from.
 * @property destinationKey target object key.
 * @property destinationBucketId target bucket id when crossing buckets; null stays in [bucketId].
 * @property copyMetadata whether to carry the object's user metadata across (copy only).
 */
@Serializable
public data class MoveRequest(
    val bucketId: String,
    val sourceKey: String,
    val destinationKey: String? = null,
    @SerialName("destinationBucket") val destinationBucketId: String? = null,
    @SerialName("copyMetadata") val copyMetadata: Boolean? = null,
)

/** Sort directive in a listing request: the [column] to sort by and the [order] (`asc`/`desc`). */
@Serializable
public data class ObjectSortByRequest(
    val column: String,
    val order: String,
)

/** Request body for the offset-paged object listing; see [io.github.androidpoet.supabase.storage.StorageClient.list]. */
@Serializable
public data class ObjectListRequest(
    val prefix: String,
    val limit: Int,
    val offset: Int,
    @SerialName("sortBy") val sortBy: ObjectSortByRequest? = null,
    val search: String? = null,
)

/** Request body for the cursor-paged object listing; see [io.github.androidpoet.supabase.storage.StorageClient.listV2]. */
@Serializable
public data class ObjectListV2Request(
    val prefix: String? = null,
    val cursor: String? = null,
    val limit: Int? = null,
    @SerialName("with_delimiter") val withDelimiter: Boolean? = null,
    @SerialName("sortBy") val sortBy: ObjectSortByRequest? = null,
)

/**
 * A single object entry in a v2 ([ObjectListV2Result]) listing.
 *
 * @property name the object name within its folder.
 * @property key the full object key, when reported.
 * @property id the object id.
 * @property updatedAt last-update timestamp.
 * @property createdAt creation timestamp.
 * @property lastAccessedAt last-access timestamp.
 * @property metadata free-form server metadata, when present.
 */
@Serializable
public data class ObjectListV2Object(
    val name: String,
    val key: String? = null,
    val id: String,
    // Timestamps tolerate omission (mirrors FileObject), defaulting to null when not reported.
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_accessed_at") val lastAccessedAt: String? = null,
    val metadata: JsonObject? = null,
)

/** A folder (common-prefix) entry in a v2 listing; [key] is the full prefix when reported. */
@Serializable
public data class ObjectListV2Folder(
    val name: String,
    val key: String? = null,
)

/**
 * Result of a cursor-paged v2 listing, separating folders from objects and carrying continuation
 * state.
 *
 * @property hasNext whether more pages remain.
 * @property folders the folder (common-prefix) entries in this page.
 * @property objects the object entries in this page.
 * @property nextCursor cursor to pass back for the next page, when [hasNext].
 */
@Serializable
public data class ObjectListV2Result(
    val hasNext: Boolean,
    val folders: List<ObjectListV2Folder>,
    val objects: List<ObjectListV2Object>,
    val nextCursor: String? = null,
)

/**
 * An analytics (Iceberg) bucket.
 *
 * @property name the analytics bucket name.
 * @property type the server-reported bucket type.
 * @property format the table data format (e.g. Iceberg).
 * @property createdAt creation timestamp, when reported.
 * @property updatedAt last-update timestamp, when reported.
 */
@Serializable
public data class AnalyticsBucket(
    val name: String,
    val type: String,
    val format: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** Request body for creating an analytics bucket by [name]. */
@Serializable
public data class AnalyticsBucketCreateRequest(
    val name: String,
)

/** Response of an analytics-bucket delete, carrying an optional server [message]. */
@Serializable
public data class AnalyticsBucketDeleteResponse(
    val message: String? = null,
)

/**
 * Iceberg catalog configuration (`GET /v1/config`): the property maps that parameterize the
 * catalog. [overrides] take precedence over [defaults]; both may carry the route `prefix` the
 * catalog client uses to address namespaces and tables.
 */
@Serializable
public data class IcebergCatalogConfig(
    val defaults: Map<String, String> = emptyMap(),
    val overrides: Map<String, String> = emptyMap(),
)

/**
 * Response listing Iceberg namespaces, each a multi-level path. The next-page token may arrive in
 * either casing depending on server version; read it through [resolvedNextPageToken].
 */
@Serializable
public data class IcebergNamespaceListResponse(
    val namespaces: List<List<String>> = emptyList(),
    @SerialName("next-page-token") val nextPageToken: String? = null,
    @SerialName("nextPageToken") val nextPageTokenCamelCase: String? = null,
) {
    /** The next-page token regardless of which casing the server returned, or null if absent. */
    public val resolvedNextPageToken: String?
        get() = nextPageToken ?: nextPageTokenCamelCase
}

/** Request body to create an Iceberg [namespace] with initial [properties]. */
@Serializable
public data class IcebergCreateNamespaceRequest(
    val namespace: List<String>,
    val properties: Map<String, String> = emptyMap(),
)

/** An Iceberg namespace and its property map, as returned by the namespace endpoints. */
@Serializable
public data class IcebergNamespaceMetadata(
    val namespace: List<String> = emptyList(),
    val properties: Map<String, String> = emptyMap(),
)

/** Request body to mutate namespace properties: keys to [removals] and/or [updates]. */
@Serializable
public data class IcebergUpdateNamespacePropertiesRequest(
    val removals: List<String>? = null,
    val updates: Map<String, String>? = null,
)

/**
 * Outcome of a namespace-properties update, reporting which keys were [removed], [updated], and
 * [missing] (requested but not found).
 */
@Serializable
public data class IcebergUpdateNamespacePropertiesResponse(
    val removed: List<String> = emptyList(),
    val updated: List<String> = emptyList(),
    val missing: List<String> = emptyList(),
)

/** Fully qualifies an Iceberg table by its [namespace] path and [name]. */
@Serializable
public data class IcebergTableIdentifier(
    val namespace: List<String>,
    val name: String,
)

/**
 * Response listing the tables in a namespace by [IcebergTableIdentifier]. As with
 * [IcebergNamespaceListResponse], read the continuation token via [resolvedNextPageToken].
 */
@Serializable
public data class IcebergTableListResponse(
    val identifiers: List<IcebergTableIdentifier> = emptyList(),
    @SerialName("next-page-token") val nextPageToken: String? = null,
    @SerialName("nextPageToken") val nextPageTokenCamelCase: String? = null,
) {
    /** The next-page token regardless of which casing the server returned, or null if absent. */
    public val resolvedNextPageToken: String?
        get() = nextPageToken ?: nextPageTokenCamelCase
}

/**
 * Request body to create an Iceberg table.
 *
 * @property name the table name within the target namespace.
 * @property schema the Iceberg schema as a raw JSON object.
 * @property location optional explicit base location for the table's data.
 * @property partitionSpec optional partition spec as a raw JSON object.
 * @property writeOrder optional sort/write order as a raw JSON object.
 * @property properties initial table properties.
 */
@Serializable
public data class IcebergTableCreateRequest(
    val name: String,
    val schema: JsonObject,
    val location: String? = null,
    val partitionSpec: JsonObject? = null,
    val writeOrder: JsonObject? = null,
    val properties: Map<String, String> = emptyMap(),
)

/**
 * Request body for an Iceberg table commit: the [requirements] that must hold and the metadata
 * [updates] to apply atomically, each as raw JSON objects per the Iceberg spec.
 */
@Serializable
public data class IcebergTableCommitRequest(
    val requirements: List<JsonObject> = emptyList(),
    val updates: List<JsonObject> = emptyList(),
)

/** Request body to register an existing table by [name] and its [metadataLocation] in storage. */
@Serializable
public data class IcebergTableRegisterRequest(
    val name: String,
    @SerialName("metadata-location") val metadataLocation: String,
)

/** Request body to rename/move a table from [source] to [destination]. */
@Serializable
public data class IcebergTableRenameRequest(
    val source: IcebergTableIdentifier,
    val destination: IcebergTableIdentifier,
)

/**
 * Response carrying an Iceberg table's metadata.
 *
 * @property name the table name, when reported.
 * @property metadata the table metadata document as a raw JSON object.
 * @property config access/IO config the client should use for this table.
 * @property metadataLocation storage location of the metadata file, when reported.
 */
@Serializable
public data class IcebergTableMetadataResponse(
    val name: String? = null,
    val metadata: JsonObject? = null,
    val config: Map<String, String> = emptyMap(),
    @SerialName("metadata-location") val metadataLocation: String? = null,
)

/** Request body to create an S3-Vectors bucket named [vectorBucketName]. */
@Serializable
public data class VectorBucketCreateRequest(
    val vectorBucketName: String,
)

/** Request body addressing a single vector bucket by [vectorBucketName] (get/delete). */
@Serializable
public data class VectorBucketRequest(
    val vectorBucketName: String,
)

/**
 * An S3-Vectors bucket.
 *
 * @property vectorBucketName the bucket name.
 * @property creationTime creation time as an epoch value, when reported.
 * @property encryptionConfiguration server encryption config as a raw JSON object, when present.
 */
@Serializable
public data class VectorBucket(
    val vectorBucketName: String,
    val creationTime: Long? = null,
    val encryptionConfiguration: JsonObject? = null,
)

/** Envelope wrapping a single [VectorBucket] in the get-bucket response. */
@Serializable
public data class VectorBucketResponse(
    val vectorBucket: VectorBucket,
)

/** Request body for listing vector buckets, with optional prefix filter and paging token. */
@Serializable
public data class VectorBucketListRequest(
    val prefix: String? = null,
    val maxResults: Int? = null,
    val nextToken: String? = null,
)

/** One entry in a vector-bucket listing: just the bucket name. */
@Serializable
public data class VectorBucketListItem(
    val vectorBucketName: String,
)

/** Response listing vector buckets, with a [nextToken] continuation when more remain. */
@Serializable
public data class VectorBucketListResponse(
    val vectorBuckets: List<VectorBucketListItem>,
    val nextToken: String? = null,
)

/** Element type of stored vectors. Currently only single-precision floats are supported. */
@Serializable
public enum class VectorDataType {
    /** 32-bit IEEE float components. */
    @SerialName("float32")
    FLOAT32,
}

/** Similarity metric a vector index uses to rank query matches. */
@Serializable
public enum class VectorDistanceMetric {
    /** Cosine similarity (angle between vectors). */
    @SerialName("cosine")
    COSINE,

    /** Euclidean (L2) distance. */
    @SerialName("euclidean")
    EUCLIDEAN,

    /** Dot-product (inner product) similarity. */
    @SerialName("dotproduct")
    DOTPRODUCT,
}

/** Declares which metadata keys are non-filterable for an index, so queries cannot filter on them. */
@Serializable
public data class VectorMetadataConfiguration(
    val nonFilterableMetadataKeys: List<String>? = null,
)

/** Request body to create a vector index; see [io.github.androidpoet.supabase.storage.StorageClient.createVectorIndex] for field semantics. */
@Serializable
public data class VectorIndexCreateRequest(
    val vectorBucketName: String,
    val indexName: String,
    val dataType: VectorDataType,
    val dimension: Int,
    val distanceMetric: VectorDistanceMetric,
    val metadataConfiguration: VectorMetadataConfiguration? = null,
)

/** Request body addressing a single index by bucket and [indexName] (get/delete). */
@Serializable
public data class VectorIndexRequest(
    val vectorBucketName: String,
    val indexName: String,
)

/**
 * A vector index's definition.
 *
 * @property indexName the index name.
 * @property vectorBucketName the owning bucket.
 * @property dataType element type of stored vectors.
 * @property dimension fixed dimensionality of stored vectors.
 * @property distanceMetric similarity metric used for queries.
 * @property metadataConfiguration non-filterable metadata declaration, when set.
 * @property creationTime creation time as an epoch value, when reported.
 */
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

/** Envelope wrapping a single [VectorIndex] in the get-index response. */
@Serializable
public data class VectorIndexResponse(
    val index: VectorIndex,
)

/** Request body for listing the indexes in a bucket, with optional prefix filter and paging token. */
@Serializable
public data class VectorIndexListRequest(
    val vectorBucketName: String,
    val prefix: String? = null,
    val maxResults: Int? = null,
    val nextToken: String? = null,
)

/** One entry in a vector-index listing: just the index name. */
@Serializable
public data class VectorIndexListItem(
    val indexName: String,
)

/** Response listing indexes, with a [nextToken] continuation when more remain. */
@Serializable
public data class VectorIndexListResponse(
    val indexes: List<VectorIndexListItem>,
    val nextToken: String? = null,
)

/** A vector value: its [float32] components. Length must match the index's dimension. */
@Serializable
public data class VectorData(
    val float32: List<Double>,
)

/**
 * A vector to store in an index.
 *
 * @property key the unique key identifying this vector within the index.
 * @property data the vector components.
 * @property metadata optional metadata attached to the vector, used for filtering and retrieval.
 */
@Serializable
public data class VectorObject(
    val key: String,
    val data: VectorData,
    val metadata: JsonObject? = null,
)

/**
 * A vector returned from a get/list/query, with optionally included data and metadata.
 *
 * @property key the vector's key.
 * @property data the vector components, when requested via `returnData`.
 * @property metadata the vector's metadata, when requested via `returnMetadata`.
 * @property distance the distance/score to the query vector, populated by query results.
 */
@Serializable
public data class VectorMatch(
    val key: String,
    val data: VectorData? = null,
    val metadata: JsonObject? = null,
    val distance: Double? = null,
)

/** Request body to insert/overwrite [vectors] in the given index. */
@Serializable
public data class VectorPutRequest(
    val vectorBucketName: String,
    val indexName: String,
    val vectors: List<VectorObject>,
)

/** Request body to fetch vectors by [keys], optionally including their data and/or metadata. */
@Serializable
public data class VectorGetRequest(
    val vectorBucketName: String,
    val indexName: String,
    val keys: List<String>,
    val returnData: Boolean? = null,
    val returnMetadata: Boolean? = null,
)

/** Request body to delete vectors by [keys] from the given index. */
@Serializable
public data class VectorDeleteRequest(
    val vectorBucketName: String,
    val indexName: String,
    val keys: List<String>,
)

/** Request body for listing vectors; see [io.github.androidpoet.supabase.storage.StorageClient.listVectors] for the segment/paging semantics. */
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

/** Response listing vectors, with a [nextToken] continuation when more remain. */
@Serializable
public data class VectorListResponse(
    val vectors: List<VectorMatch>,
    val nextToken: String? = null,
)

/** Request body for a nearest-neighbor query; see [io.github.androidpoet.supabase.storage.StorageClient.queryVectors] for field semantics. */
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

/**
 * Result of a vector query: the matching [vectors] ranked by similarity, plus the
 * [distanceMetric] the index used, when reported.
 */
@Serializable
public data class VectorQueryResponse(
    val vectors: List<VectorMatch>,
    val distanceMetric: VectorDistanceMetric? = null,
)
