package io.github.androidpoet.supabase.core.types
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Type-safe wrapper for a Supabase user id (the GoTrue/`auth.users` UUID).
 *
 * A zero-cost `value class` so a user id can't be confused with another `String`
 * at call sites; serializes transparently as its underlying [value].
 */
@JvmInline
@Serializable
public value class UserId(
    public val value: String,
)

/**
 * Type-safe wrapper for an auth session id.
 *
 * A zero-cost `value class` that serializes as its underlying [value]; keeps a
 * session id from being mixed up with other `String` identifiers.
 */
@JvmInline
@Serializable
public value class SessionId(
    public val value: String,
)

/**
 * Type-safe wrapper for a Storage bucket id/name.
 *
 * A zero-cost `value class` that serializes as its underlying [value]; keeps a
 * bucket id from being mixed up with other `String` identifiers.
 */
@JvmInline
@Serializable
public value class BucketId(
    public val value: String,
)

/**
 * Type-safe wrapper for a Realtime channel/topic id.
 *
 * A zero-cost `value class` that serializes as its underlying [value]; keeps a
 * channel id from being mixed up with other `String` identifiers.
 */
@JvmInline
@Serializable
public value class ChannelId(
    public val value: String,
)

/**
 * A project's base URL (`https://<ref>.supabase.co`), with derived per-service
 * endpoints so callers don't hand-build paths.
 *
 * Each accessor appends the service's versioned path to [value]; e.g. for
 * `https://abc.supabase.co`, [restUrl] is `https://abc.supabase.co/rest/v1`. A
 * zero-cost `value class` that serializes as its underlying [value].
 */
@JvmInline
@Serializable
public value class ProjectUrl(
    public val value: String,
) {
    /** PostgREST (database) endpoint, `<value>/rest/v1`. */
    public val restUrl: String get() = "$value/rest/v1"

    /** GoTrue (auth) endpoint, `<value>/auth/v1`. */
    public val authUrl: String get() = "$value/auth/v1"

    /** Storage endpoint, `<value>/storage/v1`. */
    public val storageUrl: String get() = "$value/storage/v1"

    /** Realtime endpoint, `<value>/realtime/v1`. */
    public val realtimeUrl: String get() = "$value/realtime/v1"

    /** Edge Functions endpoint, `<value>/functions/v1`. */
    public val functionsUrl: String get() = "$value/functions/v1"
}
