package io.github.androidpoet.supabase.sample.chat.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatRoom(
    val id: String,
    val name: String,
)

@Serializable
data class NewChatRoom(
    val name: String,
)

@Serializable
data class ChatMessage(
    val id: String,
    @SerialName("room_id") val roomId: String,
    @SerialName("sender_id") val senderId: String? = null,
    @SerialName("sender_name") val senderName: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class NewChatMessage(
    @SerialName("room_id") val roomId: String,
    @SerialName("sender_id") val senderId: String? = null,
    @SerialName("sender_name") val senderName: String,
    val body: String,
)
