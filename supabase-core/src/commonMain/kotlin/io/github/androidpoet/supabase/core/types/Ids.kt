package io.github.androidpoet.supabase.core.types
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
@JvmInline
@Serializable
public value class UserId(public val value: String)
@JvmInline
@Serializable
public value class SessionId(public val value: String)
@JvmInline
@Serializable
public value class BucketId(public val value: String)
@JvmInline
@Serializable
public value class ChannelId(public val value: String)
@JvmInline
@Serializable
public value class ProjectUrl(public val value: String) {
    public val restUrl: String get() = "$value/rest/v1"
    public val authUrl: String get() = "$value/auth/v1"
    public val storageUrl: String get() = "$value/storage/v1"
    public val realtimeUrl: String get() = "$value/realtime/v1"
    public val functionsUrl: String get() = "$value/functions/v1"
}
