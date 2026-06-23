package io.github.androidpoet.supabase.sample.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.androidpoet.supabase.client.Supabase
import io.github.androidpoet.supabase.sample.gallery.data.GalleryRepository
import io.github.androidpoet.supabase.sample.gallery.ui.GalleryScreen
import io.github.androidpoet.supabase.storage.createStorageClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val configured = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

        setContent {
            MaterialTheme {
                Surface {
                    if (!configured) {
                        MissingConfigScreen()
                    } else {
                        val supabaseClient =
                            Supabase.create(
                                projectUrl = BuildConfig.SUPABASE_URL,
                                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                            )
                        val storage = createStorageClient(supabaseClient)
                        val repository = GalleryRepository(storage, BuildConfig.SUPABASE_STORAGE_BUCKET)

                        val vm: GalleryViewModel = viewModel(factory = GalleryViewModelFactory(repository))
                        val state by vm.state.collectAsStateWithLifecycle()

                        val context = LocalContext.current
                        val picker =
                            rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                                if (uri != null) {
                                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                    if (bytes != null) {
                                        val contentType = context.contentResolver.getType(uri) ?: "image/jpeg"
                                        val ext = contentType.substringAfter('/', "jpg")
                                        vm.onImagePicked(bytes, "photo-${System.currentTimeMillis()}.$ext", contentType)
                                    }
                                }
                            }

                        GalleryScreen(
                            bucket = state.bucket,
                            images = state.images,
                            previewName = state.previewName,
                            preview = state.preview,
                            urls = state.urls,
                            status = state.status,
                            error = state.error,
                            busy = state.busy,
                            onPick = {
                                picker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            onOpen = vm::open,
                            onDelete = vm::delete,
                            onRefresh = vm::refresh,
                        )
                    }
                }
            }
        }
    }
}
