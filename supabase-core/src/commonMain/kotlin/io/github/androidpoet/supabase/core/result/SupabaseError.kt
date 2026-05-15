package io.github.androidpoet.supabase.core.result
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
@Serializable
public data class SupabaseError(
    public val message: String,
    public val code: String? = null,
    public val details: JsonElement? = null,
    public val hint: String? = null,
)
public class SupabaseException(
    public val error: SupabaseError,
) : Exception(error.message)
public fun SupabaseError.toException(): SupabaseException = SupabaseException(this)
public enum class SupabaseErrorCategory {
    Conflict,
    NotFound,
    Unauthorized,
    RateLimited,
    Validation,
    Internal,
    Unknown
}
public val SupabaseError.category: SupabaseErrorCategory
    get() = when (code) {
        SupabaseErrorCodes.Database.UNIQUENESS_VIOLATION,
        SupabaseErrorCodes.Database.FOREIGN_KEY_VIOLATION,
        SupabaseErrorCodes.Auth.USER_ALREADY_EXISTS,
        SupabaseErrorCodes.Auth.IDENTITY_ALREADY_EXISTS,
        SupabaseErrorCodes.Storage.BUCKET_ALREADY_EXISTS,
        SupabaseErrorCodes.Storage.KEY_ALREADY_EXISTS,
        SupabaseErrorCodes.Storage.ALREADY_EXISTS -> SupabaseErrorCategory.Conflict
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
        SupabaseErrorCodes.Auth.OVER_CONFIRMATION_RATE_LIMIT,
        SupabaseErrorCodes.Auth.TOO_MANY_REQUESTS,
        SupabaseErrorCodes.Storage.THROTTLING,
        SupabaseErrorCodes.Storage.TOO_MANY_REQUESTS,
        SupabaseErrorCodes.Realtime.CHANNEL_RATE_LIMIT_REACHED,
        SupabaseErrorCodes.Realtime.CONNECTION_RATE_LIMIT_REACHED,
        SupabaseErrorCodes.Management.RATE_LIMIT_EXCEEDED -> SupabaseErrorCategory.RateLimited
        SupabaseErrorCodes.Database.QUERY_PARSING_ERROR,
        SupabaseErrorCodes.Database.INVALID_REQUEST_BODY,
        SupabaseErrorCodes.Auth.WEAK_PASSWORD,
        SupabaseErrorCodes.Auth.BAD_CODE_VERIFIER,
        SupabaseErrorCodes.Storage.INVALID_BUCKET_NAME,
        SupabaseErrorCodes.Storage.ENTITY_TOO_LARGE,
        SupabaseErrorCodes.Storage.INVALID_MIME_TYPE -> SupabaseErrorCategory.Validation
        SupabaseErrorCodes.Database.CONNECTION_ERROR,
        SupabaseErrorCodes.Database.STATEMENT_TIMEOUT,
        SupabaseErrorCodes.Storage.DATABASE_TIMEOUT,
        SupabaseErrorCodes.Storage.INTERNAL_ERROR,
        SupabaseErrorCodes.Functions.BOOT_ERROR,
        SupabaseErrorCodes.Functions.WORKER_LIMIT -> SupabaseErrorCategory.Internal
        else -> SupabaseErrorCategory.Unknown
    }
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
