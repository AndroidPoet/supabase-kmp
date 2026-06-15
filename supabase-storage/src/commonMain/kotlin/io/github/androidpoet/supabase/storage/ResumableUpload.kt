package io.github.androidpoet.supabase.storage
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.flow.StateFlow

/** TUS protocol version Supabase Storage speaks. */
internal const val TUS_VERSION: String = "1.0.0"

/**
 * Default chunk size for resumable uploads. Supabase requires 6 MiB chunks for
 * every part except the last, so this is also the recommended value.
 */
public const val RESUMABLE_DEFAULT_CHUNK_SIZE: Int = 6 * 1024 * 1024

/** Progress of a [ResumableUpload]. */
public data class ResumableUploadProgress(
    public val bytesUploaded: Long,
    public val totalBytes: Long,
) {
    /** Completion fraction in `0f..1f`. */
    public val fraction: Float
        get() = if (totalBytes <= 0L) 0f else (bytesUploaded.toDouble() / totalBytes.toDouble()).toFloat()

    public val isComplete: Boolean
        get() = totalBytes > 0L && bytesUploaded >= totalBytes
}

/**
 * A resumable (TUS) upload handle.
 *
 * Uploads in chunks with server-side offset tracking, so an interrupted upload
 * resumes from where it stopped instead of restarting. Call [await] to run it to
 * completion; observe [progress] for UI.
 *
 * **Pause / resume:** cancel the coroutine running [await] to pause. To resume —
 * even after an app restart — persist [uploadUrl] and create a new handle with
 * `createResumableUpload(..., uploadUrl = saved)`, then call [await] again; it
 * `HEAD`s the server for the current offset and continues.
 */
public interface ResumableUpload {
    /**
     * The TUS upload URL once the upload has been created (null beforehand).
     * Persist this to resume the upload later.
     */
    public val uploadUrl: String?

    /** Upload progress, updated after every chunk. */
    public val progress: StateFlow<ResumableUploadProgress>

    /**
     * Runs (or resumes) the upload to completion. Cancellable: cancelling the
     * calling coroutine pauses the upload, leaving already-uploaded bytes on the
     * server for a later resume via [uploadUrl].
     */
    public suspend fun await(): SupabaseResult<Unit>
}
