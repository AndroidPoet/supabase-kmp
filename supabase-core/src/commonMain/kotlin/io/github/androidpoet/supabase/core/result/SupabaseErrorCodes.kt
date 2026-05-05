package io.github.androidpoet.supabase.core.result

/**
 * A comprehensive collection of Supabase error codes.
 */
public object SupabaseErrorCodes {

    /**
     * PostgREST (Database API) specific error codes.
     */
    public object Database {
        // Group 0: Connection
        public const val CONNECTION_ERROR: String = "PGRST000"
        public const val INTERNAL_CONNECTION_ERROR: String = "PGRST001"
        public const val SCHEMA_CACHE_CONNECTION_ERROR: String = "PGRST002"
        public const val CONNECTION_POOL_TIMEOUT: String = "PGRST003"

        // Group 1: API Request
        public const val QUERY_PARSING_ERROR: String = "PGRST100"
        public const val INVALID_HTTP_VERB: String = "PGRST101"
        public const val INVALID_REQUEST_BODY: String = "PGRST102"
        public const val INVALID_RANGE: String = "PGRST103"
        public const val UNEXPECTED_PARAMETER: String = "PGRST104"
        public const val INVALID_PUT_UPSERT: String = "PGRST105"
        public const val INVALID_SCHEMA: String = "PGRST106"
        public const val INVALID_CONTENT_TYPE: String = "PGRST107"
        public const val FILTER_EMBEDDED_NOT_SELECTED: String = "PGRST108"
        public const val LIMIT_WITHOUT_ORDERING: String = "PGRST109"
        public const val MAX_AFFECTED_VIOLATION_LIMIT: String = "PGRST110"
        public const val INVALID_RESPONSE_HEADERS: String = "PGRST111"
        public const val INVALID_STATUS_CODE: String = "PGRST112"
        public const val SCALAR_RESULT_VIOLATION: String = "PGRST113"
        public const val UPSERT_PUT_LIMIT_OFFSET: String = "PGRST114"
        public const val UPSERT_PUT_PK_MISMATCH: String = "PGRST115"
        public const val SINGULAR_RESPONSE_VIOLATION: String = "PGRST116"
        public const val UNSUPPORTED_HTTP_VERB: String = "PGRST117"
        public const val INVALID_ORDER_BY: String = "PGRST118"
        public const val INVALID_SPREAD_OPERATOR: String = "PGRST119"
        public const val INVALID_EMBEDDED_FILTER: String = "PGRST120"
        public const val JSON_PARSE_ERROR: String = "PGRST121"
        public const val INVALID_PREFERENCES: String = "PGRST122"
        public const val AGGREGATES_DISABLED: String = "PGRST123"
        public const val MAX_AFFECTED_VIOLATION: String = "PGRST124"
        public const val INVALID_PATH: String = "PGRST125"
        public const val OPENAPI_DISABLED: String = "PGRST126"
        public const val FEATURE_NOT_IMPLEMENTED: String = "PGRST127"
        public const val MAX_AFFECTED_VIOLATION_RPC: String = "PGRST128"

        // Group 2: Schema Cache
        public const val STALE_RELATIONSHIP: String = "PGRST200"
        public const val AMBIGUOUS_EMBEDDING: String = "PGRST201"
        public const val FUNCTION_NOT_FOUND: String = "PGRST202"
        public const val OVERLOADED_FUNCTION: String = "PGRST203"
        public const val COLUMN_NOT_FOUND: String = "PGRST204"
        public const val TABLE_NOT_FOUND: String = "PGRST205"

        // Group 3: JWT
        public const val JWT_SECRET_MISSING: String = "PGRST300"
        public const val JWT_INVALID: String = "PGRST301"
        public const val ANONYMOUS_ROLE_DISABLED: String = "PGRST302"
        public const val JWT_CLAIMS_DECODING_FAILED: String = "PGRST303"

        // Group X: Internal
        public const val INTERNAL_DB_LIBRARY_ERROR: String = "PGRSTX00"

        // Standard Postgres Error Codes (Common ones used by Supabase)
        public const val FOREIGN_KEY_VIOLATION: String = "23503"
        public const val UNIQUENESS_VIOLATION: String = "23505"
        public const val UNDEFINED_TABLE: String = "42P01"
        public const val INSUFFICIENT_PRIVILEGE: String = "42501"
        public const val STATEMENT_TIMEOUT: String = "57014"
    }

    /**
     * Supabase Auth (GoTrue) error codes.
     */
    public object Auth {
        public const val INVALID_CREDENTIALS: String = "invalid_credentials"
        public const val USER_NOT_FOUND: String = "user_not_found"
        public const val EMAIL_NOT_CONFIRMED: String = "email_not_confirmed"
        public const val PHONE_NOT_CONFIRMED: String = "phone_not_confirmed"
        public const val USER_ALREADY_EXISTS: String = "user_already_exists"
        public const val INVALID_GRANT: String = "invalid_grant"
        public const val OTP_EXPIRED: String = "otp_expired"
        public const val BAD_CODE_VERIFIER: String = "bad_code_verifier"
        public const val WEAK_PASSWORD: String = "weak_password"
        public const val OVER_CONFIRMATION_RATE_LIMIT: String = "over_confirmation_rate_limit"
        public const val TOO_MANY_REQUESTS: String = "too_many_requests"
        public const val IDENTITY_ALREADY_EXISTS: String = "identity_already_exists"
        public const val PROVIDER_DISABLED: String = "provider_disabled"
        public const val SIGNUP_DISABLED: String = "signup_disabled"
        public const val INVITE_NOT_FOUND: String = "invite_not_found"

        // MFA
        public const val MFA_FACTOR_NOT_FOUND: String = "mfa_factor_not_found"
        public const val MFA_IP_ADDRESS_MISMATCH: String = "mfa_ip_address_mismatch"
        public const val MFA_CHALLENGE_EXPIRED: String = "mfa_challenge_expired"
        public const val MFA_VERIFICATION_FAILED: String = "mfa_verification_failed"
    }

    /**
     * Supabase Storage error codes.
     */
    public object Storage {
        // Bucket Errors
        public const val NO_SUCH_BUCKET: String = "NoSuchBucket"
        public const val BUCKET_ALREADY_EXISTS: String = "BucketAlreadyExists"
        public const val INVALID_BUCKET_NAME: String = "InvalidBucketName"

        // Object & File Errors
        public const val NO_SUCH_KEY: String = "NoSuchKey"
        public const val KEY_ALREADY_EXISTS: String = "KeyAlreadyExists"
        public const val INVALID_KEY: String = "InvalidKey"
        public const val ENTITY_TOO_LARGE: String = "EntityTooLarge"
        public const val INVALID_MIME_TYPE: String = "InvalidMimeType"
        public const val INVALID_RANGE: String = "InvalidRange"
        public const val CHECKSUM_MISMATCH: String = "ChecksumMismatch"

        // Upload & Multipart Errors
        public const val NO_SUCH_UPLOAD: String = "NoSuchUpload"
        public const val INVALID_UPLOAD_ID: String = "InvalidUploadId"
        public const val MISSING_PART: String = "MissingPart"
        public const val INVALID_UPLOAD_SIGNATURE: String = "InvalidUploadSignature"

        // Authentication & Authorization
        public const val INVALID_JWT: String = "InvalidJWT"
        public const val ACCESS_DENIED: String = "AccessDenied"
        public const val SIGNATURE_DOES_NOT_MATCH: String = "SignatureDoesNotMatch"
        public const val INVALID_ACCESS_KEY_ID: String = "InvalidAccessKeyId"

        // System & Database Errors
        public const val DATABASE_ERROR: String = "DatabaseError"
        public const val DATABASE_TIMEOUT: String = "DatabaseTimeout"
        public const val LOCK_TIMEOUT: String = "LockTimeout"
        public const val RESOURCE_LOCKED: String = "ResourceLocked"
        public const val TENANT_NOT_FOUND: String = "TenantNotFound"
        public const val INTERNAL_ERROR: String = "InternalError"
        public const val S3_ERROR: String = "S3Error"
        public const val THROTTLING: String = "Throttling"

        // Snake Case (Newer API versions)
        public const val NOT_FOUND: String = "not_found"
        public const val ALREADY_EXISTS: String = "already_exists"
        public const val UNAUTHORIZED: String = "unauthorized"
        public const val TOO_MANY_REQUESTS: String = "too_many_requests"
        public const val DATABASE_TIMEOUT_SNAKE: String = "database_timeout"
        public const val INTERNAL_SERVER_ERROR: String = "internal_server_error"
    }

    /**
     * Supabase Realtime error codes.
     */
    public object Realtime {
        public const val CHANNEL_RATE_LIMIT_REACHED: String = "ChannelRateLimitReached"
        public const val CONNECTION_RATE_LIMIT_REACHED: String = "ConnectionRateLimitReached"
        public const val ERROR_AUTHORIZING_WEBSOCKET: String = "ErrorAuthorizingWebsocket"
        public const val DATABASE_CONNECTION_ISSUE: String = "DatabaseConnectionIssue"
        public const val CLIENT_JOIN_RATE_LIMIT_REACHED: String = "ClientJoinRateLimitReached"
        public const val AUTH_EXPIRED: String = "AuthExpired"
        public const val INVALID_TOPIC: String = "InvalidTopic"
        public const val PAYLOAD_TOO_LARGE: String = "PayloadTooLarge"
    }

    /**
     * Supabase Edge Functions error codes.
     */
    public object Functions {
        public const val BOOT_ERROR: String = "BOOT_ERROR"
        public const val WORKER_ERROR: String = "WORKER_ERROR"
        public const val WORKER_LIMIT: String = "WORKER_LIMIT"
        public const val DEPLOYMENT_FAILED: String = "EF001"
        public const val UNSUPPORTED_NODE_VERSION: String = "EF015"
        public const val FUNCTION_NOT_RETURNING_RESPONSE: String = "EF028"
    }

    /**
     * Supabase Management (Platform) API error codes.
     */
    public object Management {
        public const val PROJECT_NOT_FOUND: String = "PROJECT_NOT_FOUND"
        public const val ORGANIZATION_NOT_FOUND: String = "ORGANIZATION_NOT_FOUND"
        public const val INVALID_PROJECT_REF: String = "INVALID_PROJECT_REF"
        public const val UNAUTHORIZED: String = "UNAUTHORIZED"
        public const val FORBIDDEN: String = "FORBIDDEN"
        public const val RATE_LIMIT_EXCEEDED: String = "RATE_LIMIT_EXCEEDED"
    }
}
