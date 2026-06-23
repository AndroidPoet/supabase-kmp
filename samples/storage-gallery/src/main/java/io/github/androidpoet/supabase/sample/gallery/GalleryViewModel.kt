package io.github.androidpoet.supabase.sample.gallery

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.androidpoet.supabase.core.result.SupabaseResult
import io.github.androidpoet.supabase.sample.gallery.data.GalleryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GalleryUiState(
    val bucket: String = "",
    val images: List<String> = emptyList(),
    val previewName: String? = null,
    val preview: ImageBitmap? = null,
    val urls: String = "",
    val busy: Boolean = false,
    val status: String = "",
    val error: String? = null,
)

class GalleryViewModel(
    private val repository: GalleryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(GalleryUiState(bucket = repository.bucket))
    val state: StateFlow<GalleryUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            when (val result = repository.list()) {
                is SupabaseResult.Success -> _state.update { it.copy(busy = false, images = result.value) }
                is SupabaseResult.Failure -> _state.update { it.copy(busy = false, error = result.error.message) }
            }
        }
    }

    fun onImagePicked(bytes: ByteArray, fileName: String, contentType: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, status = "Uploading…") }
            when (val result = repository.upload(fileName, bytes, contentType)) {
                is SupabaseResult.Success -> {
                    _state.update {
                        it.copy(
                            busy = false,
                            status = "Uploaded $fileName",
                            previewName = fileName,
                            preview = bytes.toImageBitmap(),
                        )
                    }
                    refresh()
                }
                is SupabaseResult.Failure -> _state.update { it.copy(busy = false, error = result.error.message) }
            }
        }
    }

    fun open(name: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            when (val result = repository.downloadBytes(name)) {
                is SupabaseResult.Success -> {
                    val signed =
                        when (val s = repository.signedUrl(name)) {
                            is SupabaseResult.Success -> s.value
                            is SupabaseResult.Failure -> "(signed url failed: ${s.error.message})"
                        }
                    _state.update {
                        it.copy(
                            busy = false,
                            previewName = name,
                            preview = result.value.toImageBitmap(),
                            urls = "Public: ${repository.publicUrl(name)}\nSigned: $signed",
                        )
                    }
                }
                is SupabaseResult.Failure -> _state.update { it.copy(busy = false, error = result.error.message) }
            }
        }
    }

    fun delete(name: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            when (val result = repository.remove(name)) {
                is SupabaseResult.Success -> {
                    _state.update {
                        it.copy(
                            busy = false,
                            status = "Removed $name",
                            previewName = if (it.previewName == name) null else it.previewName,
                            preview = if (it.previewName == name) null else it.preview,
                            urls = if (it.previewName == name) "" else it.urls,
                        )
                    }
                    refresh()
                }
                is SupabaseResult.Failure -> _state.update { it.copy(busy = false, error = result.error.message) }
            }
        }
    }

    private fun ByteArray.toImageBitmap(): ImageBitmap? =
        runCatching { BitmapFactory.decodeByteArray(this, 0, size)?.asImageBitmap() }.getOrNull()
}

class GalleryViewModelFactory(
    private val repository: GalleryRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GalleryViewModel::class.java)) {
            return GalleryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
