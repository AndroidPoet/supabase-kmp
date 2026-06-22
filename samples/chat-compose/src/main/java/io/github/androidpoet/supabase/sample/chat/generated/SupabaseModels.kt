// Generated from the Supabase schema. Do not edit by hand.
package io.github.androidpoet.supabase.sample.chat.generated

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.String

@Serializable
public data class E2eMessages(
    public val id: String,
    public val body: String,
    @SerialName("created_at")
    public val createdAt: String,
)

@Serializable
public data class ChatRooms(
    public val id: String,
    public val name: String,
    @SerialName("created_at")
    public val createdAt: String,
)

@Serializable
public data class ChatMessages(
    public val id: String,
    @SerialName("room_id")
    public val roomId: String,
    @SerialName("sender_id")
    public val senderId: String? = null,
    @SerialName("sender_name")
    public val senderName: String,
    public val body: String,
    @SerialName("created_at")
    public val createdAt: String,
)
