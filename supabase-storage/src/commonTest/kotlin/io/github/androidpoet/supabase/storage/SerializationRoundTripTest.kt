package io.github.androidpoet.supabase.storage

import io.github.androidpoet.supabase.storage.models.Bucket
import io.github.androidpoet.supabase.storage.models.FileObject
import io.github.androidpoet.supabase.storage.models.IcebergNamespaceMetadata
import io.github.androidpoet.supabase.storage.models.SignedUrlItemResponse
import io.github.androidpoet.supabase.storage.models.SignedUrlResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializationRoundTripTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun test_bucket_decodesFromApiJson() {
        val payload =
            """
            {
              "id": "avatars",
              "name": "avatars",
              "owner": "",
              "public": true,
              "created_at": "2024-01-01T00:00:00Z",
              "updated_at": "2024-02-01T00:00:00Z",
              "file_size_limit": 5242880,
              "allowed_mime_types": ["image/png", "image/jpeg"]
            }
            """.trimIndent()

        val bucket = json.decodeFromString<Bucket>(payload)

        assertEquals("avatars", bucket.id)
        assertEquals(true, bucket.public)
        assertEquals(5242880L, bucket.fileSizeLimit)
        assertEquals(listOf("image/png", "image/jpeg"), bucket.allowedMimeTypes)
    }

    @Test
    fun test_bucket_roundTrip() {
        val original =
            Bucket(
                id = "avatars",
                name = "avatars",
                public = false,
                createdAt = "2024-01-01T00:00:00Z",
                updatedAt = "2024-02-01T00:00:00Z",
                fileSizeLimit = 1024,
                allowedMimeTypes = listOf("image/png"),
            )

        val decoded = json.decodeFromString<Bucket>(json.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun test_fileObject_decodesFromApiJson() {
        val payload =
            """
            {
              "name": "photo.png",
              "id": "object-1",
              "bucket_id": "avatars",
              "created_at": "2024-01-01T00:00:00Z",
              "updated_at": "2024-02-01T00:00:00Z",
              "last_accessed_at": "2024-02-02T00:00:00Z",
              "metadata": { "size": 1234, "mimetype": "image/png" }
            }
            """.trimIndent()

        val obj = json.decodeFromString<FileObject>(payload)

        assertEquals("photo.png", obj.name)
        assertEquals("avatars", obj.bucketId)
        assertEquals(JsonPrimitive(1234), obj.metadata?.get("size"))
    }

    @Test
    fun test_fileObject_roundTrip() {
        val original =
            FileObject(
                name = "photo.png",
                id = "object-1",
                bucketId = "avatars",
                createdAt = "2024-01-01T00:00:00Z",
                updatedAt = "2024-02-01T00:00:00Z",
                metadata = JsonObject(mapOf("mimetype" to JsonPrimitive("image/png"))),
            )

        val decoded = json.decodeFromString<FileObject>(json.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun test_signedUrlResponse_decodesFromApiJson() {
        // The storage server returns camelCase `signedURL` (verified against
        // src/http/routes/object/getSignedURL.ts), the same key the batch endpoint
        // uses — NOT snake_case `signed_url`. Decoding the latter failed every call.
        val payload = """{ "signedURL": "/object/sign/avatars/photo.png?token=abc" }"""

        val response = json.decodeFromString<SignedUrlResponse>(payload)

        assertEquals("/object/sign/avatars/photo.png?token=abc", response.signedUrl)
    }

    @Test
    fun test_signedUrlItemResponse_decodesFromApiJson() {
        val payload =
            """
            {
              "path": "avatars/photo.png",
              "signedURL": "/object/sign/avatars/photo.png?token=abc"
            }
            """.trimIndent()

        val response = json.decodeFromString<SignedUrlItemResponse>(payload)

        assertEquals("avatars/photo.png", response.path)
        assertEquals("/object/sign/avatars/photo.png?token=abc", response.signedUrl)
    }

    @Test
    fun test_signedUrlBatch_decodesPartialFailureWithNullSignedUrl() {
        // The batch sign endpoint returns 200 even on partial success: an unsignable
        // path comes back with `signedURL: null` and an `error`. A non-null signedURL
        // field would throw and fail the whole batch; both must be nullable.
        val payload =
            """
            [
              { "path": "ok.png", "signedURL": "/object/sign/avatars/ok.png?token=abc", "error": null },
              { "path": "missing.png", "signedURL": null, "error": "Either the object does not exist or you do not have access to it" }
            ]
            """.trimIndent()

        val items = json.decodeFromString<List<SignedUrlItemResponse>>(payload)

        assertEquals(2, items.size)
        assertEquals("/object/sign/avatars/ok.png?token=abc", items[0].signedUrl)
        assertEquals(null, items[1].signedUrl)
        assertEquals("Either the object does not exist or you do not have access to it", items[1].error)
    }

    @Test
    fun test_signedUrlBatch_decodesWhenPathOmitted() {
        // Some storage-server versions omit the `path` key from batch items entirely
        // (supabase/storage#353). A required `path` would throw MissingFieldException and
        // fail the whole batch decode; it must be optional.
        val payload =
            """
            [
              { "signedURL": "/object/sign/avatars/ok.png?token=abc", "error": null }
            ]
            """.trimIndent()

        val items = json.decodeFromString<List<SignedUrlItemResponse>>(payload)

        assertEquals(1, items.size)
        assertEquals(null, items[0].path)
        assertEquals("/object/sign/avatars/ok.png?token=abc", items[0].signedUrl)
    }

    @Test
    fun test_icebergNamespaceMetadata_decodesWhenNamespaceOmitted() {
        // The metadata response can return only `properties`; a required `namespace`
        // would throw MissingFieldException out of a SupabaseResult-returning call.
        val payload = """{ "properties": { "owner": "team-a" } }"""

        val metadata = json.decodeFromString<IcebergNamespaceMetadata>(payload)

        assertEquals(emptyList(), metadata.namespace)
        assertEquals("team-a", metadata.properties["owner"])
    }
}
