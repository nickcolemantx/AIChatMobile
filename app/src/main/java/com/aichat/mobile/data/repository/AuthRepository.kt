package com.aichat.mobile.data.repository

import com.aichat.mobile.data.model.LoginRequest
import com.aichat.mobile.data.prefs.AppPreferences
import com.aichat.mobile.data.remote.ApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val prefs: AppPreferences,
) {
    suspend fun login(baseUrl: String, username: String, password: String): Result<Unit> =
        runCatching {
            // Persist the base URL before calling the API so the interceptor uses it.
            prefs.saveSession(baseUrl = baseUrl, token = "", username = "")
            val resp = api.login(LoginRequest(username = username, password = password))
            prefs.saveSession(baseUrl = baseUrl, token = resp.token, username = resp.username)
        }

    suspend fun logout() = prefs.clearSession()

    val token = prefs.token
}
