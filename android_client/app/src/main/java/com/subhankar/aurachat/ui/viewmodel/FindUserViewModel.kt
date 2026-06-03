package com.subhankar.aurachat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subhankar.aurachat.network.ApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FindUserState(
    val isLoading: Boolean = false,
    val isPinLoading: Boolean = false,
    val foundUser: Map<String, Any?>? = null,
    val error: String? = null,
    val pinError: String? = null,
    val navigateToChat: Boolean = false
)

@HiltViewModel
class FindByUsernameViewModel @Inject constructor(
    private val apiClient: ApiClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(FindUserState())
    val uiState: StateFlow<FindUserState> = _uiState.asStateFlow()

    fun searchUsername(username: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                foundUser = null,
                error = null,
                pinError = null,
                navigateToChat = false
            )
            try {
                val data = apiClient.fetchPreKeysByUsername(username)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    foundUser = data
                )
            } catch (e: Exception) {
                // If the user requires a PIN and we didn't provide one, it might throw a 401 Unauthorized
                // but the backend is designed to return the profile data anyway.
                // Let's assume exceptions without profile data mean "Not found".
                val isUnauthorized = e.message?.contains("invalid_pin") == true || e.message?.contains("401") == true
                
                if (isUnauthorized) {
                    // We might not get the data here if the exception hides it.
                    // Actually, if it throws an exception, we don't have the data.
                    // But wait, the backend endpoint for `fetchPreKeysByUsername` handles the PIN check and returns profile data
                    // even if the PIN is wrong, BUT `ApiClient` throws an IOException if response.code != 200!
                    // Let's check how the Flutter app handles this. It just shows "not found".
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Username not found"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Username not found"
                    )
                }
            }
        }
    }

    fun verifyPinAndFetchKeys(username: String, pin: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPinLoading = true, pinError = null)
            try {
                val bundle = apiClient.fetchPreKeysByUsername(username, pin)
                _uiState.value = _uiState.value.copy(
                    isPinLoading = false,
                    foundUser = bundle,
                    navigateToChat = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isPinLoading = false,
                    pinError = "Incorrect PIN."
                )
            }
        }
    }
    
    fun onNavigatedToChat() {
        _uiState.value = _uiState.value.copy(navigateToChat = false)
    }
}

@HiltViewModel
class FindByIdentifierViewModel @Inject constructor(
    private val apiClient: ApiClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(FindUserState())
    val uiState: StateFlow<FindUserState> = _uiState.asStateFlow()

    fun searchIdentifier(identifier: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                foundUser = null,
                error = null,
                navigateToChat = false
            )
            try {
                val data = apiClient.fetchPreKeysByIdentifier(identifier)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    foundUser = data,
                    navigateToChat = true // Immediately navigate because no PIN is required
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "User not found"
                )
            }
        }
    }
    
    fun onNavigatedToChat() {
        _uiState.value = _uiState.value.copy(navigateToChat = false)
    }
}
