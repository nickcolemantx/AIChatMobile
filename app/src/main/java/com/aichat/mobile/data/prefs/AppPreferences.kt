package com.aichat.mobile.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "aichat_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyBaseUrl = stringPreferencesKey("base_url")
    private val keyToken = stringPreferencesKey("jwt_token")
    private val keyUsername = stringPreferencesKey("username")

    val baseUrl: Flow<String?> = context.dataStore.data.map { it[keyBaseUrl] }
    val token: Flow<String?> = context.dataStore.data.map { it[keyToken] }
    val username: Flow<String?> = context.dataStore.data.map { it[keyUsername] }

    suspend fun currentBaseUrl(): String? = baseUrl.first()
    suspend fun currentToken(): String? = token.first()

    suspend fun saveSession(baseUrl: String, token: String, username: String) {
        context.dataStore.edit {
            it[keyBaseUrl] = normalizeBaseUrl(baseUrl)
            it[keyToken] = token
            it[keyUsername] = username
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(keyToken)
            it.remove(keyUsername)
        }
    }

    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim()
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }
}
