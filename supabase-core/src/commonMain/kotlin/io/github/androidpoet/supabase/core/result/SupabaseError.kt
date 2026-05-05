package io.github.androidpoet.supabase.core.result

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Represents an error returned by the Supabase API.
 *
 * Supabase errors carry a human-readable [message] plus optional structured
 * metadata: a machine-readable [code], arbitrary [details] (JSON), and a
 * [hint] suggesting how to resolve the problem.
 */
@Serializable
public data class SupabaseError(
    public val message: String,
    public val code: String? = null,
    public val details: JsonElement? = null,
    public val hint: String? = null,
)

/**
 * Exception wrapper around [SupabaseError] for use in throw/catch flows.
 */
public class SupabaseException(
    public val error: SupabaseError,
) : Exception(error.message)

/**
 * Converts this [SupabaseError] into a throwable [SupabaseException].
 */
public fun SupabaseError.toException(): SupabaseException = SupabaseException(this)

/**
 * High-level categories for Supabase errors to simplify handling.
 */
public enum class SupabaseErrorCategory {
    /** Item already exists (Unique violations, etc.) */
    Conflict,
    /** Resource not found (Table, File, User, etc.) */
    NotFound,
    /** Authentication or Permission issues (Invalid JWT, RLS violation, etc.) */
    Unauthorized,
    /** Rate limits reached (Auth, Storage, Realtime) */
    RateLimited,
    /** Data validation issues (Weak password, malformed JSON, etc.) */
    Validation,
    /** Server-side issues (Database timeout, internal error, etc.) */
    Internal,
    /** Unknown or unmapped error */
    Unknown
}

/**
 * Maps the specific error code to a high-level category.
 */
public val SupabaseError.category: SupabaseErrorCategory
    get() = when (code) {
        // Conflict
        SupabaseErrorCodes.Database.UNIQUENESS_VIOLATION,
        SupabaseErrorCodes.Database.FOREIGN_KEY_VIOLATION,
        SupabaseErrorCodes.Auth.USER_ALREADY_EXISTS,
        SupabaseErrorCodes.Auth.IDENTITY_ALREADY_EXISTS,
        SupabaseErrorCodes.Storage.BUCKET_ALREADY_EXISTS,
        SupabaseErrorCodes.Storage.KEY_ALREADY_EXISTS,
        SupabaseErrorCodes.Storage.ALREADY_EXISTS -> SupabaseErrorCategory.Conflict

        // Not Found
        SupabaseErrorCodes.Database.TABLE_NOT_FOUND,
        SupabaseErrorCodes.Database.COLUMN_NOT_FOUND,
        SupabaseErrorCodes.Database.FUNCTION_NOT_FOUND,
        SupabaseErrorCodes.Database.UNDEFINED_TABLE,
        SupabaseErrorCodes.Auth.USER_NOT_FOUND,
        SupabaseErrorCodes.Auth.INVITE_NOT_FOUND,
        SupabaseErrorCodes.Storage.NO_SUCH_BUCKET,
        SupabaseErrorCodes.Storage.NO_SUCH_KEY,
        SupabaseErrorCodes.Storage.NOT_FOUND,
        SupabaseErrorCodes.Management.PROJECT_NOT_FOUND,
        SupabaseErrorCodes.Management.ORGANIZATION_NOT_FOUND -> SupabaseErrorCategory.NotFound

        // Unauthorized
        SupabaseErrorCodes.Database.JWT_INVALID,
        SupabaseErrorCodes.Database.INSUFFICIENT_PRIVILEGE,
        SupabaseErrorCodes.Database.ANONYMOUS_ROLE_DISABLED,
        SupabaseErrorCodes.Auth.INVALID_CREDENTIALS,
        SupabaseErrorCodes.Auth.PROVIDER_DISABLED,
        SupabaseErrorCodes.Auth.SIGNUP_DISABLED,
        SupabaseErrorCodes.Storage.INVALID_JWT,
        SupabaseErrorCodes.Storage.ACCESS_DENIED,
        SupabaseErrorCodes.Storage.UNAUTHORIZED,
        SupabaseErrorCodes.Realtime.ERROR_AUTHORIZING_WEBSOCKET,
        SupabaseErrorCodes.Management.UNAUTHORIZED,
        SupabaseErrorCodes.Management.FORBIDDEN -> SupabaseErrorCategory.Unauthorized

        // Rate Limited
        SupabaseErrorCodes.Auth.OVER_CONFIRMATION_RATE_LIMIT,
        SupabaseErrorCodes.Auth.TOO_MANY_REQUESTS,
        SupabaseErrorCodes.Storage.THROTTLING,
        SupabaseErrorCodes.Storage.TOO_MANY_REQUESTS,
        SupabaseErrorCodes.Realtime.CHANNEL_RATE_LIMIT_REACHED,
        SupabaseErrorCodes.Realtime.CONNECTION_RATE_LIMIT_REACHED,
        SupabaseErrorCodes.Management.RATE_LIMIT_EXCEEDED -> SupabaseErrorCategory.RateLimited

        // Validation
        SupabaseErrorCodes.Database.QUERY_PARSING_ERROR,
        SupabaseErrorCodes.Database.INVALID_REQUEST_BODY,
        SupabaseErrorCodes.Auth.WEAK_PASSWORD,
        SupabaseErrorCodes.Auth.BAD_CODE_VERIFIER,
        SupabaseErrorCodes.Storage.INVALID_BUCKET_NAME,
        SupabaseErrorCodes.Storage.ENTITY_TOO_LARGE,
        SupabaseErrorCodes.Storage.INVALID_MIME_TYPE -> SupabaseErrorCategory.Validation

        // Internal
        SupabaseErrorCodes.Database.CONNECTION_ERROR,
        SupabaseErrorCodes.Database.STATEMENT_TIMEOUT,
        SupabaseErrorCodes.Storage.DATABASE_TIMEOUT,
        SupabaseErrorCodes.Storage.INTERNAL_ERROR,
        SupabaseErrorCodes.Functions.BOOT_ERROR,
        SupabaseErrorCodes.Functions.WORKER_LIMIT -> SupabaseErrorCategory.Internal

        else -> SupabaseErrorCategory.Unknown
    }

/**
 * Helper extension functions to check for specific error types.
 */
public fun SupabaseError.isUniquenessViolation(): Boolean =
    code == SupabaseErrorCodes.Database.UNIQUENESS_VIOLATION

public fun SupabaseError.isForeignKeyViolation(): Boolean =
    code == SupabaseErrorCodes.Database.FOREIGN_KEY_VIOLATION

public fun SupabaseError.isInvalidCredentials(): Boolean =
    code == SupabaseErrorCodes.Auth.INVALID_CREDENTIALS

public fun SupabaseError.isUserAlreadyExists(): Boolean =
    code == SupabaseErrorCodes.Auth.USER_ALREADY_EXISTS

public fun SupabaseError.isFileNotFound(): Boolean =
    code == SupabaseErrorCodes.Storage.NO_SUCH_KEY
