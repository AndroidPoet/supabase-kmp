package io.github.androidpoet.supabase.storage
import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.client.defaultJson
import io.github.androidpoet.supabase.client.deserialize
import io.github.androidpoet.supabase.core.result.SupabaseErrorCategory
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.core.result.category
import io.github.androidpoet.supabase.core.result.map
import io.github.androidpoet.supabase.storage.models.AnalyticsBucket
import io.github.androidpoet.supabase.storage.models.AnalyticsBucketCreateRequest
import io.github.androidpoet.supabase.storage.models.Bucket
import io.github.androidpoet.supabase.storage.models.CreateBucketRequest
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
import io.github.androidpoet.supabase.storage.models.IcebergTableRenameRequest
import io.github.androidpoet.supabase.storage.models.IcebergUpdateNamespacePropertiesRequest
import io.github.androidpoet.supabase.storage.models.IcebergUpdateNamespacePropertiesResponse
import io.github.androidpoet.supabase.storage.models.MoveRequest
import io.github.androidpoet.supabase.storage.models.ObjectListRequest
import io.github.androidpoet.supabase.storage.models.ObjectListV2Request
import io.github.androidpoet.supabase.storage.models.ObjectListV2Result
import io.github.androidpoet.supabase.storage.models.ObjectSortByRequest
import io.github.androidpoet.supabase.storage.models.SignedUrlItemResponse
import io.github.androidpoet.supabase.storage.models.SignedUrlRequest
import io.github.androidpoet.supabase.storage.models.SignedUrlResponse
import io.github.androidpoet.supabase.storage.models.SignedUrlTransformRequest
import io.github.androidpoet.supabase.storage.models.SignedUrlsRequest
import io.github.androidpoet.supabase.storage.models.UpdateBucketRequest
import io.github.androidpoet.supabase.storage.models.UploadSignedUrlResponse
import io.github.androidpoet.supabase.storage.models.VectorBucket
import io.github.androidpoet.supabase.storage.models.VectorBucketCreateRequest
import io.github.androidpoet.supabase.storage.models.VectorBucketListRequest
import io.github.androidpoet.supabase.storage.models.VectorBucketListResponse
import io.github.androidpoet.supabase.storage.models.VectorBucketRequest
import io.github.androidpoet.supabase.storage.models.VectorBucketResponse
import io.github.androidpoet.supabase.storage.models.VectorData
import io.github.androidpoet.supabase.storage.models.VectorDataType
import io.github.androidpoet.supabase.storage.models.VectorDeleteRequest
import io.github.androidpoet.supabase.storage.models.VectorDistanceMetric
import io.github.androidpoet.supabase.storage.models.VectorGetRequest
import io.github.androidpoet.supabase.storage.models.VectorIndex
import io.github.androidpoet.supabase.storage.models.VectorIndexCreateRequest
import io.github.androidpoet.supabase.storage.models.VectorIndexListRequest
import io.github.androidpoet.supabase.storage.models.VectorIndexListResponse
import io.github.androidpoet.supabase.storage.models.VectorIndexRequest
import io.github.androidpoet.supabase.storage.models.VectorIndexResponse
import io.github.androidpoet.supabase.storage.models.VectorListRequest
import io.github.androidpoet.supabase.storage.models.VectorListResponse
import io.github.androidpoet.supabase.storage.models.VectorMatch
import io.github.androidpoet.supabase.storage.models.VectorMetadataConfiguration
import io.github.androidpoet.supabase.storage.models.VectorObject
import io.github.androidpoet.supabase.storage.models.VectorPutRequest
import io.github.androidpoet.supabase.storage.models.VectorQueryRequest
import io.github.androidpoet.supabase.storage.models.VectorQueryResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal class StorageClientImpl(
    private val client: SupabaseClient,
) : StorageClient {
    override suspend fun listBuckets(
        limit: Int?,
        offset: Int?,
        sortColumn: String?,
        sortOrder: SortOrder?,
        search: String?,
    ): SupabaseResult<List<Bucket>> {
        val query =
            buildList {
                limit?.let { add("limit" to it.toString()) }
                offset?.let { add("offset" to it.toString()) }
                sortColumn?.let { add("sortColumn" to it) }
                sortOrder?.let { add("sortOrder" to it.value) }
                search?.let { add("search" to it) }
            }
        return client.get(StoragePaths.BUCKET, queryParams = query).deserialize()
    }

    override suspend fun getBucket(id: String): SupabaseResult<Bucket> =
        client.get("${StoragePaths.BUCKET}/$id").deserialize()

    override suspend fun createBucket(
        id: String,
        name: String,
        public: Boolean,
        fileSizeLimit: Long?,
        allowedMimeTypes: List<String>?,
    ): SupabaseResult<Bucket> {
        val body =
            defaultJson.encodeToString(
                CreateBucketRequest(
                    id = id,
                    name = name,
                    public = public,
                    fileSizeLimit = fileSizeLimit,
                    allowedMimeTypes = allowedMimeTypes,
                ),
            )
        return client.post(StoragePaths.BUCKET, body = body).deserialize()
    }

    override suspend fun updateBucket(
        id: String,
        name: String?,
        public: Boolean?,
        fileSizeLimit: Long?,
        allowedMimeTypes: List<String>?,
    ): SupabaseResult<Bucket> {
        val body =
            defaultJson.encodeToString(
                UpdateBucketRequest(
                    name = name,
                    public = public,
                    fileSizeLimit = fileSizeLimit,
                    allowedMimeTypes = allowedMimeTypes,
                ),
            )
        return client.put("${StoragePaths.BUCKET}/$id", body = body).deserialize()
    }

    override suspend fun deleteBucket(id: String): SupabaseResult<Unit> =
        client.delete("${StoragePaths.BUCKET}/$id").map { }

    override suspend fun emptyBucket(id: String): SupabaseResult<Unit> =
        client.post("${StoragePaths.BUCKET}/$id/empty").map { }

    override suspend fun upload(
        bucket: String,
        path: String,
        data: ByteArray,
        contentType: String,
        upsert: Boolean,
        cacheControl: Int?,
    ): SupabaseResult<String> {
        val headers =
            buildMap {
                if (upsert) put("x-upsert", "true")
            }
        val endpoint = withCacheControl("${StoragePaths.OBJECT}/${objectRef(bucket, path)}", cacheControl)
        return client.postRaw(
            url = endpoint,
            body = data,
            contentType = contentType,
            headers = headers,
        )
    }

    override suspend fun update(
        bucket: String,
        path: String,
        data: ByteArray,
        contentType: String,
        upsert: Boolean,
        cacheControl: Int?,
    ): SupabaseResult<String> {
        val headers =
            buildMap {
                if (upsert) put("x-upsert", "true")
            }
        val endpoint = withCacheControl("${StoragePaths.OBJECT}/${objectRef(bucket, path)}", cacheControl)
        return client.putRaw(
            url = endpoint,
            body = data,
            contentType = contentType,
            headers = headers,
        )
    }

    override fun createResumableUpload(
        bucket: String,
        path: String,
        data: ByteArray,
        contentType: String,
        upsert: Boolean,
        cacheControl: Int?,
        chunkSize: Int,
        uploadUrl: String?,
    ): ResumableUpload =
        ResumableUploadImpl(
            client = client,
            bucket = bucket,
            path = path,
            data = data,
            contentType = contentType,
            upsert = upsert,
            cacheControl = cacheControl,
            chunkSize = chunkSize,
            initialUploadUrl = uploadUrl,
        )

    override suspend fun download(bucket: String, path: String): SupabaseResult<String> =
        client.get("${StoragePaths.OBJECT_AUTHENTICATED}/${objectRef(bucket, path)}")

    override suspend fun downloadPublic(bucket: String, path: String): SupabaseResult<String> =
        client.get("${StoragePaths.OBJECT_PUBLIC}/${objectRef(bucket, path)}")

    override suspend fun download(
        bucket: String,
        path: String,
        download: Boolean,
        fileName: String?,
    ): SupabaseResult<String> =
        client.get("${StoragePaths.OBJECT_AUTHENTICATED}/${objectRef(bucket, path)}${buildDownloadQuery(download, fileName)}")

    override suspend fun downloadPublic(
        bucket: String,
        path: String,
        download: Boolean,
        fileName: String?,
    ): SupabaseResult<String> =
        client.get("${StoragePaths.OBJECT_PUBLIC}/${objectRef(bucket, path)}${buildDownloadQuery(download, fileName)}")

    override suspend fun info(bucket: String, path: String): SupabaseResult<FileObject> =
        client.get("${StoragePaths.OBJECT_INFO}/${objectRef(bucket, path)}").deserialize()

    override suspend fun infoPublic(bucket: String, path: String): SupabaseResult<FileObject> =
        client.get("${StoragePaths.OBJECT_INFO_PUBLIC}/${objectRef(bucket, path)}").deserialize()

    override suspend fun exists(bucket: String, path: String): SupabaseResult<Boolean> =
        when (val result = info(bucket, path)) {
            is SupabaseResult.Success -> SupabaseResult.Success(true)
            is SupabaseResult.Failure ->
                if (result.error.category == SupabaseErrorCategory.NotFound) {
                    SupabaseResult.Success(false)
                } else {
                    result
                }
        }

    override suspend fun existsPublic(bucket: String, path: String): SupabaseResult<Boolean> =
        when (val result = infoPublic(bucket, path)) {
            is SupabaseResult.Success -> SupabaseResult.Success(true)
            is SupabaseResult.Failure ->
                if (result.error.category == SupabaseErrorCategory.NotFound) {
                    SupabaseResult.Success(false)
                } else {
                    result
                }
        }

    override suspend fun list(
        bucket: String,
        prefix: String,
        limit: Int,
        offset: Int,
        sortBy: String?,
        sortOrder: SortOrder,
        search: String?,
    ): SupabaseResult<List<FileObject>> {
        val body =
            defaultJson.encodeToString(
                ObjectListRequest(
                    prefix = prefix,
                    limit = limit,
                    offset = offset,
                    sortBy =
                        sortBy?.let {
                            ObjectSortByRequest(column = it, order = sortOrder.value)
                        },
                    search = search,
                ),
            )
        return client.post("${StoragePaths.OBJECT_LIST}/$bucket", body = body).deserialize()
    }

    override suspend fun listV2(
        bucket: String,
        prefix: String?,
        cursor: String?,
        limit: Int?,
        withDelimiter: Boolean?,
        sortBy: String?,
        sortOrder: SortOrder,
    ): SupabaseResult<ObjectListV2Result> {
        val body =
            defaultJson.encodeToString(
                ObjectListV2Request(
                    prefix = prefix,
                    cursor = cursor,
                    limit = limit,
                    withDelimiter = withDelimiter,
                    sortBy =
                        sortBy?.let {
                            ObjectSortByRequest(column = it, order = sortOrder.value)
                        },
                ),
            )
        return client.post("${StoragePaths.OBJECT_LIST_V2}/$bucket", body = body).deserialize()
    }

    override suspend fun move(
        bucket: String,
        fromPath: String,
        toPath: String,
        destinationBucket: String?,
    ): SupabaseResult<Unit> {
        val body =
            defaultJson.encodeToString(
                MoveRequest(
                    bucketId = bucket,
                    sourceKey = fromPath,
                    destinationKey = toPath,
                    destinationBucketId = destinationBucket,
                ),
            )
        return client.post(StoragePaths.OBJECT_MOVE, body = body).map { }
    }

    override suspend fun deleteObject(
        bucket: String,
        path: String,
    ): SupabaseResult<Unit> =
        client.delete("${StoragePaths.OBJECT}/${objectRef(bucket, path)}").map { }

    override suspend fun copy(
        bucket: String,
        fromPath: String,
        toPath: String,
        destinationBucket: String?,
        copyMetadata: Boolean?,
    ): SupabaseResult<Unit> {
        val body =
            defaultJson.encodeToString(
                MoveRequest(
                    bucketId = bucket,
                    sourceKey = fromPath,
                    destinationKey = toPath,
                    destinationBucketId = destinationBucket,
                    copyMetadata = copyMetadata,
                ),
            )
        return client.post(StoragePaths.OBJECT_COPY, body = body).map { }
    }

    override suspend fun remove(bucket: String, paths: List<String>): SupabaseResult<Unit> = removeWithResult(bucket, paths).map { }

    override suspend fun removeWithResult(
        bucket: String,
        paths: List<String>,
    ): SupabaseResult<List<FileObject>> {
        val body = defaultJson.encodeToString(mapOf("prefixes" to paths))
        return client
            .post(
                endpoint = "${StoragePaths.OBJECT_REMOVE}/$bucket",
                body = body,
            ).deserialize()
    }

    override suspend fun createSignedUrl(
        bucket: String,
        path: String,
        expiresIn: Long,
        download: Boolean,
        fileName: String?,
        transform: ImageTransformOptions?,
    ): SupabaseResult<String> {
        val body =
            defaultJson.encodeToString(
                SignedUrlRequest(
                    expiresIn = expiresIn,
                    transform = transform?.toRequest(),
                ),
            )
        return client
            .post("${StoragePaths.OBJECT_SIGN}/${objectRef(bucket, path)}", body = body)
            .deserialize<SignedUrlResponse>()
            .map { appendDownloadQuery(resolveStorageUrl(it.signedUrl), download, fileName) }
    }

    override suspend fun createSignedUrls(
        bucket: String,
        paths: List<String>,
        expiresIn: Long,
        download: Boolean,
        fileName: String?,
    ): SupabaseResult<List<String>> {
        val body = defaultJson.encodeToString(SignedUrlsRequest(paths = paths, expiresIn = expiresIn))
        return client
            .post("${StoragePaths.OBJECT_SIGN}/$bucket", body = body)
            .deserialize<List<SignedUrlItemResponse>>()
            .map { list -> list.map { appendDownloadQuery(resolveStorageUrl(it.signedUrl), download, fileName) } }
    }

    override suspend fun createUploadSignedUrl(
        bucket: String,
        path: String,
        upsert: Boolean,
    ): SupabaseResult<String> =
        createUploadSignedUrlWithPath(bucket, path, upsert)
            .map { it.token }

    override suspend fun createUploadSignedUrlWithPath(
        bucket: String,
        path: String,
        upsert: Boolean,
    ): SupabaseResult<UploadSignedUrl> =
        client
            .post(
                endpoint = "${StoragePaths.OBJECT_UPLOAD_SIGN}/${objectRef(bucket, path)}",
                headers = if (upsert) mapOf("x-upsert" to "true") else emptyMap(),
            ).deserialize<UploadSignedUrlResponse>()
            .map { UploadSignedUrl(url = resolveStorageUrl(it.url), token = it.token) }

    override suspend fun uploadToSignedUrl(
        bucket: String,
        path: String,
        token: String,
        data: ByteArray,
        contentType: String,
        upsert: Boolean,
        cacheControl: Int?,
    ): SupabaseResult<String> {
        val headers =
            buildMap {
                if (upsert) put("x-upsert", "true")
            }
        val qs =
            buildList {
                add("token=${encodeQueryComponent(token)}")
                cacheControl?.let { add("cacheControl=$it") }
            }.joinToString("&")
        return client.postRaw(
            url = "${StoragePaths.OBJECT_UPLOAD_SIGN}/${objectRef(bucket, path)}?$qs",
            body = data,
            contentType = contentType,
            headers = headers,
        )
    }

    override fun getSignedDownloadUrl(
        bucket: String,
        path: String,
        token: String,
        download: Boolean,
        fileName: String?,
    ): String {
        val downloadQuery = buildDownloadQuery(download, fileName)
        val join = if (downloadQuery.isEmpty()) "" else "&${downloadQuery.removePrefix("?")}"
        return "${client.projectUrl}${StoragePaths.OBJECT_SIGN}/${objectRef(bucket, path)}?token=${encodeQueryComponent(token)}$join"
    }

    override fun getPublicUrl(
        bucket: String,
        path: String,
        transform: ImageTransformOptions?,
    ): String =
        getPublicUrl(
            bucket = bucket,
            path = path,
            download = false,
            fileName = null,
            transform = transform,
        )

    override fun getAuthenticatedUrl(
        bucket: String,
        path: String,
        transform: ImageTransformOptions?,
    ): String =
        getAuthenticatedUrl(
            bucket = bucket,
            path = path,
            download = false,
            fileName = null,
            transform = transform,
        )

    override fun getPublicUrl(
        bucket: String,
        path: String,
        download: Boolean,
        fileName: String?,
        transform: ImageTransformOptions?,
    ): String {
        val base = "${client.projectUrl}${StoragePaths.OBJECT_PUBLIC}/${objectRef(bucket, path)}"
        val queryParts = mutableListOf<String>()
        val transformQuery = transform?.toQueryString()
        if (!transformQuery.isNullOrBlank()) {
            queryParts += transformQuery
        }
        val downloadQuery = buildDownloadQuery(download, fileName).removePrefix("?")
        if (downloadQuery.isNotBlank()) {
            queryParts += downloadQuery
        }
        return if (queryParts.isEmpty()) base else "$base?${queryParts.joinToString("&")}"
    }

    override fun getAuthenticatedUrl(
        bucket: String,
        path: String,
        download: Boolean,
        fileName: String?,
        transform: ImageTransformOptions?,
    ): String {
        val base = "${client.projectUrl}${StoragePaths.OBJECT_AUTHENTICATED}/${objectRef(bucket, path)}"
        val queryParts = mutableListOf<String>()
        val transformQuery = transform?.toQueryString()
        if (!transformQuery.isNullOrBlank()) {
            queryParts += transformQuery
        }
        val downloadQuery = buildDownloadQuery(download, fileName).removePrefix("?")
        if (downloadQuery.isNotBlank()) {
            queryParts += downloadQuery
        }
        return if (queryParts.isEmpty()) base else "$base?${queryParts.joinToString("&")}"
    }

    override fun getPublicRenderUrl(
        bucket: String,
        path: String,
        transform: ImageTransformOptions?,
    ): String {
        val base = "${client.projectUrl}${StoragePaths.RENDER_IMAGE_PUBLIC}/${objectRef(bucket, path)}"
        val transformQuery = transform?.toQueryString().orEmpty()
        return if (transformQuery.isBlank()) base else "$base?$transformQuery"
    }

    override fun getAuthenticatedRenderUrl(
        bucket: String,
        path: String,
        transform: ImageTransformOptions?,
    ): String {
        val base = "${client.projectUrl}${StoragePaths.RENDER_IMAGE_AUTHENTICATED}/${objectRef(bucket, path)}"
        val transformQuery = transform?.toQueryString().orEmpty()
        return if (transformQuery.isBlank()) base else "$base?$transformQuery"
    }

    override suspend fun createAnalyticsBucket(name: String): SupabaseResult<AnalyticsBucket> {
        val body = defaultJson.encodeToString(AnalyticsBucketCreateRequest(name = name))
        return client.post("${StoragePaths.ICEBERG}/bucket", body = body).deserialize()
    }

    override suspend fun listAnalyticsBuckets(
        limit: Int?,
        offset: Int?,
        sortColumn: String?,
        sortOrder: SortOrder?,
        search: String?,
    ): SupabaseResult<List<AnalyticsBucket>> {
        val query =
            buildList {
                limit?.let { add("limit" to it.toString()) }
                offset?.let { add("offset" to it.toString()) }
                sortColumn?.let { add("sortColumn" to it) }
                sortOrder?.let { add("sortOrder" to it.value) }
                search?.let { add("search" to it) }
            }
        val endpoint =
            if (query.isEmpty()) {
                "${StoragePaths.ICEBERG}/bucket"
            } else {
                "${StoragePaths.ICEBERG}/bucket?${query.joinToString("&") { (k, v) -> "${encodeQueryComponent(k)}=${encodeQueryComponent(v)}" }}"
            }
        return client.get(endpoint).deserialize()
    }

    override suspend fun deleteAnalyticsBucket(name: String): SupabaseResult<Unit> =
        client.delete("${StoragePaths.ICEBERG}/bucket/$name").map { }

    override fun analyticsCatalog(bucketName: String): AnalyticsCatalogClient {
        validateAnalyticsBucketName(bucketName)
        return AnalyticsCatalogClientImpl(client = client, bucketName = bucketName)
    }

    override suspend fun createVectorBucket(vectorBucketName: String): SupabaseResult<Unit> {
        val body = defaultJson.encodeToString(VectorBucketCreateRequest(vectorBucketName = vectorBucketName))
        return client.post("${StoragePaths.VECTOR}/CreateVectorBucket", body = body).map { }
    }

    override suspend fun getVectorBucket(vectorBucketName: String): SupabaseResult<VectorBucket> {
        val body = defaultJson.encodeToString(VectorBucketRequest(vectorBucketName = vectorBucketName))
        return client
            .post("${StoragePaths.VECTOR}/GetVectorBucket", body = body)
            .deserialize<VectorBucketResponse>()
            .map { it.vectorBucket }
    }

    override suspend fun listVectorBuckets(
        prefix: String?,
        maxResults: Int?,
        nextToken: String?,
    ): SupabaseResult<VectorBucketListResponse> {
        val body =
            defaultJson.encodeToString(
                VectorBucketListRequest(
                    prefix = prefix,
                    maxResults = maxResults,
                    nextToken = nextToken,
                ),
            )
        return client.post("${StoragePaths.VECTOR}/ListVectorBuckets", body = body).deserialize()
    }

    override suspend fun deleteVectorBucket(vectorBucketName: String): SupabaseResult<Unit> {
        val body = defaultJson.encodeToString(VectorBucketRequest(vectorBucketName = vectorBucketName))
        return client.post("${StoragePaths.VECTOR}/DeleteVectorBucket", body = body).map { }
    }

    override suspend fun createVectorIndex(
        vectorBucketName: String,
        indexName: String,
        dataType: VectorDataType,
        dimension: Int,
        distanceMetric: VectorDistanceMetric,
        metadataConfiguration: VectorMetadataConfiguration?,
    ): SupabaseResult<Unit> {
        val body =
            defaultJson.encodeToString(
                VectorIndexCreateRequest(
                    vectorBucketName = vectorBucketName,
                    indexName = indexName,
                    dataType = dataType,
                    dimension = dimension,
                    distanceMetric = distanceMetric,
                    metadataConfiguration = metadataConfiguration,
                ),
            )
        return client.post("${StoragePaths.VECTOR}/CreateIndex", body = body).map { }
    }

    override suspend fun getVectorIndex(
        vectorBucketName: String,
        indexName: String,
    ): SupabaseResult<VectorIndex> {
        val body =
            defaultJson.encodeToString(
                VectorIndexRequest(vectorBucketName = vectorBucketName, indexName = indexName),
            )
        return client
            .post("${StoragePaths.VECTOR}/GetIndex", body = body)
            .deserialize<VectorIndexResponse>()
            .map { it.index }
    }

    override suspend fun listVectorIndexes(
        vectorBucketName: String,
        prefix: String?,
        maxResults: Int?,
        nextToken: String?,
    ): SupabaseResult<VectorIndexListResponse> {
        val body =
            defaultJson.encodeToString(
                VectorIndexListRequest(
                    vectorBucketName = vectorBucketName,
                    prefix = prefix,
                    maxResults = maxResults,
                    nextToken = nextToken,
                ),
            )
        return client.post("${StoragePaths.VECTOR}/ListIndexes", body = body).deserialize()
    }

    override suspend fun deleteVectorIndex(
        vectorBucketName: String,
        indexName: String,
    ): SupabaseResult<Unit> {
        val body =
            defaultJson.encodeToString(
                VectorIndexRequest(vectorBucketName = vectorBucketName, indexName = indexName),
            )
        return client.post("${StoragePaths.VECTOR}/DeleteIndex", body = body).map { }
    }

    override suspend fun putVectors(
        vectorBucketName: String,
        indexName: String,
        vectors: List<VectorObject>,
    ): SupabaseResult<Unit> {
        require(vectors.size in 1..500) { "Vector batch size must be between 1 and 500 items" }
        val body =
            defaultJson.encodeToString(
                VectorPutRequest(vectorBucketName = vectorBucketName, indexName = indexName, vectors = vectors),
            )
        return client.post("${StoragePaths.VECTOR}/PutVectors", body = body).map { }
    }

    override suspend fun getVectors(
        vectorBucketName: String,
        indexName: String,
        keys: List<String>,
        returnData: Boolean?,
        returnMetadata: Boolean?,
    ): SupabaseResult<List<VectorMatch>> {
        val body =
            defaultJson.encodeToString(
                VectorGetRequest(
                    vectorBucketName = vectorBucketName,
                    indexName = indexName,
                    keys = keys,
                    returnData = returnData,
                    returnMetadata = returnMetadata,
                ),
            )
        return client
            .post("${StoragePaths.VECTOR}/GetVectors", body = body)
            .deserialize<VectorMatchesResponse>()
            .map { it.vectors }
    }

    override suspend fun listVectors(
        vectorBucketName: String,
        indexName: String,
        maxResults: Int?,
        nextToken: String?,
        returnData: Boolean?,
        returnMetadata: Boolean?,
        segmentCount: Int?,
        segmentIndex: Int?,
    ): SupabaseResult<VectorListResponse> {
        if (segmentCount != null) {
            require(segmentCount in 1..16) { "segmentCount must be between 1 and 16" }
            if (segmentIndex != null) {
                require(segmentIndex in 0 until segmentCount) {
                    "segmentIndex must be between 0 and ${segmentCount - 1}"
                }
            }
        }
        val body =
            defaultJson.encodeToString(
                VectorListRequest(
                    vectorBucketName = vectorBucketName,
                    indexName = indexName,
                    maxResults = maxResults,
                    nextToken = nextToken,
                    returnData = returnData,
                    returnMetadata = returnMetadata,
                    segmentCount = segmentCount,
                    segmentIndex = segmentIndex,
                ),
            )
        return client.post("${StoragePaths.VECTOR}/ListVectors", body = body).deserialize()
    }

    override suspend fun queryVectors(
        vectorBucketName: String,
        indexName: String,
        queryVector: VectorData,
        topK: Int?,
        filter: JsonObject?,
        returnDistance: Boolean?,
        returnMetadata: Boolean?,
    ): SupabaseResult<VectorQueryResponse> {
        val body =
            defaultJson.encodeToString(
                VectorQueryRequest(
                    vectorBucketName = vectorBucketName,
                    indexName = indexName,
                    queryVector = queryVector,
                    topK = topK,
                    filter = filter,
                    returnDistance = returnDistance,
                    returnMetadata = returnMetadata,
                ),
            )
        return client.post("${StoragePaths.VECTOR}/QueryVectors", body = body).deserialize()
    }

    override suspend fun deleteVectors(
        vectorBucketName: String,
        indexName: String,
        keys: List<String>,
    ): SupabaseResult<Unit> {
        require(keys.size in 1..500) { "Keys batch size must be between 1 and 500 items" }
        val body =
            defaultJson.encodeToString(
                VectorDeleteRequest(vectorBucketName = vectorBucketName, indexName = indexName, keys = keys),
            )
        return client.post("${StoragePaths.VECTOR}/DeleteVectors", body = body).map { }
    }

    private fun resolveStorageUrl(pathOrUrl: String): String =
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            pathOrUrl
        } else {
            "${client.projectUrl}${if (pathOrUrl.startsWith("/")) "" else "/"}$pathOrUrl"
        }

    private fun ImageTransformOptions.toRequest(): SignedUrlTransformRequest =
        SignedUrlTransformRequest(
            width = width,
            height = height,
            resize = resize?.name?.lowercase(),
            format = format,
            quality = quality,
        )

    private fun ImageTransformOptions.toQueryString(): String {
        val params =
            buildList {
                width?.let { add("width" to it.toString()) }
                height?.let { add("height" to it.toString()) }
                resize?.let { add("resize" to it.value) }
                format?.let { add("format" to it) }
                quality?.let { add("quality" to it.toString()) }
            }
        return params.joinToString("&") { (k, v) ->
            "${encodeQueryComponent(k)}=${encodeQueryComponent(v)}"
        }
    }

    private fun encodeQueryComponent(value: String): String {
        val bytes = value.encodeToByteArray()
        val out = StringBuilder(bytes.size)
        bytes.forEach { b ->
            val c = b.toInt().toChar()
            if (c.isAsciiUnreserved()) {
                out.append(c)
            } else {
                val v = b.toInt() and 0xff
                out.append('%')
                out.append("0123456789ABCDEF"[v ushr 4])
                out.append("0123456789ABCDEF"[v and 0x0f])
            }
        }
        return out.toString()
    }

    private fun Char.isAsciiUnreserved(): Boolean =
        (this in 'a'..'z') ||
            (this in 'A'..'Z') ||
            (this in '0'..'9') ||
            this == '-' ||
            this == '_' ||
            this == '.' ||
            this == '~'

    private fun buildDownloadQuery(download: Boolean, fileName: String?): String {
        if (!download) return ""
        if (fileName == null) return "?download"
        return "?download=${encodeQueryComponent(fileName)}"
    }

    private fun withCacheControl(endpoint: String, cacheControl: Int?): String {
        if (cacheControl == null) return endpoint
        return "$endpoint?cacheControl=$cacheControl"
    }

    private fun appendDownloadQuery(url: String, download: Boolean, fileName: String?): String {
        if (!download) return url
        val separator = if (url.contains("?")) "&" else "?"
        if (fileName == null) return "$url${separator}download"
        return "$url${separator}download=${encodeQueryComponent(fileName)}"
    }
}

@Serializable
private data class VectorMatchesResponse(
    val vectors: List<VectorMatch>,
)

internal class AnalyticsCatalogClientImpl(
    private val client: SupabaseClient,
    private val bucketName: String,
) : AnalyticsCatalogClient {
    private var resolvedPrefix: String? = null

    override suspend fun loadConfig(): SupabaseResult<JsonObject> =
        client
            .get(
                endpoint = "${StoragePaths.ICEBERG}/v1/config",
                queryParams = listOf("warehouse" to bucketName),
            ).toJsonObject()

    override suspend fun loadConfigTyped(): SupabaseResult<IcebergCatalogConfig> =
        client
            .get(
                endpoint = "${StoragePaths.ICEBERG}/v1/config",
                queryParams = listOf("warehouse" to bucketName),
            ).deserialize()

    override suspend fun listNamespaces(
        parent: List<String>?,
        pageToken: String?,
        pageSize: Int?,
    ): SupabaseResult<JsonObject> {
        val prefix = resolvePrefix()
        val query =
            buildList {
                parent?.let { add("parent" to it.joinToString("\u001F")) }
                pageToken?.let { add("pageToken" to it) }
                pageSize?.let { add("pageSize" to it.toString()) }
            }
        return client.get(appendQuery("$prefix/namespaces", query)).toJsonObject()
    }

    override suspend fun listNamespacesTyped(
        parent: List<String>?,
        pageToken: String?,
        pageSize: Int?,
    ): SupabaseResult<IcebergNamespaceListResponse> {
        val prefix = resolvePrefix()
        val query =
            buildList {
                parent?.let { add("parent" to it.joinToString("\u001F")) }
                pageToken?.let { add("pageToken" to it) }
                pageSize?.let { add("pageSize" to it.toString()) }
            }
        return client.get(appendQuery("$prefix/namespaces", query)).deserialize()
    }

    override suspend fun createNamespace(
        namespace: List<String>,
        properties: Map<String, String>,
    ): SupabaseResult<JsonObject> {
        val prefix = resolvePrefix()
        val body =
            defaultJson.encodeToString(
                buildJsonObject {
                    putJsonArray("namespace") {
                        namespace.forEach { add(JsonPrimitive(it)) }
                    }
                    if (properties.isNotEmpty()) {
                        putJsonObject("properties") {
                            properties.forEach { (key, value) -> put(key, value) }
                        }
                    }
                },
            )
        return client.post("$prefix/namespaces", body = body).toJsonObject()
    }

    override suspend fun createNamespaceTyped(
        request: IcebergCreateNamespaceRequest,
    ): SupabaseResult<IcebergNamespaceMetadata> =
        client
            .post(
                endpoint = "${resolvePrefix()}/namespaces",
                body = defaultJson.encodeToString(request),
            ).deserialize()

    override suspend fun dropNamespace(namespace: List<String>): SupabaseResult<Unit> =
        client.delete("${resolvePrefix()}/namespaces/${namespace.toIcebergPath()}").map { }

    override suspend fun loadNamespaceMetadata(namespace: List<String>): SupabaseResult<JsonObject> =
        client.get("${resolvePrefix()}/namespaces/${namespace.toIcebergPath()}").toJsonObject()

    override suspend fun loadNamespaceMetadataTyped(namespace: List<String>): SupabaseResult<IcebergNamespaceMetadata> =
        client.get("${resolvePrefix()}/namespaces/${namespace.toIcebergPath()}").deserialize()

    override suspend fun updateNamespaceProperties(
        namespace: List<String>,
        removals: List<String>?,
        updates: Map<String, String>?,
    ): SupabaseResult<JsonObject> {
        val body =
            defaultJson.encodeToString(
                buildJsonObject {
                    removals?.let {
                        putJsonArray("removals") {
                            it.forEach { value -> add(JsonPrimitive(value)) }
                        }
                    }
                    updates?.let {
                        putJsonObject("updates") {
                            it.forEach { (key, value) -> put(key, value) }
                        }
                    }
                },
            )
        return client
            .post(
                endpoint = "${resolvePrefix()}/namespaces/${namespace.toIcebergPath()}/properties",
                body = body,
            ).toJsonObject()
    }

    override suspend fun updateNamespacePropertiesTyped(
        namespace: List<String>,
        request: IcebergUpdateNamespacePropertiesRequest,
    ): SupabaseResult<IcebergUpdateNamespacePropertiesResponse> =
        client
            .post(
                endpoint = "${resolvePrefix()}/namespaces/${namespace.toIcebergPath()}/properties",
                body = defaultJson.encodeToString(request),
            ).deserialize()

    override suspend fun listTables(
        namespace: List<String>,
        pageToken: String?,
        pageSize: Int?,
    ): SupabaseResult<JsonObject> {
        val query =
            buildList {
                pageToken?.let { add("pageToken" to it) }
                pageSize?.let { add("pageSize" to it.toString()) }
            }
        return client
            .get(
                endpoint = appendQuery("${resolvePrefix()}/namespaces/${namespace.toIcebergPath()}/tables", query),
            ).toJsonObject()
    }

    override suspend fun listTablesTyped(
        namespace: List<String>,
        pageToken: String?,
        pageSize: Int?,
    ): SupabaseResult<IcebergTableListResponse> {
        val query =
            buildList {
                pageToken?.let { add("pageToken" to it) }
                pageSize?.let { add("pageSize" to it.toString()) }
            }
        return client
            .get(
                endpoint = appendQuery("${resolvePrefix()}/namespaces/${namespace.toIcebergPath()}/tables", query),
            ).deserialize()
    }

    override suspend fun createTable(namespace: List<String>, request: JsonObject): SupabaseResult<JsonObject> =
        client
            .post(
                endpoint = "${resolvePrefix()}/namespaces/${namespace.toIcebergPath()}/tables",
                body = defaultJson.encodeToString(request),
            ).toJsonObject()

    override suspend fun createTableTyped(
        namespace: List<String>,
        request: IcebergTableCreateRequest,
    ): SupabaseResult<IcebergTableMetadataResponse> =
        client
            .post(
                endpoint = "${resolvePrefix()}/namespaces/${namespace.toIcebergPath()}/tables",
                body = defaultJson.encodeToString(request),
            ).deserialize()

    override suspend fun updateTable(
        namespace: List<String>,
        name: String,
        request: JsonObject,
    ): SupabaseResult<JsonObject> =
        client
            .post(
                endpoint = "${resolvePrefix()}/namespaces/${namespace.toIcebergPath()}/tables/${encodePathSegment(name)}",
                body = defaultJson.encodeToString(request),
            ).toJsonObject()

    override suspend fun commitTable(
        namespace: List<String>,
        name: String,
        request: JsonObject,
    ): SupabaseResult<JsonObject> =
        updateTable(namespace = namespace, name = name, request = request)

    override suspend fun commitTableTyped(
        namespace: List<String>,
        name: String,
        request: IcebergTableCommitRequest,
    ): SupabaseResult<IcebergTableMetadataResponse> =
        client
            .post(
                endpoint = "${resolvePrefix()}/namespaces/${namespace.toIcebergPath()}/tables/${encodePathSegment(name)}",
                body = defaultJson.encodeToString(request),
            ).deserialize()

    override suspend fun dropTable(
        namespace: List<String>,
        name: String,
        purge: Boolean,
    ): SupabaseResult<Unit> =
        client
            .delete(
                endpoint = "${resolvePrefix()}/namespaces/${namespace.toIcebergPath()}/tables/${encodePathSegment(name)}?purgeRequested=$purge",
            ).map { }

    override suspend fun loadTable(
        namespace: List<String>,
        name: String,
        snapshots: String?,
    ): SupabaseResult<JsonObject> {
        val query =
            buildList {
                snapshots?.let { add("snapshots" to it) }
            }
        return client
            .get(
                endpoint =
                    appendQuery(
                        "${resolvePrefix()}/namespaces/${namespace.toIcebergPath()}/tables/${encodePathSegment(name)}",
                        query,
                    ),
            ).toJsonObject()
    }

    override suspend fun loadTableTyped(
        namespace: List<String>,
        name: String,
        snapshots: String?,
    ): SupabaseResult<IcebergTableMetadataResponse> {
        val query =
            buildList {
                snapshots?.let { add("snapshots" to it) }
            }
        return client
            .get(
                endpoint =
                    appendQuery(
                        "${resolvePrefix()}/namespaces/${namespace.toIcebergPath()}/tables/${encodePathSegment(name)}",
                        query,
                    ),
            ).deserialize()
    }

    override suspend fun registerTable(namespace: List<String>, request: JsonObject): SupabaseResult<JsonObject> =
        client
            .post(
                endpoint = "${resolvePrefix()}/namespaces/${namespace.toIcebergPath()}/register",
                body = defaultJson.encodeToString(request),
            ).toJsonObject()

    override suspend fun registerTableTyped(
        namespace: List<String>,
        request: IcebergTableRegisterRequest,
    ): SupabaseResult<IcebergTableMetadataResponse> =
        client
            .post(
                endpoint = "${resolvePrefix()}/namespaces/${namespace.toIcebergPath()}/register",
                body = defaultJson.encodeToString(request),
            ).deserialize()

    override suspend fun renameTable(request: JsonObject): SupabaseResult<Unit> =
        client
            .post(
                endpoint = "${resolvePrefix()}/tables/rename",
                body = defaultJson.encodeToString(request),
            ).map { }

    override suspend fun renameTableTyped(
        source: IcebergTableIdentifier,
        destination: IcebergTableIdentifier,
    ): SupabaseResult<Unit> =
        client
            .post(
                endpoint = "${resolvePrefix()}/tables/rename",
                body = defaultJson.encodeToString(IcebergTableRenameRequest(source = source, destination = destination)),
            ).map { }

    private suspend fun resolvePrefix(): String {
        resolvedPrefix?.let { return it }
        val prefix =
            when (val config = loadConfig()) {
                is SupabaseResult.Success -> config.value.extractIcebergPrefix()
                is SupabaseResult.Failure -> null
            } ?: bucketName
        return "${StoragePaths.ICEBERG}/v1/$prefix".also { resolvedPrefix = it }
    }

    private fun JsonObject.extractIcebergPrefix(): String? =
        this["overrides"]
            ?.jsonObject
            ?.get("prefix")
            ?.jsonPrimitive
            ?.contentOrNull()
            ?: this["defaults"]
                ?.jsonObject
                ?.get("prefix")
                ?.jsonPrimitive
                ?.contentOrNull()

    private fun JsonPrimitive.contentOrNull(): String? =
        runCatching { content }.getOrNull()

    private fun appendQuery(endpoint: String, params: List<Pair<String, String>>): String {
        if (params.isEmpty()) return endpoint
        val qs =
            params.joinToString("&") { (key, value) ->
                "${encodePathSegment(key)}=${encodePathSegment(value)}"
            }
        return "$endpoint?$qs"
    }
}

private fun SupabaseResult<String>.toJsonObject(): SupabaseResult<JsonObject> =
    when (this) {
        is SupabaseResult.Failure -> SupabaseResult.Failure(error)
        is SupabaseResult.Success -> SupabaseResult.Success(defaultJson.parseToJsonElement(value).jsonObject)
    }

private fun validateAnalyticsBucketName(value: String) {
    require(value.isNotBlank()) { "bucketName cannot be blank" }
    require(value.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }) {
        "Invalid bucket name: $value"
    }
}

private fun List<String>.toIcebergPath(): String =
    joinToString("%1F") { encodePathSegment(it) }

// Percent-encodes each `/`-separated segment of a storage object path while
// preserving the slashes (which are real path separators in object keys). Object
// keys and bucket ids can legally contain spaces, '#', '?', '+', etc., which
// would otherwise corrupt the request URL.
private fun encodeStoragePath(path: String): String =
    path.split('/').joinToString("/") { encodePathSegment(it) }

// URL-safe `bucket/object/path` reference for object-storage endpoints.
private fun objectRef(bucket: String, path: String): String =
    "${encodePathSegment(bucket)}/${encodeStoragePath(path)}"

private fun encodePathSegment(value: String): String {
    val bytes = value.encodeToByteArray()
    val out = StringBuilder(bytes.size)
    bytes.forEach { b ->
        val c = b.toInt().toChar()
        if (
            (c in 'a'..'z') ||
            (c in 'A'..'Z') ||
            (c in '0'..'9') ||
            c == '-' ||
            c == '_' ||
            c == '.' ||
            c == '~'
        ) {
            out.append(c)
        } else {
            val v = b.toInt() and 0xff
            out.append('%')
            out.append("0123456789ABCDEF"[v ushr 4])
            out.append("0123456789ABCDEF"[v and 0x0f])
        }
    }
    return out.toString()
}
