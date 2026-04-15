package com.aichat.mobile.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichat.mobile.data.model.ChatMessageDto
import com.aichat.mobile.data.remote.StreamEvent
import com.aichat.mobile.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class ChatUiState(
    val chatId: String = "",
    val title: String = "",
    val messages: List<ChatMessageDto> = emptyList(),
    val streaming: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repo: ChatRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var streamJob: Job? = null

    fun load(chatId: String) {
        if (_state.value.chatId == chatId && _state.value.messages.isNotEmpty()) return
        _state.value = _state.value.copy(chatId = chatId, loading = true, error = null)
        viewModelScope.launch {
            runCatching { repo.getChat(chatId) }
                .onSuccess { chat ->
                    _state.value = _state.value.copy(
                        chatId = chat.id,
                        title = chat.title,
                        messages = chat.messages,
                        loading = false,
                    )
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(loading = false, error = t.message)
                }
        }
    }

    fun sendStreaming(content: String) {
        val chatId = _state.value.chatId
        if (chatId.isBlank() || content.isBlank() || _state.value.streaming) return

        val now = Instant.now().toString()
        val userMsg = ChatMessageDto(role = "user", content = content, timestamp = now)
        val assistantSeed = ChatMessageDto(role = "assistant", content = "", timestamp = now)
        _state.value = _state.value.copy(
            messages = _state.value.messages + userMsg + assistantSeed,
            streaming = true,
            error = null,
        )

        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            val buffer = StringBuilder()
            runCatching {
                repo.streamMessage(chatId, content).collect { event ->
                    when (event) {
                        is StreamEvent.Token -> {
                            buffer.append(event.text)
                            appendToLastAssistant(buffer.toString())
                        }
                        StreamEvent.Done -> {
                            appendToLastAssistant(buffer.toString())
                            _state.value = _state.value.copy(streaming = false)
                        }
                        is StreamEvent.Error -> {
                            _state.value = _state.value.copy(
                                streaming = false,
                                error = event.message,
                            )
                        }
                    }
                }
            }.onFailure { t ->
                _state.value = _state.value.copy(streaming = false, error = t.message)
            }
            _state.value = _state.value.copy(streaming = false)
        }
    }

    fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        _state.value = _state.value.copy(streaming = false)
    }

    private fun appendToLastAssistant(text: String) {
        val current = _state.value.messages.toMutableList()
        val idx = current.indexOfLast { it.role == "assistant" }
        if (idx >= 0) {
            current[idx] = current[idx].copy(content = text)
            _state.value = _state.value.copy(messages = current)
        }
    }

    override fun onCleared() {
        streamJob?.cancel()
        super.onCleared()
    }
}
