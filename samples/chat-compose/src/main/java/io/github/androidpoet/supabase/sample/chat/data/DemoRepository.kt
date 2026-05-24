package io.github.androidpoet.supabase.sample.chat.data

import io.github.androidpoet.supabase.auth.AuthClient
import io.github.androidpoet.supabase.auth.getUserForCurrentSession
import io.github.androidpoet.supabase.auth.models.Session
import io.github.androidpoet.supabase.auth.session.SessionManager
import io.github.androidpoet.supabase.auth.session.SessionState
import io.github.androidpoet.supabase.auth.signInWithEmailAndSaveSession
import io.github.androidpoet.supabase.auth.signOutCurrentSession
import io.github.androidpoet.supabase.auth.signUpWithEmailAndSaveSession
import io.github.androidpoet.supabase.client.defaultJson
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.core.result.map
import io.github.androidpoet.supabase.database.DatabaseClient
import io.github.androidpoet.supabase.database.insertTyped
import io.github.androidpoet.supabase.database.selectTyped
import io.github.androidpoet.supabase.functions.FunctionsClient
import io.github.androidpoet.supabase.functions.invokeTyped
import io.github.androidpoet.supabase.realtime.RealtimeClient
import io.github.androidpoet.supabase.realtime.RealtimeSubscription
import io.github.androidpoet.supabase.realtime.models.PostgresChangeEvent
import io.github.androidpoet.supabase.storage.StorageClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

class DemoRepository(
    private val auth: AuthClient,
    private val sessionManager: SessionManager,
    private val database: DatabaseClient,
    private val storage: StorageClient,
    private val realtime: RealtimeClient,
    private val functions: FunctionsClient,
    val defaultBucket: String,
    val defaultFunctionName: String,
) {
    private var subscription: RealtimeSubscription? = null

    suspend fun restoreSession(): SupabaseResult<Session> =
        sessionManager.restoreSession()

    suspend fun signUp(email: String, password: String): SupabaseResult<Session> =
        auth.signUpWithEmailAndSaveSession(sessionManager, email, password)

    suspend fun signIn(email: String, password: String): SupabaseResult<Session> =
        auth.signInWithEmailAndSaveSession(sessionManager, email, password)

    suspend fun getCurrentUser() = auth.getUserForCurrentSession(sessionManager)

    suspend fun signOut(): SupabaseResult<Unit> = auth.signOutCurrentSession(sessionManager)

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
        subscription?.unsubscribe()
        subscription = realtime.channel("room:$roomId")
            .onPostgresChange(
                schema = "public",
                table = "chat_messages",
                filter = "room_id=eq.$roomId",
                event = PostgresChangeEvent.INSERT,
            ) { payload: JsonObject ->
                payload["record"]?.let {
                    onInserted(defaultJson.decodeFromJsonElement(ChatMessage.serializer(), it))
                }
            }
            .subscribe()
    }

    suspend fun uploadText(
        bucket: String,
        path: String,
        content: String,
    ): SupabaseResult<String> = storage.upload(
        bucket = bucket,
        path = path,
        data = content.encodeToByteArray(),
        contentType = "text/plain",
        upsert = true,
    )

    suspend fun listFiles(bucket: String) = storage.list(bucket = bucket)

    suspend fun buildStorageUrls(bucket: String, path: String): SupabaseResult<StorageUrls> {
        val signed = storage.createSignedUrl(bucket = bucket, path = path, expiresIn = 3600)
        return when (signed) {
            is SupabaseResult.Failure -> signed
            is SupabaseResult.Success -> {
                SupabaseResult.Success(
                    StorageUrls(
                        publicUrl = storage.getPublicUrl(bucket = bucket, path = path),
                        signedUrl = signed.value,
                    ),
                )
            }
        }
    }

    suspend fun invokeFunction(functionName: String, body: String) =
        functions.invoke(functionName = functionName, body = body)

    suspend fun invokeFunctionTyped(functionName: String, body: String): SupabaseResult<FunctionEchoResponse> {
        val bodyValue = runCatching { defaultJson.decodeFromString<FunctionEchoRequest>(body) }
            .getOrElse {
                return SupabaseResult.Failure(SupabaseError(message = "Invalid JSON body: ${it.message}"))
            }
        return functions.invokeTyped<FunctionEchoRequest, FunctionEchoResponse>(
            functionName = functionName,
            request = bodyValue,
        )
    }

    suspend fun disconnect() {
        subscription?.unsubscribe()
        subscription = null
        realtime.disconnect()
        if (sessionManager.sessionState.value is SessionState.Authenticated) {
            sessionManager.close()
        }
    }
}

data class StorageUrls(
    val publicUrl: String,
    val signedUrl: String,
)

@Serializable
data class FunctionEchoRequest(
    val ping: String = "pong",
)

@Serializable
data class FunctionEchoResponse(
    val reply: String = "",
)
