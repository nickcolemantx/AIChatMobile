package com.aichat.mobile.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichat.mobile.data.model.ChatSummaryDto
import com.aichat.mobile.data.repository.AuthRepository
import com.aichat.mobile.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatListUiState(
    val chats: List<ChatSummaryDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepo: ChatRepository,
    private val authRepo: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatListUiState(loading = true))
    val state: StateFlow<ChatListUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { chatRepo.listChats() }
                .onSuccess { chats ->
                    _state.value = _state.value.copy(
                        chats = chats.sortedByDescending { it.updatedAt ?: it.createdAt ?: "" },
                        loading = false,
                        error = null,
                    )
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = t.message ?: "Failed to load chats",
                    )
                }
        }
    }

    fun createChat(title: String?, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { chatRepo.createChat(title) }
                .onSuccess { chat ->
                    refresh()
                    onCreated(chat.id)
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(error = t.message ?: "Failed to create")
                }
        }
    }

    fun deleteChat(id: String) {
        viewModelScope.launch {
            runCatching { chatRepo.deleteChat(id) }
                .onSuccess { refresh() }
                .onFailure { t ->
                    _state.value = _state.value.copy(error = t.message ?: "Failed to delete")
                }
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            authRepo.logout()
            onLoggedOut()
        }
    }
}
