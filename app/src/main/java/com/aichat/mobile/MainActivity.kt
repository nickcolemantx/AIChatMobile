package com.aichat.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.aichat.mobile.ui.navigation.AppNav
import com.aichat.mobile.ui.theme.AIChatTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var deepLinkChatId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLinkChatId = intent.getStringExtra("chatId")
        enableEdgeToEdge()
        setContent {
            AIChatTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNav(deepLinkChatId = deepLinkChatId)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkChatId = intent.getStringExtra("chatId")
    }
}
