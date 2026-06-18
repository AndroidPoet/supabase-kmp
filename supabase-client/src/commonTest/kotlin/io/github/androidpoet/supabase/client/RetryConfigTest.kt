package io.github.androidpoet.supabase.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RetryConfigTest {
    @Test
    fun test_backoff_isExponentialFromBase() {
        // jitter off: assert the deterministic exponential ceiling.
        val config = RetryConfig(baseDelayMillis = 1_000, maxDelayMillis = 100_000, jitter = false)
        assertEquals(1_000, config.backoffMillis(0))
        assertEquals(2_000, config.backoffMillis(1))
        assertEquals(4_000, config.backoffMillis(2))
        assertEquals(8_000, config.backoffMillis(3))
    }

    @Test
    fun test_backoff_isCappedAtMaxDelay() {
        val config = RetryConfig(baseDelayMillis = 1_000, maxDelayMillis = 3_000, jitter = false)
        assertEquals(1_000, config.backoffMillis(0))
        assertEquals(2_000, config.backoffMillis(1))
        assertEquals(3_000, config.backoffMillis(2))
        assertEquals(3_000, config.backoffMillis(5))
    }

    @Test
    fun test_backoff_doesNotOverflowForLargeAttempts() {
        val config = RetryConfig(baseDelayMillis = 1_000, maxDelayMillis = 30_000, jitter = false)
        assertEquals(30_000, config.backoffMillis(1_000))
    }

    @Test
    fun test_backoff_jitterStaysWithinEqualJitterBounds() {
        val config = RetryConfig(baseDelayMillis = 1_000, maxDelayMillis = 100_000, jitter = true)
        // attempt 3: ceiling = 8_000, floor = min(8_000/2, 1_000) = 1_000.
        repeat(200) {
            val delay = config.backoffMillis(3)
            assertTrue(delay in 1_000..8_000, "jittered delay $delay out of [1000,8000]")
        }
    }

    @Test
    fun test_defaults_matchDocumentedValues() {
        val config = RetryConfig.Default
        assertEquals(3, config.maxRetries)
        assertEquals(1_000, config.baseDelayMillis)
        assertEquals(30_000, config.maxDelayMillis)
        assertTrue(429 in config.retryableStatuses)
        assertTrue(503 in config.retryableStatuses)
        assertTrue(404 !in config.retryableStatuses)
    }

    @Test
    fun test_init_rejectsInvalidValues() {
        assertFailsWith<IllegalArgumentException> { RetryConfig(maxRetries = -1) }
        assertFailsWith<IllegalArgumentException> { RetryConfig(baseDelayMillis = -1) }
        assertFailsWith<IllegalArgumentException> {
            RetryConfig(baseDelayMillis = 5_000, maxDelayMillis = 1_000)
        }
    }
}
