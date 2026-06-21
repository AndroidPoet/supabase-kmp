package io.github.androidpoet.supabase.client.transport

import io.github.androidpoet.supabase.client.SupabaseConfig
import io.github.androidpoet.supabase.core.result.SupabaseErrorCategory
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.core.result.category
import io.github.androidpoet.supabase.core.result.isFileNotFound
import io.github.androidpoet.supabase.core.result.isRetryable
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Conformance tests for error-body parsing across the heterogeneous shapes the
 * Supabase services actually return. The transport must parse all of them and
 * always categorize by HTTP status even when the body has no usable code.
 *
 * Error-shape references:
 *  - PostgREST: {code,message,details,hint} — https://docs.postgrest.org/en/v12/references/errors.html
 *  - GoTrue legacy {code:<int>,error_code,msg} vs new {code:<string>,message}
 *    and OAuth {error,error_description} —
 *    https://supabase.com/docs/guides/auth/debugging/error-codes
 *  - Storage {code,message} — https://supabase.com/docs/guides/storage/debugging/error-codes
 */
class ErrorParsingConformanceTest {
    private fun transportReturning(
        status: HttpStatusCode,
        body: String,
        retryAfter: String? = null,
    ): HttpTransport {
        val headers =
            if (retryAfter != null) {
                headersOf("Retry-After", retryAfter)
            } else {
                headersOf("Content-Type", "application/json")
            }
        return HttpTransport(
            config = SupabaseConfig(logging = false, logLevel = io.ktor.client.plugins.logging.LogLevel.NONE, headers = emptyMap()),
            engineFactory = TestMockEngineFactory { respond(content = body, status = status, headers = headers) },
            projectUrl = "https://example.supabase.co",
            apiKey = "anon",
        )
    }

    private suspend fun errorFor(
        status: HttpStatusCode,
        body: String,
        retryAfter: String? = null,
    ) = (transportReturning(status, body, retryAfter).get(url = "https://example.supabase.co/rest/v1/x") as SupabaseResult.Failure).error

    @Test
    fun test_postgrest_uniqueViolation_parsesCodeAndMapsConflict() =
        runTest {
            val error =
                errorFor(
                    HttpStatusCode.Conflict,
                    """{"code":"23505","message":"duplicate key violates unique constraint","details":"Key exists.","hint":null}""",
                )
            assertEquals("23505", error.code)
            assertEquals("duplicate key violates unique constraint", error.message)
            assertEquals(409, error.httpStatus)
            assertEquals(SupabaseErrorCategory.Conflict, error.category)
        }

    @Test
    fun test_gotrue_legacyShape_prefersStringErrorCodeOverNumericCode() =
        runTest {
            // Legacy GoTrue: `code` is the numeric HTTP status; `error_code` is the
            // real machine code, and the message is under `msg`.
            val error =
                errorFor(
                    HttpStatusCode.UnprocessableEntity,
                    """{"code":422,"error_code":"weak_password","msg":"Password is too weak"}""",
                )
            assertEquals("weak_password", error.code)
            assertEquals("Password is too weak", error.message)
            assertEquals(422, error.httpStatus)
            assertEquals(SupabaseErrorCategory.Validation, error.category)
        }

    @Test
    fun test_gotrue_newShape_parsesStringCodeAndMessage() =
        runTest {
            val error =
                errorFor(
                    HttpStatusCode.BadRequest,
                    """{"code":"weak_password","message":"Password should be at least 6 characters"}""",
                )
            assertEquals("weak_password", error.code)
            assertEquals("Password should be at least 6 characters", error.message)
            assertEquals(SupabaseErrorCategory.Validation, error.category)
        }

    @Test
    fun test_gotrue_oauthShape_usesErrorDescriptionAsMessage() =
        runTest {
            // OAuth endpoints always return {error, error_description} with HTTP 400.
            val error =
                errorFor(
                    HttpStatusCode.BadRequest,
                    """{"error":"invalid_grant","error_description":"Invalid login credentials"}""",
                )
            assertEquals("Invalid login credentials", error.message)
            assertEquals(SupabaseErrorCategory.Validation, error.category)
        }

    @Test
    fun test_storage_notFound_parsesCodeAndMapsNotFound() =
        runTest {
            val error =
                errorFor(
                    HttpStatusCode.NotFound,
                    """{"code":"NoSuchKey","message":"Object not found"}""",
                )
            assertEquals("NoSuchKey", error.code)
            assertEquals("Object not found", error.message)
            assertEquals(SupabaseErrorCategory.NotFound, error.category)
        }

    @Test
    fun test_storage_realShape_extractsStringCodeFromErrorField() =
        runTest {
            // The Storage server's actual error shape puts the string code in `error`
            // and only a numeric `statusCode` — NOT a string `code`. Reading only
            // `code`/`statusCode` set the code to "404", so isFileNotFound() and every
            // Storage code-set match were dead.
            val error =
                errorFor(
                    HttpStatusCode.NotFound,
                    """{"statusCode":"404","error":"NoSuchKey","message":"Object not found"}""",
                )
            assertEquals("NoSuchKey", error.code)
            assertEquals("Object not found", error.message)
            assertTrue(error.isFileNotFound())
            assertEquals(SupabaseErrorCategory.NotFound, error.category)
        }

    @Test
    fun test_requestTimeout408_mapsInternalAndIsRetryable() =
        runTest {
            // 408 is transient: it must be retryable and must NOT collapse to Unknown,
            // or the session transient-failure guard could sign the user out on a fluke.
            val error = errorFor(HttpStatusCode.RequestTimeout, "")
            assertEquals(SupabaseErrorCategory.Internal, error.category)
            assertTrue(error.category.isRetryable)
        }

    @Test
    fun test_rateLimited_capturesRetryAfterAndIsRetryable() =
        runTest {
            val error =
                errorFor(
                    HttpStatusCode.TooManyRequests,
                    """{"message":"rate limit exceeded"}""",
                    retryAfter = "30",
                )
            assertEquals(30, error.retryAfterSeconds)
            assertEquals(SupabaseErrorCategory.RateLimited, error.category)
            assertTrue(error.category.isRetryable)
        }

    @Test
    fun test_unauthorized_status401_mapsUnauthorized() =
        runTest {
            val error = errorFor(HttpStatusCode.Unauthorized, """{"message":"JWT expired"}""")
            assertEquals(401, error.httpStatus)
            assertEquals(SupabaseErrorCategory.Unauthorized, error.category)
        }

    @Test
    fun test_serverError_emptyBody_fallsBackToStatusMessageAndInternalCategory() =
        runTest {
            val error = errorFor(HttpStatusCode.InternalServerError, "")
            assertEquals("HTTP 500", error.message)
            assertEquals(500, error.httpStatus)
            assertEquals(SupabaseErrorCategory.Internal, error.category)
            assertTrue(error.category.isRetryable)
        }

    @Test
    fun test_nonJsonBody_usedAsMessageVerbatim() =
        runTest {
            val error = errorFor(HttpStatusCode.BadGateway, "upstream connection error")
            assertEquals("upstream connection error", error.message)
            assertEquals(502, error.httpStatus)
            assertEquals(SupabaseErrorCategory.Internal, error.category)
        }
}
