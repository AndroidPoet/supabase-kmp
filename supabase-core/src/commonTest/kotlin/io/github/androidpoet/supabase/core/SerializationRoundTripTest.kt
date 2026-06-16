package io.github.androidpoet.supabase.core

import io.github.androidpoet.supabase.core.models.ErrorResponse
import io.github.androidpoet.supabase.core.models.PostgrestResponse
import io.github.androidpoet.supabase.core.result.SupabaseError
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializationRoundTripTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun test_errorResponse_decodesFromApiJson() {
        val payload =
            """
            {
              "message": "duplicate key value violates unique constraint",
              "code": "23505",
              "details": "Key (email)=(a@example.com) already exists.",
              "hint": null
            }
            """.trimIndent()

        val response = json.decodeFromString<ErrorResponse>(payload)

        assertEquals("duplicate key value violates unique constraint", response.message)
        assertEquals("23505", response.code)
    }

    @Test
    fun test_errorResponse_roundTrip() {
        val original =
            ErrorResponse(
                message = "not found",
                code = "PGRST116",
                details = "0 rows",
                hint = "check filters",
            )

        val decoded = json.decodeFromString<ErrorResponse>(json.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun test_supabaseError_decodesFromApiJson() {
        val payload =
            """
            {
              "message": "rate limit exceeded",
              "code": "over_request_rate_limit",
              "details": { "remaining": 0 },
              "hint": "slow down",
              "httpStatus": 429,
              "retryAfterSeconds": 30
            }
            """.trimIndent()

        val error = json.decodeFromString<SupabaseError>(payload)

        assertEquals("rate limit exceeded", error.message)
        assertEquals("over_request_rate_limit", error.code)
        assertEquals(429, error.httpStatus)
        assertEquals(30L, error.retryAfterSeconds)
    }

    @Test
    fun test_supabaseError_roundTrip() {
        val original =
            SupabaseError(
                message = "conflict",
                code = "23505",
                hint = "unique violation",
                httpStatus = 409,
            )

        val decoded = json.decodeFromString<SupabaseError>(json.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun test_postgrestResponse_decodesWithCount() {
        val payload =
            """
            {
              "data": ["alice", "bob"],
              "count": 2
            }
            """.trimIndent()

        val serializer = PostgrestResponse.serializer(ListSerializer(String.serializer()))
        val response = json.decodeFromString(serializer, payload)

        assertEquals(listOf("alice", "bob"), response.data)
        assertEquals(2L, response.count)
    }

    @Test
    fun test_postgrestResponse_roundTrip() {
        val original = PostgrestResponse(data = listOf("alice"), count = 1)
        val serializer = PostgrestResponse.serializer(ListSerializer(String.serializer()))

        val decoded = json.decodeFromString(serializer, json.encodeToString(serializer, original))

        assertEquals(original, decoded)
    }

    @Test
    fun test_supabaseErrorDetails_preservesNestedJson() {
        val error =
            SupabaseError(
                message = "validation failed",
                details = JsonPrimitive("email is required"),
            )

        val decoded = json.decodeFromString<SupabaseError>(json.encodeToString(error))

        assertEquals(error, decoded)
        assertEquals(JsonPrimitive("email is required"), decoded.details)
    }
}
