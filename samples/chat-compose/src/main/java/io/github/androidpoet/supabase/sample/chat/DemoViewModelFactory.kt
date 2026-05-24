package io.github.androidpoet.supabase.sample.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.androidpoet.supabase.sample.chat.data.DemoRepository

class DemoViewModelFactory(
    private val repository: DemoRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DemoViewModel::class.java)) {
            return DemoViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
