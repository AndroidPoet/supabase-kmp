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
import io.github.androidpoet.supabase.client.Supabase
import io.github.androidpoet.supabase.database.createDatabaseClient
import io.github.androidpoet.supabase.realtime.RealtimeConfig
import io.github.androidpoet.supabase.realtime.createRealtimeClient
import io.github.androidpoet.supabase.sample.chat.data.ChatRepository
import io.github.androidpoet.supabase.sample.chat.ui.ChatScreen

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
                        val supabaseClient = Supabase.create(
                            projectUrl = BuildConfig.SUPABASE_URL,
                            apiKey = BuildConfig.SUPABASE_ANON_KEY,
                        )
                        val database = createDatabaseClient(supabaseClient)
                        val realtime = createRealtimeClient(supabaseClient, RealtimeConfig())

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
}
