package io.github.androidpoet.supabase.storage

import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.storage.models.AnalyticsBucket
import io.github.androidpoet.supabase.storage.models.Bucket
import io.github.androidpoet.supabase.storage.models.FileObject
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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StorageClientExtTest {
    @Test
    fun test_createSignedDownloadUrl_addsDownloadParam() = runTest {
        val client = FakeStorageClient()

        val result = client.createSignedDownloadUrl(
            bucket = "avatars",
            path = "a.png",
            expiresIn = 60,
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals("https://example.supabase.co/storage/v1/object/sign/avatars/a.png?token=abc&download", result.value)
    }

    @Test
    fun test_createSignedDownloadUrl_encodesFileName() = runTest {
        val client = FakeStorageClient()

        val result = client.createSignedDownloadUrl(
            bucket = "avatars",
            path = "a.png",
            expiresIn = 60,
            fileName = "my avatar.png",
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(
            "https://example.supabase.co/storage/v1/object/sign/avatars/a.png?token=abc&download=my%20avatar.png",
            result.value,
        )
    }

    @Test
    fun test_createSignedDownloadUrl_passesDownloadOptionsToCoreApi() = runTest {
        val client = FakeStorageClient()

        client.createSignedDownloadUrl(
            bucket = "avatars",
            path = "a.png",
            expiresIn = 60,
            fileName = "avatar.png",
        )

        assertEquals(true, client.lastSignedDownload)
        assertEquals("avatar.png", client.lastSignedFileName)
    }

    @Test
    fun test_createSignedDownloadUrl_transformOverload_passesTransform() = runTest {
        val client = FakeStorageClient()

        client.createSignedDownloadUrl(
            bucket = "avatars",
            path = "a.png",
            expiresIn = 60,
            transform = ImageTransformOptions(width = 120),
        )

        assertEquals(120, client.lastSignedTransform?.width)
    }

    @Test
    fun test_createSignedRenderUrl_setsDownloadFalseAndPassesTransform() = runTest {
        val client = FakeStorageClient()

        client.createSignedRenderUrl(
            bucket = "avatars",
            path = "a.png",
            expiresIn = 60,
            transform = ImageTransformOptions(height = 240),
        )

        assertEquals(false, client.lastSignedDownload)
        assertEquals(240, client.lastSignedTransform?.height)
    }

    @Test
    fun test_createSignedDownloadUrls_vararg_routesToListOverload() = runTest {
        val client = FakeStorageClient()

        val result = client.createSignedDownloadUrls(
            bucket = "avatars",
            expiresIn = 60,
            "a.png",
            "b.png",
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(listOf("a.png", "b.png"), client.lastSignedPaths)
        assertEquals(true, client.lastSignedUrlsDownload)
    }

    @Test
    fun test_createSignedDownloadUrls_vararg_withFileName_routesToListOverload() = runTest {
        val client = FakeStorageClient()

        val result = client.createSignedDownloadUrls(
            bucket = "avatars",
            expiresIn = 60,
            fileName = "avatar.png",
            "a.png",
            "b.png",
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(listOf("a.png", "b.png"), client.lastSignedPaths)
        assertEquals(true, client.lastSignedUrlsDownload)
        assertEquals("avatar.png", client.lastSignedUrlsFileName)
    }

    @Test
    fun test_createSignedRenderUrls_list_appendsTransformQuery() = runTest {
        val client = FakeStorageClient()

        val result = client.createSignedRenderUrls(
            bucket = "avatars",
            paths = listOf("a.png"),
            expiresIn = 60,
            transform = ImageTransformOptions(width = 200, resize = ResizeMode.COVER),
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(
            "https://example.supabase.co/storage/v1/object/sign/avatars/a.png?token=abc&width=200&resize=cover",
            result.value.first(),
        )
    }

    @Test
    fun test_createSignedRenderUrls_vararg_routesAndAppendsTransform() = runTest {
        val client = FakeStorageClient()

        val result = client.createSignedRenderUrls(
            bucket = "avatars",
            expiresIn = 60,
            transform = ImageTransformOptions(height = 120),
            "a.png",
            "b.png",
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(
            listOf(
                "https://example.supabase.co/storage/v1/object/sign/avatars/a.png?token=abc&height=120",
                "https://example.supabase.co/storage/v1/object/sign/avatars/b.png?token=abc&height=120",
            ),
            result.value,
        )
    }

    @Test
    fun test_createSignedDownloadUrlsByPath_returnsPathUrlMap() = runTest {
        val client = FakeStorageClient()

        val result = client.createSignedDownloadUrlsByPath(
            bucket = "avatars",
            paths = listOf("a.png", "b.png"),
            expiresIn = 60,
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(
            mapOf(
                "a.png" to "https://example.supabase.co/storage/v1/object/sign/avatars/a.png?token=abc",
                "b.png" to "https://example.supabase.co/storage/v1/object/sign/avatars/b.png?token=abc",
            ),
            result.value,
        )
    }

    @Test
    fun test_createSignedRenderUrlsByPath_returnsPathUrlMapWithTransformQuery() = runTest {
        val client = FakeStorageClient()

        val result = client.createSignedRenderUrlsByPath(
            bucket = "avatars",
            paths = listOf("a.png", "b.png"),
            expiresIn = 60,
            transform = ImageTransformOptions(width = 200),
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(
            mapOf(
                "a.png" to "https://example.supabase.co/storage/v1/object/sign/avatars/a.png?token=abc&width=200",
                "b.png" to "https://example.supabase.co/storage/v1/object/sign/avatars/b.png?token=abc&width=200",
            ),
            result.value,
        )
    }

    @Test
    fun test_createSignedDownloadUrlsByPath_vararg_routesToListOverload() = runTest {
        val client = FakeStorageClient()

        val result = client.createSignedDownloadUrlsByPath(
            bucket = "avatars",
            expiresIn = 60,
            "a.png",
            "b.png",
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(
            mapOf(
                "a.png" to "https://example.supabase.co/storage/v1/object/sign/avatars/a.png?token=abc",
                "b.png" to "https://example.supabase.co/storage/v1/object/sign/avatars/b.png?token=abc",
            ),
            result.value,
        )
    }

    @Test
    fun test_createSignedRenderUrlsByPath_vararg_routesToListOverload() = runTest {
        val client = FakeStorageClient()

        val result = client.createSignedRenderUrlsByPath(
            bucket = "avatars",
            expiresIn = 60,
            transform = ImageTransformOptions(height = 100),
            "a.png",
            "b.png",
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(
            mapOf(
                "a.png" to "https://example.supabase.co/storage/v1/object/sign/avatars/a.png?token=abc&height=100",
                "b.png" to "https://example.supabase.co/storage/v1/object/sign/avatars/b.png?token=abc&height=100",
            ),
            result.value,
        )
    }

    @Test
    fun test_remove_vararg_routesToListOverload() = runTest {
        val client = FakeStorageClient()

        val result = client.remove(
            bucket = "avatars",
            "a.png",
            "b.png",
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(listOf("a.png", "b.png"), client.lastRemovedPaths)
    }

    @Test
    fun test_removeWithResult_vararg_routesToListOverload() = runTest {
        val client = FakeStorageClient()

        val result = client.removeWithResult(
            bucket = "avatars",
            "a.png",
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals(listOf("a.png"), client.lastRemovedPaths)
    }

    @Test
    fun test_getPublicUrlsByPath_buildsMap() {
        val client = FakeStorageClient()

        val urls = client.getPublicUrlsByPath(
            bucket = "avatars",
            paths = listOf("a.png", "b.png"),
            download = true,
            fileName = "avatar.png",
        )

        assertEquals(
            mapOf(
                "a.png" to "https://example.supabase.co/storage/v1/object/public/avatars/a.png?download=avatar.png",
                "b.png" to "https://example.supabase.co/storage/v1/object/public/avatars/b.png?download=avatar.png",
            ),
            urls,
        )
    }

    @Test
    fun test_getPublicUrls_vararg_withOptions_buildsList() {
        val client = FakeStorageClient()

        val urls = client.getPublicUrls(
            bucket = "avatars",
            download = true,
            fileName = "avatar.png",
            paths = arrayOf("a.png", "b.png"),
        )

        assertEquals(
            listOf(
                "https://example.supabase.co/storage/v1/object/public/avatars/a.png?download=avatar.png",
                "https://example.supabase.co/storage/v1/object/public/avatars/b.png?download=avatar.png",
            ),
            urls,
        )
    }

    @Test
    fun test_getAuthenticatedUrls_buildsList() {
        val client = FakeStorageClient()

        val urls = client.getAuthenticatedUrls(
            bucket = "avatars",
            paths = listOf("a.png", "b.png"),
        )

        assertEquals(
            listOf(
                "https://example.supabase.co/storage/v1/object/authenticated/avatars/a.png",
                "https://example.supabase.co/storage/v1/object/authenticated/avatars/b.png",
            ),
            urls,
        )
    }

    @Test
    fun test_getPublicRenderUrlsByPath_buildsMap() {
        val client = FakeStorageClient()

        val urls = client.getPublicRenderUrlsByPath(
            bucket = "avatars",
            paths = listOf("a.png"),
            transform = ImageTransformOptions(width = 100),
        )

        assertEquals(
            mapOf(
                "a.png" to "https://example.supabase.co/storage/v1/render/image/public/avatars/a.png?width=100",
            ),
            urls,
        )
    }

    @Test
    fun test_getAuthenticatedRenderUrls_vararg_buildsList() {
        val client = FakeStorageClient()

        val urls = client.getAuthenticatedRenderUrls(
            bucket = "avatars",
            transform = ImageTransformOptions(width = 40),
            "a.png",
            "b.png",
        )

        assertEquals(
            listOf(
                "https://example.supabase.co/storage/v1/render/image/authenticated/avatars/a.png?width=40",
                "https://example.supabase.co/storage/v1/render/image/authenticated/avatars/b.png?width=40",
            ),
            urls,
        )
    }
}

private class FakeStorageClient : StorageClient {
    var lastSignedDownload: Boolean? = null
    var lastSignedFileName: String? = null
    var lastSignedTransform: ImageTransformOptions? = null
    var lastSignedPath: String? = null
    var lastSignedBucket: String? = null
    var lastSignedPaths: List<String> = emptyList()
    var lastSignedUrlsDownload: Boolean? = null
    var lastSignedUrlsFileName: String? = null
    var lastRemovedPaths: List<String> = emptyList()
    override suspend fun listBuckets(limit: Int?, offset: Int?, sortColumn: String?, sortOrder: SortOrder?, search: String?): SupabaseResult<List<Bucket>> =
        SupabaseResult.Success(emptyList())
    override suspend fun getBucket(id: String): SupabaseResult<Bucket> = error("not used")
    override suspend fun createBucket(id: String, name: String, public: Boolean, fileSizeLimit: Long?, allowedMimeTypes: List<String>?): SupabaseResult<Bucket> = error("not used")
    override suspend fun updateBucket(id: String, name: String?, public: Boolean?, fileSizeLimit: Long?, allowedMimeTypes: List<String>?): SupabaseResult<Bucket> = error("not used")
    override suspend fun deleteBucket(id: String): SupabaseResult<Unit> = error("not used")
    override suspend fun emptyBucket(id: String): SupabaseResult<Unit> = error("not used")
    override suspend fun upload(bucket: String, path: String, data: ByteArray, contentType: String, upsert: Boolean, cacheControl: Int?): SupabaseResult<String> = error("not used")
    override suspend fun update(bucket: String, path: String, data: ByteArray, contentType: String, upsert: Boolean, cacheControl: Int?): SupabaseResult<String> = error("not used")
    override suspend fun download(bucket: String, path: String): SupabaseResult<String> = error("not used")
    override suspend fun downloadPublic(bucket: String, path: String): SupabaseResult<String> = error("not used")
    override suspend fun download(bucket: String, path: String, download: Boolean, fileName: String?): SupabaseResult<String> = error("not used")
    override suspend fun downloadPublic(bucket: String, path: String, download: Boolean, fileName: String?): SupabaseResult<String> = error("not used")
    override suspend fun info(bucket: String, path: String): SupabaseResult<FileObject> = error("not used")
    override suspend fun infoPublic(bucket: String, path: String): SupabaseResult<FileObject> = error("not used")
    override suspend fun exists(bucket: String, path: String): SupabaseResult<Boolean> = error("not used")
    override suspend fun existsPublic(bucket: String, path: String): SupabaseResult<Boolean> = error("not used")
    override suspend fun list(bucket: String, prefix: String, limit: Int, offset: Int, sortBy: String?, sortOrder: SortOrder, search: String?): SupabaseResult<List<FileObject>> = error("not used")
    override suspend fun listV2(
        bucket: String,
        prefix: String?,
        cursor: String?,
        limit: Int?,
        withDelimiter: Boolean?,
        sortBy: String?,
        sortOrder: SortOrder,
    ): SupabaseResult<ObjectListV2Result> = error("not used")
    override suspend fun move(bucket: String, fromPath: String, toPath: String, destinationBucket: String?): SupabaseResult<Unit> = error("not used")
    override suspend fun deleteObject(bucket: String, path: String): SupabaseResult<Unit> = error("not used")
    override suspend fun copy(bucket: String, fromPath: String, toPath: String, destinationBucket: String?, copyMetadata: Boolean?): SupabaseResult<Unit> = error("not used")
    override suspend fun remove(bucket: String, paths: List<String>): SupabaseResult<Unit> {
        lastRemovedPaths = paths
        return SupabaseResult.Success(Unit)
    }
    override suspend fun removeWithResult(bucket: String, paths: List<String>): SupabaseResult<List<FileObject>> {
        lastRemovedPaths = paths
        return SupabaseResult.Success(emptyList())
    }
    override suspend fun createSignedUrl(
        bucket: String,
        path: String,
        expiresIn: Long,
        download: Boolean,
        fileName: String?,
        transform: ImageTransformOptions?,
    ): SupabaseResult<String> {
        lastSignedDownload = download
        lastSignedFileName = fileName
        lastSignedTransform = transform
        lastSignedPath = path
        lastSignedBucket = bucket
        val base = "https://example.supabase.co/storage/v1/object/sign/$bucket/$path?token=abc"
        val value = when {
            !download -> {
                val transformQuery = buildList {
                    transform?.width?.let { add("width=$it") }
                    transform?.height?.let { add("height=$it") }
                    transform?.resize?.let { add("resize=${it.name.lowercase()}") }
                    transform?.format?.let { add("format=$it") }
                    transform?.quality?.let { add("quality=$it") }
                }.joinToString("&")
                if (transformQuery.isBlank()) base else "$base&$transformQuery"
            }
            fileName == null -> "$base&download"
            else -> "$base&download=my%20avatar.png"
        }
        return SupabaseResult.Success(value)
    }
    override suspend fun createSignedUrls(
        bucket: String,
        paths: List<String>,
        expiresIn: Long,
        download: Boolean,
        fileName: String?,
    ): SupabaseResult<List<String>> {
        lastSignedPaths = paths
        lastSignedUrlsDownload = download
        lastSignedUrlsFileName = fileName
        return SupabaseResult.Success(paths.map { "https://example.supabase.co/storage/v1/object/sign/$bucket/$it?token=abc" })
    }
    override suspend fun createUploadSignedUrl(bucket: String, path: String, upsert: Boolean): SupabaseResult<String> = error("not used")
    override suspend fun createUploadSignedUrlWithPath(bucket: String, path: String, upsert: Boolean): SupabaseResult<UploadSignedUrl> = error("not used")
    override suspend fun uploadToSignedUrl(bucket: String, path: String, token: String, data: ByteArray, contentType: String, upsert: Boolean, cacheControl: Int?): SupabaseResult<String> = error("not used")
    override fun getPublicUrl(bucket: String, path: String, transform: ImageTransformOptions?): String =
        "https://example.supabase.co/storage/v1/object/public/$bucket/$path"
    override fun getAuthenticatedUrl(bucket: String, path: String, transform: ImageTransformOptions?): String =
        "https://example.supabase.co/storage/v1/object/authenticated/$bucket/$path"
    override fun getPublicUrl(bucket: String, path: String, download: Boolean, fileName: String?, transform: ImageTransformOptions?): String {
        val base = "https://example.supabase.co/storage/v1/object/public/$bucket/$path"
        return when {
            !download -> base
            fileName == null -> "$base?download"
            else -> "$base?download=$fileName"
        }
    }
    override fun getAuthenticatedUrl(bucket: String, path: String, download: Boolean, fileName: String?, transform: ImageTransformOptions?): String {
        val base = "https://example.supabase.co/storage/v1/object/authenticated/$bucket/$path"
        return when {
            !download -> base
            fileName == null -> "$base?download"
            else -> "$base?download=$fileName"
        }
    }
    override fun getPublicRenderUrl(bucket: String, path: String, transform: ImageTransformOptions?): String {
        val base = "https://example.supabase.co/storage/v1/render/image/public/$bucket/$path"
        return transform?.width?.let { "$base?width=$it" } ?: base
    }
    override fun getAuthenticatedRenderUrl(bucket: String, path: String, transform: ImageTransformOptions?): String {
        val base = "https://example.supabase.co/storage/v1/render/image/authenticated/$bucket/$path"
        return transform?.width?.let { "$base?width=$it" } ?: base
    }
    override fun getSignedDownloadUrl(bucket: String, path: String, token: String, download: Boolean, fileName: String?): String = error("not used")
    override suspend fun createAnalyticsBucket(name: String): SupabaseResult<AnalyticsBucket> = error("not used")
    override suspend fun listAnalyticsBuckets(limit: Int?, offset: Int?, sortColumn: String?, sortOrder: SortOrder?, search: String?): SupabaseResult<List<AnalyticsBucket>> = error("not used")
    override suspend fun deleteAnalyticsBucket(name: String): SupabaseResult<Unit> = error("not used")
    override fun analyticsCatalog(bucketName: String): AnalyticsCatalogClient = error("not used")
    override suspend fun createVectorBucket(vectorBucketName: String): SupabaseResult<Unit> = error("not used")
    override suspend fun getVectorBucket(vectorBucketName: String): SupabaseResult<VectorBucket> = error("not used")
    override suspend fun listVectorBuckets(prefix: String?, maxResults: Int?, nextToken: String?): SupabaseResult<VectorBucketListResponse> = error("not used")
    override suspend fun deleteVectorBucket(vectorBucketName: String): SupabaseResult<Unit> = error("not used")
    override suspend fun createVectorIndex(
        vectorBucketName: String,
        indexName: String,
        dataType: VectorDataType,
        dimension: Int,
        distanceMetric: VectorDistanceMetric,
        metadataConfiguration: VectorMetadataConfiguration?,
    ): SupabaseResult<Unit> = error("not used")
    override suspend fun getVectorIndex(vectorBucketName: String, indexName: String): SupabaseResult<VectorIndex> = error("not used")
    override suspend fun listVectorIndexes(vectorBucketName: String, prefix: String?, maxResults: Int?, nextToken: String?): SupabaseResult<VectorIndexListResponse> = error("not used")
    override suspend fun deleteVectorIndex(vectorBucketName: String, indexName: String): SupabaseResult<Unit> = error("not used")
    override suspend fun putVectors(vectorBucketName: String, indexName: String, vectors: List<VectorObject>): SupabaseResult<Unit> = error("not used")
    override suspend fun getVectors(vectorBucketName: String, indexName: String, keys: List<String>, returnData: Boolean?, returnMetadata: Boolean?): SupabaseResult<List<VectorMatch>> = error("not used")
    override suspend fun listVectors(
        vectorBucketName: String,
        indexName: String,
        maxResults: Int?,
        nextToken: String?,
        returnData: Boolean?,
        returnMetadata: Boolean?,
        segmentCount: Int?,
        segmentIndex: Int?,
    ): SupabaseResult<VectorListResponse> = error("not used")
    override suspend fun queryVectors(
        vectorBucketName: String,
        indexName: String,
        queryVector: VectorData,
        topK: Int?,
        filter: JsonObject?,
        returnDistance: Boolean?,
        returnMetadata: Boolean?,
    ): SupabaseResult<VectorQueryResponse> = error("not used")
    override suspend fun deleteVectors(vectorBucketName: String, indexName: String, keys: List<String>): SupabaseResult<Unit> = error("not used")
}
