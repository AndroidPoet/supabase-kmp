package io.github.androidpoet.supabase.sample.chat.data

import io.github.androidpoet.supabase.client.defaultJson
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.core.result.map
import io.github.androidpoet.supabase.database.DatabaseClient
import io.github.androidpoet.supabase.database.insertTyped
import io.github.androidpoet.supabase.database.selectTyped
import io.github.androidpoet.supabase.realtime.PostgresChangeEvent
import io.github.androidpoet.supabase.realtime.RealtimeClient
import io.github.androidpoet.supabase.realtime.RealtimeSubscription
import kotlinx.serialization.json.JsonObject

class ChatRepository(
    private val database: DatabaseClient,
    private val realtime: RealtimeClient,
) {
    private var subscription: RealtimeSubscription? = null

    suspend fun loadMessages(
        roomId: String,
        beforeCreatedAt: String?,
        pageSize: Int,
    ): SupabaseResult<List<ChatMessage>> = database.selectTyped(table = "chat_messages") {
        eq("room_id", roomId)
        if (beforeCreatedAt != null) lt("created_at", beforeCreatedAt)
        order("created_at", ascending = false)
        limit(pageSize)
    }

    suspend fun sendMessage(roomId: String, senderId: String, body: String): SupabaseResult<Unit> =
        database.insertTyped(
            table = "chat_messages",
            value = NewChatMessage(roomId = roomId, senderId = senderId, body = body),
        ).map { Unit }

    suspend fun connectAndSubscribe(
        roomId: String,
        onInserted: suspend (ChatMessage) -> Unit,
    ) {
        realtime.connect()
        subscription = realtime.channel("room:$roomId")
            .onPostgresChange(
                schema = "public",
                table = "chat_messages",
                filter = "room_id=eq.$roomId",
                event = PostgresChangeEvent.INSERT,
            ) { payload: JsonObject ->
                val record = payload["record"]
                if (record != null) {
                    onInserted(defaultJson.decodeFromJsonElement(record))
                }
            }
            .subscribe()
    }

    suspend fun disconnect() {
        subscription?.unsubscribe()
        subscription = null
        realtime.disconnect()
    }
}
