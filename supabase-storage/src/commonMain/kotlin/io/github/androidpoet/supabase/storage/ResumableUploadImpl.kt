package io.github.androidpoet.supabase.storage
import io.github.androidpoet.supabase.client.SupabaseClient
import io.github.androidpoet.supabase.client.SupabaseHttpMethod
import io.github.androidpoet.supabase.client.SupabaseHttpResponse
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.Volatile
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal class ResumableUploadImpl(
    private val client: SupabaseClient,
    private val bucket: String,
    private val path: String,
    private val data: ByteArray,
    private val contentType: String,
    private val upsert: Boolean,
    private val cacheControl: Int?,
    private val chunkSize: Int,
    initialUploadUrl: String?,
) : ResumableUpload {
    @Volatile
    private var resumableUrl: String? = initialUploadUrl
    override val uploadUrl: String? get() = resumableUrl

    private val total: Long = data.size.toLong()
    private val _progress = MutableStateFlow(ResumableUploadProgress(0L, total))
    override val progress: StateFlow<ResumableUploadProgress> = _progress.asStateFlow()

    override suspend fun await(): SupabaseResult<Unit> {
        var offset =
            when (val start = resolveOffset()) {
                is SupabaseResult.Success -> start.value
                is SupabaseResult.Failure -> return start
            }
        // A resumed offset outside the file means the stored upload URL belongs to a
        // different file (or a corrupt/misreported server state). Fail fast rather
        // than copyOfRange-crash or "succeed" on an upload that never sent its bytes.
        if (offset < 0 || offset > total) {
            return SupabaseResult.Failure(
                SupabaseError("Resumable upload server reported offset $offset outside the file (length $total)"),
            )
        }
        publish(offset)

        val url = resumableUrl ?: return SupabaseResult.Failure(SupabaseError("Resumable upload was not created"))
        return uploadChunks(url, offset)
    }

    private suspend fun uploadChunks(url: String, startOffset: Long): SupabaseResult<Unit> {
        var offset = startOffset
        while (offset < total) {
            val end = minOf(offset + chunkSize, total)
            val chunk = data.copyOfRange(offset.toInt(), end.toInt())
            val previous = offset
            offset =
                when (val patched = patchChunk(url, offset, chunk)) {
                    is SupabaseResult.Success -> patched.value
                    is SupabaseResult.Failure -> return patched
                }
            // The server's new Upload-Offset must advance and stay within the file.
            // If it doesn't advance we'd spin forever re-sending the same chunk; if
            // it runs past the end we'd "succeed" on a partial/corrupt upload. Treat
            // both as failures instead of looping or silently losing data.
            if (offset <= previous || offset > total) {
                return SupabaseResult.Failure(
                    SupabaseError(
                        "Resumable upload server returned an invalid Upload-Offset ($offset) " +
                            "after sending bytes $previous..$end of $total",
                    ),
                )
            }
            publish(offset)
        }
        return SupabaseResult.Success(Unit)
    }

    private fun publish(offset: Long) {
        _progress.value = ResumableUploadProgress(offset, total)
    }

    // Either create a fresh upload (offset 0) or HEAD an existing one to find
    // how many bytes the server already holds.
    private suspend fun resolveOffset(): SupabaseResult<Long> {
        val existing = resumableUrl
        return if (existing == null) {
            when (val created = createUpload()) {
                is SupabaseResult.Success -> {
                    resumableUrl = created.value
                    SupabaseResult.Success(0L)
                }
                is SupabaseResult.Failure -> created
            }
        } else {
            fetchOffset(existing)
        }
    }

    private suspend fun createUpload(): SupabaseResult<String> {
        val headers =
            mapOf(
                "Tus-Resumable" to TUS_VERSION,
                "Upload-Length" to total.toString(),
                "Upload-Metadata" to uploadMetadata(),
                "x-upsert" to upsert.toString(),
            )
        return when (val res = client.rawRequest(SupabaseHttpMethod.POST, StoragePaths.RESUMABLE_UPLOAD, headers = headers)) {
            is SupabaseResult.Success ->
                res.value.header("Location")?.let { SupabaseResult.Success(it) }
                    ?: SupabaseResult.Failure(SupabaseError("Resumable upload create returned no Location header"))
            is SupabaseResult.Failure -> res
        }
    }

    private suspend fun fetchOffset(url: String): SupabaseResult<Long> {
        val headers = mapOf("Tus-Resumable" to TUS_VERSION)
        return when (val res = client.rawRequest(SupabaseHttpMethod.HEAD, url, headers = headers)) {
            is SupabaseResult.Success -> readOffset(res.value)
            is SupabaseResult.Failure -> res
        }
    }

    private suspend fun patchChunk(url: String, offset: Long, chunk: ByteArray): SupabaseResult<Long> {
        val headers =
            mapOf(
                "Tus-Resumable" to TUS_VERSION,
                "Upload-Offset" to offset.toString(),
            )
        val res =
            client.rawRequest(
                method = SupabaseHttpMethod.PATCH,
                url = url,
                body = chunk,
                contentType = "application/offset+octet-stream",
                headers = headers,
            )
        return when (res) {
            is SupabaseResult.Success -> readOffset(res.value)
            is SupabaseResult.Failure -> res
        }
    }

    private fun readOffset(response: SupabaseHttpResponse): SupabaseResult<Long> {
        val offset = response.header("Upload-Offset")?.toLongOrNull()
        return if (offset != null) {
            SupabaseResult.Success(offset)
        } else {
            SupabaseResult.Failure(SupabaseError("Resumable upload response missing Upload-Offset header"))
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun uploadMetadata(): String {
        val entries =
            buildList {
                add("bucketName" to bucket)
                add("objectName" to path)
                add("contentType" to contentType)
                cacheControl?.let { add("cacheControl" to it.toString()) }
            }
        return entries.joinToString(",") { (key, value) ->
            "$key ${Base64.encode(value.encodeToByteArray())}"
        }
    }
}
