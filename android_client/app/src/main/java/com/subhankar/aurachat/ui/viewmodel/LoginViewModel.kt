package com.subhankar.aurachat.ui.viewmodel

import android.app.Activity
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.subhankar.aurachat.data.local.SessionManager
import com.subhankar.aurachat.network.AppConfig
import com.subhankar.aurachat.network.AuthResult
import com.subhankar.aurachat.network.AuthService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Auth step enum — mirrors Flutter's AuthStep.
 */
enum class AuthStep {
    IDENTIFY,
    PASSWORD_INLINE,
    PASSWORD_SETUP,
    PHONE_EMAIL_COLLECT,
    OTP,
    FORGOT_PASSWORD_OTP,
    FORGOT_PASSWORD_SETUP
}

/**
 * Login screen UI state.
 */
data class LoginUiState(
    val currentStep: AuthStep = AuthStep.IDENTIFY,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val tempToken: String? = null,
    val isNewUser: Boolean = false,
    val isPhone: Boolean = false,
    val resendCooldown: Int = 0,
    val rateLimitCooldown: Int = 0
)

/**
 * Navigation events emitted by LoginViewModel.
 */
sealed class LoginNavEvent {
    data object NavigateToHome : LoginNavEvent()
    data object NavigateToProfileSetup : LoginNavEvent()
    data object NavigateToOnboarding : LoginNavEvent()
}

/**
 * LoginViewModel — manages multi-step authentication flow.
 * Kotlin port of Flutter's LoginScreen state management.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authService: AuthService,
    private val sessionManager: SessionManager
) : ViewModel() {

    companion object {
        private const val TAG = "LoginViewModel"
        private const val GOOGLE_SERVER_CLIENT_ID =
            "132565546749-7fjpe9g1ekrtnvjri1dlk2034vmgf2r4.apps.googleusercontent.com"
    }

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    private val _navEvent = MutableStateFlow<LoginNavEvent?>(null)
    val navEvent: StateFlow<LoginNavEvent?> = _navEvent

    // Store for forgot password flow
    private var resetOtpCode: String = ""

    // Store identifier & email for OTP resend
    var identifier: String = ""
        private set
    var emailForOtp: String = ""
        private set

    fun clearNavEvent() {
        _navEvent.value = null
    }

    // ─── Step 1: Identify ────────────────────────────────────────

    fun handleIdentify(identifierInput: String) {
        identifier = identifierInput.trim()
        if (identifier.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val response = authService.identify(identifier)
                val tempToken = response["temp_token"] as? String
                val nextStep = response["next_step"] as? String

                val isPhoneFormat = Regex("^\\+?[0-9\\s\\-()]+$").matches(identifier) &&
                        identifier.replace(Regex("[^0-9]"), "").length >= 7

                val step = when (nextStep) {
                    "password_setup" -> AuthStep.PASSWORD_SETUP
                    else -> AuthStep.PASSWORD_INLINE
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentStep = step,
                    tempToken = tempToken,
                    isNewUser = nextStep == "password_setup",
                    isPhone = isPhoneFormat,
                    errorMessage = null
                )
            } catch (e: Exception) {
                val msg = e.message ?: "Something went wrong"
                val isRateLimit = msg.lowercase().contains("too many")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = if (isRateLimit) null else msg
                )
                if (isRateLimit) {
                    startRateLimitTimer()
                }
            }
        }
    }

    // ─── Step 2: Submit Password or OTP ──────────────────────────

    fun handlePasswordSubmit(password: String) {
        val token = _uiState.value.tempToken ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                AppConfig.serverIp = AppConfig.serverIp // Ensure IP is current
                val result = authService.verifyPassword(token, password)
                _uiState.value = _uiState.value.copy(isLoading = false)
                handleAuthResult(result)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Invalid credentials"
                )
            }
        }
    }

    fun handleOtpSubmit(otp: String) {
        val token = _uiState.value.tempToken ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val result = authService.verifyOtp(token, otp.trim())
                _uiState.value = _uiState.value.copy(isLoading = false)
                handleAuthResult(result)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Invalid OTP"
                )
            }
        }
    }

    // ─── Phone Email Collection ──────────────────────────────────

    fun handlePhoneEmailSubmit(email: String) {
        val token = _uiState.value.tempToken ?: return
        emailForOtp = email.trim()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val result = authService.sendPhoneOtp(token, emailForOtp)
                _uiState.value = _uiState.value.copy(isLoading = false)
                if (result.nextStep == "otp_verify") {
                    _uiState.value = _uiState.value.copy(
                        currentStep = AuthStep.OTP,
                        errorMessage = null
                    )
                    startResendTimer()
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Failed to send verification"
                val isExpired = msg.lowercase().contains("session has expired")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = msg
                )
                if (isExpired) {
                    // Auto-navigate back after delay
                    kotlinx.coroutines.delay(2000)
                    goBackToIdentify()
                }
            }
        }
    }

    // ─── Resend OTP ──────────────────────────────────────────────

    fun handleResendOtp() {
        val token = _uiState.value.tempToken ?: return
        if (_uiState.value.resendCooldown > 0) return

        val email = if (_uiState.value.isPhone) emailForOtp else identifier

        viewModelScope.launch {
            try {
                authService.resendOtp(token, email)
                startResendTimer()
            } catch (_: Exception) {
                // Silently fail
            }
        }
    }

    // ─── Forgot Password ─────────────────────────────────────────

    fun handleForgotPassword() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                authService.forgotPassword(identifier)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentStep = AuthStep.FORGOT_PASSWORD_OTP
                )
                startResendTimer()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to send reset code"
                )
            }
        }
    }

    fun handleForgotOtpSubmit(otp: String) {
        if (otp.length == 6) {
            resetOtpCode = otp
            _uiState.value = _uiState.value.copy(
                currentStep = AuthStep.FORGOT_PASSWORD_SETUP,
                errorMessage = null
            )
        }
    }

    fun handleForgotPasswordReset(newPassword: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                authService.resetPassword(identifier, resetOtpCode, newPassword)
                // Auto-login after reset
                val identifyResult = authService.identify(identifier)
                val tempToken = identifyResult["temp_token"] as? String
                if (tempToken != null) {
                    val authResult = authService.verifyPassword(tempToken, newPassword)
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    handleAuthResult(authResult)
                } else {
                    throw Exception("Failed to auto-login. Please login manually.")
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Reset failed"
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = msg
                )
                if (msg.contains("invalid_reset_code") || msg.contains("expired")) {
                    _uiState.value = _uiState.value.copy(currentStep = AuthStep.FORGOT_PASSWORD_OTP)
                }
            }
        }
    }

    // ─── Google Sign-In ──────────────────────────────────────────

    fun handleGoogleSignIn(activity: Activity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val credentialManager = CredentialManager.create(activity)

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId(GOOGLE_SERVER_CLIENT_ID)
                    .setFilterByAuthorizedAccounts(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential

                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken

                    val authResult = authService.googleAuth(idToken)
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    handleAuthResult(authResult)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Google sign-in failed"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Google sign-in failed"
                )
            }
        }
    }

    // ─── Navigation ──────────────────────────────────────────────

    fun goBackToIdentify() {
        _uiState.value = LoginUiState() // Reset to initial state
    }

    fun goBack() {
        _uiState.value = _uiState.value.copy(
            currentStep = AuthStep.IDENTIFY,
            errorMessage = null
        )
    }

    // ─── Server Config (Debug) ───────────────────────────────────

    fun updateServerIp(ip: String) {
        AppConfig.serverIp = ip
    }

    // ─── Private Helpers ─────────────────────────────────────────

    private fun handleAuthResult(result: AuthResult) {
        when {
            result.nextStep == "email_collect" -> {
                _uiState.value = _uiState.value.copy(
                    currentStep = AuthStep.PHONE_EMAIL_COLLECT,
                    errorMessage = null
                )
            }
            result.nextStep == "otp_verify" -> {
                _uiState.value = _uiState.value.copy(
                    currentStep = AuthStep.OTP,
                    errorMessage = null
                )
                startResendTimer()
            }
            result.isNewUser -> {
                _navEvent.value = LoginNavEvent.NavigateToProfileSetup
            }
            result.requiresKeySetup -> {
                _navEvent.value = LoginNavEvent.NavigateToOnboarding
            }
            else -> {
                _navEvent.value = LoginNavEvent.NavigateToHome
            }
        }
    }

    private fun startResendTimer() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(resendCooldown = 60)
            for (i in 59 downTo 0) {
                kotlinx.coroutines.delay(1000)
                _uiState.value = _uiState.value.copy(resendCooldown = i)
            }
        }
    }

    private fun startRateLimitTimer() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(rateLimitCooldown = 60)
            for (i in 59 downTo 0) {
                kotlinx.coroutines.delay(1000)
                _uiState.value = _uiState.value.copy(rateLimitCooldown = i)
                if (i == 0) {
                    _uiState.value = _uiState.value.copy(errorMessage = null)
                }
            }
        }
    }
}
