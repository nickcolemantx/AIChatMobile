package com.aichat.mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aichat.mobile.ui.chat.ChatScreen
import com.aichat.mobile.ui.chatlist.ChatListScreen
import com.aichat.mobile.ui.login.LoginScreen
import com.aichat.mobile.ui.login.LoginViewModel

object Routes {
    const val LOGIN = "login"
    const val CHATS = "chats"
    const val CHAT = "chat/{chatId}"
    fun chat(id: String) = "chat/$id"
}

@Composable
fun AppNav(nav: NavHostController = rememberNavController()) {
    val loginVm: LoginViewModel = hiltViewModel()
    val token by loginVm.token.collectAsState(initial = null)
    val startDestination = if (token.isNullOrBlank()) Routes.LOGIN else Routes.CHATS

    // If the user gets logged out elsewhere, bounce them back to login.
    LaunchedEffect(token) {
        if (token.isNullOrBlank() && nav.currentDestination?.route != Routes.LOGIN) {
            nav.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(navController = nav, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = {
                nav.navigate(Routes.CHATS) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            })
        }
        composable(Routes.CHATS) {
            ChatListScreen(
                onOpenChat = { id -> nav.navigate(Routes.chat(id)) },
                onLogout = {
                    nav.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.CHAT) { entry ->
            val id = entry.arguments?.getString("chatId").orEmpty()
            ChatScreen(chatId = id, onBack = { nav.popBackStack() })
        }
    }
}
