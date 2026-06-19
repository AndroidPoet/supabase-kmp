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

/**
 * Apache Iceberg REST catalog client scoped to one analytics bucket, exposing the
 * namespace and table operations the Supabase analytics endpoint (`/storage/v1/iceberg`)
 * speaks. Obtain one via [StorageClient.analyticsCatalog]; the bucket it targets is fixed
 * at creation.
 *
 * Most operations come in two flavours: a raw `…` variant returning the server's
 * [JsonObject] verbatim (forward-compatible with Iceberg spec fields this library does not
 * yet model) and a `…Typed` variant deserializing into a model from
 * [io.github.androidpoet.supabase.storage.models]. Prefer the typed variants unless you need
 * a spec field that is not yet mapped.
 *
 * Namespaces are multi-level and modeled as `List<String>` (e.g. `["analytics", "sales"]`),
 * which the implementation joins with the Iceberg unit-separator convention before sending.
 * The catalog prefix is resolved lazily from the server config on the first call and cached.
 * Every call returns a [SupabaseResult] — network/HTTP errors arrive as
 * [SupabaseResult.Failure] (e.g. `404` for a missing namespace/table) rather than thrown.
 */
public interface AnalyticsCatalogClient {
    /**
     * Loads the raw Iceberg catalog config (`GET /v1/config`) for this bucket as a
     * [JsonObject]. The config carries the `defaults`/`overrides` property maps, including the
     * route `prefix` this client uses to address namespaces and tables. See [loadConfigTyped]
     * for the modeled form.
     */
    public suspend fun loadConfig(): SupabaseResult<JsonObject>

    /** Typed counterpart of [loadConfig], decoding the response into [IcebergCatalogConfig]. */
    public suspend fun loadConfigTyped(): SupabaseResult<IcebergCatalogConfig>

    /**
     * Lists namespaces under the catalog as a raw [JsonObject]. See [listNamespacesTyped] for
     * the modeled form including the resolved next-page token.
     *
     * @param parent restrict results to direct children of this namespace; null lists from the
     *   catalog root.
     * @param pageToken continuation token from a previous page's next-page token.
     * @param pageSize maximum number of namespaces to return in this page.
     */
    public suspend fun listNamespaces(
        parent: List<String>? = null,
        pageToken: String? = null,
        pageSize: Int? = null,
    ): SupabaseResult<JsonObject>

    /** Typed counterpart of [listNamespaces], decoding into [IcebergNamespaceListResponse]. */
    public suspend fun listNamespacesTyped(
        parent: List<String>? = null,
        pageToken: String? = null,
        pageSize: Int? = null,
    ): SupabaseResult<IcebergNamespaceListResponse>

    /**
     * Creates [namespace] with optional [properties], returning the server's raw [JsonObject].
     * See [createNamespaceTyped] for the request-object form.
     *
     * @param namespace the multi-level namespace to create (e.g. `["analytics", "sales"]`).
     * @param properties initial namespace properties; omitted from the request body when empty.
     */
    public suspend fun createNamespace(
        namespace: List<String>,
        properties: Map<String, String> = emptyMap(),
    ): SupabaseResult<JsonObject>

    /** Typed counterpart of [createNamespace] taking [IcebergCreateNamespaceRequest]. */
    public suspend fun createNamespaceTyped(
        request: IcebergCreateNamespaceRequest,
    ): SupabaseResult<IcebergNamespaceMetadata>

    /**
     * Drops [namespace] from the catalog. Fails with [SupabaseResult.Failure] if the namespace
     * is missing or still contains tables.
     */
    public suspend fun dropNamespace(namespace: List<String>): SupabaseResult<Unit>

    /**
     * Loads [namespace]'s metadata (its property map) as a raw [JsonObject]. See
     * [loadNamespaceMetadataTyped] for the modeled form.
     */
    public suspend fun loadNamespaceMetadata(namespace: List<String>): SupabaseResult<JsonObject>

    /** Typed counterpart of [loadNamespaceMetadata], decoding into [IcebergNamespaceMetadata]. */
    public suspend fun loadNamespaceMetadataTyped(namespace: List<String>): SupabaseResult<IcebergNamespaceMetadata>

    /**
     * Adds/updates and/or removes properties on [namespace] in one call, returning the raw
     * [JsonObject] outcome. See [updateNamespacePropertiesTyped] for the modeled form.
     *
     * @param removals property keys to remove.
     * @param updates property keys to set or overwrite.
     */
    public suspend fun updateNamespaceProperties(
        namespace: List<String>,
        removals: List<String>? = null,
        updates: Map<String, String>? = null,
    ): SupabaseResult<JsonObject>

    /**
     * Typed counterpart of [updateNamespaceProperties]; the
     * [IcebergUpdateNamespacePropertiesResponse] reports which keys were removed, updated, and
     * missing.
     */
    public suspend fun updateNamespacePropertiesTyped(
        namespace: List<String>,
        request: IcebergUpdateNamespacePropertiesRequest,
    ): SupabaseResult<IcebergUpdateNamespacePropertiesResponse>

    /**
     * Lists the tables in [namespace] as a raw [JsonObject]. See [listTablesTyped] for the
     * modeled form.
     *
     * @param pageToken continuation token from a previous page's next-page token.
     * @param pageSize maximum number of table identifiers to return in this page.
     */
    public suspend fun listTables(
        namespace: List<String>,
        pageToken: String? = null,
        pageSize: Int? = null,
    ): SupabaseResult<JsonObject>

    /** Typed counterpart of [listTables], decoding into [IcebergTableListResponse]. */
    public suspend fun listTablesTyped(
        namespace: List<String>,
        pageToken: String? = null,
        pageSize: Int? = null,
    ): SupabaseResult<IcebergTableListResponse>

    /**
     * Creates a table in [namespace] from a raw Iceberg create-table [request] body, returning
     * the server's raw [JsonObject] (table metadata and access config). Use this when you need
     * spec fields not modeled by [IcebergTableCreateRequest]; otherwise prefer [createTableTyped].
     */
    public suspend fun createTable(
        namespace: List<String>,
        request: JsonObject,
    ): SupabaseResult<JsonObject>

    /** Typed counterpart of [createTable] taking [IcebergTableCreateRequest]. */
    public suspend fun createTableTyped(
        namespace: List<String>,
        request: IcebergTableCreateRequest,
    ): SupabaseResult<IcebergTableMetadataResponse>

    /**
     * Commits requirements/updates to the table [name] in [namespace] from a raw [request] body,
     * returning the server's raw [JsonObject]. This is the underlying table-commit operation;
     * [commitTable] is a semantically named alias over it. Prefer [updateTableTyped]-style typed
     * variants ([commitTableTyped]) for modeled requests.
     */
    public suspend fun updateTable(
        namespace: List<String>,
        name: String,
        request: JsonObject,
    ): SupabaseResult<JsonObject>

    /** Typed counterpart of [commitTable] taking [IcebergTableCommitRequest] (requirements + updates). */
    public suspend fun commitTableTyped(
        namespace: List<String>,
        name: String,
        request: IcebergTableCommitRequest,
    ): SupabaseResult<IcebergTableMetadataResponse>

    /**
     * Commits requirements/updates to the table [name] in [namespace] from a raw [request] body.
     * A semantically named alias for [updateTable] (the Iceberg commit-table endpoint); see
     * [commitTableTyped] for the modeled form.
     */
    public suspend fun commitTable(
        namespace: List<String>,
        name: String,
        request: JsonObject,
    ): SupabaseResult<JsonObject>

    /**
     * Drops the table [name] from [namespace].
     *
     * @param purge when true, asks the server to also delete the table's underlying data files
     *   (`purgeRequested=true`), not just the catalog entry.
     */
    public suspend fun dropTable(
        namespace: List<String>,
        name: String,
        purge: Boolean = false,
    ): SupabaseResult<Unit>

    /**
     * Loads the table [name] in [namespace] as a raw [JsonObject] (its current metadata). See
     * [loadTableTyped] for the modeled form.
     *
     * @param snapshots optional snapshot-loading mode passed through to the server (e.g. to
     *   request all snapshots vs. only referenced ones).
     */
    public suspend fun loadTable(
        namespace: List<String>,
        name: String,
        snapshots: String? = null,
    ): SupabaseResult<JsonObject>

    /** Typed counterpart of [loadTable], decoding into [IcebergTableMetadataResponse]. */
    public suspend fun loadTableTyped(
        namespace: List<String>,
        name: String,
        snapshots: String? = null,
    ): SupabaseResult<IcebergTableMetadataResponse>

    /**
     * Registers an existing table (one whose metadata file already lives in storage) into
     * [namespace] from a raw [request] body, returning the server's raw [JsonObject]. See
     * [registerTableTyped] for the modeled form.
     */
    public suspend fun registerTable(
        namespace: List<String>,
        request: JsonObject,
    ): SupabaseResult<JsonObject>

    /** Typed counterpart of [registerTable] taking [IcebergTableRegisterRequest] (name + metadata location). */
    public suspend fun registerTableTyped(
        namespace: List<String>,
        request: IcebergTableRegisterRequest,
    ): SupabaseResult<IcebergTableMetadataResponse>

    /**
     * Renames a table from a raw [request] body holding `source`/`destination` identifiers. See
     * [renameTableTyped] for the modeled form.
     */
    public suspend fun renameTable(request: JsonObject): SupabaseResult<Unit>

    /**
     * Typed counterpart of [renameTable], moving/renaming the table at [source] to [destination]
     * (which may live in a different namespace).
     */
    public suspend fun renameTableTyped(
        source: IcebergTableIdentifier,
        destination: IcebergTableIdentifier,
    ): SupabaseResult<Unit>
}

/**
 * Typed, Result-first client for Supabase Storage (`/storage/v1`): buckets, object
 * upload/download, listing, signed URLs, public/authenticated/render URL building, plus the
 * analytics (Iceberg) and S3-Vectors surfaces.
 *
 * Every suspending call returns a [SupabaseResult] rather than throwing — network and HTTP
 * errors (e.g. `404` for a missing object, `409` on an upsert conflict) arrive as
 * [SupabaseResult.Failure]; the non-suspending URL builders compute a string locally and never
 * hit the network. Obtain an instance from the storage accessor on `SupabaseClient`; the
 * implementation is internal.
 *
 * Object paths are bucket-relative keys (`folder/file.png`) and are URL-encoded for you,
 * preserving `/` as a path separator. Many list/URL operations also have bulk and
 * `vararg`/by-path convenience overloads as extension functions; see `StorageClientExt`.
 */
public interface StorageClient {
    /**
     * Lists the buckets visible to the current credentials (`GET /bucket`).
     *
     * @param limit maximum number of buckets to return.
     * @param offset number of buckets to skip, for paging.
     * @param sortColumn column to sort by (server-defined, e.g. `name`/`created_at`).
     * @param sortOrder ascending or descending order for [sortColumn].
     * @param search case-insensitive filter applied server-side to bucket names.
     */
    public suspend fun listBuckets(
        limit: Int? = null,
        offset: Int? = null,
        sortColumn: String? = null,
        sortOrder: SortOrder? = null,
        search: String? = null,
    ): SupabaseResult<List<Bucket>>

    /** Fetches a single bucket by [id]. Fails with `404` if no such bucket exists. */
    public suspend fun getBucket(id: String): SupabaseResult<Bucket>

    /**
     * Creates a bucket, returning the created [Bucket]. Fails (e.g. `409`) if [id] is already taken.
     *
     * @param id the bucket id (its stable identifier and URL segment).
     * @param name human-readable bucket name.
     * @param public when true, objects are readable via the public endpoint without a token.
     * @param fileSizeLimit optional per-object size cap in bytes; null leaves it unset.
     * @param allowedMimeTypes optional whitelist of accepted content types; null allows any.
     */
    public suspend fun createBucket(
        id: String,
        name: String,
        public: Boolean = false,
        fileSizeLimit: Long? = null,
        allowedMimeTypes: List<String>? = null,
    ): SupabaseResult<Bucket>

    /**
     * Updates the bucket [id]'s mutable attributes. Only non-null arguments are sent, so omitted
     * fields are left unchanged. See [createBucket] for the meaning of each attribute.
     */
    public suspend fun updateBucket(
        id: String,
        name: String? = null,
        public: Boolean? = null,
        fileSizeLimit: Long? = null,
        allowedMimeTypes: List<String>? = null,
    ): SupabaseResult<Bucket>

    /** Deletes the bucket [id]. The bucket must be empty; see [emptyBucket] to clear it first. */
    public suspend fun deleteBucket(id: String): SupabaseResult<Unit>

    /** Removes every object from the bucket [id] without deleting the bucket itself. */
    public suspend fun emptyBucket(id: String): SupabaseResult<Unit>

    /**
     * Uploads [data] to [path] in [bucket].
     *
     * When [metadata] is provided, it is Base64-encoded and sent in the
     * `x-metadata` request header so the server stores it as the object's user
     * metadata.
     */
    public suspend fun upload(
        bucket: String,
        path: String,
        data: ByteArray,
        contentType: String = "application/octet-stream",
        upsert: Boolean = false,
        cacheControl: Int? = null,
        metadata: JsonObject? = null,
    ): SupabaseResult<String>

    /**
     * Creates a resumable (TUS) upload handle. The upload is not started until
     * [ResumableUpload.await] is called. Pass [uploadUrl] (a previously captured
     * [ResumableUpload.uploadUrl]) to resume an interrupted upload instead of
     * starting a new one.
     *
     * [chunkSize] must be positive (a non-positive value throws
     * [IllegalArgumentException] eagerly, before any request is made). The TUS
     * server additionally requires every chunk except the last to be a multiple of
     * [RESUMABLE_DEFAULT_CHUNK_SIZE] (6 MB); keep the default unless you have a
     * reason to change it, as a non-conforming size is rejected server-side.
     *
     * When [metadata] is provided, it is Base64-encoded and sent as the `metadata`
     * entry of the TUS `Upload-Metadata` header so the server stores it as the
     * object's user metadata (the same encoding as the non-resumable [upload]).
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
        metadata: JsonObject? = null,
    ): ResumableUpload

    /**
     * Replaces the object at [path] in [bucket] with [data].
     *
     * When [metadata] is provided, it is Base64-encoded and sent in the
     * `x-metadata` request header so the server stores it as the object's user
     * metadata.
     */
    public suspend fun update(
        bucket: String,
        path: String,
        data: ByteArray,
        contentType: String = "application/octet-stream",
        upsert: Boolean = false,
        cacheControl: Int? = null,
        metadata: JsonObject? = null,
    ): SupabaseResult<String>

    /**
     * Downloads an object's body as a UTF-8 string from the authenticated endpoint
     * (`/object/authenticated`). Only safe for text content — for binary data use
     * [downloadBytes], which does not decode the bytes.
     */
    public suspend fun download(bucket: String, path: String): SupabaseResult<String>

    /** Like [download] but reads from the public endpoint (`/object/public`); no token required. */
    public suspend fun downloadPublic(bucket: String, path: String): SupabaseResult<String>

    /**
     * Downloads an object's raw bytes from the authenticated endpoint. Prefer
     * this over [download] for any non-text content (images, PDFs, archives):
     * [download] decodes the body as UTF-8 text, which corrupts binary data.
     * An empty (zero-byte) object is returned as an empty [ByteArray], not a
     * failure.
     */
    public suspend fun downloadBytes(
        bucket: String,
        path: String,
        download: Boolean = false,
        fileName: String? = null,
    ): SupabaseResult<ByteArray>

    /** Binary download from the public endpoint. See [downloadBytes]. */
    public suspend fun downloadPublicBytes(
        bucket: String,
        path: String,
        download: Boolean = false,
        fileName: String? = null,
    ): SupabaseResult<ByteArray>

    /**
     * Downloads a transformed image's raw bytes from the authenticated render
     * endpoint (`/render/image/authenticated`). [transform] controls the
     * server-side resize/format/quality; the same options accepted by
     * [getAuthenticatedRenderUrl]. See [downloadBytes] for the binary-vs-text
     * rationale.
     */
    public suspend fun downloadBytes(
        bucket: String,
        path: String,
        transform: ImageTransformOptions,
        download: Boolean = false,
        fileName: String? = null,
    ): SupabaseResult<ByteArray>

    /**
     * Downloads a transformed image's raw bytes from the public render endpoint
     * (`/render/image/public`). See [downloadBytes] with [transform].
     */
    public suspend fun downloadPublicBytes(
        bucket: String,
        path: String,
        transform: ImageTransformOptions,
        download: Boolean = false,
        fileName: String? = null,
    ): SupabaseResult<ByteArray>

    /**
     * Downloads an object as a UTF-8 string from the authenticated endpoint, with control over
     * the `download` query param so the server can suggest "save as" instead of inline display.
     * Text-only; see [downloadBytes] for binary content.
     *
     * @param download when true, sends the `?download` query so the server marks the response
     *   as an attachment.
     * @param fileName optional file name suggested for the saved attachment (`?download=<name>`).
     */
    public suspend fun download(
        bucket: String,
        path: String,
        download: Boolean = true,
        fileName: String? = null,
    ): SupabaseResult<String>

    /** Like [download] (with [download]/[fileName]) but from the public endpoint. */
    public suspend fun downloadPublic(
        bucket: String,
        path: String,
        download: Boolean = true,
        fileName: String? = null,
    ): SupabaseResult<String>

    /**
     * Fetches an object's metadata ([FileObject]) without its body, via the authenticated
     * info endpoint (`/object/info`). Fails with `404` if the object does not exist; see
     * [exists] for a boolean check that swallows the not-found case.
     */
    public suspend fun info(bucket: String, path: String): SupabaseResult<FileObject>

    /** Like [info] but from the public info endpoint (`/object/info/public`). */
    public suspend fun infoPublic(bucket: String, path: String): SupabaseResult<FileObject>

    /**
     * Reports whether an object exists, via [info]. A `404` is mapped to `Success(false)`; any
     * other failure (auth, network) is still surfaced as [SupabaseResult.Failure].
     */
    public suspend fun exists(bucket: String, path: String): SupabaseResult<Boolean>

    /** Like [exists] but probes the public endpoint via [infoPublic]. */
    public suspend fun existsPublic(bucket: String, path: String): SupabaseResult<Boolean>

    /**
     * Lists objects and folders under [prefix] in [bucket] (`POST /object/list`). This is the
     * offset-paged listing; for cursor-based paging with explicit folder/object separation use
     * [listV2].
     *
     * @param prefix folder path to list within; empty lists the bucket root.
     * @param limit maximum number of entries to return.
     * @param offset number of entries to skip, for paging.
     * @param sortBy column to sort by; null leaves the server default ordering.
     * @param sortOrder ascending or descending order, applied when [sortBy] is set.
     * @param search case-insensitive filter applied server-side to entry names.
     */
    public suspend fun list(
        bucket: String,
        prefix: String = "",
        limit: Int = 100,
        offset: Int = 0,
        sortBy: String? = null,
        sortOrder: SortOrder = SortOrder.ASC,
        search: String? = null,
    ): SupabaseResult<List<FileObject>>

    /**
     * Cursor-paged object listing (`POST /object/list-v2`) returning an [ObjectListV2Result] that
     * separates folders from objects and carries a `nextCursor`/`hasNext` for continuation.
     * Prefer this over [list] for large buckets or delimiter-style folder navigation.
     *
     * @param prefix folder path to list within; null lists the bucket root.
     * @param cursor continuation cursor from a previous result's `nextCursor`.
     * @param limit maximum number of entries to return in this page.
     * @param withDelimiter when true, collapse nested keys into folder entries instead of
     *   returning every descendant object.
     * @param sortBy column to sort by; null leaves the server default ordering.
     * @param sortOrder ascending or descending order, applied when [sortBy] is set.
     */
    public suspend fun listV2(
        bucket: String,
        prefix: String? = null,
        cursor: String? = null,
        limit: Int? = null,
        withDelimiter: Boolean? = null,
        sortBy: String? = null,
        sortOrder: SortOrder = SortOrder.ASC,
    ): SupabaseResult<ObjectListV2Result>

    /**
     * Moves (renames) the object at [fromPath] to [toPath] (`POST /object/move`), optionally
     * across buckets. Unlike [copy], the source no longer exists afterward.
     *
     * @param destinationBucket target bucket when moving across buckets; null moves within [bucket].
     */
    public suspend fun move(
        bucket: String,
        fromPath: String,
        toPath: String,
        destinationBucket: String? = null,
    ): SupabaseResult<Unit>

    /**
     * Deletes a single object at [path] in [bucket]. For batch deletes prefer [remove], which
     * removes many paths in one request.
     */
    public suspend fun deleteObject(
        bucket: String,
        path: String,
    ): SupabaseResult<Unit>

    /**
     * Copies the object at [fromPath] to [toPath] (`POST /object/copy`), optionally across
     * buckets; the source is left in place (contrast [move]).
     *
     * @param destinationBucket target bucket when copying across buckets; null copies within [bucket].
     * @param copyMetadata when true, also copy the object's user metadata to the destination.
     */
    public suspend fun copy(
        bucket: String,
        fromPath: String,
        toPath: String,
        destinationBucket: String? = null,
        copyMetadata: Boolean? = null,
    ): SupabaseResult<Unit>

    /**
     * Deletes multiple objects by [paths] in one request. Use [removeWithResult] instead when you
     * need the list of objects the server reports as removed.
     */
    public suspend fun remove(bucket: String, paths: List<String>): SupabaseResult<Unit>

    /**
     * Deletes multiple objects by [paths] and returns the [FileObject] entries the server reports
     * as removed — the same operation as [remove] but surfacing the deleted set.
     */
    public suspend fun removeWithResult(bucket: String, paths: List<String>): SupabaseResult<List<FileObject>>

    /**
     * Creates a time-limited signed download URL for a private object (`POST /object/sign`),
     * letting an unauthenticated client read it until the URL expires.
     *
     * @param expiresIn lifetime of the signed URL in seconds.
     * @param download when true, the returned URL carries the `download` param so the object is
     *   served as an attachment.
     * @param fileName optional file name suggested for the saved attachment.
     * @param transform optional image transform to bake into the signed URL (render endpoint).
     */
    public suspend fun createSignedUrl(
        bucket: String,
        path: String,
        expiresIn: Long,
        download: Boolean = false,
        fileName: String? = null,
        transform: ImageTransformOptions? = null,
    ): SupabaseResult<String>

    /**
     * Batch variant of [createSignedUrl]: signs every path in [paths] in one request and returns
     * the URLs in the same order. See [createSignedUrl] for the parameter meanings.
     */
    public suspend fun createSignedUrls(
        bucket: String,
        paths: List<String>,
        expiresIn: Long,
        download: Boolean = false,
        fileName: String? = null,
    ): SupabaseResult<List<String>>

    /**
     * Creates a signed *upload* token (`POST /object/upload/sign`) so a client without storage
     * credentials can later upload to [path] via [uploadToSignedUrl]. Returns just the token; use
     * [createUploadSignedUrlWithPath] when you also need the upload URL and bound path.
     *
     * @param upsert when true, the resulting token permits overwriting an existing object.
     */
    public suspend fun createUploadSignedUrl(
        bucket: String,
        path: String,
        upsert: Boolean = false,
    ): SupabaseResult<String>

    /**
     * Like [createUploadSignedUrl] but returns the full [UploadSignedUrl] (absolute upload URL,
     * token, and the server-bound path) instead of only the token.
     */
    public suspend fun createUploadSignedUrlWithPath(
        bucket: String,
        path: String,
        upsert: Boolean = false,
    ): SupabaseResult<UploadSignedUrl>

    /**
     * Builds the signed download URL for a previously issued signed-URL [token] locally, without a
     * network call. Useful when you already hold the token and want the addressable URL.
     *
     * @param download when true, append the `download` param so the object is served as an attachment.
     * @param fileName optional file name suggested for the saved attachment.
     */
    public fun getSignedDownloadUrl(
        bucket: String,
        path: String,
        token: String,
        download: Boolean = false,
        fileName: String? = null,
    ): String

    /**
     * Uploads [data] to a pre-signed upload URL identified by [token].
     *
     * When [metadata] is provided, it is Base64-encoded and sent in the
     * `x-metadata` request header so the server stores it as the object's user
     * metadata.
     */
    public suspend fun uploadToSignedUrl(
        bucket: String,
        path: String,
        token: String,
        data: ByteArray,
        contentType: String = "application/octet-stream",
        upsert: Boolean = false,
        cacheControl: Int? = null,
        metadata: JsonObject? = null,
    ): SupabaseResult<String>

    /**
     * Builds the public URL (`/object/public`) for [path] locally — no network call and no token.
     * Only resolves for objects in a public bucket. Pass [transform] to target the render endpoint
     * with a server-side image transform.
     */
    public fun getPublicUrl(
        bucket: String,
        path: String,
        transform: ImageTransformOptions? = null,
    ): String

    /**
     * Builds the authenticated URL (`/object/authenticated`) for [path] locally. The URL still
     * requires valid credentials when fetched; this only composes the address. Pass [transform] to
     * target the render endpoint with a server-side image transform. See [getPublicUrl] for public
     * buckets.
     */
    public fun getAuthenticatedUrl(
        bucket: String,
        path: String,
        transform: ImageTransformOptions? = null,
    ): String

    /**
     * [getPublicUrl] with download control.
     *
     * @param download when true, append the `download` param so the object is served as an attachment.
     * @param fileName optional file name suggested for the saved attachment.
     */
    public fun getPublicUrl(
        bucket: String,
        path: String,
        download: Boolean = false,
        fileName: String? = null,
        transform: ImageTransformOptions? = null,
    ): String

    /**
     * [getAuthenticatedUrl] with download control.
     *
     * @param download when true, append the `download` param so the object is served as an attachment.
     * @param fileName optional file name suggested for the saved attachment.
     */
    public fun getAuthenticatedUrl(
        bucket: String,
        path: String,
        download: Boolean = false,
        fileName: String? = null,
        transform: ImageTransformOptions? = null,
    ): String

    /**
     * Builds the public render/transform URL (`/render/image/public`) for [path] locally, applying
     * [transform] (resize/format/quality) as query params. For private buckets use
     * [getAuthenticatedRenderUrl] or a signed render URL.
     */
    public fun getPublicRenderUrl(
        bucket: String,
        path: String,
        transform: ImageTransformOptions? = null,
    ): String

    /**
     * Builds the authenticated render/transform URL (`/render/image/authenticated`) for [path]
     * locally, applying [transform]. The URL still requires credentials when fetched.
     */
    public fun getAuthenticatedRenderUrl(
        bucket: String,
        path: String,
        transform: ImageTransformOptions? = null,
    ): String

    /**
     * Creates an analytics (Iceberg) bucket named [name] (`POST /iceberg/bucket`). Use
     * [analyticsCatalog] afterward to manage its namespaces and tables.
     */
    public suspend fun createAnalyticsBucket(name: String): SupabaseResult<AnalyticsBucket>

    /**
     * Lists analytics (Iceberg) buckets. Mirrors [listBuckets]'s paging/sort/search semantics but
     * over the analytics-bucket endpoint.
     */
    public suspend fun listAnalyticsBuckets(
        limit: Int? = null,
        offset: Int? = null,
        sortColumn: String? = null,
        sortOrder: SortOrder? = null,
        search: String? = null,
    ): SupabaseResult<List<AnalyticsBucket>>

    /** Deletes the analytics (Iceberg) bucket [name]. */
    public suspend fun deleteAnalyticsBucket(name: String): SupabaseResult<Unit>

    /**
     * Returns an [AnalyticsCatalogClient] scoped to the analytics bucket [bucketName] for managing
     * its Iceberg namespaces and tables. Validates [bucketName] eagerly and throws
     * `IllegalArgumentException` if it is blank or contains characters outside `[A-Za-z0-9._-]`.
     */
    public fun analyticsCatalog(bucketName: String): AnalyticsCatalogClient

    /** Creates an S3-Vectors bucket named [vectorBucketName] (`POST /vector/CreateVectorBucket`). */
    public suspend fun createVectorBucket(vectorBucketName: String): SupabaseResult<Unit>

    /** Fetches the S3-Vectors bucket [vectorBucketName]. Fails if it does not exist. */
    public suspend fun getVectorBucket(vectorBucketName: String): SupabaseResult<VectorBucket>

    /**
     * Lists S3-Vectors buckets.
     *
     * @param prefix restrict results to bucket names starting with this prefix.
     * @param maxResults maximum number of buckets to return in this page.
     * @param nextToken continuation token from a previous response's `nextToken`.
     */
    public suspend fun listVectorBuckets(
        prefix: String? = null,
        maxResults: Int? = null,
        nextToken: String? = null,
    ): SupabaseResult<VectorBucketListResponse>

    /** Deletes the S3-Vectors bucket [vectorBucketName]. */
    public suspend fun deleteVectorBucket(vectorBucketName: String): SupabaseResult<Unit>

    /**
     * Creates a vector index [indexName] inside [vectorBucketName] (`POST /vector/CreateIndex`).
     *
     * @param dataType element type of the stored vectors (e.g. float32).
     * @param dimension fixed dimensionality every stored vector must match.
     * @param distanceMetric similarity metric used for queries (cosine/euclidean/dot-product).
     * @param metadataConfiguration optional declaration of non-filterable metadata keys.
     */
    public suspend fun createVectorIndex(
        vectorBucketName: String,
        indexName: String,
        dataType: VectorDataType,
        dimension: Int,
        distanceMetric: VectorDistanceMetric,
        metadataConfiguration: VectorMetadataConfiguration? = null,
    ): SupabaseResult<Unit>

    /** Fetches the vector index [indexName] in [vectorBucketName], including its dimension and metric. */
    public suspend fun getVectorIndex(
        vectorBucketName: String,
        indexName: String,
    ): SupabaseResult<VectorIndex>

    /**
     * Lists the vector indexes in [vectorBucketName].
     *
     * @param prefix restrict results to index names starting with this prefix.
     * @param maxResults maximum number of indexes to return in this page.
     * @param nextToken continuation token from a previous response's `nextToken`.
     */
    public suspend fun listVectorIndexes(
        vectorBucketName: String,
        prefix: String? = null,
        maxResults: Int? = null,
        nextToken: String? = null,
    ): SupabaseResult<VectorIndexListResponse>

    /** Deletes the vector index [indexName] from [vectorBucketName]. */
    public suspend fun deleteVectorIndex(
        vectorBucketName: String,
        indexName: String,
    ): SupabaseResult<Unit>

    /**
     * Inserts or overwrites [vectors] in the index [indexName] (`POST /vector/PutVectors`). The
     * batch must contain between 1 and 500 vectors; an out-of-range size throws
     * `IllegalArgumentException` before any request is sent.
     */
    public suspend fun putVectors(
        vectorBucketName: String,
        indexName: String,
        vectors: List<VectorObject>,
    ): SupabaseResult<Unit>

    /**
     * Fetches vectors by [keys] from the index [indexName].
     *
     * @param returnData when true, include each vector's raw data in the response.
     * @param returnMetadata when true, include each vector's metadata in the response.
     */
    public suspend fun getVectors(
        vectorBucketName: String,
        indexName: String,
        keys: List<String>,
        returnData: Boolean? = null,
        returnMetadata: Boolean? = null,
    ): SupabaseResult<List<VectorMatch>>

    /**
     * Lists vectors in the index [indexName], optionally sharded for parallel scans.
     *
     * @param maxResults maximum number of vectors to return in this page.
     * @param nextToken continuation token from a previous response's `nextToken`.
     * @param returnData when true, include each vector's raw data.
     * @param returnMetadata when true, include each vector's metadata.
     * @param segmentCount split the index into this many segments (1..16) for parallel listing;
     *   out-of-range values throw `IllegalArgumentException`.
     * @param segmentIndex which segment (0 until [segmentCount]) this call scans.
     */
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

    /**
     * Runs a nearest-neighbor search against the index [indexName] for [queryVector]
     * (`POST /vector/QueryVectors`).
     *
     * @param topK number of closest matches to return.
     * @param filter optional metadata filter expression to restrict candidates.
     * @param returnDistance when true, include each match's distance score.
     * @param returnMetadata when true, include each match's metadata.
     */
    public suspend fun queryVectors(
        vectorBucketName: String,
        indexName: String,
        queryVector: VectorData,
        topK: Int? = null,
        filter: JsonObject? = null,
        returnDistance: Boolean? = null,
        returnMetadata: Boolean? = null,
    ): SupabaseResult<VectorQueryResponse>

    /**
     * Deletes vectors by [keys] from the index [indexName]. The batch must contain between 1 and
     * 500 keys; an out-of-range size throws `IllegalArgumentException` before any request is sent.
     */
    public suspend fun deleteVectors(
        vectorBucketName: String,
        indexName: String,
        keys: List<String>,
    ): SupabaseResult<Unit>
}

/**
 * A signed upload target returned by [StorageClient.createUploadSignedUrlWithPath]: the absolute
 * upload [url] and the [token] a client passes to [StorageClient.uploadToSignedUrl].
 *
 * @property url the absolute URL to upload to.
 * @property token the signed token authorizing the upload.
 * @property path the object path the server bound to this signed-upload token, when returned.
 */
public data class UploadSignedUrl(
    public val url: String,
    public val token: String,
    public val path: String? = null,
)

/**
 * Server-side image transform options applied by the render endpoints and by signed/public render
 * URLs. Each field maps to a render query param; null fields are omitted, leaving the server
 * default. Used wherever a `transform` argument appears on [StorageClient].
 *
 * @property width target width in pixels.
 * @property height target height in pixels.
 * @property resize how the image is fitted to [width]/[height]; see [ResizeMode].
 * @property format output format (e.g. `origin`, `webp`); server-defined values.
 * @property quality output quality, typically 1..100 for lossy formats.
 */
public data class ImageTransformOptions(
    public val width: Int? = null,
    public val height: Int? = null,
    public val resize: ResizeMode? = null,
    public val format: String? = null,
    public val quality: Int? = null,
)

/**
 * How a transformed image is fitted to the requested width/height. The [value] is the literal
 * sent to the render endpoint.
 */
public enum class ResizeMode(
    public val value: String,
) {
    /** Scale to fill the box, cropping any overflow (preserves aspect ratio). */
    COVER("cover"),

    /** Scale to fit entirely within the box, leaving letterboxing (preserves aspect ratio). */
    CONTAIN("contain"),

    /** Stretch to exactly the box, ignoring aspect ratio. */
    FILL("fill"),
}

/**
 * Sort direction for listing operations. The [value] is the literal sent to the storage API.
 */
public enum class SortOrder(
    public val value: String,
) {
    /** Ascending order. */
    ASC("asc"),

    /** Descending order. */
    DESC("desc"),
}
