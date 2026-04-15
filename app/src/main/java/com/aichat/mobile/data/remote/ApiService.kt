package com.aichat.mobile.data.remote

import com.aichat.mobile.data.model.ChatDto
import com.aichat.mobile.data.model.ChatMessageDto
import com.aichat.mobile.data.model.ChatSummaryDto
import com.aichat.mobile.data.model.CreateChatRequest
import com.aichat.mobile.data.model.LoginRequest
import com.aichat.mobile.data.model.LoginResponse
import com.aichat.mobile.data.model.SendMessageRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body req: LoginRequest): LoginResponse

    @GET("api/chats")
    suspend fun listChats(): List<ChatSummaryDto>

    @POST("api/chats")
    suspend fun createChat(@Body req: CreateChatRequest): ChatDto

    @GET("api/chats/{id}")
    suspend fun getChat(@Path("id") id: String): ChatDto

    @DELETE("api/chats/{id}")
    suspend fun deleteChat(@Path("id") id: String)

    @POST("api/chats/{id}/messages")
    suspend fun sendMessage(
        @Path("id") id: String,
        @Body req: SendMessageRequest,
    ): ChatMessageDto
}
