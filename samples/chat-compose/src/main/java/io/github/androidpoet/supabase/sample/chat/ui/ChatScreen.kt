package io.github.androidpoet.supabase.sample.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.androidpoet.supabase.sample.chat.ChatUiState

@Composable
fun ChatScreen(
    state: ChatUiState,
    onUserIdChanged: (String) -> Unit,
    onRoomChanged: (String) -> Unit,
    onComposerChanged: (String) -> Unit,
    onSend: () -> Unit,
    onLoadOlder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.currentUserId,
                onValueChange = onUserIdChanged,
                label = { Text("User ID") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.roomId,
                onValueChange = onRoomChanged,
                label = { Text("Room") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }

        if (state.error != null) {
            Text(
                text = state.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        LaunchedEffect(state.messages.size) {
                            onLoadOlder()
                        }
                        if (state.loadingMore) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    items(state.messages, key = { it.id }) { message ->
                        val mine = message.senderId == state.currentUserId
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                        ) {
                            Column(
                                modifier = Modifier
                                    .background(
                                        color = if (mine) MaterialTheme.colorScheme.primary else Color(0xFFE9EDF2),
                                        shape = RoundedCornerShape(14.dp),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = message.senderId,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = message.body,
                                    color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.composerText,
                onValueChange = onComposerChanged,
                modifier = Modifier.weight(1f),
                label = { Text("Message") },
                maxLines = 3,
            )
            Button(onClick = onSend, enabled = !state.sending) {
                Text(if (state.sending) "..." else "Send", textAlign = TextAlign.Center)
            }
        }
    }
}
