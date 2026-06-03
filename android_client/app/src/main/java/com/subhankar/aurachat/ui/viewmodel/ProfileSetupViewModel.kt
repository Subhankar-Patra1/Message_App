package com.subhankar.aurachat.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subhankar.aurachat.network.AuthService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileSetupUiState(
    val firstName: String = "",
    val lastName: String = "",
    val username: String = "",
    val pin: String = "",
    val isUsernameAvailable: Boolean? = null,
    val isCheckingUsername: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isComplete: Boolean = false
)

@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    private val authService: AuthService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileSetupUiState())
    val uiState: StateFlow<ProfileSetupUiState> = _uiState

    private var usernameCheckJob: Job? = null

    fun updateFirstName(name: String) {
        _uiState.value = _uiState.value.copy(firstName = name)
    }

    fun updateLastName(name: String) {
        _uiState.value = _uiState.value.copy(lastName = name)
    }

    fun updateUsername(username: String) {
        _uiState.value = _uiState.value.copy(username = username, isUsernameAvailable = null)
        // Debounced availability check
        usernameCheckJob?.cancel()
        if (username.length >= 3) {
            usernameCheckJob = viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isCheckingUsername = true)
                delay(500)
                try {
                    val available = authService.checkUsername(username)
                    _uiState.value = _uiState.value.copy(isUsernameAvailable = available, isCheckingUsername = false)
                } catch (_: Exception) {
                    _uiState.value = _uiState.value.copy(isCheckingUsername = false)
                }
            }
        }
    }

    fun updatePin(pin: String) {
        _uiState.value = _uiState.value.copy(pin = pin)
    }

    fun submit() {
        val state = _uiState.value
        if (state.firstName.isBlank()) {
            _uiState.value = state.copy(errorMessage = "First name is required")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                // Update profile name
                authService.updateProfile(firstName = state.firstName, lastName = state.lastName.ifBlank { null })

                // Set username if provided
                if (state.username.isNotBlank()) {
                    authService.setUsername(state.username, pin = state.pin.ifBlank { null })
                }

                _uiState.value = _uiState.value.copy(isLoading = false, isComplete = true)
            } catch (e: Exception) {
                Log.e("ProfileSetup", "Failed", e)
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message ?: "Setup failed")
            }
        }
    }
}
