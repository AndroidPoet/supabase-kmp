package io.github.androidpoet.supabase.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RetryConfigTest {
    @Test
    fun test_backoff_isExponentialFromBase() {
        val config = RetryConfig(baseDelayMillis = 1_000, maxDelayMillis = 100_000)
        assertEquals(1_000, config.backoffMillis(0))
        assertEquals(2_000, config.backoffMillis(1))
        assertEquals(4_000, config.backoffMillis(2))
        assertEquals(8_000, config.backoffMillis(3))
    }

    @Test
    fun test_backoff_isCappedAtMaxDelay() {
        val config = RetryConfig(baseDelayMillis = 1_000, maxDelayMillis = 3_000)
        assertEquals(1_000, config.backoffMillis(0))
        assertEquals(2_000, config.backoffMillis(1))
        assertEquals(3_000, config.backoffMillis(2))
        assertEquals(3_000, config.backoffMillis(5))
    }

    @Test
    fun test_backoff_doesNotOverflowForLargeAttempts() {
        val config = RetryConfig(baseDelayMillis = 1_000, maxDelayMillis = 30_000)
        assertEquals(30_000, config.backoffMillis(1_000))
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
