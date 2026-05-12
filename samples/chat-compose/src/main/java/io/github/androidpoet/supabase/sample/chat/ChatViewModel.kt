package io.github.androidpoet.supabase.sample.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.sample.chat.data.ChatMessage
import io.github.androidpoet.supabase.sample.chat.data.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val roomId: String = "general",
    val currentUserId: String = "demo-user",
    val messages: List<ChatMessage> = emptyList(),
    val composerText: String = "",
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val sending: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
)

class ChatViewModel(
    private val repository: ChatRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    private val pageSize = 30

    fun setUserId(userId: String) {
        _state.update { it.copy(currentUserId = userId.trim().ifEmpty { it.currentUserId }) }
    }

    fun setRoom(roomId: String) {
        val cleaned = roomId.trim()
        if (cleaned.isNotEmpty() && cleaned != _state.value.roomId) {
            _state.update { ChatUiState(roomId = cleaned, currentUserId = it.currentUserId) }
            openRoom()
        }
    }

    fun updateComposer(value: String) {
        _state.update { it.copy(composerText = value) }
    }

    fun openRoom() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, messages = emptyList(), hasMore = true) }

            repository.connectAndSubscribe(_state.value.roomId) { inserted ->
                _state.update { state ->
                    if (state.messages.any { it.id == inserted.id }) state
                    else state.copy(messages = listOf(inserted) + state.messages)
                }
            }

            when (val result = repository.loadMessages(_state.value.roomId, null, pageSize)) {
                is SupabaseResult.Success -> {
                    _state.update {
                        it.copy(
                            loading = false,
                            messages = result.value,
                            hasMore = result.value.size >= pageSize,
                        )
                    }
                }
                is SupabaseResult.Failure -> {
                    _state.update { it.copy(loading = false, error = result.error.message) }
                }
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
                is SupabaseResult.Failure -> {
                    _state.update { it.copy(loadingMore = false, error = result.error.message) }
                }
            }
        }
    }

    fun send() {
        val snapshot = _state.value
        val text = snapshot.composerText.trim()
        if (text.isEmpty() || snapshot.sending) return

        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null) }
            when (val result = repository.sendMessage(snapshot.roomId, snapshot.currentUserId, text)) {
                is SupabaseResult.Success -> _state.update { it.copy(sending = false, composerText = "") }
                is SupabaseResult.Failure -> _state.update { it.copy(sending = false, error = result.error.message) }
            }
        }
    }

    override fun onCleared() {
        viewModelScope.launch { repository.disconnect() }
    }
}
