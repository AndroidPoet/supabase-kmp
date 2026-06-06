package io.github.androidpoet.supabase.sample.desktop

import io.github.androidpoet.supabase.auth.createAuthClient
import io.github.androidpoet.supabase.auth.getUserForCurrentSession
import io.github.androidpoet.supabase.auth.signOutCurrentSession
import io.github.androidpoet.supabase.auth.signUpWithEmailAndSaveSession
import io.github.androidpoet.supabase.auth.session.createSessionManager
import io.github.androidpoet.supabase.client.Supabase
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.database.createDatabaseClient
import io.github.androidpoet.supabase.functions.createFunctionsClient
import io.github.androidpoet.supabase.realtime.createRealtimeClient
import io.github.androidpoet.supabase.storage.createStorageClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

private fun env(name: String): String = System.getenv(name)?.trim().orEmpty()

fun main(): Unit = runBlocking {
    val url = env("SUPABASE_URL")
    val anonKey = env("SUPABASE_ANON_KEY")
    val bucket = env("SUPABASE_STORAGE_BUCKET").ifBlank { "public" }
    val functionName = env("SUPABASE_FUNCTION_NAME").ifBlank { "hello-world" }

    if (url.isBlank() || anonKey.isBlank()) {
        println("Missing config. Set env vars:")
        println("  SUPABASE_URL=https://your-project.supabase.co")
        println("  SUPABASE_ANON_KEY=your-anon-key")
        println("Optional:")
        println("  SUPABASE_STORAGE_BUCKET=public")
        println("  SUPABASE_FUNCTION_NAME=hello-world")
        return@runBlocking
    }

    val client = Supabase.create(projectUrl = url, apiKey = anonKey)
    val auth = createAuthClient(client)
    val sessionManager = createSessionManager(auth, client)
    val database = createDatabaseClient(client)
    val storage = createStorageClient(client)
    val realtime = createRealtimeClient(client)
    val functions = createFunctionsClient(client)

    println("╔══════════════════════════════════════════════════════════╗")
    println("║     SUPABASE KMP — FULL FEATURE TEST (JVM)              ║")
    println("║     Project: $url")
    println("╚══════════════════════════════════════════════════════════╝")
    println()

    // ════════════════════════════════════════════════════════════════
    //  1. AUTH  — Sign up, sign in, get user, sign out
    // ════════════════════════════════════════════════════════════════
    println("┌─ 1. AUTH ──────────────────────────────────────────────┐")
    val testEmail = "test-${System.currentTimeMillis()}@example.com"
    val testPassword = "password123!"

    println("│ 🔸 Signing up: $testEmail")
    val signUpResult = auth.signUpWithEmailAndSaveSession(sessionManager, testEmail, testPassword)
    when (signUpResult) {
        is SupabaseResult.Success -> println("│    ✅ signUp: user=${signUpResult.value.user.id}")
        is SupabaseResult.Failure -> println("│    ⚠️  signUp: ${signUpResult.error.message}")
    }

    println("│ 🔸 Signing out...")
    auth.signOutCurrentSession(sessionManager)
    println("│    ✅ signOut")

    println("│ 🔸 Signing in: $testEmail")
    // Use extension that saves session
    val signInResult = auth.signInWithEmail(email = testEmail, password = testPassword)
    when (signInResult) {
        is SupabaseResult.Success -> {
            sessionManager.saveSession(signInResult.value)
            println("│    ✅ signIn: session token=${signInResult.value.accessToken.take(20)}...")
        }
        is SupabaseResult.Failure -> println("│    ❌ signIn: ${signInResult.error.message}")
    }

    println("│ 🔸 Getting current user...")
    val currentUser = auth.getUserForCurrentSession(sessionManager)
    when (currentUser) {
        is SupabaseResult.Success -> println("│    ✅ getUser: id=${currentUser.value.id}, email=${currentUser.value.email}")
        is SupabaseResult.Failure -> println("│    ❌ getUser: ${currentUser.error.message}")
    }

    println("└────────────────────────────────────────────────────────┘")
    println()

    // ════════════════════════════════════════════════════════════════
    //  2. DATABASE  — Read rooms, Insert/Select messages
    // ════════════════════════════════════════════════════════════════
    println("┌─ 2. DATABASE ─────────────────────────────────────────┐")
    println("│ 🔸 Listing chat rooms...")
    val roomsResult = database.select(table = "chat_rooms", columns = "id,name") { limit(10) }
    when (roomsResult) {
        is SupabaseResult.Success -> println("│    ✅ rooms: ${roomsResult.value.take(200)}")
        is SupabaseResult.Failure -> println("│    ❌ rooms: ${roomsResult.error.message}")
    }
    // Extract first room id for insert
    var roomId = ""
    if (roomsResult is SupabaseResult.Success) {
        try {
            val roomsArr = Json.parseToJsonElement(roomsResult.value).jsonArray
            if (roomsArr.isNotEmpty()) {
                roomId = roomsArr[0].jsonObject["id"]?.jsonPrimitive?.content ?: ""
            }
        } catch (_: Exception) {}
    }
    println("│    Using roomId=$roomId")

    println("│ 🔸 Inserting a message...")
    val messageBody = "Hello from KMP test at ${System.currentTimeMillis()}"
    val insertPayload = buildJsonObject {
        put("room_id", JsonPrimitive(roomId))
        put("sender_name", JsonPrimitive("test-bot"))
        put("body", JsonPrimitive(messageBody))
    }.toString()
    val insertResult = database.insert(table = "chat_messages", body = insertPayload)
    when (insertResult) {
        is SupabaseResult.Success -> println("│    ✅ insert: ${insertResult.value.take(150)}")
        is SupabaseResult.Failure -> println("│    ❌ insert: ${insertResult.error.message}")
    }

    println("│ 🔸 Selecting messages...")
    val selectResult = database.select(table = "chat_messages", columns = "id,room_id,sender_name,body,created_at") {
        limit(5)
        order("created_at", ascending = false)
    }
    when (selectResult) {
        is SupabaseResult.Success -> println("│    ✅ select: ${selectResult.value.take(300)}")
        is SupabaseResult.Failure -> println("│    ❌ select: ${selectResult.error.message}")
    }

    println("└────────────────────────────────────────────────────────┘")
    println()

    // ════════════════════════════════════════════════════════════════
    //  3. STORAGE  — List buckets, Upload, List files
    // ════════════════════════════════════════════════════════════════
    println("┌─ 3. STORAGE ──────────────────────────────────────────┐")
    println("│ 🔸 Listing buckets...")
    val bucketsResult = storage.listBuckets(limit = 5)
    when (bucketsResult) {
        is SupabaseResult.Success -> println("│    ✅ buckets: ${bucketsResult.value.map { it.name }}")
        is SupabaseResult.Failure -> println("│    ❌ buckets: ${bucketsResult.error.message}")
    }

    println("│ 🔸 Uploading test file...")
    val fileContent = "Hello from Supabase KMP! Timestamp: ${System.currentTimeMillis()}"
    val uploadBytes = fileContent.encodeToByteArray()
    val uploadResult = storage.upload(
        bucket = bucket,
        path = "test/hello-${System.currentTimeMillis()}.txt",
        data = uploadBytes,
        contentType = "text/plain"
    )
    when (uploadResult) {
        is SupabaseResult.Success -> println("│    ✅ upload: path=${uploadResult.value}")
        is SupabaseResult.Failure -> println("│    ❌ upload: ${uploadResult.error.message}")
    }

    println("│ 🔸 Listing files in bucket '$bucket'...")
    val listResult = storage.list(bucket = bucket, limit = 10)
    when (listResult) {
        is SupabaseResult.Success -> println("│    ✅ list: ${listResult.value.map { "${it.name} (${it.metadata?.get("size") ?: "?"} bytes)" }}")
        is SupabaseResult.Failure -> println("│    ❌ list: ${listResult.error.message}")
    }

    println("└────────────────────────────────────────────────────────┘")
    println()

    // ════════════════════════════════════════════════════════════════
    //  4. EDGE FUNCTIONS  — Invoke hello-world
    // ════════════════════════════════════════════════════════════════
    println("┌─ 4. EDGE FUNCTIONS ───────────────────────────────────┐")
    println("│ 🔸 Invoking '$functionName'...")
    val invokeResult = functions.invoke(
        functionName = functionName,
        body = """{"ping":"pong","time":${System.currentTimeMillis()}}"""
    )
    when (invokeResult) {
        is SupabaseResult.Success -> println("│    ✅ invoke: ${invokeResult.value.take(200)}")
        is SupabaseResult.Failure -> println("│    ❌ invoke: ${invokeResult.error.message}")
    }
    println("└────────────────────────────────────────────────────────┘")
    println()

    // ════════════════════════════════════════════════════════════════
    //  5. REALTIME  — Connect, Broadcast, Receive
    // ════════════════════════════════════════════════════════════════
    println("┌─ 5. REALTIME ─────────────────────────────────────────┐")
    println("│ 🔸 Connecting to realtime...")

    // Set up channel BEFORE connecting
    val channelBuilder = realtime.channel("test-channel")
    val receivedMessages = mutableListOf<String>()

    // Listen for broadcasts
    channelBuilder.onBroadcast("chat") { payload ->
        val text = payload["text"]?.jsonPrimitive?.content ?: "?"
        println("│    ⚡ RECEIVED broadcast: $text")
        receivedMessages.add(text)
    }

    // Connect to realtime server
    realtime.connect()
    delay(500)

    println("│    ✅ realtime connected: ${realtime.isConnected}")

    // Subscribe to channel
    println("│ 🔸 Subscribing to channel...")
    val subscription = channelBuilder.subscribe()
    delay(2000)

    println("│ 🔸 Sending broadcast...")
    subscription.broadcast(
        event = "chat",
        payload = buildJsonObject {
            put("text", JsonPrimitive("Hello from KMP!"))
            put("timestamp", JsonPrimitive(System.currentTimeMillis()))
        }
    )
    delay(2000)
    println("│    Broadcasts received: ${receivedMessages.size}")
    if (receivedMessages.isNotEmpty()) {
        println("│    ✅ realtime: Messages received OK")
    } else {
        println("│    ⚠️  realtime: No messages (broadcast may not self-reflect)")
    }

    subscription.unsubscribe()
    println("│    ✅ channel unsubscribed")
    println("└────────────────────────────────────────────────────────┘")
    println()

    // ════════════════════════════════════════════════════════════════
    //  6. CLEANUP
    // ════════════════════════════════════════════════════════════════
    println("┌─ 6. CLEANUP ──────────────────────────────────────────┐")
    auth.signOutCurrentSession(sessionManager)
    println("│    ✅ signed out")
    realtime.disconnect()
    println("│    ✅ realtime disconnected")
    println("└────────────────────────────────────────────────────────┘")
    println()

    println("╔══════════════════════════════════════════════════════════╗")
    println("║     ✅ ALL TESTS COMPLETED                              ║")
    println("╚══════════════════════════════════════════════════════════╝")
}
