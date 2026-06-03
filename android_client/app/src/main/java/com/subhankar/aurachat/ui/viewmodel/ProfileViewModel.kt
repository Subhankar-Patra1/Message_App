package com.subhankar.aurachat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subhankar.aurachat.network.AuthService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authService: AuthService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        fetchProfile()
    }

    private fun fetchProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val data = authService.getProfile()
                // Convert Map<String, Any?> to Map<String, String?>
                val profileMap = data.mapValues { it.value?.toString() }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    profileData = profileMap
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to fetch profile"
                )
            }
        }
    }
}

data class ProfileUiState(
    val isLoading: Boolean = false,
    val profileData: Map<String, String?>? = null,
    val error: String? = null
)
