package com.aichat.mobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aichat.mobile.R
import com.aichat.mobile.data.model.ChatMessageDto
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    vm: ChatViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var followBottom by remember { mutableStateOf(true) }

    LaunchedEffect(chatId) { vm.load(chatId) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                val info = listState.layoutInfo
                val total = info.totalItemsCount
                followBottom = total == 0 || info.visibleItemsInfo
                    .lastOrNull { it.index == total - 1 }
                    ?.let { (it.offset + it.size) <= info.viewportEndOffset + 4 } == true
            }
        }
    }

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content?.length) {
        if (state.messages.isEmpty() || !followBottom) return@LaunchedEffect
        val last = state.messages.lastIndex
        listState.scrollToItem(last)
        val info = listState.layoutInfo.visibleItemsInfo.lastOrNull { it.index == last }
            ?: return@LaunchedEffect
        val overflow = (info.offset + info.size) - listState.layoutInfo.viewportEndOffset
        if (overflow > 0) listState.scrollBy(overflow.toFloat())
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title.ifBlank { "Chat" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            ) {
                items(state.messages) { msg ->
                    MessageBubble(
                        msg = msg,
                        onCopy = {
                            clipboard.setText(AnnotatedString(msg.content))
                            scope.launch { snackbar.showSnackbar("Copied") }
                        },
                    )
                }
                if (state.streaming && state.messages.lastOrNull()?.content?.isBlank() == true) {
                    item("typing") {
                        Text(
                            "…",
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            MessageInput(
                value = input,
                onValueChange = { input = it },
                streaming = state.streaming,
                onSend = {
                    val text = input.trim()
                    if (text.isNotBlank()) {
                        vm.sendStreaming(text)
                        input = ""
                    }
                },
                onStop = vm::stopStreaming,
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    msg: ChatMessageDto,
    onCopy: () -> Unit,
) {
    val isUser = msg.role == "user"
    val bg = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 16.dp,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(color = bg, shape = shape)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onCopy,
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Column {
                Text(
                    text = msg.content.ifBlank { " " },
                    color = fg,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (!isUser && msg.content.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onCopy, modifier = Modifier.height(24.dp)) {
                            Icon(
                                painter = painterResource(R.drawable.ic_content_copy),
                                contentDescription = "Copy",
                                tint = fg.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    streaming: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("Message") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            maxLines = 6,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        if (streaming) {
            FilledIconButton(onClick = onStop) {
                Icon(painterResource(R.drawable.ic_stop), contentDescription = "Stop")
            }
        } else {
            FilledIconButton(onClick = onSend, enabled = value.isNotBlank()) {
                Icon(painterResource(R.drawable.ic_send), contentDescription = "Send")
            }
        }
    }
}
