package com.aichat.mobile.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichat.mobile.data.model.ChatMessageDto
import com.aichat.mobile.data.remote.StreamEvent
import com.aichat.mobile.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.Instant
import javax.inject.Inject

data class ChatUiState(
    val chatId: String = "",
    val title: String = "",
    val messages: List<ChatMessageDto> = emptyList(),
    val pendingImages: List<String> = emptyList(),
    val streaming: Boolean = false,
    val polling: Boolean = false,
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
    private var reconnectJob: Job? = null
    private var pollJob: Job? = null
    private var generationOpened = false
    private var reconnectAttempted = false
    private var lastContent: String = ""
    private var lastImages: List<String>? = null
    private var jobGeneration = 0

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
                    checkForActiveGeneration(chatId)
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(loading = false, error = t.message)
                }
        }
    }

    private fun checkForActiveGeneration(chatId: String) {
        viewModelScope.launch {
            val status = runCatching { repo.getGenerationStatus(chatId) }.getOrNull() ?: return@launch
            when (status.status) {
                "GENERATING" -> {
                    val assistantSeed = ChatMessageDto(
                        role = "assistant",
                        content = status.partialContent ?: "",
                        timestamp = Instant.now().toString(),
                    )
                    _state.value = _state.value.copy(
                        messages = _state.value.messages + assistantSeed,
                        streaming = true,
                        error = null,
                    )
                    lastContent = ""
                    lastImages = null
                    reconnectAttempted = false
                    generationOpened = false
                    startStreamJob(chatId)
                }
                "DONE" -> {
                    // Generation finished between getChat and getGenerationStatus; reload for final message
                    runCatching { repo.getChat(chatId) }
                        .onSuccess { chat -> _state.value = _state.value.copy(messages = chat.messages) }
                }
            }
        }
    }

    fun addImage(base64: String) {
        _state.value = _state.value.copy(pendingImages = _state.value.pendingImages + base64)
    }

    fun removeImage(index: Int) {
        val current = _state.value.pendingImages
        if (index !in current.indices) return
        _state.value = _state.value.copy(pendingImages = current.toMutableList().also { it.removeAt(index) })
    }

    fun sendStreaming(content: String) {
        val chatId = _state.value.chatId
        val images = _state.value.pendingImages
        if (chatId.isBlank() || _state.value.streaming || _state.value.polling) return
        if (content.isBlank() && images.isEmpty()) return

        val now = Instant.now().toString()
        val userMsg = ChatMessageDto(
            role = "user",
            content = content,
            timestamp = now,
            images = images.takeIf { it.isNotEmpty() },
        )
        val assistantSeed = ChatMessageDto(role = "assistant", content = "", timestamp = now)
        _state.value = _state.value.copy(
            messages = _state.value.messages + userMsg + assistantSeed,
            pendingImages = emptyList(),
            streaming = true,
            error = null,
        )

        lastContent = content
        lastImages = images.takeIf { it.isNotEmpty() }
        reconnectAttempted = false
        generationOpened = false
        reconnectJob?.cancel()
        startStreamJob(chatId)
    }

    private fun startStreamJob(chatId: String) {
        streamJob?.cancel()
        val myGeneration = ++jobGeneration
        streamJob = viewModelScope.launch {
            val buffer = StringBuilder()
            try {
                repo.streamMessage(chatId, lastContent, lastImages).collect { event ->
                    when (event) {
                        StreamEvent.Opened -> { generationOpened = true }
                        is StreamEvent.Token -> {
                            buffer.append(event.text)
                            updateLastAssistantContent(buffer.toString())
                        }
                        is StreamEvent.Replay -> {
                            // Server replays all content generated before we reconnected
                            buffer.setLength(0)
                            buffer.append(event.content)
                            updateLastAssistantContent(buffer.toString())
                        }
                        StreamEvent.Done -> {
                            updateLastAssistantContent(buffer.toString())
                            if (myGeneration == jobGeneration) {
                                _state.value = _state.value.copy(streaming = false)
                            }
                        }
                        is StreamEvent.Error -> {
                            if ((generationOpened || reconnectAttempted) && myGeneration == jobGeneration) {
                                handleStreamDisconnect(chatId)
                            } else if (myGeneration == jobGeneration) {
                                _state.value = _state.value.copy(streaming = false, error = event.message)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if ((generationOpened || reconnectAttempted) && myGeneration == jobGeneration) {
                    handleStreamDisconnect(chatId)
                } else if (myGeneration == jobGeneration) {
                    _state.value = _state.value.copy(streaming = false, error = e.message)
                }
            }
            if (myGeneration == jobGeneration && !_state.value.polling) {
                _state.value = _state.value.copy(streaming = false)
            }
        }
    }

    // Called when the SSE connection drops after the server confirmed generation started.
    // Checks generation status to decide: SSE reconnect (first failure), poll loop (second failure),
    // or immediate chat reload (DONE/ERROR).
    private fun handleStreamDisconnect(chatId: String) {
        _state.value = _state.value.copy(streaming = false, polling = true, error = null)
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            val status = runCatching { repo.getGenerationStatus(chatId) }.getOrNull()
            if (!isActive) return@launch
            when (status?.status) {
                "GENERATING" -> {
                    status.partialContent?.takeIf { it.isNotEmpty() }?.let {
                        updateLastAssistantContent(it)
                    }
                    if (!reconnectAttempted) {
                        // Re-POST to the same endpoint; server detects the running task and reconnects
                        _state.value = _state.value.copy(polling = false, streaming = true)
                        reconnectAttempted = true
                        generationOpened = false
                        startStreamJob(chatId)
                    } else {
                        startPollingLoop(chatId)
                    }
                }
                "DONE" -> {
                    runCatching { repo.getChat(chatId) }
                        .onSuccess { chat ->
                            _state.value = _state.value.copy(messages = chat.messages, polling = false)
                        }
                        .onFailure {
                            _state.value = _state.value.copy(polling = false)
                        }
                }
                "ERROR" -> {
                    _state.value = _state.value.copy(
                        polling = false,
                        error = status.error ?: "Generation failed",
                    )
                }
                "CANCELLED" -> {
                    _state.value = _state.value.copy(polling = false)
                }
                else -> startPollingLoop(chatId)
            }
        }
    }

    private fun startPollingLoop(chatId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(2_000L)
                val result = runCatching { repo.getGenerationStatus(chatId) }
                if (result.isFailure) {
                    val e = result.exceptionOrNull()
                    if (e is HttpException && e.code() == 404) {
                        // Task was removed after completion; reload the full chat
                        runCatching { repo.getChat(chatId) }
                            .onSuccess { chat ->
                                _state.value = _state.value.copy(messages = chat.messages, polling = false)
                            }
                            .onFailure {
                                _state.value = _state.value.copy(polling = false)
                            }
                        break
                    }
                    continue // Network error: keep polling
                }
                val status = result.getOrNull() ?: continue
                when (status.status) {
                    "GENERATING" -> {
                        status.partialContent?.takeIf { it.isNotEmpty() }?.let {
                            updateLastAssistantContent(it)
                        }
                    }
                    "DONE" -> {
                        runCatching { repo.getChat(chatId) }
                            .onSuccess { chat ->
                                _state.value = _state.value.copy(messages = chat.messages, polling = false)
                            }
                            .onFailure {
                                _state.value = _state.value.copy(polling = false)
                            }
                        break
                    }
                    "ERROR" -> {
                        _state.value = _state.value.copy(
                            polling = false,
                            error = status.error ?: "Generation failed",
                        )
                        break
                    }
                    "CANCELLED" -> {
                        _state.value = _state.value.copy(polling = false)
                        break
                    }
                }
            }
        }
    }

    fun stopStreaming() {
        val chatId = _state.value.chatId
        streamJob?.cancel()
        streamJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        pollJob?.cancel()
        pollJob = null
        _state.value = _state.value.copy(streaming = false, polling = false)
        if (chatId.isNotBlank() && generationOpened) {
            viewModelScope.launch { runCatching { repo.cancelGeneration(chatId) } }
        }
    }

    private fun updateLastAssistantContent(text: String) {
        val current = _state.value.messages.toMutableList()
        val idx = current.indexOfLast { it.role == "assistant" }
        if (idx >= 0) {
            current[idx] = current[idx].copy(content = text)
            _state.value = _state.value.copy(messages = current)
        }
    }

    override fun onCleared() {
        streamJob?.cancel()
        reconnectJob?.cancel()
        pollJob?.cancel()
        super.onCleared()
    }
}
