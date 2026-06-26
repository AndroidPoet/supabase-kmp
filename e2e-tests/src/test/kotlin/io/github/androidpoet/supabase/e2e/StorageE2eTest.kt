package io.github.androidpoet.supabase.e2e

import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.storage.createStorageClient
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * Storage round-trip against the hosted project using ONLY synthetic data.
 *
 * Isolation/safety:
 *  - Creates its OWN throwaway bucket named with the run-id; it never reads,
 *    lists, or touches any pre-existing bucket.
 *  - Uploads only [FakeData.pngOnePixel] — a hardcoded 1x1 PNG constant.
 *  - In `finally`, empties and deletes ONLY the bucket it created (best-effort,
 *    so a cleanup hiccup is reported but never masks the real assertion).
 */
class StorageE2eTest {
    @Test
    fun test_storage_uploadFakePng_roundTripsThenCleansUp() {
        // Needs hosted config + the service role for bucket admin. Skip (not fail)
        // when absent — e.g. in CI, which holds no hosted/service-role credentials.
        val config = E2e.configOrNull()
        assumeTrue(
            "requires hosted E2E config + SUPABASE_E2E_SERVICE_KEY — skipped without it",
            config?.serviceKey != null,
        )
        runTest {
            val storage = createStorageClient(E2e.serviceClient(config!!))

            val bucketId = E2e.artifact("bucket")
            val objectPath = "fake.png"

            storage.createBucket(id = bucketId, name = bucketId, public = false).unwrap("createBucket")
            try {
                storage
                    .upload(
                        bucket = bucketId,
                        path = objectPath,
                        data = FakeData.pngOnePixel,
                        contentType = "image/png",
                    ).unwrap("upload")

                val downloaded =
                    storage.downloadBytes(bucket = bucketId, path = objectPath).unwrap("downloadBytes")
                assertContentEquals(FakeData.pngOnePixel, downloaded)
            } finally {
                cleanupBucket(bucketId)
            }
        }
    }

    /** Best-effort teardown: removes only the run-id bucket; warns instead of throwing. */
    private suspend fun cleanupBucket(bucketId: String) {
        val emptied = createStorageClientForCleanup().clearBucket(bucketId)
        if (emptied is SupabaseResult.Failure) println("WARN e2e cleanup clearBucket($bucketId): ${emptied.error}")
        val deleted = createStorageClientForCleanup().deleteBucket(bucketId)
        if (deleted is SupabaseResult.Failure) println("WARN e2e cleanup deleteBucket($bucketId): ${deleted.error}")
    }

    private fun createStorageClientForCleanup() = createStorageClient(E2e.serviceClient(E2e.config()))
}
