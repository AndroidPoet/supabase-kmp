package io.github.androidpoet.passkeys.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.androidpoet.passkeys.AndroidPasskeyClient
import io.github.androidpoet.supabase.auth.AuthClient
import io.github.androidpoet.supabase.auth.createAuthClient
import io.github.androidpoet.supabase.auth.passkey.PasskeysKmpAuthenticator
import io.github.androidpoet.supabase.auth.passkey.registerPasskey
import io.github.androidpoet.supabase.auth.passkey.signInWithPasskey
import io.github.androidpoet.supabase.client.Supabase
import io.github.androidpoet.supabase.core.result.SupabaseResult
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Minimal Supabase passkey sample driven entirely by the cross-platform
 * passkeys-kmp client. The whole ceremony runs through supabase-kmp's own
 * helpers ([registerPasskey] / [signInWithPasskey]); the only Android-specific
 * line is constructing [AndroidPasskeyClient] with this Activity.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PasskeyDemo()
                }
            }
        }
    }

    @Composable
    private fun PasskeyDemo() {
        val scope = rememberCoroutineScope()
        var log by remember { mutableStateOf("Ready.") }
        fun append(line: String) {
            log = "$log\n$line"
        }

        val configured = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

        // One Supabase + passkeys-kmp wiring, reused by both buttons.
        val auth: AuthClient? =
            remember {
                if (!configured) {
                    null
                } else {
                    val client = Supabase.create(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
                    createAuthClient(client)
                }
            }
        val authenticator = remember { PasskeysKmpAuthenticator(AndroidPasskeyClient(this@MainActivity)) }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Supabase Passkeys (passkeys-kmp)", style = MaterialTheme.typography.titleLarge)

            if (auth == null) {
                Text(
                    "Missing config. Run with -PSUPABASE_URL=... -PSUPABASE_ANON_KEY=...",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Button(onClick = {
                    scope.launch {
                        val email = "pk-${UUID.randomUUID()}@example.com"
                        val password = "Pk!${UUID.randomUUID()}"
                        append("\nSigning up $email …")
                        when (val signUp = auth.signUpWithEmail(email = email, password = password)) {
                            is SupabaseResult.Success -> {
                                append("User ${signUp.value.user.id}. Starting registration ceremony…")
                                when (val reg = auth.registerPasskey(signUp.value.accessToken, authenticator)) {
                                    is SupabaseResult.Success -> append("Registered passkey id=${reg.value.id}")
                                    is SupabaseResult.Failure ->
                                        append("Register failed: ${reg.error.code} ${reg.error.message}")
                                }
                            }
                            is SupabaseResult.Failure -> append("Sign-up failed: ${signUp.error.message}")
                        }
                    }
                }) { Text("Sign up + register passkey") }

                Button(onClick = {
                    scope.launch {
                        append("\nStarting authentication ceremony…")
                        when (val res = auth.signInWithPasskey(authenticator)) {
                            is SupabaseResult.Success ->
                                append("Signed in. user=${res.value.user.id}")
                            is SupabaseResult.Failure ->
                                append("Sign-in failed: ${res.error.code} ${res.error.message}")
                        }
                    }
                }) { Text("Sign in with passkey") }
            }

            Text(log, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}
