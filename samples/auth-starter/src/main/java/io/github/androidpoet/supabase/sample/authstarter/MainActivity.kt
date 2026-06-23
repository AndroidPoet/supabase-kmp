package io.github.androidpoet.supabase.sample.authstarter

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
import io.github.androidpoet.supabase.sample.authstarter.data.AuthRepository
import io.github.androidpoet.supabase.sample.authstarter.ui.AuthScreen

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
                        val sessionManager = createSessionManager(auth, supabaseClient)

                        val vm: AuthViewModel =
                            viewModel(factory = AuthViewModelFactory(AuthRepository(auth, sessionManager)))
                        val state by vm.state.collectAsStateWithLifecycle()

                        AuthScreen(
                            state = state,
                            onEmailChanged = vm::setEmail,
                            onPasswordChanged = vm::setPassword,
                            onSignUp = vm::signUp,
                            onSignIn = vm::signIn,
                            onSignInAnonymously = vm::signInAnonymously,
                            onCurrentUser = vm::currentUser,
                            onRefresh = vm::refreshSession,
                            onInspectJwt = vm::inspectJwt,
                            onSignOut = vm::signOut,
                        )
                    }
                }
            }
        }
    }
}
