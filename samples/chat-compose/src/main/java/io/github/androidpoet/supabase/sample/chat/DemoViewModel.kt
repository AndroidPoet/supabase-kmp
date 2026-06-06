package io.github.androidpoet.supabase.sample.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.sample.chat.data.ChatMessage
import io.github.androidpoet.supabase.sample.chat.data.ChatRoom
import io.github.androidpoet.supabase.sample.chat.data.DemoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DemoTab(val label: String) {
    AUTH("Auth"),
    CHAT("Chat"),
    STORAGE("Storage"),
    FUNCTIONS("Functions"),
}

data class DemoUiState(
    val selectedTab: DemoTab = DemoTab.AUTH,
    val authEmail: String = "",
    val authPassword: String = "",
    val authStatus: String = "Not signed in",
    val authDiagnostics: String = "",
    val rooms: List<ChatRoom> = emptyList(),
    val roomId: String = "",
    val roomName: String = "",
    val currentUserId: String? = null,
    val senderName: String = "Guest",
    val chatDiagnostics: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val chatComposer: String = "",
    val loadingMessages: Boolean = false,
    val loadingMore: Boolean = false,
    val sending: Boolean = false,
    val hasMore: Boolean = true,
    val bucket: String = "",
    val storagePath: String = "demo.txt",
    val storageContent: String = "Hello from supabase-kmp",
    val storageResult: String = "",
    val functionName: String = "",
    val functionBody: String = "{\"ping\":\"pong\"}",
    val functionResult: String = "",
    val error: String? = null,
)

class DemoViewModel(
    private val repository: DemoRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        DemoUiState(
            bucket = repository.defaultBucket,
            functionName = repository.defaultFunctionName,
        ),
    )
    val state: StateFlow<DemoUiState> = _state.asStateFlow()

    private val pageSize = 20
    private var chatSubscribed = false

    init {
        restoreSession()
        loadRooms()
    }

    fun selectTab(tab: DemoTab) {
        _state.update { it.copy(selectedTab = tab) }
        if (tab == DemoTab.CHAT) openChatIfNeeded()
    }

    fun setAuthEmail(value: String) = _state.update { it.copy(authEmail = value) }
    fun setAuthPassword(value: String) = _state.update { it.copy(authPassword = value) }
    private fun selectRoom(room: ChatRoom) {
        if (room.id == _state.value.roomId) return
        _state.update {
            it.copy(
                roomId = room.id,
                roomName = room.name,
                messages = emptyList(),
                hasMore = true,
            )
        }
        chatSubscribed = false
        openChatIfNeeded()
    }

    fun setRoomName(value: String) {
        val cleaned = value.trim()
        _state.update { it.copy(roomName = value) }
        val room = _state.value.rooms.firstOrNull { it.name.equals(cleaned, ignoreCase = true) }
        if (room != null) selectRoom(room)
    }

    fun setSenderName(value: String) {
        val cleaned = value.trim()
        if (cleaned.isNotEmpty()) _state.update { it.copy(senderName = cleaned) }
    }

    fun setChatComposer(value: String) = _state.update { it.copy(chatComposer = value) }
    fun setBucket(value: String) = _state.update { it.copy(bucket = value) }
    fun setStoragePath(value: String) = _state.update { it.copy(storagePath = value) }
    fun setStorageContent(value: String) = _state.update { it.copy(storageContent = value) }
    fun setFunctionName(value: String) = _state.update { it.copy(functionName = value) }
    fun setFunctionBody(value: String) = _state.update { it.copy(functionBody = value) }

    fun signUp() {
        val snapshot = _state.value
        if (snapshot.authEmail.isBlank() || snapshot.authPassword.isBlank()) return
        viewModelScope.launch {
            when (val result = repository.signUp(snapshot.authEmail.trim(), snapshot.authPassword)) {
                is SupabaseResult.Success -> {
                    _state.update {
                        it.copy(
                            authStatus = "Signed up: ${result.value.user.email}",
                            currentUserId = result.value.user.id,
                            senderName = displayNameFor(result.value.user.email, result.value.user.id),
                            error = null,
                        )
                    }
                    chatSubscribed = false
                    openChatIfNeeded()
                }
                is SupabaseResult.Failure -> _state.update { it.copy(error = result.error.message) }
            }
        }
    }

    fun signIn() {
        val snapshot = _state.value
        if (snapshot.authEmail.isBlank() || snapshot.authPassword.isBlank()) return
        viewModelScope.launch {
            when (val result = repository.signIn(snapshot.authEmail.trim(), snapshot.authPassword)) {
                is SupabaseResult.Success -> {
                    _state.update {
                        it.copy(
                            authStatus = "Signed in: ${result.value.user.email}",
                            currentUserId = result.value.user.id,
                            senderName = displayNameFor(result.value.user.email, result.value.user.id),
                            error = null,
                        )
                    }
                    chatSubscribed = false
                    openChatIfNeeded()
                }
                is SupabaseResult.Failure -> _state.update { it.copy(error = result.error.message) }
            }
        }
    }

    fun signInAnonymously() {
        viewModelScope.launch {
            when (val result = repository.signInAnonymously()) {
                is SupabaseResult.Success -> {
                    _state.update {
                        it.copy(
                            authStatus = "Anonymous session: ${result.value.user.id}",
                            currentUserId = result.value.user.id,
                            senderName = "anon-${result.value.user.id.take(6)}",
                            error = null,
                        )
                    }
                    chatSubscribed = false
                    openChatIfNeeded()
                }
                is SupabaseResult.Failure -> _state.update { it.copy(error = result.error.message) }
            }
        }
    }

    fun getCurrentUser() {
        viewModelScope.launch {
            when (val result = repository.getCurrentUser()) {
                is SupabaseResult.Success -> _state.update {
                    it.copy(
                        authStatus = "Current user: ${result.value.email ?: result.value.id}",
                        currentUserId = result.value.id,
                        senderName = displayNameFor(result.value.email, result.value.id),
                        error = null,
                    )
                }
                is SupabaseResult.Failure -> _state.update { it.copy(error = result.error.message) }
            }
        }
    }

    fun refreshSession() {
        viewModelScope.launch {
            when (val result = repository.refreshSession()) {
                is SupabaseResult.Success -> _state.update {
                    it.copy(
                        authStatus = "Session refreshed: ${result.value.user.email ?: result.value.user.id}",
                        currentUserId = result.value.user.id,
                        senderName = displayNameFor(result.value.user.email, result.value.user.id),
                        error = null,
                    )
                }
                is SupabaseResult.Failure -> _state.update { it.copy(error = result.error.message) }
            }
        }
    }

    fun inspectJwt() {
        when (val result = repository.describeJwtClaims()) {
            is SupabaseResult.Success -> _state.update { it.copy(authDiagnostics = result.value, error = null) }
            is SupabaseResult.Failure -> _state.update { it.copy(error = result.error.message) }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            val result = repository.signOut()
            when (result) {
                is SupabaseResult.Success -> _state.update {
                    it.copy(
                        authStatus = "Signed out",
                        currentUserId = null,
                        senderName = "Guest",
                        error = null,
                    )
                }
                is SupabaseResult.Failure -> _state.update { it.copy(error = result.error.message) }
            }
            if (result is SupabaseResult.Success) {
                _state.update { it.copy(authDiagnostics = "") }
                chatSubscribed = false
                openChatIfNeeded()
            }
        }
    }

    fun sendMessage() {
        val snapshot = _state.value
        val text = snapshot.chatComposer.trim()
        if (text.isEmpty() || snapshot.sending) return
        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null) }
            val room = ensureRoomSelected(snapshot.roomName)
            if (room == null) {
                _state.update { it.copy(sending = false) }
                return@launch
            }
            when (
                val result = repository.sendMessage(
                    roomId = room.id,
                    senderId = snapshot.currentUserId,
                    senderName = snapshot.senderName,
                    body = text,
                )
            ) {
                is SupabaseResult.Success -> _state.update { it.copy(sending = false, chatComposer = "") }
                is SupabaseResult.Failure -> _state.update { it.copy(sending = false, error = result.error.message) }
            }
        }
    }

    fun sendBroadcast() {
        val snapshot = _state.value
        viewModelScope.launch {
            when (val result = repository.sendBroadcast(snapshot.senderName, "ping from ${snapshot.senderName}")) {
                is SupabaseResult.Success -> _state.update {
                    it.copy(chatDiagnostics = "Broadcast sent on ${snapshot.roomName}", error = null)
                }
                is SupabaseResult.Failure -> _state.update { it.copy(error = result.error.message) }
            }
        }
    }

    fun updatePresence() {
        val snapshot = _state.value
        viewModelScope.launch {
            when (val result = repository.updatePresence(snapshot.senderName)) {
                is SupabaseResult.Success -> _state.update {
                    it.copy(chatDiagnostics = "Presence tracked as ${snapshot.senderName}", error = null)
                }
                is SupabaseResult.Failure -> _state.update { it.copy(error = result.error.message) }
            }
        }
    }

    fun loadRoomDiagnostics() {
        val roomId = _state.value.roomId
        if (roomId.isBlank()) return
        viewModelScope.launch {
            when (val count = repository.loadRoomMessageCount(roomId)) {
                is SupabaseResult.Success -> {
                    when (val report = repository.buildDatabaseReport(roomId)) {
                        is SupabaseResult.Success -> _state.update {
                            it.copy(
                                chatDiagnostics = "RPC count: ${count.value}\n${report.value}",
                                error = null,
                            )
                        }
                        is SupabaseResult.Failure -> _state.update { it.copy(error = report.error.message) }
                    }
                }
                is SupabaseResult.Failure -> _state.update { it.copy(error = count.error.message) }
            }
        }
    }

    fun loadOlder() {
        val snapshot = _state.value
        if (snapshot.loadingMore || !snapshot.hasMore || snapshot.messages.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            val oldest = snapshot.messages.last().createdAt
            when (val result = repository.loadMessages(snapshot.roomId, oldest, pageSize)) {
                is SupabaseResult.Success -> {
                    _state.update {
                        it.copy(
                            loadingMore = false,
                            messages = (it.messages + result.value).distinctBy { msg -> msg.id },
                            hasMore = result.value.size >= pageSize,
                        )
                    }
                }
                is SupabaseResult.Failure -> _state.update { it.copy(loadingMore = false, error = result.error.message) }
            }
        }
    }

    fun uploadTextFile() {
        val snapshot = _state.value
        if (snapshot.bucket.isBlank() || snapshot.storagePath.isBlank()) return
        viewModelScope.launch {
            when (
                val result = repository.uploadText(
                    bucket = snapshot.bucket.trim(),
                    path = snapshot.storagePath.trim(),
                    content = snapshot.storageContent,
                )
            ) {
                is SupabaseResult.Success -> _state.update { it.copy(storageResult = "Uploaded key: ${result.value}", error = null) }
                is SupabaseResult.Failure -> _state.update { it.copy(error = result.error.message) }
            }
        }
    }

    fun inspectStorage() {
        val snapshot = _state.value
        if (snapshot.bucket.isBlank() || snapshot.storagePath.isBlank()) return
        viewModelScope.launch {
            when (val result = repository.inspectStorage(snapshot.bucket.trim(), snapshot.storagePath.trim())) {
                is SupabaseResult.Success -> _state.update { it.copy(storageResult = result.value, error = null) }
                is SupabaseResult.Failure -> _state.update { it.copy(error = result.error.message) }
            }
        }
    }

    fun listFiles() {
        val snapshot = _state.value
        if (snapshot.bucket.isBlank()) return
        viewModelScope.launch {
            when (val result = repository.listFiles(snapshot.bucket.trim())) {
                is SupabaseResult.Success -> {
                    val joined = result.value.joinToString(limit = 10) { it.name }
                    _state.update { it.copy(storageResult = "Files: $joined", error = null) }
                }
                is SupabaseResult.Failure -> _state.update { it.copy(error = result.error.message) }
            }
        }
    }

    fun removeFile() {
        val snapshot = _state.value
        if (snapshot.bucket.isBlank() || snapshot.storagePath.isBlank()) return
        viewModelScope.launch {
            when (val result = repository.removeFile(snapshot.bucket.trim(), snapshot.storagePath.trim())) {
                is SupabaseResult.Success -> _state.update { it.copy(storageResult = "Removed ${snapshot.storagePath}", error = null) }
                is SupabaseResult.Failure -> _state.update { it.copy(error = result.error.message) }
            }
        }
    }

    fun buildUrls() {
        val snapshot = _state.value
        if (snapshot.bucket.isBlank() || snapshot.storagePath.isBlank()) return
        viewModelScope.launch {
            when (val result = repository.buildStorageUrls(snapshot.bucket.trim(), snapshot.storagePath.trim())) {
                is SupabaseResult.Success -> _state.update {
                    it.copy(
                        storageResult = "Public: ${result.value.publicUrl}\nSigned: ${result.value.signedUrl}",
                        error = null,
                    )
                }
                is SupabaseResult.Failure -> _state.update { it.copy(error = result.error.message) }
            }
        }
    }

    fun invokeFunction() {
        val snapshot = _state.value
        if (snapshot.functionName.isBlank()) return
        viewModelScope.launch {
            when (val result = repository.invokeFunction(snapshot.functionName.trim(), snapshot.functionBody)) {
                is SupabaseResult.Success -> _state.update { it.copy(functionResult = result.value, error = null) }
                is SupabaseResult.Failure -> _state.update { it.copy(error = result.error.message) }
            }
        }
    }

    fun invokeFunctionTyped() {
        val snapshot = _state.value
        if (snapshot.functionName.isBlank()) return
        viewModelScope.launch {
            when (val result = repository.invokeFunctionTyped(snapshot.functionName.trim(), snapshot.functionBody)) {
                is SupabaseResult.Success -> _state.update { it.copy(functionResult = "Typed reply: ${result.value.reply}", error = null) }
                is SupabaseResult.Failure -> _state.update { it.copy(error = result.error.message) }
            }
        }
    }

    private fun restoreSession() {
        viewModelScope.launch {
            when (val result = repository.restoreSession()) {
                is SupabaseResult.Success -> _state.update {
                    it.copy(
                        authStatus = "Session restored: ${result.value.user.email ?: result.value.user.id}",
                        currentUserId = result.value.user.id,
                        senderName = displayNameFor(result.value.user.email, result.value.user.id),
                    )
                }
                is SupabaseResult.Failure -> Unit
            }
        }
    }

    private fun loadRooms() {
        viewModelScope.launch {
            _state.update { it.copy(loadingMessages = true, error = null) }
            when (val result = repository.loadRooms()) {
                is SupabaseResult.Success -> {
                    val rooms = if (result.value.isEmpty()) {
                        when (val created = repository.createRoom("general")) {
                            is SupabaseResult.Success -> listOf(created.value)
                            is SupabaseResult.Failure -> {
                                _state.update { it.copy(loadingMessages = false, error = created.error.message) }
                                return@launch
                            }
                        }
                    } else {
                        result.value
                    }
                    val selected = rooms.firstOrNull { it.name == "general" } ?: rooms.first()
                    _state.update {
                        it.copy(
                            rooms = rooms,
                            roomId = selected.id,
                            roomName = selected.name,
                            loadingMessages = false,
                            error = null,
                        )
                    }
                    openChatIfNeeded()
                }
                is SupabaseResult.Failure -> _state.update {
                    it.copy(loadingMessages = false, error = result.error.message)
                }
            }
        }
    }

    private fun openChatIfNeeded() {
        if (_state.value.roomId.isBlank()) return
        if (chatSubscribed) return
        chatSubscribed = true
        viewModelScope.launch {
            _state.update { it.copy(loadingMessages = true, error = null) }
            repository.connectAndSubscribe(
                roomId = _state.value.roomId,
                onInserted = { inserted ->
                    _state.update { state ->
                        if (state.messages.any { it.id == inserted.id }) state
                        else state.copy(messages = listOf(inserted) + state.messages)
                    }
                },
                onBroadcast = { status ->
                    _state.update { it.copy(chatDiagnostics = status) }
                },
            )
            when (val result = repository.loadMessages(_state.value.roomId, beforeCreatedAt = null, pageSize = pageSize)) {
                is SupabaseResult.Success -> {
                    _state.update {
                        it.copy(
                            loadingMessages = false,
                            messages = result.value,
                            hasMore = result.value.size >= pageSize,
                        )
                    }
                }
                is SupabaseResult.Failure -> _state.update { it.copy(loadingMessages = false, error = result.error.message) }
            }
        }
    }

    override fun onCleared() {
        viewModelScope.launch { repository.disconnect() }
    }

    private suspend fun ensureRoomSelected(roomName: String): ChatRoom? {
        val cleaned = roomName.trim().ifEmpty { "general" }
        val snapshot = _state.value
        snapshot.rooms.firstOrNull { it.id == snapshot.roomId && it.name.equals(cleaned, ignoreCase = true) }?.let {
            return it
        }
        snapshot.rooms.firstOrNull { it.name.equals(cleaned, ignoreCase = true) }?.let {
            selectRoom(it)
            return it
        }
        return when (val created = repository.createRoom(cleaned)) {
            is SupabaseResult.Success -> {
                val room = created.value
                _state.update { it.copy(rooms = (it.rooms + room).distinctBy { known -> known.id }) }
                selectRoom(room)
                room
            }
            is SupabaseResult.Failure -> {
                _state.update { it.copy(error = created.error.message) }
                null
            }
        }
    }

    private fun displayNameFor(email: String?, userId: String): String =
        email?.substringBefore('@')?.takeIf { it.isNotBlank() } ?: userId.take(8)
}
