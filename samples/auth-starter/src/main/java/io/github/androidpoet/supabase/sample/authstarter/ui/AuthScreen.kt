package io.github.androidpoet.supabase.sample.authstarter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.androidpoet.supabase.sample.authstarter.AuthUiState

@Composable
fun AuthScreen(
    state: AuthUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSignUp: () -> Unit,
    onSignIn: () -> Unit,
    onSignInAnonymously: () -> Unit,
    onCurrentUser: () -> Unit,
    onRefresh: () -> Unit,
    onInspectJwt: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Supabase Auth Starter", style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.status, style = MaterialTheme.typography.titleMedium)
                state.userId?.let { Text("user id: $it", style = MaterialTheme.typography.bodySmall) }
            }
        }

        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChanged,
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChanged,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSignIn, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Sign in") }
            OutlinedButton(onClick = onSignUp, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Sign up") }
        }
        OutlinedButton(onClick = onSignInAnonymously, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Continue anonymously")
        }

        if (state.signedIn) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCurrentUser, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Who am I") }
                OutlinedButton(onClick = onRefresh, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Refresh") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onInspectJwt, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("JWT claims") }
                Button(onClick = onSignOut, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Sign out") }
            }
        }

        if (state.claims.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    state.claims,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        state.error?.let {
            Text("Error: $it", color = MaterialTheme.colorScheme.error)
        }
    }
}
