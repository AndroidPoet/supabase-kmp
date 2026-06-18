@file:OptIn(ExperimentalEncodingApi::class)

package io.github.androidpoet.supabase.storage

import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.client.SupabaseHttpMethod
import io.github.androidpoet.supabase.client.SupabaseHttpResponse
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.storage.models.IcebergCreateNamespaceRequest
import io.github.androidpoet.supabase.storage.models.IcebergTableCommitRequest
import io.github.androidpoet.supabase.storage.models.IcebergTableCreateRequest
import io.github.androidpoet.supabase.storage.models.IcebergTableIdentifier
import io.github.androidpoet.supabase.storage.models.IcebergTableRegisterRequest
import io.github.androidpoet.supabase.storage.models.IcebergUpdateNamespacePropertiesRequest
import io.github.androidpoet.supabase.storage.models.VectorData
import io.github.androidpoet.supabase.storage.models.VectorDataType
import io.github.androidpoet.supabase.storage.models.VectorDistanceMetric
import io.github.androidpoet.supabase.storage.models.VectorMetadataConfiguration
import io.github.androidpoet.supabase.storage.models.VectorObject
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StorageClientImplTest {
    @Test
    fun test_resumableUpload_chunksAndCompletes() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)
            val data = ByteArray(10) { it.toByte() }

            val upload = sut.createResumableUpload(bucket = "videos", path = "a/b.bin", data = data, chunkSize = 4)
            val result = upload.await()

            assertTrue(result is SupabaseResult.Success)
            assertEquals(1, client.resumableCreateCount)
            assertEquals(listOf(4, 4, 2), client.resumableChunkSizes)
            assertEquals(10L, upload.progress.value.bytesUploaded)
            assertTrue(upload.progress.value.isComplete)
            assertEquals(client.resumableUploadUrl, upload.uploadUrl)
        }

    @Test
    fun test_resumableUpload_serverOffsetPastEnd_failsInsteadOfSilentSuccess() =
        runTest {
            val client = FakeSupabaseClient()
            client.resumableForcedOffset = 99L // server reports more bytes than the file holds
            val sut = StorageClientImpl(client)
            val data = ByteArray(10) { it.toByte() }

            val upload = sut.createResumableUpload(bucket = "videos", path = "a/b.bin", data = data, chunkSize = 4)
            val result = upload.await()

            // Must surface a failure rather than "succeed" on a corrupt offset.
            assertTrue(result is SupabaseResult.Failure)
        }

    @Test
    fun test_resumableUpload_nonAdvancingOffset_failsInsteadOfLoopingForever() =
        runTest {
            val client = FakeSupabaseClient()
            client.resumableForcedOffset = 0L // server never advances the offset
            val sut = StorageClientImpl(client)
            val data = ByteArray(10) { it.toByte() }

            val upload = sut.createResumableUpload(bucket = "videos", path = "a/b.bin", data = data, chunkSize = 4)
            val result = upload.await()

            // A non-advancing offset would otherwise re-send the same chunk forever.
            assertTrue(result is SupabaseResult.Failure)
            assertEquals(1, client.resumableChunkSizes.size) // bailed after the first PATCH
        }

    @Test
    fun test_resumableUpload_resumeOffsetPastEnd_failsBeforeSending() =
        runTest {
            val client = FakeSupabaseClient()
            client.presetResumableReceived(99L) // HEAD reports offset beyond the file
            val sut = StorageClientImpl(client)
            val data = ByteArray(10) { it.toByte() }

            val upload =
                sut.createResumableUpload(
                    bucket = "videos",
                    path = "a/b.bin",
                    data = data,
                    chunkSize = 4,
                    uploadUrl = client.resumableUploadUrl,
                )
            val result = upload.await()

            assertTrue(result is SupabaseResult.Failure)
            assertEquals(0, client.resumableChunkSizes.size) // no bytes sent
        }

    @Test
    fun test_resumableUpload_resumesFromExistingOffset() =
        runTest {
            val client = FakeSupabaseClient()
            client.presetResumableReceived(6L) // server already holds 6 of 10 bytes
            val sut = StorageClientImpl(client)
            val data = ByteArray(10) { it.toByte() }

            val upload =
                sut.createResumableUpload(
                    bucket = "videos",
                    path = "a/b.bin",
                    data = data,
                    chunkSize = 4,
                    uploadUrl = client.resumableUploadUrl,
                )
            val result = upload.await()

            assertTrue(result is SupabaseResult.Success)
            assertEquals(0, client.resumableCreateCount) // resumed, did not re-create
            assertEquals(listOf(4), client.resumableChunkSizes) // only the remaining 4 bytes
            assertTrue(upload.progress.value.isComplete)
        }

    @Test
    fun test_listBuckets_includesQueryParams() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            val result =
                sut.listBuckets(
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
    fun test_createSignedUrls_usesBatchSignEndpoint() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            val result =
                sut.createSignedUrls(
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
    fun test_listV2_postsOptionsToListV2Endpoint() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            val result =
                sut.listV2(
                    bucket = "avatars",
                    prefix = "folder/",
                    cursor = "cursor-1",
                    limit = 50,
                    withDelimiter = true,
                    sortBy = "created_at",
                    sortOrder = SortOrder.DESC,
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("/storage/v1/object/list-v2/avatars", client.lastPostEndpoint)
            assertEquals(
                """{"prefix":"folder/","cursor":"cursor-1","limit":50,"with_delimiter":true,"sortBy":{"column":"created_at","order":"desc"}}""",
                client.lastPostBody,
            )
            assertEquals(true, result.value.hasNext)
            assertEquals(
                "nested",
                result.value.folders
                    .first()
                    .name,
            )
            assertEquals(
                "avatar.png",
                result.value.objects
                    .first()
                    .name,
            )
            assertEquals("cursor-2", result.value.nextCursor)
        }

    @Test
    fun test_createSignedUrl_withDownload_addsDownloadQuery() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            val result =
                sut.createSignedUrl(
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
    fun test_createSignedUrl_returnsResolvedAbsoluteUrl() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            val result =
                sut.createSignedUrl(
                    bucket = "avatars",
                    path = "a.png",
                    expiresIn = 120,
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("https://example.supabase.co/sign/single", result.value)
        }

    @Test
    fun test_createUploadSignedUrl_returnsToken() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            val result =
                sut.createUploadSignedUrl(
                    bucket = "avatars",
                    path = "a.png",
                )

            assertEquals("/storage/v1/object/upload/sign/avatars/a.png", client.lastPostEndpoint)
            assertTrue(result is SupabaseResult.Success)
            assertEquals("upload-token-123", result.value)
        }

    @Test
    fun test_createUploadSignedUrl_withUpsert_setsUpsertHeader() =
        runTest {
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
    fun test_createUploadSignedUrlWithPath_returnsResolvedUrlAndToken() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            val result =
                sut.createUploadSignedUrlWithPath(
                    bucket = "avatars",
                    path = "a.png",
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals("https://example.supabase.co/upload/sign/path", result.value.url)
            assertEquals("upload-token-123", result.value.token)
        }

    @Test
    fun test_updateBucket_usesPutBucketEndpoint() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            val result =
                sut.updateBucket(
                    id = "avatars",
                    public = true,
                )

            assertEquals("/storage/v1/bucket/avatars", client.lastPostEndpoint)
            assertTrue(result is SupabaseResult.Success)
        }

    @Test
    fun test_uploadToSignedUrl_usesSignedUploadPathWithToken() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            sut.uploadToSignedUrl(
                bucket = "avatars",
                path = "a.png",
                token = "abc",
                data = byteArrayOf(1, 2, 3),
                upsert = true,
            )

            assertEquals("/storage/v1/object/upload/sign/avatars/a.png?token=abc", client.lastPutRawUrl)
            assertEquals("true", client.lastPutRawHeaders["x-upsert"])
        }

    @Test
    fun test_uploadToSignedUrl_withCacheControl_sendsCacheControlHeader() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            sut.uploadToSignedUrl(
                bucket = "avatars",
                path = "a.png",
                token = "abc",
                data = byteArrayOf(1, 2, 3),
                cacheControl = 3600,
            )

            // cacheControl is a request header, not a query param — the server ignores the query form.
            assertEquals("/storage/v1/object/upload/sign/avatars/a.png?token=abc", client.lastPutRawUrl)
            assertEquals("max-age=3600", client.lastPutRawHeaders["Cache-Control"])
        }

    @Test
    fun test_update_usesPutRawAndUpsertHeader() =
        runTest {
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
    fun test_upload_withCacheControl_sendsCacheControlHeader() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            sut.upload(
                bucket = "avatars",
                path = "a.png",
                data = byteArrayOf(1),
                cacheControl = 1200,
            )

            assertEquals("/storage/v1/object/avatars/a.png", client.lastPostRawUrl)
            assertEquals("max-age=1200", client.lastPostRawHeaders["Cache-Control"])
        }

    @Test
    fun test_update_withCacheControl_sendsCacheControlHeader() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            sut.update(
                bucket = "avatars",
                path = "a.png",
                data = byteArrayOf(1),
                cacheControl = 900,
            )

            assertEquals("/storage/v1/object/avatars/a.png", client.lastPutRawUrl)
            assertEquals("max-age=900", client.lastPutRawHeaders["Cache-Control"])
        }

    @Test
    fun test_copy_usesCopyEndpoint() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            val result =
                sut.copy(
                    bucket = "avatars",
                    fromPath = "old.png",
                    toPath = "new.png",
                )

            assertEquals("/storage/v1/object/copy", client.lastPostEndpoint)
            assertTrue(result is SupabaseResult.Success)
        }

    @Test
    fun test_copy_includesCopyMetadataWhenProvided() =
        runTest {
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
    fun test_removeWithResult_returnsDeletedFileEntries() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            val result = sut.removeWithResult(bucket = "avatars", paths = listOf("a.png"))

            assertEquals("/storage/v1/object/avatars", client.lastDeleteEndpoint)
            assertTrue(result is SupabaseResult.Success)
            assertEquals(1, result.value.size)
            assertEquals("a.png", result.value.first().name)
        }

    @Test
    fun test_move_allowsDestinationBucket() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            val result =
                sut.move(
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
    fun test_deleteObject_usesDeleteObjectEndpoint() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            val result = sut.deleteObject(bucket = "avatars", path = "a.png")

            assertEquals("/storage/v1/object/avatars/a.png", client.lastDeleteEndpoint)
            assertTrue(result is SupabaseResult.Success)
        }

    @Test
    fun test_downloadPublic_usesPublicEndpoint() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            sut.downloadPublic(bucket = "avatars", path = "a.png")

            assertEquals("/storage/v1/object/public/avatars/a.png", client.lastGetEndpoint)
        }

    @Test
    fun test_download_usesAuthenticatedEndpoint() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            sut.download(bucket = "avatars", path = "a.png")

            assertEquals("/storage/v1/object/authenticated/avatars/a.png", client.lastGetEndpoint)
        }

    @Test
    fun test_download_withDownloadFileName_appendsDownloadQuery() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            sut.download(bucket = "avatars", path = "a.png", download = true, fileName = "avatar.png")

            assertEquals("/storage/v1/object/authenticated/avatars/a.png?download=avatar.png", client.lastGetEndpoint)
        }

    @Test
    fun test_getSignedDownloadUrl_withFileName_buildsExpectedUrl() {
        val sut = StorageClientImpl(FakeSupabaseClient())

        val url =
            sut.getSignedDownloadUrl(
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
    fun test_info_usesAuthenticatedInfoEndpoint() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            val result = sut.info(bucket = "avatars", path = "a.png")

            assertTrue(result is SupabaseResult.Success)
            assertEquals("/storage/v1/object/info/avatars/a.png", client.lastGetEndpoint)
            assertEquals("a.png", result.value.name)
        }

    @Test
    fun test_infoPublic_usesPublicInfoEndpoint() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            val result = sut.infoPublic(bucket = "avatars", path = "a.png")

            assertTrue(result is SupabaseResult.Success)
            assertEquals("/storage/v1/object/info/public/avatars/a.png", client.lastGetEndpoint)
            assertEquals("a.png", result.value.name)
        }

    @Test
    fun test_exists_returnsTrueWhenInfoExists() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            val result = sut.exists(bucket = "avatars", path = "a.png")

            assertTrue(result is SupabaseResult.Success)
            assertEquals(true, result.value)
        }

    @Test
    fun test_exists_returnsFalseWhenInfoNotFound() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            val result = sut.exists(bucket = "avatars", path = "missing.png")

            assertTrue(result is SupabaseResult.Success)
            assertEquals(false, result.value)
        }

    @Test
    fun test_list_includesSortOrderAndSearchInBody() =
        runTest {
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
    fun test_createSignedUrl_withTransform_includesTransformPayload() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            sut.createSignedUrl(
                bucket = "avatars",
                path = "a.png",
                expiresIn = 120,
                transform =
                    ImageTransformOptions(
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

        val url =
            sut.getPublicUrl(
                bucket = "avatars",
                path = "a.png",
                transform =
                    ImageTransformOptions(
                        width = 100,
                        height = 100,
                        resize = ResizeMode.FILL,
                        format = "webp",
                    ),
            )

        assertEquals(
            "https://example.supabase.co/storage/v1/render/image/public/avatars/a.png?width=100&height=100&resize=fill&format=webp",
            url,
        )
    }

    @Test
    fun test_getPublicUrl_withDownloadAndTransform_buildsCombinedQuery() {
        val sut = StorageClientImpl(FakeSupabaseClient())

        val url =
            sut.getPublicUrl(
                bucket = "avatars",
                path = "a.png",
                download = true,
                fileName = "avatar.png",
                transform = ImageTransformOptions(width = 50),
            )

        assertEquals(
            "https://example.supabase.co/storage/v1/render/image/public/avatars/a.png?width=50&download=avatar.png",
            url,
        )
    }

    @Test
    fun test_getAuthenticatedUrl_withTransform_includesQueryParams() {
        val sut = StorageClientImpl(FakeSupabaseClient())

        val url =
            sut.getAuthenticatedUrl(
                bucket = "avatars",
                path = "a.png",
                transform =
                    ImageTransformOptions(
                        width = 80,
                        height = 120,
                        resize = ResizeMode.CONTAIN,
                    ),
            )

        assertEquals(
            "https://example.supabase.co/storage/v1/render/image/authenticated/avatars/a.png?width=80&height=120&resize=contain",
            url,
        )
    }

    @Test
    fun test_getAuthenticatedUrl_withDownloadAndTransform_buildsCombinedQuery() {
        val sut = StorageClientImpl(FakeSupabaseClient())

        val url =
            sut.getAuthenticatedUrl(
                bucket = "avatars",
                path = "a.png",
                download = true,
                fileName = "auth-avatar.png",
                transform = ImageTransformOptions(width = 50),
            )

        assertEquals(
            "https://example.supabase.co/storage/v1/render/image/authenticated/avatars/a.png?width=50&download=auth-avatar.png",
            url,
        )
    }

    @Test
    fun test_getPublicRenderUrl_withTransform_usesRenderEndpoint() {
        val sut = StorageClientImpl(FakeSupabaseClient())

        val url =
            sut.getPublicRenderUrl(
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

        val url =
            sut.getAuthenticatedRenderUrl(
                bucket = "avatars",
                path = "a.png",
                transform = ImageTransformOptions(height = 300, resize = ResizeMode.FILL),
            )

        assertEquals(
            "https://example.supabase.co/storage/v1/render/image/authenticated/avatars/a.png?height=300&resize=fill",
            url,
        )
    }

    @Test
    fun test_analyticsBucketMethods_useIcebergEndpoints() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            val created = sut.createAnalyticsBucket("events")
            val listed =
                sut.listAnalyticsBuckets(
                    limit = 10,
                    offset = 2,
                    sortColumn = "created_at",
                    sortOrder = SortOrder.DESC,
                    search = "event",
                )
            val deleted = sut.deleteAnalyticsBucket("events")

            assertTrue(created is SupabaseResult.Success)
            assertEquals("events", created.value.name)
            assertTrue(listed is SupabaseResult.Success)
            assertEquals("events", listed.value.first().name)
            assertEquals("/storage/v1/iceberg/bucket/events", client.lastDeleteEndpoint)
            assertTrue(deleted is SupabaseResult.Success)
        }

    @Test
    fun test_analyticsCatalog_namespaceMethods_useIcebergRestCatalogEndpoints() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)
            val catalog = sut.analyticsCatalog("events")

            val namespaces = catalog.listNamespaces(pageSize = 10)
            assertTrue(namespaces is SupabaseResult.Success)
            assertEquals("/storage/v1/iceberg/v1/catalog-prefix/namespaces?pageSize=10", client.lastGetEndpoint)

            val created =
                catalog.createNamespace(
                    namespace = listOf("prod", "events"),
                    properties = mapOf("owner" to "data-team"),
                )
            assertTrue(created is SupabaseResult.Success)
            assertTrue(client.lastPostBody?.contains("\"namespace\":[\"prod\",\"events\"]") == true)

            val metadata = catalog.loadNamespaceMetadata(listOf("prod", "events"))
            val updated =
                catalog.updateNamespaceProperties(
                    namespace = listOf("prod", "events"),
                    removals = listOf("old"),
                    updates = mapOf("owner" to "platform"),
                )
            val dropped = catalog.dropNamespace(listOf("prod", "events"))

            assertTrue(metadata is SupabaseResult.Success)
            assertTrue(updated is SupabaseResult.Success)
            assertTrue(client.lastPostBody?.contains("\"removals\":[\"old\"]") == true)
            assertEquals("/storage/v1/iceberg/v1/catalog-prefix/namespaces/prod%1Fevents", client.lastDeleteEndpoint)
            assertTrue(dropped is SupabaseResult.Success)
        }

    @Test
    fun test_analyticsCatalog_tableMethods_useIcebergRestCatalogEndpoints() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)
            val catalog = sut.analyticsCatalog("events")
            val tableRequest =
                buildJsonObject {
                    put("name", "clicks")
                    put("schema", buildJsonObject { put("type", "struct") })
                }

            val tables = catalog.listTables(namespace = listOf("prod"), pageToken = "p1", pageSize = 20)
            assertTrue(tables is SupabaseResult.Success)
            assertEquals("/storage/v1/iceberg/v1/catalog-prefix/namespaces/prod/tables?pageToken=p1&pageSize=20", client.lastGetEndpoint)

            val created = catalog.createTable(namespace = listOf("prod"), request = tableRequest)
            val loaded = catalog.loadTable(namespace = listOf("prod"), name = "clicks", snapshots = "all")
            val updated = catalog.updateTable(namespace = listOf("prod"), name = "clicks", request = buildJsonObject { put("updates", "[]") })
            val registered = catalog.registerTable(namespace = listOf("prod"), request = buildJsonObject { put("name", "external") })
            val renamed = catalog.renameTable(buildJsonObject { put("source", "clicks") })
            val dropped = catalog.dropTable(namespace = listOf("prod"), name = "clicks", purge = true)

            assertTrue(created is SupabaseResult.Success)
            assertEquals("clicks", created.value["name"]?.toString()?.trim('"'))
            assertTrue(loaded is SupabaseResult.Success)
            assertTrue(updated is SupabaseResult.Success)
            assertTrue(registered is SupabaseResult.Success)
            assertEquals("/storage/v1/iceberg/v1/catalog-prefix/tables/rename", client.lastPostEndpoint)
            assertTrue(renamed is SupabaseResult.Success)
            assertEquals("/storage/v1/iceberg/v1/catalog-prefix/namespaces/prod/tables/clicks?purgeRequested=true", client.lastDeleteEndpoint)
            assertTrue(dropped is SupabaseResult.Success)
        }

    @Test
    fun test_analyticsCatalogTypedMethods_decodeIcebergModels() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)
            val catalog = sut.analyticsCatalog("events")

            val config = catalog.loadConfigTyped()
            val namespaces = catalog.listNamespacesTyped(pageSize = 10)
            val namespace =
                catalog.createNamespaceTyped(
                    IcebergCreateNamespaceRequest(
                        namespace = listOf("prod", "events"),
                        properties = mapOf("owner" to "data-team"),
                    ),
                )
            val namespaceMetadata = catalog.loadNamespaceMetadataTyped(listOf("prod", "events"))
            val namespaceUpdate =
                catalog.updateNamespacePropertiesTyped(
                    namespace = listOf("prod", "events"),
                    request =
                        IcebergUpdateNamespacePropertiesRequest(
                            removals = listOf("old"),
                            updates = mapOf("owner" to "platform"),
                        ),
                )
            val tables = catalog.listTablesTyped(namespace = listOf("prod"))
            val table =
                catalog.createTableTyped(
                    namespace = listOf("prod"),
                    request =
                        IcebergTableCreateRequest(
                            name = "clicks",
                            schema = buildJsonObject { put("type", "struct") },
                        ),
                )
            val loaded = catalog.loadTableTyped(namespace = listOf("prod"), name = "clicks")
            val committed =
                catalog.commitTableTyped(
                    namespace = listOf("prod"),
                    name = "clicks",
                    request = IcebergTableCommitRequest(updates = listOf(buildJsonObject { put("action", "noop") })),
                )
            val registered =
                catalog.registerTableTyped(
                    namespace = listOf("prod"),
                    request = IcebergTableRegisterRequest(name = "external", metadataLocation = "s3://bucket/metadata.json"),
                )
            val renamed =
                catalog.renameTableTyped(
                    source = IcebergTableIdentifier(namespace = listOf("prod"), name = "clicks"),
                    destination = IcebergTableIdentifier(namespace = listOf("prod"), name = "clicks_renamed"),
                )

            assertTrue(config is SupabaseResult.Success)
            assertEquals("catalog-prefix", config.value.overrides["prefix"])
            assertTrue(namespaces is SupabaseResult.Success)
            assertEquals(listOf("prod", "events"), namespaces.value.namespaces.first())
            assertTrue(namespace is SupabaseResult.Success)
            assertEquals("data-team", namespace.value.properties["owner"])
            assertTrue(namespaceMetadata is SupabaseResult.Success)
            assertEquals(listOf("prod", "events"), namespaceMetadata.value.namespace)
            assertTrue(namespaceUpdate is SupabaseResult.Success)
            assertEquals(listOf("owner"), namespaceUpdate.value.updated)
            assertTrue(tables is SupabaseResult.Success)
            assertEquals(
                "clicks",
                tables.value.identifiers
                    .first()
                    .name,
            )
            assertTrue(table is SupabaseResult.Success)
            assertEquals("clicks", table.value.name)
            assertTrue(loaded is SupabaseResult.Success)
            assertEquals(
                2,
                loaded.value.metadata
                    ?.get("format-version")
                    ?.toString()
                    ?.toInt(),
            )
            assertTrue(committed is SupabaseResult.Success)
            assertTrue(registered is SupabaseResult.Success)
            assertEquals("/storage/v1/iceberg/v1/catalog-prefix/tables/rename", client.lastPostEndpoint)
            assertTrue(client.lastPostBody?.contains("\"destination\"") == true)
            assertTrue(renamed is SupabaseResult.Success)
        }

    @Test
    fun test_vectorBucketAndIndexMethods_useVectorActionEndpoints() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            val createBucket = sut.createVectorBucket("embeddings")
            val bucket = sut.getVectorBucket("embeddings")
            val buckets = sut.listVectorBuckets(prefix = "emb", maxResults = 10, nextToken = "n1")
            val createIndex =
                sut.createVectorIndex(
                    vectorBucketName = "embeddings",
                    indexName = "documents",
                    dataType = VectorDataType.FLOAT32,
                    dimension = 1536,
                    distanceMetric = VectorDistanceMetric.COSINE,
                    metadataConfiguration = VectorMetadataConfiguration(nonFilterableMetadataKeys = listOf("raw_text")),
                )
            val index = sut.getVectorIndex("embeddings", "documents")
            val indexes = sut.listVectorIndexes("embeddings", prefix = "doc", maxResults = 5)
            val deleteIndex = sut.deleteVectorIndex("embeddings", "documents")
            val deleteBucket = sut.deleteVectorBucket("embeddings")

            assertTrue(createBucket is SupabaseResult.Success)
            assertTrue(bucket is SupabaseResult.Success)
            assertEquals("embeddings", bucket.value.vectorBucketName)
            assertTrue(buckets is SupabaseResult.Success)
            assertEquals(
                "embeddings",
                buckets.value.vectorBuckets
                    .first()
                    .vectorBucketName,
            )
            assertTrue(createIndex is SupabaseResult.Success)
            assertTrue(index is SupabaseResult.Success)
            assertEquals("documents", index.value.indexName)
            assertEquals(VectorDistanceMetric.COSINE, index.value.distanceMetric)
            assertTrue(indexes is SupabaseResult.Success)
            assertEquals(
                "documents",
                indexes.value.indexes
                    .first()
                    .indexName,
            )
            assertTrue(deleteIndex is SupabaseResult.Success)
            assertEquals("/storage/v1/vector/DeleteVectorBucket", client.lastPostEndpoint)
            assertTrue(client.lastPostBody?.contains("\"vectorBucketName\":\"embeddings\"") == true)
            assertTrue(deleteBucket is SupabaseResult.Success)
        }

    @Test
    fun test_vectorDataMethods_useVectorActionEndpointsAndPayloads() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)
            val vector =
                VectorObject(
                    key = "doc-1",
                    data = VectorData(float32 = listOf(0.1, 0.2)),
                    metadata = buildJsonObject { put("category", "docs") },
                )

            val putResult = sut.putVectors("embeddings", "documents", listOf(vector))
            val getResult = sut.getVectors("embeddings", "documents", keys = listOf("doc-1"), returnData = true, returnMetadata = true)
            val listResult = sut.listVectors("embeddings", "documents", maxResults = 10, segmentCount = 2, segmentIndex = 1)
            val queryResult =
                sut.queryVectors(
                    vectorBucketName = "embeddings",
                    indexName = "documents",
                    queryVector = VectorData(float32 = listOf(0.1, 0.2)),
                    topK = 5,
                    filter = buildJsonObject { put("category", "docs") },
                    returnDistance = true,
                    returnMetadata = true,
                )
            val deleteResult = sut.deleteVectors("embeddings", "documents", keys = listOf("doc-1"))

            assertTrue(putResult is SupabaseResult.Success)
            assertTrue(getResult is SupabaseResult.Success)
            assertEquals("doc-1", getResult.value.first().key)
            assertTrue(listResult is SupabaseResult.Success)
            assertEquals("cursor-2", listResult.value.nextToken)
            assertTrue(queryResult is SupabaseResult.Success)
            assertEquals(VectorDistanceMetric.COSINE, queryResult.value.distanceMetric)
            assertEquals("/storage/v1/vector/DeleteVectors", client.lastPostEndpoint)
            assertTrue(client.lastPostBody?.contains("\"keys\":[\"doc-1\"]") == true)
            assertTrue(deleteResult is SupabaseResult.Success)
        }

    @Test
    fun test_putVectors_rejectsInvalidBatchSize() =
        runTest {
            val sut = StorageClientImpl(FakeSupabaseClient())

            assertFailsWith<IllegalArgumentException> {
                sut.putVectors("embeddings", "documents", emptyList())
            }
        }

    @Test
    fun test_upload_withMetadata_setsBase64MetadataHeader() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)
            val meta = buildJsonObject { put("owner", "alice") }

            sut.upload(
                bucket = "avatars",
                path = "a.png",
                data = byteArrayOf(1),
                metadata = meta,
            )

            val encoded = client.lastPostRawHeaders["x-metadata"]
            assertEquals(Base64.encode(meta.toString().encodeToByteArray()), encoded)
        }

    @Test
    fun test_upload_withoutMetadata_omitsMetadataHeader() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            sut.upload(bucket = "avatars", path = "a.png", data = byteArrayOf(1))

            assertTrue(!client.lastPostRawHeaders.containsKey("x-metadata"))
        }

    @Test
    fun test_update_withMetadata_setsBase64MetadataHeader() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)
            val meta = buildJsonObject { put("tag", "v2") }

            sut.update(
                bucket = "avatars",
                path = "a.png",
                data = byteArrayOf(1),
                metadata = meta,
            )

            assertEquals(
                Base64.encode(meta.toString().encodeToByteArray()),
                client.lastPutRawHeaders["x-metadata"],
            )
        }

    @Test
    fun test_uploadToSignedUrl_withMetadata_setsBase64MetadataHeader() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)
            val meta = buildJsonObject { put("k", "v") }

            sut.uploadToSignedUrl(
                bucket = "avatars",
                path = "a.png",
                token = "abc",
                data = byteArrayOf(1, 2, 3),
                metadata = meta,
            )

            assertEquals(
                Base64.encode(meta.toString().encodeToByteArray()),
                client.lastPutRawHeaders["x-metadata"],
            )
        }

    @Test
    fun test_createUploadSignedUrlWithPath_returnsServerPath() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            val result = sut.createUploadSignedUrlWithPath(bucket = "avatars", path = "a.png")

            assertTrue(result is SupabaseResult.Success)
            assertEquals("avatars/a.png", result.value.path)
        }

    @Test
    fun test_info_decodesWidenedServerFields() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            val result = sut.info(bucket = "avatars", path = "meta.png")

            assertTrue(result is SupabaseResult.Success)
            assertEquals(2048L, result.value.size)
            assertEquals("image/png", result.value.contentType)
            assertEquals("\"abc123\"", result.value.etag)
            assertEquals("2026-01-02T00:00:00Z", result.value.lastAccessedAt)
            assertEquals("max-age=3600", result.value.cacheControl)
        }

    @Test
    fun test_downloadBytes_withTransform_usesAuthenticatedRenderEndpoint() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            val result =
                sut.downloadBytes(
                    bucket = "avatars",
                    path = "a.png",
                    transform = ImageTransformOptions(width = 100, resize = ResizeMode.COVER),
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals(
                "/storage/v1/render/image/authenticated/avatars/a.png?width=100&resize=cover",
                client.lastRawRequestUrl,
            )
        }

    @Test
    fun test_downloadPublicBytes_withTransformAndDownload_usesPublicRenderEndpoint() =
        runTest {
            val client = FakeSupabaseClient()
            val sut = StorageClientImpl(client)

            val result =
                sut.downloadPublicBytes(
                    bucket = "avatars",
                    path = "a.png",
                    transform = ImageTransformOptions(height = 50),
                    download = true,
                    fileName = "out.png",
                )

            assertTrue(result is SupabaseResult.Success)
            assertEquals(
                "/storage/v1/render/image/public/avatars/a.png?height=50&download=out.png",
                client.lastRawRequestUrl,
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
    var lastRawRequestUrl: String? = null

    // Resumable (TUS) upload simulation state.
    val resumableUploadUrl: String = "https://example.supabase.co/storage/v1/upload/resumable/upload-1"
    var resumableCreateCount: Int = 0
    val resumableChunkSizes: MutableList<Int> = mutableListOf()
    var resumableMetadata: String? = null
    var resumableUpsertHeader: String? = null
    private var resumableReceived: Long = 0L

    // When set, PATCH/HEAD report this Upload-Offset instead of the real running
    // total — used to simulate a misbehaving server (offset past end, or one that
    // never advances).
    var resumableForcedOffset: Long? = null

    fun presetResumableReceived(bytes: Long) {
        resumableReceived = bytes
    }

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
            endpoint.contains("/object/info/") && endpoint.contains("meta.png") ->
                SupabaseResult.Success(
                    """{"name":"meta.png","size":2048,"content_type":"image/png",""" +
                        """"etag":"\"abc123\"","last_accessed_at":"2026-01-02T00:00:00Z",""" +
                        """"cache_control":"max-age=3600"}""",
                )
            endpoint.contains("/object/info/") -> SupabaseResult.Success("""{"name":"a.png"}""")
            endpoint == "/storage/v1/bucket" -> SupabaseResult.Success("""[]""")
            endpoint == "/storage/v1/iceberg/v1/config" ->
                SupabaseResult.Success("""{"overrides":{"prefix":"catalog-prefix"}}""")
            endpoint.startsWith("/storage/v1/iceberg/bucket") ->
                SupabaseResult.Success("""[{"name":"events","type":"ANALYTICS","format":"iceberg","created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-01T00:00:00Z"}]""")
            endpoint.contains("/storage/v1/iceberg/v1/catalog-prefix/namespaces") &&
                endpoint.contains("/tables/clicks") ->
                SupabaseResult.Success("""{"name":"clicks","metadata":{"format-version":2}}""")
            endpoint.contains("/storage/v1/iceberg/v1/catalog-prefix/namespaces") &&
                endpoint.contains("/tables") ->
                SupabaseResult.Success("""{"identifiers":[{"namespace":["prod"],"name":"clicks"}]}""")
            endpoint == "/storage/v1/iceberg/v1/catalog-prefix/namespaces/prod%1Fevents" ->
                SupabaseResult.Success("""{"namespace":["prod","events"],"properties":{"owner":"data-team"}}""")
            endpoint.contains("/storage/v1/iceberg/v1/catalog-prefix/namespaces") ->
                SupabaseResult.Success("""{"namespaces":[["prod","events"]]}""")
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
                    // Server returns camelCase `signedURL` (same as the batch endpoint).
                    SupabaseResult.Success("""{"signedURL":"/sign/single"}""")
                } else {
                    SupabaseResult.Success("""[{"path":"a.png","signedURL":"/sign/a"},{"path":"b.png","signedURL":"/sign/b"}]""")
                }

            endpoint.contains("/upload/sign/") ->
                SupabaseResult.Success("""{"url":"/upload/sign/path","token":"upload-token-123","path":"avatars/a.png"}""")

            endpoint.contains("/object/list-v2/") ->
                SupabaseResult.Success(
                    """{"hasNext":true,"nextCursor":"cursor-2","folders":[{"name":"nested","key":"folder/nested/"}],"objects":[{"name":"avatar.png","key":"folder/avatar.png","id":"file-1","updated_at":"2026-01-01T00:00:00Z","created_at":"2026-01-01T00:00:00Z","last_accessed_at":"2026-01-01T00:00:00Z","metadata":{"size":123}}]}""",
                )

            endpoint.contains("/object/list/") ->
                SupabaseResult.Success("""[]""")

            endpoint == "/storage/v1/iceberg/bucket" && body?.contains("\"name\":\"events\"") == true ->
                SupabaseResult.Success("""{"name":"events","type":"ANALYTICS","format":"iceberg","created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-01T00:00:00Z"}""")
            endpoint.contains("/storage/v1/iceberg/v1/catalog-prefix/namespaces") &&
                endpoint.contains("/tables") ->
                SupabaseResult.Success("""{"name":"clicks","metadata":{"format-version":2}}""")
            endpoint.contains("/storage/v1/iceberg/v1/catalog-prefix/namespaces") ->
                SupabaseResult.Success("""{"namespace":["prod","events"],"properties":{"owner":"data-team"},"updated":["owner"],"removed":["old"]}""")
            endpoint == "/storage/v1/iceberg/v1/catalog-prefix/tables/rename" ->
                SupabaseResult.Success("""{}""")

            endpoint == "/storage/v1/vector/GetVectorBucket" ->
                SupabaseResult.Success("""{"vectorBucket":{"vectorBucketName":"embeddings","creationTime":1893456000}}""")

            endpoint == "/storage/v1/vector/ListVectorBuckets" ->
                SupabaseResult.Success("""{"vectorBuckets":[{"vectorBucketName":"embeddings"}],"nextToken":"n2"}""")

            endpoint == "/storage/v1/vector/GetIndex" ->
                SupabaseResult.Success("""{"index":{"indexName":"documents","vectorBucketName":"embeddings","dataType":"float32","dimension":1536,"distanceMetric":"cosine","creationTime":1893456000}}""")

            endpoint == "/storage/v1/vector/ListIndexes" ->
                SupabaseResult.Success("""{"indexes":[{"indexName":"documents"}],"nextToken":"n2"}""")

            endpoint == "/storage/v1/vector/GetVectors" ->
                SupabaseResult.Success("""{"vectors":[{"key":"doc-1","data":{"float32":[0.1,0.2]},"metadata":{"category":"docs"}}]}""")

            endpoint == "/storage/v1/vector/ListVectors" ->
                SupabaseResult.Success("""{"vectors":[{"key":"doc-1"}],"nextToken":"cursor-2"}""")

            endpoint == "/storage/v1/vector/QueryVectors" ->
                SupabaseResult.Success("""{"vectors":[{"key":"doc-1","distance":0.01,"metadata":{"category":"docs"}}],"distanceMetric":"cosine"}""")

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
        body: String?,
        headers: Map<String, String>,
    ): SupabaseResult<String> {
        lastDeleteEndpoint = endpoint
        return when {
            // Object removal: DELETE /object/{bucket} with {"prefixes":[...]} returns the deleted entries.
            endpoint.startsWith("/storage/v1/object/") -> SupabaseResult.Success("""[{"name":"a.png"}]""")
            else -> SupabaseResult.Success("{}")
        }
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

    override suspend fun rawRequest(
        method: SupabaseHttpMethod,
        url: String,
        body: ByteArray?,
        contentType: String?,
        headers: Map<String, String>,
    ): SupabaseResult<SupabaseHttpResponse> =
        when (method) {
            SupabaseHttpMethod.GET -> {
                lastRawRequestUrl = url
                SupabaseResult.Success(SupabaseHttpResponse(200, emptyMap(), byteArrayOf(7, 8, 9)))
            }
            SupabaseHttpMethod.POST -> {
                resumableCreateCount++
                resumableReceived = 0L
                resumableMetadata = headers["Upload-Metadata"]
                resumableUpsertHeader = headers["x-upsert"]
                SupabaseResult.Success(
                    SupabaseHttpResponse(201, mapOf("Location" to resumableUploadUrl), ByteArray(0)),
                )
            }
            SupabaseHttpMethod.HEAD ->
                SupabaseResult.Success(
                    SupabaseHttpResponse(
                        200,
                        mapOf("Upload-Offset" to (resumableForcedOffset ?: resumableReceived).toString()),
                        ByteArray(0),
                    ),
                )
            SupabaseHttpMethod.PATCH -> {
                resumableChunkSizes.add(body?.size ?: 0)
                resumableReceived += (body?.size ?: 0).toLong()
                SupabaseResult.Success(
                    SupabaseHttpResponse(
                        204,
                        mapOf("Upload-Offset" to (resumableForcedOffset ?: resumableReceived).toString()),
                        ByteArray(0),
                    ),
                )
            }
            else -> SupabaseResult.Failure(SupabaseError("unexpected method $method"))
        }

    override fun setAccessToken(token: String) = Unit

    override fun clearAccessToken() = Unit

    override fun close() = Unit
}
