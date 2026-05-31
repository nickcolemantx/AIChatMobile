package com.aichat.mobile.service

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aichat.mobile.AIChatApp
import com.aichat.mobile.MainActivity
import com.aichat.mobile.R
import com.aichat.mobile.data.prefs.AppPreferences
import com.aichat.mobile.data.repository.ChatRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AIChatFcmService : FirebaseMessagingService() {

    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var repo: ChatRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            prefs.saveFcmToken(token)
            runCatching { repo.registerFcmToken(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val chatId = message.data["chatId"]
        val title = message.notification?.title ?: getString(R.string.app_name)
        val body = message.notification?.body ?: "Your response is ready"
        showNotification(title, body, chatId)
    }

    private fun showNotification(title: String, body: String, chatId: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            chatId?.let { putExtra("chatId", it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, chatId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, AIChatApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (canPost) {
            NotificationManagerCompat.from(this).notify(chatId.hashCode(), notification)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
