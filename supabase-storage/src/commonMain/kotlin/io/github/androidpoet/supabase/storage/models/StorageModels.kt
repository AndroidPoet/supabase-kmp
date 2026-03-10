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
    @SerialName("expires_in") val expiresIn: Long,
)

@Serializable
public data class SignedUrlResponse(
    @SerialName("signed_url") val signedUrl: String,
)

@Serializable
public data class MoveRequest(
    @SerialName("bucket_id") val bucketId: String,
    @SerialName("source_key") val sourceKey: String,
    @SerialName("destination_key") val destinationKey: String? = null,
    @SerialName("destination_bucket_id") val destinationBucketId: String? = null,
)
