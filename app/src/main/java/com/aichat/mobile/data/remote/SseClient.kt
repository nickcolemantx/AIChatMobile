package com.aichat.mobile.data.remote

import android.util.Log
import com.aichat.mobile.data.model.SendMessageRequest
import com.aichat.mobile.data.prefs.AppPreferences
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AIChatSse"

sealed interface StreamEvent {
    data class Token(val text: String) : StreamEvent
    data object Done : StreamEvent
    data class Error(val message: String) : StreamEvent
}

@Singleton
class SseClient @Inject constructor(
    private val client: OkHttpClient,
    private val prefs: AppPreferences,
    private val moshi: Moshi,
) {

    private val tokenAdapter: JsonAdapter<Map<String, String>> =
        moshi.adapter(Types.newParameterizedType(Map::class.java, String::class.java, String::class.java))

    fun streamMessage(chatId: String, content: String): Flow<StreamEvent> = callbackFlow {
        val base = prefs.currentBaseUrl()
        val token = prefs.currentToken()
        if (base.isNullOrBlank() || token.isNullOrBlank()) {
            trySend(StreamEvent.Error("Not signed in"))
            close()
            return@callbackFlow
        }

        val bodyJson = moshi.adapter(SendMessageRequest::class.java)
            .toJson(SendMessageRequest(content = content))
        val request = Request.Builder()
            .url(base.trimEnd('/') + "/api/chats/" + chatId + "/messages")
            .header("Authorization", "Bearer $token")
            .header("Accept", "text/event-stream")
            .header("X-Stream", "true")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.d(TAG, "onOpen status=${response.code} ct=${response.header("Content-Type")}")
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                Log.d(TAG, "onEvent type=$type len=${data.length} head=${data.take(40)}")
                when (type) {
                    "token" -> {
                        val text = runCatching { tokenAdapter.fromJson(data)?.get("t") }.getOrNull() ?: data
                        trySend(StreamEvent.Token(text))
                    }
                    "done" -> {
                        trySend(StreamEvent.Done)
                        close()
                    }
                    "error" -> {
                        trySend(StreamEvent.Error(data))
                        close()
                    }
                    else -> {
                        // Unnamed SSE events default to "message". Treat them as tokens too.
                        if (!data.isEmpty()) trySend(StreamEvent.Token(data))
                    }
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val body = runCatching { response?.body?.string() }.getOrNull()
                val msg = body?.takeIf { it.isNotBlank() } ?: t?.message ?: response?.message ?: "Stream failed"
                Log.w(TAG, "onFailure status=${response?.code} ct=${response?.header("Content-Type")} body=$body", t)
                trySend(StreamEvent.Error(msg))
                close()
            }

            override fun onClosed(eventSource: EventSource) {
                Log.d(TAG, "onClosed")
                close()
            }
        }

        val source = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose { source.cancel() }
    }
}
