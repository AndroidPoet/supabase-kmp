package io.github.androidpoet.supabase.storage

import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class StorageClientImplTest {
    @Test
    fun test_listBuckets_includesQueryParams() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        val result = sut.listBuckets(
            limit = 10,
            offset = 20,
            sortColumn = "name",
            sortOrder = SortOrder.DESC,
            search = "prod",
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals("/storage/v1/bucket", client.lastGetEndpoint)
        assertEquals(
            listOf(
                "limit" to "10",
                "offset" to "20",
                "sortColumn" to "name",
                "sortOrder" to "desc",
                "search" to "prod",
            ),
            client.lastGetQueryParams,
        )
    }

    @Test
    fun test_createSignedUrls_usesBatchSignEndpoint() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        val result = sut.createSignedUrls(
            bucket = "avatars",
            paths = listOf("a.png", "b.png"),
            expiresIn = 60,
        )

        assertEquals("/storage/v1/object/sign/avatars", client.lastPostEndpoint)
        assertTrue(result is SupabaseResult.Success)
        assertEquals(2, result.value.size)
        assertEquals("https://example.supabase.co/sign/a", result.value[0])
    }

    @Test
    fun test_createSignedUrl_withDownload_addsDownloadQuery() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        val result = sut.createSignedUrl(
            bucket = "avatars",
            path = "a.png",
            expiresIn = 120,
            download = true,
            fileName = "my avatar.png",
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals("https://example.supabase.co/sign/single?download=my%20avatar.png", result.value)
    }

    @Test
    fun test_createSignedUrl_returnsResolvedAbsoluteUrl() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        val result = sut.createSignedUrl(
            bucket = "avatars",
            path = "a.png",
            expiresIn = 120,
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals("https://example.supabase.co/sign/single", result.value)
    }

    @Test
    fun test_createUploadSignedUrl_returnsToken() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        val result = sut.createUploadSignedUrl(
            bucket = "avatars",
            path = "a.png",
        )

        assertEquals("/storage/v1/object/upload/sign/avatars/a.png", client.lastPostEndpoint)
        assertTrue(result is SupabaseResult.Success)
        assertEquals("upload-token-123", result.value)
    }

    @Test
    fun test_createUploadSignedUrl_withUpsert_setsUpsertHeader() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        sut.createUploadSignedUrl(
            bucket = "avatars",
            path = "a.png",
            upsert = true,
        )

        assertEquals("true", client.lastPostHeaders["x-upsert"])
    }

    @Test
    fun test_createUploadSignedUrlWithPath_returnsResolvedUrlAndToken() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        val result = sut.createUploadSignedUrlWithPath(
            bucket = "avatars",
            path = "a.png",
        )

        assertTrue(result is SupabaseResult.Success)
        assertEquals("https://example.supabase.co/upload/sign/path", result.value.url)
        assertEquals("upload-token-123", result.value.token)
    }

    @Test
    fun test_updateBucket_usesPutBucketEndpoint() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        val result = sut.updateBucket(
            id = "avatars",
            public = true,
        )

        assertEquals("/storage/v1/bucket/avatars", client.lastPostEndpoint)
        assertTrue(result is SupabaseResult.Success)
    }

    @Test
    fun test_uploadToSignedUrl_usesSignedUploadPathWithToken() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        sut.uploadToSignedUrl(
            bucket = "avatars",
            path = "a.png",
            token = "abc",
            data = byteArrayOf(1, 2, 3),
            upsert = true,
        )

        assertEquals("/storage/v1/object/upload/sign/avatars/a.png?token=abc", client.lastPostRawUrl)
        assertEquals("true", client.lastPostRawHeaders["x-upsert"])
    }

    @Test
    fun test_uploadToSignedUrl_withCacheControl_includesCacheControlQuery() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        sut.uploadToSignedUrl(
            bucket = "avatars",
            path = "a.png",
            token = "abc",
            data = byteArrayOf(1, 2, 3),
            cacheControl = 3600,
        )

        assertEquals("/storage/v1/object/upload/sign/avatars/a.png?token=abc&cacheControl=3600", client.lastPostRawUrl)
    }

    @Test
    fun test_update_usesPutRawAndUpsertHeader() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        sut.update(
            bucket = "avatars",
            path = "a.png",
            data = byteArrayOf(1, 2, 3),
            upsert = true,
        )

        assertEquals("/storage/v1/object/avatars/a.png", client.lastPutRawUrl)
        assertEquals("true", client.lastPutRawHeaders["x-upsert"])
    }

    @Test
    fun test_upload_withCacheControl_includesCacheControlQuery() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        sut.upload(
            bucket = "avatars",
            path = "a.png",
            data = byteArrayOf(1),
            cacheControl = 1200,
        )

        assertEquals("/storage/v1/object/avatars/a.png?cacheControl=1200", client.lastPostRawUrl)
    }

    @Test
    fun test_update_withCacheControl_includesCacheControlQuery() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        sut.update(
            bucket = "avatars",
            path = "a.png",
            data = byteArrayOf(1),
            cacheControl = 900,
        )

        assertEquals("/storage/v1/object/avatars/a.png?cacheControl=900", client.lastPutRawUrl)
    }

    @Test
    fun test_copy_usesCopyEndpoint() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        val result = sut.copy(
            bucket = "avatars",
            fromPath = "old.png",
            toPath = "new.png",
        )

        assertEquals("/storage/v1/object/copy", client.lastPostEndpoint)
        assertTrue(result is SupabaseResult.Success)
    }

    @Test
    fun test_copy_includesCopyMetadataWhenProvided() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        sut.copy(
            bucket = "avatars",
            fromPath = "old.png",
            toPath = "new.png",
            copyMetadata = true,
        )

        assertTrue(client.lastPostBody?.contains("\"copyMetadata\":true") == true)
    }

    @Test
    fun test_removeWithResult_returnsDeletedFileEntries() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        val result = sut.removeWithResult(bucket = "avatars", paths = listOf("a.png"))

        assertEquals("/storage/v1/object/remove/avatars", client.lastPostEndpoint)
        assertTrue(result is SupabaseResult.Success)
        assertEquals(1, result.value.size)
        assertEquals("a.png", result.value.first().name)
    }

    @Test
    fun test_move_allowsDestinationBucket() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        val result = sut.move(
            bucket = "avatars",
            fromPath = "old.png",
            toPath = "new.png",
            destinationBucket = "archive",
        )

        assertEquals("/storage/v1/object/move", client.lastPostEndpoint)
        assertTrue(client.lastPostBody?.contains("\"destinationBucket\":\"archive\"") == true)
        assertTrue(result is SupabaseResult.Success)
    }

    @Test
    fun test_deleteObject_usesDeleteObjectEndpoint() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        val result = sut.deleteObject(bucket = "avatars", path = "a.png")

        assertEquals("/storage/v1/object/avatars/a.png", client.lastDeleteEndpoint)
        assertTrue(result is SupabaseResult.Success)
    }

    @Test
    fun test_downloadPublic_usesPublicEndpoint() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        sut.downloadPublic(bucket = "avatars", path = "a.png")

        assertEquals("/storage/v1/object/public/avatars/a.png", client.lastGetEndpoint)
    }

    @Test
    fun test_download_usesAuthenticatedEndpoint() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        sut.download(bucket = "avatars", path = "a.png")

        assertEquals("/storage/v1/object/authenticated/avatars/a.png", client.lastGetEndpoint)
    }

    @Test
    fun test_download_withDownloadFileName_appendsDownloadQuery() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        sut.download(bucket = "avatars", path = "a.png", download = true, fileName = "avatar.png")

        assertEquals("/storage/v1/object/authenticated/avatars/a.png?download=avatar.png", client.lastGetEndpoint)
    }

    @Test
    fun test_getSignedDownloadUrl_withFileName_buildsExpectedUrl() {
        val sut = StorageClientImpl(FakeSupabaseClient())

        val url = sut.getSignedDownloadUrl(
            bucket = "avatars",
            path = "a.png",
            token = "tok123",
            download = true,
            fileName = "avatar.png",
        )

        assertEquals(
            "https://example.supabase.co/storage/v1/object/sign/avatars/a.png?token=tok123&download=avatar.png",
            url,
        )
    }

    @Test
    fun test_info_usesAuthenticatedInfoEndpoint() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        val result = sut.info(bucket = "avatars", path = "a.png")

        assertTrue(result is SupabaseResult.Success)
        assertEquals("/storage/v1/object/info/avatars/a.png", client.lastGetEndpoint)
        assertEquals("a.png", result.value.name)
    }

    @Test
    fun test_infoPublic_usesPublicInfoEndpoint() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        val result = sut.infoPublic(bucket = "avatars", path = "a.png")

        assertTrue(result is SupabaseResult.Success)
        assertEquals("/storage/v1/object/info/public/avatars/a.png", client.lastGetEndpoint)
        assertEquals("a.png", result.value.name)
    }

    @Test
    fun test_exists_returnsTrueWhenInfoExists() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        val result = sut.exists(bucket = "avatars", path = "a.png")

        assertTrue(result is SupabaseResult.Success)
        assertEquals(true, result.value)
    }

    @Test
    fun test_exists_returnsFalseWhenInfoNotFound() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        val result = sut.exists(bucket = "avatars", path = "missing.png")

        assertTrue(result is SupabaseResult.Success)
        assertEquals(false, result.value)
    }

    @Test
    fun test_list_includesSortOrderAndSearchInBody() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        sut.list(
            bucket = "avatars",
            prefix = "folder/",
            sortBy = "name",
            sortOrder = SortOrder.DESC,
            search = "cat",
        )

        assertEquals("/storage/v1/object/list/avatars", client.lastPostEndpoint)
        assertTrue(client.lastPostBody?.contains("\"order\":\"desc\"") == true)
        assertTrue(client.lastPostBody?.contains("\"search\":\"cat\"") == true)
    }

    @Test
    fun test_createSignedUrl_withTransform_includesTransformPayload() = runBlocking {
        val client = FakeSupabaseClient()
        val sut = StorageClientImpl(client)

        sut.createSignedUrl(
            bucket = "avatars",
            path = "a.png",
            expiresIn = 120,
            transform = ImageTransformOptions(
                width = 200,
                height = 300,
                resize = ResizeMode.COVER,
                quality = 80,
            ),
        )

        assertTrue(client.lastPostBody?.contains("\"transform\"") == true)
        assertTrue(client.lastPostBody?.contains("\"width\":200") == true)
        assertTrue(client.lastPostBody?.contains("\"resize\":\"cover\"") == true)
    }

    @Test
    fun test_getPublicUrl_withTransform_includesQueryParams() {
        val sut = StorageClientImpl(FakeSupabaseClient())

        val url = sut.getPublicUrl(
            bucket = "avatars",
            path = "a.png",
            transform = ImageTransformOptions(
                width = 100,
                height = 100,
                resize = ResizeMode.FILL,
                format = "webp",
            ),
        )

        assertEquals(
            "https://example.supabase.co/storage/v1/object/public/avatars/a.png?width=100&height=100&resize=fill&format=webp",
            url,
        )
    }

    @Test
    fun test_getPublicUrl_withDownloadAndTransform_buildsCombinedQuery() {
        val sut = StorageClientImpl(FakeSupabaseClient())

        val url = sut.getPublicUrl(
            bucket = "avatars",
            path = "a.png",
            download = true,
            fileName = "avatar.png",
            transform = ImageTransformOptions(width = 50),
        )

        assertEquals(
            "https://example.supabase.co/storage/v1/object/public/avatars/a.png?width=50&download=avatar.png",
            url,
        )
    }

    @Test
    fun test_getAuthenticatedUrl_withTransform_includesQueryParams() {
        val sut = StorageClientImpl(FakeSupabaseClient())

        val url = sut.getAuthenticatedUrl(
            bucket = "avatars",
            path = "a.png",
            transform = ImageTransformOptions(
                width = 80,
                height = 120,
                resize = ResizeMode.CONTAIN,
            ),
        )

        assertEquals(
            "https://example.supabase.co/storage/v1/object/authenticated/avatars/a.png?width=80&height=120&resize=contain",
            url,
        )
    }

    @Test
    fun test_getAuthenticatedUrl_withDownloadAndTransform_buildsCombinedQuery() {
        val sut = StorageClientImpl(FakeSupabaseClient())

        val url = sut.getAuthenticatedUrl(
            bucket = "avatars",
            path = "a.png",
            download = true,
            fileName = "auth-avatar.png",
            transform = ImageTransformOptions(width = 50),
        )

        assertEquals(
            "https://example.supabase.co/storage/v1/object/authenticated/avatars/a.png?width=50&download=auth-avatar.png",
            url,
        )
    }

    @Test
    fun test_getPublicRenderUrl_withTransform_usesRenderEndpoint() {
        val sut = StorageClientImpl(FakeSupabaseClient())

        val url = sut.getPublicRenderUrl(
            bucket = "avatars",
            path = "a.png",
            transform = ImageTransformOptions(width = 200, resize = ResizeMode.COVER),
        )

        assertEquals(
            "https://example.supabase.co/storage/v1/render/image/public/avatars/a.png?width=200&resize=cover",
            url,
        )
    }

    @Test
    fun test_getAuthenticatedRenderUrl_withTransform_usesRenderEndpoint() {
        val sut = StorageClientImpl(FakeSupabaseClient())

        val url = sut.getAuthenticatedRenderUrl(
            bucket = "avatars",
            path = "a.png",
            transform = ImageTransformOptions(height = 300, resize = ResizeMode.FILL),
        )

        assertEquals(
            "https://example.supabase.co/storage/v1/render/image/authenticated/avatars/a.png?height=300&resize=fill",
            url,
        )
    }
}

private class FakeSupabaseClient : SupabaseClient {
    override val projectUrl: String = "https://example.supabase.co"
    override val apiKey: String = "anon"
    override val accessTokenOrNull: String? = null

    var lastPostEndpoint: String? = null
    var lastPostBody: String? = null
    var lastPostHeaders: Map<String, String> = emptyMap()
    var lastGetEndpoint: String? = null
    var lastGetQueryParams: List<Pair<String, String>> = emptyList()
    var lastPostRawUrl: String? = null
    var lastPostRawHeaders: Map<String, String> = emptyMap()
    var lastDeleteEndpoint: String? = null
    var lastPutRawUrl: String? = null
    var lastPutRawHeaders: Map<String, String> = emptyMap()

    override suspend fun get(
        endpoint: String,
        queryParams: List<Pair<String, String>>,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastGetEndpoint = endpoint
        lastGetQueryParams = queryParams
        return when {
            endpoint.contains("/object/info/") && endpoint.contains("missing.png") ->
                SupabaseResult.Failure(SupabaseError(message = "not found", code = "404"))
            endpoint.contains("/object/info/") -> SupabaseResult.Success("""{"name":"a.png"}""")
            endpoint == "/storage/v1/bucket" -> SupabaseResult.Success("""[]""")
            else -> SupabaseResult.Success("ok")
        }
    }

    override suspend fun post(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastPostEndpoint = endpoint
        lastPostBody = body
        lastPostHeaders = headers
        return when {
            endpoint.contains("/object/sign/") && !endpoint.contains("/upload/sign/") ->
                if (endpoint.matches(Regex("/storage/v1/object/sign/[^/]+/.*"))) {
                    SupabaseResult.Success("""{"signed_url":"/sign/single"}""")
                } else {
                    SupabaseResult.Success("""[{"path":"a.png","signedURL":"/sign/a"},{"path":"b.png","signedURL":"/sign/b"}]""")
                }

            endpoint.contains("/upload/sign/") ->
                SupabaseResult.Success("""{"url":"/upload/sign/path","token":"upload-token-123"}""")

            endpoint.contains("/object/list/") ->
                SupabaseResult.Success("""[]""")

            endpoint.contains("/object/remove/") ->
                SupabaseResult.Success("""[{"name":"a.png"}]""")

            else -> SupabaseResult.Success("""{}""")
        }
    }
    override suspend fun put(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastPostEndpoint = endpoint
        lastPostBody = body
        return if (endpoint.startsWith("/storage/v1/bucket/")) {
            SupabaseResult.Success("""{"id":"avatars","name":"avatars","public":true}""")
        } else {
            SupabaseResult.Success("""{}""")
        }
    }

    override suspend fun patch(
        endpoint: String,
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> = SupabaseResult.Success("{}")

    override suspend fun delete(
        endpoint: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastDeleteEndpoint = endpoint
        return SupabaseResult.Success("{}")
    }

    override suspend fun postRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastPostRawUrl = url
        lastPostRawHeaders = headers
        return SupabaseResult.Success("ok")
    }

    override suspend fun putRaw(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastPutRawUrl = url
        lastPutRawHeaders = headers
        return SupabaseResult.Success("ok")
    }

    override fun setAccessToken(token: String) = Unit

    override fun clearAccessToken() = Unit

    override fun close() = Unit
}
