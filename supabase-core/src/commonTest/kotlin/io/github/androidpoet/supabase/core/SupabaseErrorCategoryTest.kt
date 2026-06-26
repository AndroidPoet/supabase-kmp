package io.github.androidpoet.supabase.core

import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseErrorCategory
import io.github.androidpoet.supabase.core.result.SupabaseErrorCodes
import io.github.androidpoet.supabase.core.result.category
import io.github.androidpoet.supabase.core.result.isNetworkError
import io.github.androidpoet.supabase.core.result.isRetryable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupabaseErrorCategoryTest {
    @Test
    fun test_category_mapsKnownSupabaseCode() {
        val error =
            SupabaseError(
                message = "duplicate",
                code = SupabaseErrorCodes.Database.UNIQUENESS_VIOLATION,
            )

        assertEquals(SupabaseErrorCategory.CONFLICT, error.category)
    }

    @Test
    fun test_category_mapsHttpStatusUnauthorized() {
        val error = SupabaseError(message = "unauthorized", code = "401")

        assertEquals(SupabaseErrorCategory.UNAUTHORIZED, error.category)
    }

    @Test
    fun test_category_mapsHttpStatusConflict() {
        val error = SupabaseError(message = "conflict", code = "409")

        assertEquals(SupabaseErrorCategory.CONFLICT, error.category)
    }

    @Test
    fun test_category_mapsHttpStatusRateLimited() {
        val error = SupabaseError(message = "rate limit", code = "429")

        assertEquals(SupabaseErrorCategory.RATE_LIMITED, error.category)
    }

    @Test
    fun test_category_mapsHttpStatusValidation() {
        val error = SupabaseError(message = "validation", code = "422")

        assertEquals(SupabaseErrorCategory.VALIDATION, error.category)
    }

    @Test
    fun test_category_unknownForUnrecognizedCode() {
        val error = SupabaseError(message = "unknown", code = "XYZ")

        assertEquals(SupabaseErrorCategory.UNKNOWN, error.category)
    }

    @Test
    fun test_category_usesHttpStatusWhenCodeAbsent() {
        // Regression: structured error bodies (e.g. Storage 404) carry no `code`
        // but do carry an HTTP status — must categorize, not fall to Unknown.
        val error = SupabaseError(message = "not found", code = null, httpStatus = 404)

        assertEquals(SupabaseErrorCategory.NOT_FOUND, error.category)
    }

    @Test
    fun test_category_mapsHttpStatus400ToValidation() {
        val error = SupabaseError(message = "bad request", httpStatus = 400)

        assertEquals(SupabaseErrorCategory.VALIDATION, error.category)
    }

    @Test
    fun test_category_networkCodeIsNetworkAndRetryable() {
        val error = SupabaseError(message = "offline", code = SupabaseErrorCodes.Client.NETWORK_ERROR)

        assertEquals(SupabaseErrorCategory.NETWORK, error.category)
        assertTrue(error.isNetworkError())
        assertTrue(error.category.isRetryable)
    }

    @Test
    fun test_isRetryable_byCategory() {
        assertTrue(SupabaseErrorCategory.RATE_LIMITED.isRetryable)
        assertTrue(SupabaseErrorCategory.INTERNAL.isRetryable)
        assertTrue(SupabaseErrorCategory.NETWORK.isRetryable)
        assertFalse(SupabaseErrorCategory.VALIDATION.isRetryable)
        assertFalse(SupabaseErrorCategory.NOT_FOUND.isRetryable)
        assertFalse(SupabaseErrorCategory.UNAUTHORIZED.isRetryable)
    }
}
