package com.subhankar.aurachat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subhankar.aurachat.data.local.SessionManager
import com.subhankar.aurachat.network.AuthService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authService: AuthService,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var saveJob: Job? = null

    init {
        fetchProfile()
    }

    private fun fetchProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val data = authService.getProfile()
                val profileMap = data.mapValues { it.value?.toString() }
                
                val bio = sessionManager.getBio() ?: ""
                val phone = sessionManager.getPhone() ?: (profileMap["email"] ?: "")
                val birthday = sessionManager.getBirthday() ?: ""
                val username = profileMap["username"] ?: ""
                val photoPath = sessionManager.getProfilePhotoPath()
                val textAvatarText = sessionManager.getTextAvatarText()
                val textAvatarBgIndex = sessionManager.getTextAvatarBgIndex()
                val textAvatarTextColorIndex = sessionManager.getTextAvatarTextColorIndex()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    profileData = profileMap,
                    firstName = profileMap["first_name"] ?: "",
                    lastName = profileMap["last_name"] ?: "",
                    bio = bio,
                    phone = phone,
                    birthday = birthday,
                    username = username,
                    profilePhotoUri = photoPath,
                    textAvatarText = textAvatarText,
                    textAvatarBgIndex = textAvatarBgIndex,
                    textAvatarTextColorIndex = textAvatarTextColorIndex,
                    originalFirstName = profileMap["first_name"] ?: "",
                    originalLastName = profileMap["last_name"] ?: "",
                    originalBio = bio,
                    originalPhone = phone,
                    originalBirthday = birthday,
                    originalUsername = username,
                    originalProfilePhotoUri = photoPath,
                    originalTextAvatarText = textAvatarText,
                    originalTextAvatarBgIndex = textAvatarBgIndex,
                    originalTextAvatarTextColorIndex = textAvatarTextColorIndex
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to fetch profile"
                )
            }
        }
    }

    fun updateFirstName(name: String) {
        _uiState.value = _uiState.value.copy(firstName = name)
    }

    fun updateLastName(name: String) {
        _uiState.value = _uiState.value.copy(lastName = name)
    }

    fun updateBio(bio: String) {
        if (bio.length <= 140) {
            _uiState.value = _uiState.value.copy(bio = bio)
        }
    }

    fun updatePhone(phone: String) {
        _uiState.value = _uiState.value.copy(phone = phone)
    }

    fun updateBirthday(birthday: String) {
        _uiState.value = _uiState.value.copy(birthday = birthday)
    }

    fun updateUsername(username: String) {
        _uiState.value = _uiState.value.copy(username = username)
    }

    fun updateProfilePhotoUri(uri: String?) {
        val isTextAvatar = uri != null && uri.contains("text_avatar_")
        if (!isTextAvatar) {
            _uiState.value = _uiState.value.copy(
                profilePhotoUri = uri,
                textAvatarText = null,
                textAvatarBgIndex = null,
                textAvatarTextColorIndex = null
            )
        } else {
            _uiState.value = _uiState.value.copy(profilePhotoUri = uri)
        }
    }

    fun updateTextAvatarState(text: String?, bgIdx: Int?, textColIdx: Int?) {
        _uiState.value = _uiState.value.copy(
            textAvatarText = text,
            textAvatarBgIndex = bgIdx,
            textAvatarTextColorIndex = textColIdx
        )
    }

    fun hasChanges(): Boolean {
        val state = _uiState.value
        return state.firstName != state.originalFirstName ||
               state.lastName != state.originalLastName ||
               state.bio != state.originalBio ||
               state.phone != state.originalPhone ||
               state.birthday != state.originalBirthday ||
               state.username != state.originalUsername ||
               state.profilePhotoUri != state.originalProfilePhotoUri ||
               state.textAvatarText != state.originalTextAvatarText ||
               state.textAvatarBgIndex != state.originalTextAvatarBgIndex ||
               state.textAvatarTextColorIndex != state.originalTextAvatarTextColorIndex
    }

    fun discardChanges() {
        val state = _uiState.value
        _uiState.value = _uiState.value.copy(
            firstName = state.originalFirstName,
            lastName = state.originalLastName,
            bio = state.originalBio,
            phone = state.originalPhone,
            birthday = state.originalBirthday,
            username = state.originalUsername,
            profilePhotoUri = state.originalProfilePhotoUri,
            textAvatarText = state.originalTextAvatarText,
            textAvatarBgIndex = state.originalTextAvatarBgIndex,
            textAvatarTextColorIndex = state.originalTextAvatarTextColorIndex
        )
    }

    fun saveAllChanges(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            val startTime = System.currentTimeMillis()
            try {
                val state = _uiState.value
                
                // 1. Update Profile (Name) if changed
                if (state.firstName != state.originalFirstName || state.lastName != state.originalLastName) {
                    authService.updateProfile(firstName = state.firstName, lastName = state.lastName)
                }

                // 2. Set Username if changed
                if (state.username != state.originalUsername) {
                    authService.setUsername(state.username)
                }

                // 3. Save Bio to SessionManager if changed
                if (state.bio != state.originalBio) {
                    sessionManager.saveBio(state.bio)
                }

                // 4. Save Phone to SessionManager if changed
                if (state.phone != state.originalPhone) {
                    sessionManager.savePhone(state.phone)
                }

                // 5. Save Birthday to SessionManager if changed
                if (state.birthday != state.originalBirthday) {
                    sessionManager.saveBirthday(state.birthday)
                }

                // 6. Save Profile Photo to SessionManager if changed
                if (state.profilePhotoUri != state.originalProfilePhotoUri) {
                    sessionManager.saveProfilePhotoPath(state.profilePhotoUri)
                }

                // 7. Save Text Avatar State if changed
                if (state.profilePhotoUri != state.originalProfilePhotoUri ||
                    state.textAvatarText != state.originalTextAvatarText ||
                    state.textAvatarBgIndex != state.originalTextAvatarBgIndex ||
                    state.textAvatarTextColorIndex != state.originalTextAvatarTextColorIndex
                ) {
                    sessionManager.saveTextAvatarState(
                        state.textAvatarText,
                        state.textAvatarBgIndex,
                        state.textAvatarTextColorIndex
                    )
                }

                // Ensure the loading/spinning state is visible for at least 1000ms
                val elapsedTime = System.currentTimeMillis() - startTime
                if (elapsedTime < 1000L) {
                    delay(1000L - elapsedTime)
                }

                // Update original values in state to match the newly saved values
                val updatedProfile = state.profileData?.toMutableMap() ?: mutableMapOf()
                updatedProfile["first_name"] = state.firstName
                updatedProfile["last_name"] = state.lastName
                updatedProfile["username"] = state.username

                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    profileData = updatedProfile,
                    originalFirstName = state.firstName,
                    originalLastName = state.lastName,
                    originalBio = state.bio,
                    originalPhone = state.phone,
                    originalBirthday = state.birthday,
                    originalUsername = state.username,
                    originalProfilePhotoUri = state.profilePhotoUri,
                    originalTextAvatarText = state.textAvatarText,
                    originalTextAvatarBgIndex = state.textAvatarBgIndex,
                    originalTextAvatarTextColorIndex = state.textAvatarTextColorIndex
                )

                onSuccess()
            } catch (e: Exception) {
                // Ensure spinner visibility even on error
                val elapsedTime = System.currentTimeMillis() - startTime
                if (elapsedTime < 1000L) {
                    delay(1000L - elapsedTime)
                }
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.message ?: "Failed to save changes"
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                authService.logout()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoggedOut = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to logout"
                )
            }
        }
    }
}

data class ProfileUiState(
    val isLoading: Boolean = false,
    val profileData: Map<String, String?>? = null,
    val error: String? = null,
    val firstName: String = "",
    val lastName: String = "",
    val bio: String = "",
    val phone: String = "",
    val birthday: String = "",
    val username: String = "",
    val profilePhotoUri: String? = null,
    val textAvatarText: String? = null,
    val textAvatarBgIndex: Int? = null,
    val textAvatarTextColorIndex: Int? = null,
    val isSaving: Boolean = false,
    val isLoggedOut: Boolean = false,
    
    // Original values to detect unsaved changes
    val originalFirstName: String = "",
    val originalLastName: String = "",
    val originalBio: String = "",
    val originalPhone: String = "",
    val originalBirthday: String = "",
    val originalUsername: String = "",
    val originalProfilePhotoUri: String? = null,
    val originalTextAvatarText: String? = null,
    val originalTextAvatarBgIndex: Int? = null,
    val originalTextAvatarTextColorIndex: Int? = null
)
