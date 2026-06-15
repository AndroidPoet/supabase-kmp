package io.github.androidpoet.supabase.core.result
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
public data class SupabaseError(
    public val message: String,
    public val code: String? = null,
    public val details: JsonElement? = null,
    public val hint: String? = null,
    /**
     * The HTTP status that produced this error, when one is known. Captured
     * independently of [code] (which may hold a textual PostgREST/GoTrue code
     * or a synthetic [SupabaseErrorCodes.Client] code), so categorization keeps
     * working even when the error body carries no machine-readable code.
     */
    public val httpStatus: Int? = null,
    /**
     * The server's `Retry-After` hint in seconds, when present (typically on a
     * `429`). Useful for surfacing "try again in N seconds" to users.
     */
    public val retryAfterSeconds: Long? = null,
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

    /**
     * The request never reached the server or never produced a usable response:
     * the device is offline, the connection timed out, or the body could not be
     * decoded. Distinct from [Unknown] so callers can show offline/retry UI.
     */
    Network,
    Unknown,
}

/**
 * Whether retrying the same request could plausibly succeed. True for transient
 * categories ([RateLimited], [Internal], [Network]); false for client-fault
 * categories that will fail again unchanged.
 */
public val SupabaseErrorCategory.isRetryable: Boolean
    get() =
        when (this) {
            SupabaseErrorCategory.RateLimited,
            SupabaseErrorCategory.Internal,
            SupabaseErrorCategory.Network,
            -> true
            else -> false
        }

private val networkCodes =
    setOf(
        SupabaseErrorCodes.Client.NETWORK_ERROR,
        SupabaseErrorCodes.Client.TIMEOUT,
        SupabaseErrorCodes.Client.CONNECTION_FAILED,
    )

private val conflictCodes =
    setOf(
        SupabaseErrorCodes.Database.UNIQUENESS_VIOLATION,
        SupabaseErrorCodes.Database.FOREIGN_KEY_VIOLATION,
        SupabaseErrorCodes.Auth.USER_ALREADY_EXISTS,
        SupabaseErrorCodes.Auth.IDENTITY_ALREADY_EXISTS,
        SupabaseErrorCodes.Storage.BUCKET_ALREADY_EXISTS,
        SupabaseErrorCodes.Storage.KEY_ALREADY_EXISTS,
        SupabaseErrorCodes.Storage.ALREADY_EXISTS,
    )

private val notFoundCodes =
    setOf(
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
        SupabaseErrorCodes.Management.ORGANIZATION_NOT_FOUND,
    )

private val unauthorizedCodes =
    setOf(
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
        SupabaseErrorCodes.Management.FORBIDDEN,
    )

private val rateLimitedCodes =
    setOf(
        SupabaseErrorCodes.Auth.OVER_CONFIRMATION_RATE_LIMIT,
        SupabaseErrorCodes.Auth.TOO_MANY_REQUESTS,
        SupabaseErrorCodes.Storage.THROTTLING,
        SupabaseErrorCodes.Storage.TOO_MANY_REQUESTS,
        SupabaseErrorCodes.Realtime.CHANNEL_RATE_LIMIT_REACHED,
        SupabaseErrorCodes.Realtime.CONNECTION_RATE_LIMIT_REACHED,
        SupabaseErrorCodes.Management.RATE_LIMIT_EXCEEDED,
    )

private val validationCodes =
    setOf(
        SupabaseErrorCodes.Database.QUERY_PARSING_ERROR,
        SupabaseErrorCodes.Database.INVALID_REQUEST_BODY,
        SupabaseErrorCodes.Auth.WEAK_PASSWORD,
        SupabaseErrorCodes.Auth.BAD_CODE_VERIFIER,
        SupabaseErrorCodes.Storage.INVALID_BUCKET_NAME,
        SupabaseErrorCodes.Storage.ENTITY_TOO_LARGE,
        SupabaseErrorCodes.Storage.INVALID_MIME_TYPE,
    )

private val internalCodes =
    setOf(
        SupabaseErrorCodes.Database.CONNECTION_ERROR,
        SupabaseErrorCodes.Database.STATEMENT_TIMEOUT,
        SupabaseErrorCodes.Storage.DATABASE_TIMEOUT,
        SupabaseErrorCodes.Storage.INTERNAL_ERROR,
        SupabaseErrorCodes.Functions.BOOT_ERROR,
        SupabaseErrorCodes.Functions.WORKER_LIMIT,
    )

public val SupabaseError.category: SupabaseErrorCategory
    get() =
        when {
            code in networkCodes -> SupabaseErrorCategory.Network
            code in conflictCodes -> SupabaseErrorCategory.Conflict
            code in notFoundCodes -> SupabaseErrorCategory.NotFound
            code in unauthorizedCodes -> SupabaseErrorCategory.Unauthorized
            code in rateLimitedCodes -> SupabaseErrorCategory.RateLimited
            code in validationCodes -> SupabaseErrorCategory.Validation
            code in internalCodes -> SupabaseErrorCategory.Internal
            else -> categorizeByStatus(httpStatus ?: code?.toIntOrNull())
        }

// HTTP-status fallback. Uses [SupabaseError.httpStatus] first (always captured
// by the transport, even when the body has no textual code) and only then a
// numeric [SupabaseError.code], so structured error bodies without a `code`
// field are still categorized instead of collapsing to Unknown.
private fun categorizeByStatus(status: Int?): SupabaseErrorCategory =
    when (status) {
        401, 403 -> SupabaseErrorCategory.Unauthorized
        404 -> SupabaseErrorCategory.NotFound
        409 -> SupabaseErrorCategory.Conflict
        400, 422 -> SupabaseErrorCategory.Validation
        429 -> SupabaseErrorCategory.RateLimited
        in 500..599 -> SupabaseErrorCategory.Internal
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

/**
 * True when the request failed before producing a usable server response
 * (offline, timeout, connection or decoding failure). Equivalent to
 * `category == SupabaseErrorCategory.Network`.
 */
public fun SupabaseError.isNetworkError(): Boolean =
    category == SupabaseErrorCategory.Network
