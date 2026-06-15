package io.github.androidpoet.supabase.sample.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.androidpoet.supabase.sample.chat.DemoTab
import io.github.androidpoet.supabase.sample.chat.DemoUiState

@Composable
fun DemoScreen(
    state: DemoUiState,
    onTabSelected: (DemoTab) -> Unit,
    onAuthEmailChanged: (String) -> Unit,
    onAuthPasswordChanged: (String) -> Unit,
    onSignUp: () -> Unit,
    onSignIn: () -> Unit,
    onSignInAnonymously: () -> Unit,
    onGetCurrentUser: () -> Unit,
    onRefreshSession: () -> Unit,
    onInspectJwt: () -> Unit,
    onSignOut: () -> Unit,
    onRoomNameChanged: (String) -> Unit,
    onSenderNameChanged: (String) -> Unit,
    onChatComposerChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onSendBroadcast: () -> Unit,
    onUpdatePresence: () -> Unit,
    onLoadRoomDiagnostics: () -> Unit,
    onLoadOlder: () -> Unit,
    onBucketChanged: (String) -> Unit,
    onStoragePathChanged: (String) -> Unit,
    onStorageContentChanged: (String) -> Unit,
    onUploadTextFile: () -> Unit,
    onInspectStorage: () -> Unit,
    onListFiles: () -> Unit,
    onBuildUrls: () -> Unit,
    onRemoveFile: () -> Unit,
    onFunctionNameChanged: (String) -> Unit,
    onFunctionBodyChanged: (String) -> Unit,
    onInvokeFunction: () -> Unit,
    onInvokeFunctionTyped: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TabRow(selectedTabIndex = state.selectedTab.ordinal) {
            DemoTab.entries.forEach { tab ->
                Tab(
                    selected = state.selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    text = { Text(tab.label) },
                )
            }
        }

        state.error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        when (state.selectedTab) {
            DemoTab.AUTH ->
                AuthTab(
                    state = state,
                    onAuthEmailChanged = onAuthEmailChanged,
                    onAuthPasswordChanged = onAuthPasswordChanged,
                    onSignUp = onSignUp,
                    onSignIn = onSignIn,
                    onSignInAnonymously = onSignInAnonymously,
                    onGetCurrentUser = onGetCurrentUser,
                    onRefreshSession = onRefreshSession,
                    onInspectJwt = onInspectJwt,
                    onSignOut = onSignOut,
                )
            DemoTab.CHAT ->
                ChatTab(
                    state = state,
                    onRoomNameChanged = onRoomNameChanged,
                    onSenderNameChanged = onSenderNameChanged,
                    onChatComposerChanged = onChatComposerChanged,
                    onSendMessage = onSendMessage,
                    onSendBroadcast = onSendBroadcast,
                    onUpdatePresence = onUpdatePresence,
                    onLoadRoomDiagnostics = onLoadRoomDiagnostics,
                    onLoadOlder = onLoadOlder,
                )
            DemoTab.STORAGE ->
                StorageTab(
                    state = state,
                    onBucketChanged = onBucketChanged,
                    onStoragePathChanged = onStoragePathChanged,
                    onStorageContentChanged = onStorageContentChanged,
                    onUploadTextFile = onUploadTextFile,
                    onInspectStorage = onInspectStorage,
                    onListFiles = onListFiles,
                    onBuildUrls = onBuildUrls,
                    onRemoveFile = onRemoveFile,
                )
            DemoTab.FUNCTIONS ->
                FunctionsTab(
                    state = state,
                    onFunctionNameChanged = onFunctionNameChanged,
                    onFunctionBodyChanged = onFunctionBodyChanged,
                    onInvokeFunction = onInvokeFunction,
                    onInvokeFunctionTyped = onInvokeFunctionTyped,
                )
        }
    }
}

@Composable
private fun AuthTab(
    state: DemoUiState,
    onAuthEmailChanged: (String) -> Unit,
    onAuthPasswordChanged: (String) -> Unit,
    onSignUp: () -> Unit,
    onSignIn: () -> Unit,
    onSignInAnonymously: () -> Unit,
    onGetCurrentUser: () -> Unit,
    onRefreshSession: () -> Unit,
    onInspectJwt: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = state.authEmail, onValueChange = onAuthEmailChanged, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = state.authPassword, onValueChange = onAuthPasswordChanged, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSignUp) { Text("Sign Up") }
            Button(onClick = onSignIn) { Text("Sign In") }
            Button(onClick = onSignInAnonymously) { Text("Anon") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onGetCurrentUser) { Text("Get User") }
            Button(onClick = onRefreshSession) { Text("Refresh") }
            Button(onClick = onInspectJwt) { Text("JWT") }
            Button(onClick = onSignOut) { Text("Sign Out") }
        }
        Text(text = state.authStatus)
        if (state.authDiagnostics.isNotBlank()) {
            Text(text = state.authDiagnostics)
        }
    }
}

@Composable
private fun ChatTab(
    state: DemoUiState,
    onRoomNameChanged: (String) -> Unit,
    onSenderNameChanged: (String) -> Unit,
    onChatComposerChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onSendBroadcast: () -> Unit,
    onUpdatePresence: () -> Unit,
    onLoadRoomDiagnostics: () -> Unit,
    onLoadOlder: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(value = state.senderName, onValueChange = onSenderNameChanged, label = { Text("Display name") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = state.roomName, onValueChange = onRoomNameChanged, label = { Text("Room") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        if (state.rooms.isEmpty() && !state.loadingMessages) {
            Text("No rooms found")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onSendBroadcast) { Text("Broadcast") }
            Button(onClick = onUpdatePresence) { Text("Presence") }
            Button(onClick = onLoadRoomDiagnostics) { Text("DB") }
        }
        if (state.chatDiagnostics.isNotBlank()) {
            Text(text = state.chatDiagnostics)
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.loadingMessages) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(reverseLayout = true, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                    item {
                        LaunchedEffect(state.messages.size) { onLoadOlder() }
                        if (state.loadingMore) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    items(state.messages, key = { it.id }) { message ->
                        val mine = message.senderId != null && message.senderId == state.currentUserId
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .background(
                                            color = if (mine) MaterialTheme.colorScheme.primary else Color(0xFFE9EDF2),
                                            shape = RoundedCornerShape(14.dp),
                                        ).padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = message.senderName,
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
                value = state.chatComposer,
                onValueChange = onChatComposerChanged,
                label = { Text("Message") },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onSendMessage, enabled = !state.sending) { Text(if (state.sending) "..." else "Send") }
        }
    }
}

@Composable
private fun StorageTab(
    state: DemoUiState,
    onBucketChanged: (String) -> Unit,
    onStoragePathChanged: (String) -> Unit,
    onStorageContentChanged: (String) -> Unit,
    onUploadTextFile: () -> Unit,
    onInspectStorage: () -> Unit,
    onListFiles: () -> Unit,
    onBuildUrls: () -> Unit,
    onRemoveFile: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = state.bucket, onValueChange = onBucketChanged, label = { Text("Bucket") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = state.storagePath, onValueChange = onStoragePathChanged, label = { Text("Path") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = state.storageContent, onValueChange = onStorageContentChanged, label = { Text("Text Content") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onUploadTextFile) { Text("Upload") }
            Button(onClick = onInspectStorage) { Text("Inspect") }
            Button(onClick = onListFiles) { Text("List") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBuildUrls) { Text("URLs") }
            Button(onClick = onRemoveFile) { Text("Remove") }
        }
        Text(text = state.storageResult, modifier = Modifier.height(120.dp))
    }
}

@Composable
private fun FunctionsTab(
    state: DemoUiState,
    onFunctionNameChanged: (String) -> Unit,
    onFunctionBodyChanged: (String) -> Unit,
    onInvokeFunction: () -> Unit,
    onInvokeFunctionTyped: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = state.functionName, onValueChange = onFunctionNameChanged, label = { Text("Function Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = state.functionBody, onValueChange = onFunctionBodyChanged, label = { Text("JSON Body") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onInvokeFunction) { Text("Invoke Raw") }
            Button(onClick = onInvokeFunctionTyped) { Text("Invoke Typed") }
        }
        Text(text = state.functionResult)
    }
}
