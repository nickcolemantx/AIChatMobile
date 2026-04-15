package com.aichat.mobile.ui.chatlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aichat.mobile.R
import com.aichat.mobile.data.model.ChatSummaryDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    vm: ChatListViewModel = hiltViewModel(),
    onOpenChat: (String) -> Unit,
    onLogout: () -> Unit,
) {
    val state by vm.state.collectAsState()
    var showNewDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ChatSummaryDto?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chats") },
                actions = {
                    IconButton(onClick = { vm.logout(onLogout) }) {
                        Icon(painterResource(R.drawable.ic_logout), contentDescription = "Log out")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewDialog = true }) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = "New chat")
            }
        },
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            when {
                state.loading && state.chats.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                state.chats.isEmpty() -> {
                    Text(
                        state.error ?: "No chats yet. Tap + to start one.",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.chats, key = { it.id }) { chat ->
                            ChatRow(
                                chat = chat,
                                onClick = { onOpenChat(chat.id) },
                                onDelete = { pendingDelete = chat },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (showNewDialog) {
        NewChatDialog(
            onDismiss = { showNewDialog = false },
            onCreate = { title ->
                showNewDialog = false
                vm.createChat(title) { id -> onOpenChat(id) }
            },
        )
    }

    pendingDelete?.let { chat ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete chat?") },
            text = { Text("\"${chat.title}\" will be removed permanently.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteChat(chat.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ChatRow(
    chat: ChatSummaryDto,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(chat.title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                "${chat.model} · ${chat.messageCount} messages",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete")
        }
    }
}

@Composable
private fun NewChatDialog(
    onDismiss: () -> Unit,
    onCreate: (String?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New chat") },
        text = {
            Column {
                Text("Give it a title (optional)", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(title.ifBlank { null }) },
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
