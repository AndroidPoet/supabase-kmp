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
    @SerialName("transform") val transform: SignedUrlTransformRequest? = null,
)

@Serializable
public data class SignedUrlTransformRequest(
    val width: Int? = null,
    val height: Int? = null,
    val resize: String? = null,
    val format: String? = null,
    val quality: Int? = null,
)

@Serializable
public data class SignedUrlResponse(
    @SerialName("signed_url") val signedUrl: String,
)

@Serializable
public data class SignedUrlsRequest(
    @SerialName("paths") val paths: List<String>,
    @SerialName("expires_in") val expiresIn: Long,
)

@Serializable
public data class SignedUrlItemResponse(
    @SerialName("path") val path: String,
    @SerialName("signedURL") val signedUrl: String,
)

@Serializable
public data class UploadSignedUrlResponse(
    @SerialName("url") val url: String,
    @SerialName("token") val token: String,
)

@Serializable
public data class UpdateBucketRequest(
    val id: String? = null,
    val name: String? = null,
    val public: Boolean? = null,
    @SerialName("file_size_limit") val fileSizeLimit: Long? = null,
    @SerialName("allowed_mime_types") val allowedMimeTypes: List<String>? = null,
)

@Serializable
public data class MoveRequest(
    val bucketId: String,
    val sourceKey: String,
    val destinationKey: String? = null,
    @SerialName("destinationBucket") val destinationBucketId: String? = null,
    @SerialName("copyMetadata") val copyMetadata: Boolean? = null,
)

@Serializable
public data class ObjectSortByRequest(
    val column: String,
    val order: String,
)

@Serializable
public data class ObjectListRequest(
    val prefix: String,
    val limit: Int,
    val offset: Int,
    @SerialName("sortBy") val sortBy: ObjectSortByRequest? = null,
    val search: String? = null,
)
