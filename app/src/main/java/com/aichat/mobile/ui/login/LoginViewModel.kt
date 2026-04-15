package com.aichat.mobile.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichat.mobile.data.prefs.AppPreferences
import com.aichat.mobile.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    val token = auth.token

    init {
        viewModelScope.launch {
            prefs.currentBaseUrl()?.let { saved ->
                _state.value = _state.value.copy(serverUrl = saved)
            }
        }
    }

    fun setServerUrl(v: String) { _state.value = _state.value.copy(serverUrl = v, error = null) }
    fun setUsername(v: String) { _state.value = _state.value.copy(username = v, error = null) }
    fun setPassword(v: String) { _state.value = _state.value.copy(password = v, error = null) }

    fun submit(onSuccess: () -> Unit) {
        val s = _state.value
        if (s.serverUrl.isBlank() || s.username.isBlank() || s.password.isBlank()) {
            _state.value = s.copy(error = "Fill every field")
            return
        }
        _state.value = s.copy(submitting = true, error = null)
        viewModelScope.launch {
            val result = auth.login(s.serverUrl, s.username, s.password)
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(submitting = false, password = "")
                    onSuccess()
                },
                onFailure = { t ->
                    _state.value = _state.value.copy(
                        submitting = false,
                        error = t.message ?: "Login failed",
                    )
                },
            )
        }
    }
}
