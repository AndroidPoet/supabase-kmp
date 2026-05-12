package io.github.androidpoet.supabase.sample.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.androidpoet.supabase.sample.chat.data.ChatRepository

class ChatViewModelFactory(
    private val repository: ChatRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(repository) as T
    }
}
