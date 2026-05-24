package io.github.androidpoet.supabase.storage
import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.client.defaultJson
import io.github.androidpoet.supabase.client.deserialize
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.core.result.SupabaseErrorCategory
import io.github.androidpoet.supabase.core.result.category
import io.github.androidpoet.supabase.core.result.map
import io.github.androidpoet.supabase.storage.models.Bucket
import io.github.androidpoet.supabase.storage.models.CreateBucketRequest
import io.github.androidpoet.supabase.storage.models.FileObject
import io.github.androidpoet.supabase.storage.models.MoveRequest
import io.github.androidpoet.supabase.storage.models.ObjectListRequest
import io.github.androidpoet.supabase.storage.models.ObjectSortByRequest
import io.github.androidpoet.supabase.storage.models.SignedUrlRequest
import io.github.androidpoet.supabase.storage.models.SignedUrlItemResponse
import io.github.androidpoet.supabase.storage.models.SignedUrlsRequest
import io.github.androidpoet.supabase.storage.models.SignedUrlTransformRequest
import io.github.androidpoet.supabase.storage.models.SignedUrlResponse
import io.github.androidpoet.supabase.storage.models.UpdateBucketRequest
import io.github.androidpoet.supabase.storage.models.UploadSignedUrlResponse
import kotlinx.serialization.encodeToString
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
        val query = buildList {
            limit?.let { add("limit" to it.toString()) }
            offset?.let { add("offset" to it.toString()) }
            sortColumn?.let { add("sortColumn" to it) }
            sortOrder?.let { add("sortOrder" to it.name.lowercase()) }
            search?.let { add("search" to it) }
        }
        return client.get("/storage/v1/bucket", queryParams = query).deserialize()
    }
    override suspend fun getBucket(id: String): SupabaseResult<Bucket> =
        client.get("/storage/v1/bucket/$id").deserialize()
    override suspend fun createBucket(
        id: String,
        name: String,
        public: Boolean,
        fileSizeLimit: Long?,
        allowedMimeTypes: List<String>?,
    ): SupabaseResult<Bucket> {
        val body = defaultJson.encodeToString(
            CreateBucketRequest(
                id = id,
                name = name,
                public = public,
                fileSizeLimit = fileSizeLimit,
                allowedMimeTypes = allowedMimeTypes,
            ),
        )
        return client.post("/storage/v1/bucket", body = body).deserialize()
    }
    override suspend fun updateBucket(
        id: String,
        name: String?,
        public: Boolean?,
        fileSizeLimit: Long?,
        allowedMimeTypes: List<String>?,
    ): SupabaseResult<Bucket> {
        val body = defaultJson.encodeToString(
            UpdateBucketRequest(
                name = name,
                public = public,
                fileSizeLimit = fileSizeLimit,
                allowedMimeTypes = allowedMimeTypes,
            ),
        )
        return client.put("/storage/v1/bucket/$id", body = body).deserialize()
    }
    override suspend fun deleteBucket(id: String): SupabaseResult<Unit> =
        client.delete("/storage/v1/bucket/$id").map { }
    override suspend fun emptyBucket(id: String): SupabaseResult<Unit> =
        client.post("/storage/v1/bucket/$id/empty").map { }
    override suspend fun upload(
        bucket: String,
        path: String,
        data: ByteArray,
        contentType: String,
        upsert: Boolean,
        cacheControl: Int?,
    ): SupabaseResult<String> {
        val headers = buildMap {
            if (upsert) put("x-upsert", "true")
        }
        val endpoint = withCacheControl("/storage/v1/object/$bucket/$path", cacheControl)
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
        val headers = buildMap {
            if (upsert) put("x-upsert", "true")
        }
        val endpoint = withCacheControl("/storage/v1/object/$bucket/$path", cacheControl)
        return client.putRaw(
            url = endpoint,
            body = data,
            contentType = contentType,
            headers = headers,
        )
    }
    override suspend fun download(bucket: String, path: String): SupabaseResult<String> =
        client.get("/storage/v1/object/authenticated/$bucket/$path")
    override suspend fun downloadPublic(bucket: String, path: String): SupabaseResult<String> =
        client.get("/storage/v1/object/public/$bucket/$path")
    override suspend fun download(
        bucket: String,
        path: String,
        download: Boolean,
        fileName: String?,
    ): SupabaseResult<String> =
        client.get("/storage/v1/object/authenticated/$bucket/$path${buildDownloadQuery(download, fileName)}")
    override suspend fun downloadPublic(
        bucket: String,
        path: String,
        download: Boolean,
        fileName: String?,
    ): SupabaseResult<String> =
        client.get("/storage/v1/object/public/$bucket/$path${buildDownloadQuery(download, fileName)}")
    override suspend fun info(bucket: String, path: String): SupabaseResult<FileObject> =
        client.get("/storage/v1/object/info/$bucket/$path").deserialize()
    override suspend fun infoPublic(bucket: String, path: String): SupabaseResult<FileObject> =
        client.get("/storage/v1/object/info/public/$bucket/$path").deserialize()
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
        val body = defaultJson.encodeToString(
            ObjectListRequest(
                prefix = prefix,
                limit = limit,
                offset = offset,
                sortBy = sortBy?.let {
                    ObjectSortByRequest(column = it, order = sortOrder.name.lowercase())
                },
                search = search,
            ),
        )
        return client.post("/storage/v1/object/list/$bucket", body = body).deserialize()
    }
    override suspend fun move(
        bucket: String,
        fromPath: String,
        toPath: String,
        destinationBucket: String?,
    ): SupabaseResult<Unit> {
        val body = defaultJson.encodeToString(
            MoveRequest(
                bucketId = bucket,
                sourceKey = fromPath,
                destinationKey = toPath,
                destinationBucketId = destinationBucket,
            ),
        )
        return client.post("/storage/v1/object/move", body = body).map { }
    }
    override suspend fun deleteObject(
        bucket: String,
        path: String,
    ): SupabaseResult<Unit> =
        client.delete("/storage/v1/object/$bucket/$path").map { }
    override suspend fun copy(
        bucket: String,
        fromPath: String,
        toPath: String,
        destinationBucket: String?,
        copyMetadata: Boolean?,
    ): SupabaseResult<Unit> {
        val body = defaultJson.encodeToString(
            MoveRequest(
                bucketId = bucket,
                sourceKey = fromPath,
                destinationKey = toPath,
                destinationBucketId = destinationBucket,
                copyMetadata = copyMetadata,
            ),
        )
        return client.post("/storage/v1/object/copy", body = body).map { }
    }
    override suspend fun remove(bucket: String, paths: List<String>): SupabaseResult<Unit> {
        return removeWithResult(bucket, paths).map { }
    }

    override suspend fun removeWithResult(
        bucket: String,
        paths: List<String>,
    ): SupabaseResult<List<FileObject>> {
        val body = defaultJson.encodeToString(mapOf("prefixes" to paths))
        return client.post(
            endpoint = "/storage/v1/object/remove/$bucket",
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
        val body = defaultJson.encodeToString(
            SignedUrlRequest(
                expiresIn = expiresIn,
                transform = transform?.toRequest(),
            ),
        )
        return client.post("/storage/v1/object/sign/$bucket/$path", body = body)
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
        return client.post("/storage/v1/object/sign/$bucket", body = body)
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
        client.post(
            endpoint = "/storage/v1/object/upload/sign/$bucket/$path",
            headers = if (upsert) mapOf("x-upsert" to "true") else emptyMap(),
        )
            .deserialize<UploadSignedUrlResponse>()
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
        val headers = buildMap {
            if (upsert) put("x-upsert", "true")
        }
        val qs = buildList {
            add("token=$token")
            cacheControl?.let { add("cacheControl=$it") }
        }.joinToString("&")
        return client.postRaw(
            url = "/storage/v1/object/upload/sign/$bucket/$path?$qs",
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
        return "${client.projectUrl}/storage/v1/object/sign/$bucket/$path?token=$token$join"
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
        val base = "${client.projectUrl}/storage/v1/object/public/$bucket/$path"
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
        val base = "${client.projectUrl}/storage/v1/object/authenticated/$bucket/$path"
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
        val base = "${client.projectUrl}/storage/v1/render/image/public/$bucket/$path"
        val transformQuery = transform?.toQueryString().orEmpty()
        return if (transformQuery.isBlank()) base else "$base?$transformQuery"
    }

    override fun getAuthenticatedRenderUrl(
        bucket: String,
        path: String,
        transform: ImageTransformOptions?,
    ): String {
        val base = "${client.projectUrl}/storage/v1/render/image/authenticated/$bucket/$path"
        val transformQuery = transform?.toQueryString().orEmpty()
        return if (transformQuery.isBlank()) base else "$base?$transformQuery"
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
        val params = buildList {
            width?.let { add("width" to it.toString()) }
            height?.let { add("height" to it.toString()) }
            resize?.let { add("resize" to it.name.lowercase()) }
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
