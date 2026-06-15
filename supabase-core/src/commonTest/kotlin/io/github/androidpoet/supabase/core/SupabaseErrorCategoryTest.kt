package io.github.androidpoet.supabase.core

import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseErrorCategory
import io.github.androidpoet.supabase.core.result.SupabaseErrorCodes
import io.github.androidpoet.supabase.core.result.category
import kotlin.test.Test
import kotlin.test.assertEquals

class SupabaseErrorCategoryTest {
    @Test
    fun test_category_mapsKnownSupabaseCode() {
        val error =
            SupabaseError(
                message = "duplicate",
                code = SupabaseErrorCodes.Database.UNIQUENESS_VIOLATION,
            )

        assertEquals(SupabaseErrorCategory.Conflict, error.category)
    }

    @Test
    fun test_category_mapsHttpStatusUnauthorized() {
        val error = SupabaseError(message = "unauthorized", code = "401")

        assertEquals(SupabaseErrorCategory.Unauthorized, error.category)
    }

    @Test
    fun test_category_mapsHttpStatusConflict() {
        val error = SupabaseError(message = "conflict", code = "409")

        assertEquals(SupabaseErrorCategory.Conflict, error.category)
    }

    @Test
    fun test_category_mapsHttpStatusRateLimited() {
        val error = SupabaseError(message = "rate limit", code = "429")

        assertEquals(SupabaseErrorCategory.RateLimited, error.category)
    }

    @Test
    fun test_category_mapsHttpStatusValidation() {
        val error = SupabaseError(message = "validation", code = "422")

        assertEquals(SupabaseErrorCategory.Validation, error.category)
    }

    @Test
    fun test_category_unknownForUnrecognizedCode() {
        val error = SupabaseError(message = "unknown", code = "XYZ")

        assertEquals(SupabaseErrorCategory.Unknown, error.category)
    }
}
