package io.github.androidpoet.supabase.sample.gallery.data

import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.core.result.map
import io.github.androidpoet.supabase.storage.StorageClient

/**
 * Storage-only wrapper over supabase-kmp. The bucket is reached with the anon
 * key — the bundled `supabase/policies.sql` grants the anon role full access to
 * the demo bucket so no sign-in is needed. A production app would tighten those
 * policies and upload under an authenticated session.
 */
class GalleryRepository(
    private val storage: StorageClient,
    val bucket: String,
) {
    suspend fun upload(path: String, bytes: ByteArray, contentType: String): SupabaseResult<String> =
        storage.upload(
            bucket = bucket,
            path = path,
            data = bytes,
            contentType = contentType,
            upsert = true,
        )

    /** Object names in the bucket root, newest entries excluded of folder placeholders. */
    suspend fun list(): SupabaseResult<List<String>> =
        storage.list(bucket = bucket).map { objects ->
            objects.map { it.name }.filter { it.isNotBlank() && it != ".emptyFolderPlaceholder" }
        }

    suspend fun downloadBytes(path: String): SupabaseResult<ByteArray> =
        storage.downloadBytes(bucket = bucket, path = path)

    suspend fun signedUrl(path: String): SupabaseResult<String> =
        storage.createSignedUrl(bucket = bucket, path = path, expiresIn = 3600)

    fun publicUrl(path: String): String =
        storage.getPublicUrl(bucket = bucket, path = path)

    suspend fun remove(path: String): SupabaseResult<Unit> =
        storage.remove(bucket = bucket, paths = listOf(path))
}
