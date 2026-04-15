package com.aichat.mobile.data.repository

import com.aichat.mobile.data.model.ChatDto
import com.aichat.mobile.data.model.ChatSummaryDto
import com.aichat.mobile.data.model.CreateChatRequest
import com.aichat.mobile.data.model.SendMessageRequest
import com.aichat.mobile.data.remote.ApiService
import com.aichat.mobile.data.remote.SseClient
import com.aichat.mobile.data.remote.StreamEvent
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val api: ApiService,
    private val sse: SseClient,
) {
    suspend fun listChats(): List<ChatSummaryDto> = api.listChats()

    suspend fun createChat(title: String?, model: String? = null): ChatDto =
        api.createChat(CreateChatRequest(title = title?.takeIf { it.isNotBlank() }, model = model))

    suspend fun getChat(id: String): ChatDto = api.getChat(id)

    suspend fun deleteChat(id: String) = api.deleteChat(id)

    suspend fun sendMessage(id: String, content: String) =
        api.sendMessage(id, SendMessageRequest(content = content))

    fun streamMessage(chatId: String, content: String): Flow<StreamEvent> =
        sse.streamMessage(chatId, content)
}
