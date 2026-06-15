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
import io.github.androidpoet.supabase.auth.createAuthClient
import io.github.androidpoet.supabase.auth.session.createSessionManager
import io.github.androidpoet.supabase.client.Supabase
import io.github.androidpoet.supabase.database.createDatabaseClient
import io.github.androidpoet.supabase.functions.createFunctionsClient
import io.github.androidpoet.supabase.realtime.RealtimeConfig
import io.github.androidpoet.supabase.realtime.createRealtimeClient
import io.github.androidpoet.supabase.sample.chat.data.DemoRepository
import io.github.androidpoet.supabase.sample.chat.ui.DemoScreen
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
                        val auth = createAuthClient(supabaseClient)
                        val database = createDatabaseClient(supabaseClient)
                        val storage = createStorageClient(supabaseClient)
                        val realtime = createRealtimeClient(supabaseClient, RealtimeConfig())
                        val functions = createFunctionsClient(supabaseClient)
                        val sessionManager = createSessionManager(auth, supabaseClient)

                        val vm: DemoViewModel =
                            viewModel(
                                factory =
                                    DemoViewModelFactory(
                                        DemoRepository(
                                            auth = auth,
                                            sessionManager = sessionManager,
                                            database = database,
                                            storage = storage,
                                            realtime = realtime,
                                            functions = functions,
                                            defaultBucket = BuildConfig.SUPABASE_STORAGE_BUCKET,
                                            defaultFunctionName = BuildConfig.SUPABASE_FUNCTION_NAME,
                                        ),
                                    ),
                            )
                        val state by vm.state.collectAsStateWithLifecycle()

                        DemoScreen(
                            state = state,
                            onTabSelected = vm::selectTab,
                            onAuthEmailChanged = vm::setAuthEmail,
                            onAuthPasswordChanged = vm::setAuthPassword,
                            onSignUp = vm::signUp,
                            onSignIn = vm::signIn,
                            onSignInAnonymously = vm::signInAnonymously,
                            onGetCurrentUser = vm::getCurrentUser,
                            onRefreshSession = vm::refreshSession,
                            onInspectJwt = vm::inspectJwt,
                            onSignOut = vm::signOut,
                            onRoomNameChanged = vm::setRoomName,
                            onSenderNameChanged = vm::setSenderName,
                            onChatComposerChanged = vm::setChatComposer,
                            onSendMessage = vm::sendMessage,
                            onSendBroadcast = vm::sendBroadcast,
                            onUpdatePresence = vm::updatePresence,
                            onLoadRoomDiagnostics = vm::loadRoomDiagnostics,
                            onLoadOlder = vm::loadOlder,
                            onBucketChanged = vm::setBucket,
                            onStoragePathChanged = vm::setStoragePath,
                            onStorageContentChanged = vm::setStorageContent,
                            onUploadTextFile = vm::uploadTextFile,
                            onInspectStorage = vm::inspectStorage,
                            onListFiles = vm::listFiles,
                            onBuildUrls = vm::buildUrls,
                            onRemoveFile = vm::removeFile,
                            onFunctionNameChanged = vm::setFunctionName,
                            onFunctionBodyChanged = vm::setFunctionBody,
                            onInvokeFunction = vm::invokeFunction,
                            onInvokeFunctionTyped = vm::invokeFunctionTyped,
                        )
                    }
                }
            }
        }
    }
}
