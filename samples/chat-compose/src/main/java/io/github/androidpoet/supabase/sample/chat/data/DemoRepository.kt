package io.github.androidpoet.supabase.sample.chat.data

import io.github.androidpoet.supabase.auth.AuthClient
import io.github.androidpoet.supabase.auth.getUserForCurrentSession
import io.github.androidpoet.supabase.auth.models.Session
import io.github.androidpoet.supabase.auth.parseCurrentSessionJwtClaims
import io.github.androidpoet.supabase.auth.refreshCurrentSession
import io.github.androidpoet.supabase.auth.session.SessionManager
import io.github.androidpoet.supabase.auth.session.SessionState
import io.github.androidpoet.supabase.auth.signInAnonymouslyAndSaveSession
import io.github.androidpoet.supabase.auth.signInWithEmailAndSaveSession
import io.github.androidpoet.supabase.auth.signOutCurrentSession
import io.github.androidpoet.supabase.auth.signUpWithEmailAndSaveSession
import io.github.androidpoet.supabase.client.defaultJson
import io.github.androidpoet.supabase.client.deserialize
import io.github.androidpoet.supabase.core.result.SupabaseError
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.core.result.map
import io.github.androidpoet.supabase.database.CountOption
import io.github.androidpoet.supabase.database.DatabaseClient
import io.github.androidpoet.supabase.database.insertTyped
import io.github.androidpoet.supabase.database.rpcGetSingleTyped
import io.github.androidpoet.supabase.database.selectCsv
import io.github.androidpoet.supabase.database.selectHead
import io.github.androidpoet.supabase.database.selectTyped
import io.github.androidpoet.supabase.functions.FunctionsClient
import io.github.androidpoet.supabase.functions.invokeTyped
import io.github.androidpoet.supabase.realtime.RealtimeClient
import io.github.androidpoet.supabase.realtime.RealtimeSubscription
import io.github.androidpoet.supabase.realtime.models.PostgresChangeEvent
import io.github.androidpoet.supabase.storage.StorageClient
import io.github.androidpoet.supabase.storage.createSignedDownloadUrlsByPath
import io.github.androidpoet.supabase.storage.getAuthenticatedUrlsByPath
import io.github.androidpoet.supabase.storage.getPublicUrlsByPath
import io.github.androidpoet.supabase.storage.remove
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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

    suspend fun signInAnonymously(): SupabaseResult<Session> =
        auth.signInAnonymouslyAndSaveSession(sessionManager)

    suspend fun getCurrentUser() = auth.getUserForCurrentSession(sessionManager)

    suspend fun refreshSession(): SupabaseResult<Session> =
        auth.refreshCurrentSession(sessionManager)

    fun describeJwtClaims(): SupabaseResult<String> =
        sessionManager.parseCurrentSessionJwtClaims().map { claims ->
            listOf("sub", "role", "exp", "iss")
                .mapNotNull { key -> claims[key]?.let { "$key=$it" } }
                .joinToString(prefix = "JWT claims: ")
                .ifBlank { "JWT claims parsed" }
        }

    suspend fun signOut(): SupabaseResult<Unit> = auth.signOutCurrentSession(sessionManager)

    suspend fun loadRooms(): SupabaseResult<List<ChatRoom>> = database.selectTyped(table = "chat_rooms") {
        order("name", ascending = true)
    }

    suspend fun createRoom(name: String): SupabaseResult<ChatRoom> =
        database.insert(
            table = "chat_rooms",
            body = defaultJson.encodeToString(NewChatRoom.serializer(), NewChatRoom(name = name)),
        ).deserialize<List<ChatRoom>>().map { it.first() }

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

    suspend fun sendMessage(
        roomId: String,
        senderId: String?,
        senderName: String,
        body: String,
    ): SupabaseResult<Unit> =
        database.insertTyped(
            table = "chat_messages",
            value = NewChatMessage(
                roomId = roomId,
                senderId = senderId,
                senderName = senderName,
                body = body,
            ),
        ).map { Unit }

    suspend fun connectAndSubscribe(
        roomId: String,
        onInserted: suspend (ChatMessage) -> Unit,
        onBroadcast: suspend (String) -> Unit,
    ) {
        realtime.connect()
        subscription?.unsubscribe()
        subscription = realtime.channel("room:$roomId")
            .configureBroadcast(receiveOwnBroadcasts = true, acknowledgeBroadcasts = true)
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
            .onBroadcast("sample_ping") { payload ->
                onBroadcast("Broadcast received: ${payload["text"] ?: payload}")
            }
            .subscribe()
    }

    suspend fun sendBroadcast(senderName: String, text: String): SupabaseResult<Unit> {
        val active = subscription ?: return SupabaseResult.Failure(SupabaseError(message = "Realtime channel is not open"))
        active.broadcast(
            event = "sample_ping",
            payload = buildJsonObject {
                put("sender", JsonPrimitive(senderName))
                put("text", JsonPrimitive(text))
            },
        )
        return SupabaseResult.Success(Unit)
    }

    suspend fun updatePresence(senderName: String): SupabaseResult<Unit> {
        val active = subscription ?: return SupabaseResult.Failure(SupabaseError(message = "Realtime channel is not open"))
        active.track(
            buildJsonObject {
                put("name", JsonPrimitive(senderName))
                put("status", JsonPrimitive("online"))
            },
        )
        return SupabaseResult.Success(Unit)
    }

    suspend fun loadRoomMessageCount(roomId: String): SupabaseResult<Int> =
        database.rpcGetSingleTyped(
            function = "chat_room_message_count",
            queryParams = mapOf("room" to roomId),
        )

    suspend fun buildDatabaseReport(roomId: String): SupabaseResult<String> {
        val head = database.selectHead(table = "chat_messages", count = CountOption.EXACT) {
            eq("room_id", roomId)
        }
        if (head is SupabaseResult.Failure) return head

        val csv = database.selectCsv(table = "chat_rooms", columns = "id,name") {
            order("name", ascending = true)
            limit(5)
        }
        return when (csv) {
            is SupabaseResult.Failure -> csv
            is SupabaseResult.Success -> SupabaseResult.Success("HEAD count check passed\nRooms CSV:\n${csv.value}")
        }
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

    suspend fun inspectStorage(bucket: String, path: String): SupabaseResult<String> {
        val buckets = when (val result = storage.listBuckets()) {
            is SupabaseResult.Failure -> return result
            is SupabaseResult.Success -> result.value
        }

        val bucketInfo = when (val result = storage.getBucket(bucket)) {
            is SupabaseResult.Failure -> return result
            is SupabaseResult.Success -> result.value
        }

        val exists = when (val result = storage.exists(bucket = bucket, path = path)) {
            is SupabaseResult.Failure -> return result
            is SupabaseResult.Success -> result.value
        }

        val infoName = if (exists) {
            when (val result = storage.info(bucket = bucket, path = path)) {
                is SupabaseResult.Failure -> return result
                is SupabaseResult.Success -> result.value.name
            }
        } else {
            "not uploaded yet"
        }

        val downloadPreview = if (exists) {
            when (val result = storage.download(bucket = bucket, path = path)) {
                is SupabaseResult.Failure -> return result
                is SupabaseResult.Success -> result.value.take(80)
            }
        } else {
            "not uploaded yet"
        }

        val signedPreview = if (exists) {
            when (
                val result = storage.createSignedDownloadUrlsByPath(
                    bucket = bucket,
                    paths = listOf(path),
                    expiresIn = 3600,
                )
            ) {
                is SupabaseResult.Failure -> return result
                is SupabaseResult.Success -> result.value[path]?.take(80)
            }
        } else {
            "not uploaded yet"
        }

        val publicUrls = storage.getPublicUrlsByPath(bucket = bucket, paths = listOf(path))
        val authenticatedUrls = storage.getAuthenticatedUrlsByPath(bucket = bucket, paths = listOf(path))

        return SupabaseResult.Success(
            buildString {
                appendLine("Buckets: ${buckets.joinToString(limit = 5) { it.name }}")
                appendLine("Bucket: ${bucketInfo.name}, public=${bucketInfo.public}")
                appendLine("Exists: $exists")
                appendLine("Info: $infoName")
                appendLine("Download: $downloadPreview")
                appendLine("Signed: $signedPreview")
                appendLine("Public URL: ${publicUrls[path]?.take(80)}")
                appendLine("Authenticated URL: ${authenticatedUrls[path]?.take(80)}")
            },
        )
    }

    suspend fun removeFile(bucket: String, path: String): SupabaseResult<Unit> =
        storage.remove(bucket, path)

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
