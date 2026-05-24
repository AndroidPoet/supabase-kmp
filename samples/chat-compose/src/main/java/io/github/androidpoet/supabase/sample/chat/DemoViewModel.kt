package io.github.androidpoet.supabase.sample.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.sample.chat.data.ChatMessage
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
    val roomId: String = "general",
    val currentUserId: String = "demo-user",
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
        openChatIfNeeded()
    }

    fun selectTab(tab: DemoTab) {
        _state.update { it.copy(selectedTab = tab) }
        if (tab == DemoTab.CHAT) openChatIfNeeded()
    }

    fun setAuthEmail(value: String) = _state.update { it.copy(authEmail = value) }
    fun setAuthPassword(value: String) = _state.update { it.copy(authPassword = value) }
    fun setRoom(value: String) {
        val cleaned = value.trim()
        if (cleaned.isEmpty()) return
        _state.update { it.copy(roomId = cleaned, messages = emptyList(), hasMore = true) }
        chatSubscribed = false
        openChatIfNeeded()
    }

    fun setUserId(value: String) {
        val cleaned = value.trim()
        if (cleaned.isNotEmpty()) _state.update { it.copy(currentUserId = cleaned) }
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
                is SupabaseResult.Success -> _state.update { it.copy(authStatus = "Signed up: ${result.value.user.email}", error = null) }
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
                        error = null,
                    )
                }
                is SupabaseResult.Failure -> _state.update { it.copy(error = result.error.message) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            when (val result = repository.signOut()) {
                is SupabaseResult.Success -> _state.update { it.copy(authStatus = "Signed out", error = null) }
                is SupabaseResult.Failure -> _state.update { it.copy(error = result.error.message) }
            }
        }
    }

    fun sendMessage() {
        val snapshot = _state.value
        val text = snapshot.chatComposer.trim()
        if (text.isEmpty() || snapshot.sending) return
        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null) }
            when (val result = repository.sendMessage(snapshot.roomId, snapshot.currentUserId, text)) {
                is SupabaseResult.Success -> _state.update { it.copy(sending = false, chatComposer = "") }
                is SupabaseResult.Failure -> _state.update { it.copy(sending = false, error = result.error.message) }
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
                    )
                }
                is SupabaseResult.Failure -> Unit
            }
        }
    }

    private fun openChatIfNeeded() {
        if (chatSubscribed) return
        chatSubscribed = true
        viewModelScope.launch {
            _state.update { it.copy(loadingMessages = true, error = null) }
            repository.connectAndSubscribe(_state.value.roomId) { inserted ->
                _state.update { state ->
                    if (state.messages.any { it.id == inserted.id }) state
                    else state.copy(messages = listOf(inserted) + state.messages)
                }
            }
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
}
