package io.github.androidpoet.supabase.sample.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.androidpoet.supabase.client.SupabaseConfig
import io.github.androidpoet.supabase.client.di.supabaseModule
import io.github.androidpoet.supabase.database.DatabaseClient
import io.github.androidpoet.supabase.database.di.databaseModule
import io.github.androidpoet.supabase.realtime.RealtimeClient
import io.github.androidpoet.supabase.realtime.RealtimeConfig
import io.github.androidpoet.supabase.realtime.di.realtimeModule
import io.github.androidpoet.supabase.sample.chat.data.ChatRepository
import io.github.androidpoet.supabase.sample.chat.ui.ChatScreen
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

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
                        ensureKoin()
                        val koin = GlobalContext.get().koin
                        val database = koin.get<DatabaseClient>()
                        val realtime = koin.get<RealtimeClient>()

                        val vm: ChatViewModel = viewModel(
                            factory = ChatViewModelFactory(
                                ChatRepository(
                                    database = database,
                                    realtime = realtime,
                                ),
                            ),
                        )
                        val state by vm.state.collectAsStateWithLifecycle()

                        ChatScreen(
                            state = state,
                            onUserIdChanged = vm::setUserId,
                            onRoomChanged = vm::setRoom,
                            onComposerChanged = vm::updateComposer,
                            onSend = vm::send,
                            onLoadOlder = vm::loadOlder,
                        )
                    }
                }
            }
        }
    }

    private fun ensureKoin() {
        if (GlobalContext.getOrNull() != null) return
        startKoin {
            modules(
                supabaseModule(
                    projectUrl = BuildConfig.SUPABASE_URL,
                    apiKey = BuildConfig.SUPABASE_ANON_KEY,
                    config = SupabaseConfig(),
                ),
                databaseModule,
                realtimeModule(RealtimeConfig()),
            )
        }
    }
}
