package com.aichat.mobile.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val username: String,
    val password: String,
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val token: String,
    val username: String,
)

@JsonClass(generateAdapter = true)
data class CreateChatRequest(
    val title: String? = null,
    val model: String? = null,
)

@JsonClass(generateAdapter = true)
data class SendMessageRequest(
    val content: String,
)

@JsonClass(generateAdapter = true)
data class ChatSummaryDto(
    val id: String,
    val title: String,
    val model: String,
    val createdAt: String?,
    val updatedAt: String?,
    val messageCount: Int,
)

@JsonClass(generateAdapter = true)
data class ChatMessageDto(
    val role: String,
    val content: String,
    val timestamp: String?,
)

@JsonClass(generateAdapter = true)
data class ChatDto(
    val id: String,
    val userId: String?,
    val title: String,
    val model: String,
    val createdAt: String?,
    val updatedAt: String?,
    val messages: List<ChatMessageDto> = emptyList(),
)
