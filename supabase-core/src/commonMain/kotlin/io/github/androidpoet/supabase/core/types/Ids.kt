package io.github.androidpoet.supabase.core.types

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** Unique identifier for a Supabase user. */
@JvmInline
@Serializable
public value class UserId(public val value: String)

/** Unique identifier for an authentication session. */
@JvmInline
@Serializable
public value class SessionId(public val value: String)

/** Unique identifier for a Supabase Storage bucket. */
@JvmInline
@Serializable
public value class BucketId(public val value: String)

/** Unique identifier for a Supabase Realtime channel. */
@JvmInline
@Serializable
public value class ChannelId(public val value: String)

/**
 * The base URL of a Supabase project (e.g. `https://xyzcompany.supabase.co`).
 *
 * Derived properties expose the well-known service endpoints.
 */
@JvmInline
@Serializable
public value class ProjectUrl(public val value: String) {

    /** PostgREST endpoint — `{base}/rest/v1` */
    public val restUrl: String get() = "$value/rest/v1"

    /** GoTrue auth endpoint — `{base}/auth/v1` */
    public val authUrl: String get() = "$value/auth/v1"

    /** Storage endpoint — `{base}/storage/v1` */
    public val storageUrl: String get() = "$value/storage/v1"

    /** Realtime endpoint — `{base}/realtime/v1` */
    public val realtimeUrl: String get() = "$value/realtime/v1"

    /** Edge Functions endpoint — `{base}/functions/v1` */
    public val functionsUrl: String get() = "$value/functions/v1"
}
