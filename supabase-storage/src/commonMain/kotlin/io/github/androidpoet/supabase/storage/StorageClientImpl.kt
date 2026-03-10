package io.github.androidpoet.supabase.storage

import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.client.defaultJson
import io.github.androidpoet.supabase.client.deserialize
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.core.result.map
import io.github.androidpoet.supabase.storage.models.Bucket
import io.github.androidpoet.supabase.storage.models.CreateBucketRequest
import io.github.androidpoet.supabase.storage.models.FileObject
import io.github.androidpoet.supabase.storage.models.MoveRequest
import io.github.androidpoet.supabase.storage.models.SignedUrlRequest
import io.github.androidpoet.supabase.storage.models.SignedUrlResponse
import kotlinx.serialization.encodeToString

/**
 * Internal implementation of [StorageClient] backed by [SupabaseClient].
 *
 * All requests target the Supabase Storage v1 endpoints, with the project URL
 * resolved by the underlying client.
 */
internal class StorageClientImpl(
    private val client: SupabaseClient,
) : StorageClient {

    override suspend fun listBuckets(): SupabaseResult<List<Bucket>> =
        client.get("/storage/v1/bucket").deserialize()

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
    ): SupabaseResult<String> {
        val headers = buildMap {
            if (upsert) put("x-upsert", "true")
        }
        return client.postRaw(
            url = "/storage/v1/object/$bucket/$path",
            body = data,
            contentType = contentType,
            headers = headers,
        )
    }

    override suspend fun download(bucket: String, path: String): SupabaseResult<String> =
        client.get("/storage/v1/object/$bucket/$path")

    override suspend fun list(
        bucket: String,
        prefix: String,
        limit: Int,
        offset: Int,
        sortBy: String?,
    ): SupabaseResult<List<FileObject>> {
        val bodyMap = buildMap<String, Any> {
            put("prefix", prefix)
            put("limit", limit)
            put("offset", offset)
            if (sortBy != null) put("sortBy", mapOf("column" to sortBy, "order" to "asc"))
        }
        val body = defaultJson.encodeToString(bodyMap)
        return client.post("/storage/v1/object/list/$bucket", body = body).deserialize()
    }

    override suspend fun move(
        bucket: String,
        fromPath: String,
        toPath: String,
    ): SupabaseResult<Unit> {
        val body = defaultJson.encodeToString(
            MoveRequest(
                bucketId = bucket,
                sourceKey = fromPath,
                destinationKey = toPath,
            ),
        )
        return client.post("/storage/v1/object/move", body = body).map { }
    }

    override suspend fun remove(bucket: String, paths: List<String>): SupabaseResult<Unit> {
        val body = defaultJson.encodeToString(mapOf("prefixes" to paths))
        return client.post(
            endpoint = "/storage/v1/object/remove/$bucket",
            body = body,
        ).map { }
    }

    override suspend fun createSignedUrl(
        bucket: String,
        path: String,
        expiresIn: Long,
    ): SupabaseResult<String> {
        val body = defaultJson.encodeToString(SignedUrlRequest(expiresIn = expiresIn))
        return client.post("/storage/v1/object/sign/$bucket/$path", body = body)
            .deserialize<SignedUrlResponse>()
            .map { it.signedUrl }
    }

    override fun getPublicUrl(bucket: String, path: String): String =
        "${client.projectUrl}/storage/v1/object/public/$bucket/$path"
}
