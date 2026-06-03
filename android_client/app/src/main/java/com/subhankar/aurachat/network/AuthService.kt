package com.subhankar.aurachat.network

import android.util.Log
import com.subhankar.aurachat.data.local.SessionManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Authentication result from verify_password or verify_otp.
 * Matches Flutter's AuthResult class.
 */
data class AuthResult(
    val requiresKeySetup: Boolean,
    val isNewUser: Boolean,
    val nextStep: String? = null,
    val message: String? = null
)

/**
 * Auth service — replaces Dart auth_service.dart.
 *
 * Handles all authentication API calls to the Phoenix backend:
 *   • identify (email/phone detection)
 *   • verify_password (login or signup)
 *   • verify_otp (6-digit code verification)
 *   • resend_otp
 *   • send_phone_otp (phone users → email collection)
 *   • forgot_password / reset_password
 *   • Google Sign-In
 *   • Token refresh
 *   • Profile management
 */
class AuthService(
    private val sessionManager: SessionManager
) {
    companion object {
        private const val TAG = "AuthService"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val baseUrl: String get() = AppConfig.apiBaseUrl

    // ─── Step 1: Identify ────────────────────────────────────────

    /**
     * Detect if email/phone exists and get next auth step.
     * Returns map with: temp_token, next_step, message
     */
    suspend fun identify(identifier: String): Map<String, Any?> {
        val body = JSONObject().apply {
            put("identifier", identifier)
        }
        val response = post("$baseUrl/api/v1/auth/identify", body)

        if (response.code == 200) {
            return JSONObject(response.body!!.string()).toMap()
        } else if (response.code == 429) {
            throw IOException("Too many attempts. Please try again later.")
        } else {
            val error = try {
                JSONObject(response.body!!.string()).optString("error", "Identification failed")
            } catch (_: Exception) { "Identification failed" }
            throw IOException(error)
        }
    }

    // ─── Step 2a: Verify Password ────────────────────────────────

    /**
     * Verify password for login (existing user) or signup (new user).
     */
    suspend fun verifyPassword(tempToken: String, password: String): AuthResult {
        val body = JSONObject().apply {
            put("temp_token", tempToken)
            put("password", password)
        }
        val response = post("$baseUrl/api/v1/auth/verify_password", body)

        if (response.code == 200) {
            val data = JSONObject(response.body!!.string())

            // Check if more steps are needed (OTP or email collection)
            val nextStep = data.optString("next_step", "")
            if (nextStep == "otp_verify" || nextStep == "email_collect") {
                return AuthResult(
                    requiresKeySetup = false,
                    isNewUser = true,
                    nextStep = nextStep,
                    message = data.optString("message", null)
                )
            }

            // Full login success — save session
            sessionManager.saveSession(
                token = data.getString("token"),
                userId = data.getString("user_id"),
                deviceId = data.getString("device_id"),
                refreshToken = data.optString("refresh_token", null)
            )
            return AuthResult(
                requiresKeySetup = data.optBoolean("requires_key_setup", false),
                isNewUser = data.optBoolean("is_new_user", false)
            )
        } else if (response.code == 422) {
            val data = JSONObject(response.body!!.string())
            val details = try {
                val arr = data.optJSONArray("details")
                if (arr != null) {
                    (0 until arr.length()).joinToString(", ") { arr.getString(it) }
                } else "Password too weak"
            } catch (_: Exception) { "Password too weak" }
            throw IOException(details)
        } else {
            val error = try {
                JSONObject(response.body!!.string()).optString("error", "Invalid credentials")
            } catch (_: Exception) { "Invalid credentials" }
            throw IOException(error)
        }
    }

    // ─── Step 2b: Verify OTP ─────────────────────────────────────

    /**
     * Verify 6-digit OTP code.
     */
    suspend fun verifyOtp(tempToken: String, otp: String): AuthResult {
        val body = JSONObject().apply {
            put("temp_token", tempToken)
            put("otp", otp)
        }
        val response = post("$baseUrl/api/v1/auth/verify_otp", body)

        if (response.code == 200) {
            val data = JSONObject(response.body!!.string())
            sessionManager.saveSession(
                token = data.getString("token"),
                userId = data.getString("user_id"),
                deviceId = data.getString("device_id"),
                refreshToken = data.optString("refresh_token", null)
            )
            return AuthResult(
                requiresKeySetup = data.optBoolean("requires_key_setup", false),
                isNewUser = data.optBoolean("is_new_user", false)
            )
        } else {
            val error = try {
                JSONObject(response.body!!.string()).optString("error", "Invalid OTP")
            } catch (_: Exception) { "Invalid OTP" }
            throw IOException(error)
        }
    }

    // ─── Resend OTP ──────────────────────────────────────────────

    suspend fun resendOtp(tempToken: String, email: String) {
        val body = JSONObject().apply {
            put("temp_token", tempToken)
            put("email", email)
        }
        val response = post("$baseUrl/api/v1/auth/resend_otp", body)

        if (response.code == 429) {
            throw IOException("Please wait before requesting a new code.")
        } else if (response.code != 200) {
            val error = try {
                JSONObject(response.body!!.string()).optString("error", "Failed to resend")
            } catch (_: Exception) { "Failed to resend" }
            throw IOException(error)
        }
    }

    // ─── Phone OTP (email collection for phone signups) ──────────

    suspend fun sendPhoneOtp(tempToken: String, email: String): AuthResult {
        val body = JSONObject().apply {
            put("temp_token", tempToken)
            put("email", email)
        }
        val response = post("$baseUrl/api/v1/auth/send_phone_otp", body)

        if (response.code == 200) {
            val data = JSONObject(response.body!!.string())
            return AuthResult(
                requiresKeySetup = false,
                isNewUser = true,
                nextStep = data.optString("next_step", null),
                message = data.optString("message", null)
            )
        } else if (response.code == 422) {
            val error = try {
                JSONObject(response.body!!.string()).optString("error", "Invalid email")
            } catch (_: Exception) { "Invalid email" }
            throw IOException(error)
        } else {
            val error = try {
                JSONObject(response.body!!.string()).optString("error", "Failed to send verification")
            } catch (_: Exception) { "Failed to send verification" }
            throw IOException(error)
        }
    }

    // ─── Forgot Password ─────────────────────────────────────────

    suspend fun forgotPassword(email: String) {
        val body = JSONObject().apply {
            put("email", email)
        }
        post("$baseUrl/api/v1/auth/forgot_password", body)
        // Always succeeds (anti-enumeration)
    }

    // ─── Reset Password ──────────────────────────────────────────

    suspend fun resetPassword(email: String, code: String, newPassword: String) {
        val body = JSONObject().apply {
            put("email", email)
            put("code", code)
            put("new_password", newPassword)
        }
        val response = post("$baseUrl/api/v1/auth/reset_password", body)

        if (response.code == 422) {
            val data = JSONObject(response.body!!.string())
            val details = try {
                val arr = data.optJSONArray("details")
                if (arr != null) {
                    (0 until arr.length()).joinToString(", ") { arr.getString(it) }
                } else "Password too weak"
            } catch (_: Exception) { "Password too weak" }
            throw IOException(details)
        } else if (response.code != 200) {
            val error = try {
                JSONObject(response.body!!.string()).optString("error", "Reset failed")
            } catch (_: Exception) { "Reset failed" }
            throw IOException(error)
        }
    }

    // ─── Google Sign-In ──────────────────────────────────────────

    /**
     * Authenticate with Google ID token.
     * Called after Google Credential Manager returns the ID token.
     */
    suspend fun googleAuth(idToken: String): AuthResult {
        val body = JSONObject().apply {
            put("id_token", idToken)
        }
        val response = post("$baseUrl/api/v1/auth/google", body)

        if (response.code == 200) {
            val data = JSONObject(response.body!!.string())
            sessionManager.saveSession(
                token = data.getString("token"),
                userId = data.getString("user_id"),
                deviceId = data.getString("device_id"),
                refreshToken = data.optString("refresh_token", null)
            )
            return AuthResult(
                requiresKeySetup = data.optBoolean("requires_key_setup", false),
                isNewUser = data.optBoolean("is_new_user", false)
            )
        } else {
            val error = try {
                JSONObject(response.body!!.string()).optString("error", "Google login failed")
            } catch (_: Exception) { "Google login failed" }
            throw IOException(error)
        }
    }

    // ─── Token Refresh ───────────────────────────────────────────

    /**
     * Silently refresh the access token using the stored refresh token.
     * Returns true if refresh was successful.
     */
    suspend fun refreshToken(): Boolean {
        val storedRefreshToken = sessionManager.getRefreshToken() ?: return false

        val body = JSONObject().apply {
            put("refresh_token", storedRefreshToken)
        }

        return try {
            val response = post("$baseUrl/api/v1/auth/refresh", body)
            if (response.code == 200) {
                val data = JSONObject(response.body!!.string())
                sessionManager.updateTokens(
                    token = data.getString("token"),
                    refreshToken = data.getString("refresh_token")
                )
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed: ${e.message}")
            false
        }
    }

    // ─── Profile Management ──────────────────────────────────────

    /**
     * Update user profile (name and avatar).
     */
    suspend fun updateProfile(
        firstName: String,
        lastName: String? = null,
        imagePath: String? = null
    ) {
        val token = sessionManager.getToken() ?: throw IOException("Not authenticated")

        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        builder.addFormDataPart("first_name", firstName)
        if (!lastName.isNullOrEmpty()) {
            builder.addFormDataPart("last_name", lastName)
        }

        if (!imagePath.isNullOrEmpty()) {
            val file = java.io.File(imagePath)
            if (file.exists()) {
                val mediaType = "image/jpeg".toMediaType()
                builder.addFormDataPart(
                    "avatar",
                    file.name,
                    file.readBytes().toRequestBody(mediaType)
                )
            }
        }

        val request = Request.Builder()
            .url("$baseUrl/api/v1/account/profile")
            .addHeader("Authorization", "Bearer $token")
            .put(builder.build())
            .build()

        val response = execute(request)
        if (response.code == 401) {
            // Try refresh
            if (refreshToken()) {
                val newToken = sessionManager.getToken() ?: throw IOException("Not authenticated")
                val retryRequest = request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
                val retryResponse = execute(retryRequest)
                if (retryResponse.code != 200) {
                    throw IOException("Failed to update profile")
                }
            }
        } else if (response.code != 200) {
            val error = try {
                JSONObject(response.body!!.string()).optString("error", "Failed to update profile")
            } catch (_: Exception) { "Failed to update profile" }
            throw IOException(error)
        }
    }

    /**
     * Set username and optional PIN.
     */
    suspend fun setUsername(username: String, pin: String? = null) {
        val body = JSONObject().apply {
            put("username", username)
            if (!pin.isNullOrEmpty()) {
                put("pin", pin)
            }
        }
        val response = authenticatedPut("$baseUrl/api/v1/account/username", body)

        if (response.code != 200) {
            val errorBody = try {
                JSONObject(response.body!!.string())
            } catch (_: Exception) { JSONObject() }

            val errors = errorBody.optJSONObject("errors")
            if (errors != null && errors.has("username")) {
                val msgs = errors.optJSONArray("username")
                if (msgs != null && msgs.length() > 0) {
                    throw IOException("Username ${msgs.getString(0)}")
                }
            }
            throw IOException(errorBody.optString("error", "Failed to set username"))
        }
    }

    /**
     * Check if a username is available.
     */
    suspend fun checkUsername(username: String): Boolean {
        return try {
            val encoded = java.net.URLEncoder.encode(username, "UTF-8")
            val request = Request.Builder()
                .url("$baseUrl/api/v1/account/check_username?username=$encoded")
                .get()
                .build()
            val response = execute(request)
            if (response.code == 200) {
                val data = JSONObject(response.body!!.string())
                data.optBoolean("available", false)
            } else {
                false
            }
        } catch (_: Exception) {
            true // Assume available on network error to not block typing
        }
    }

    /**
     * Fetch current user's profile.
     */
    suspend fun getProfile(): Map<String, Any?> {
        val response = authenticatedGet("$baseUrl/api/v1/account/profile")
        if (response.code == 200) {
            return JSONObject(response.body!!.string()).toMap()
        } else {
            throw IOException("Failed to fetch profile")
        }
    }

    // ─── Logout ──────────────────────────────────────────────────

    suspend fun logout() {
        try {
            authenticatedPost("$baseUrl/api/v1/auth/logout", JSONObject())
        } catch (_: Exception) {
            // Best effort
        }
        sessionManager.clearSession()
    }

    // ─── Internal HTTP helpers with auto-refresh ─────────────────

    private suspend fun authenticatedGet(url: String): Response {
        val token = sessionManager.getToken() ?: throw IOException("Not authenticated")
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        var response = execute(request)

        if (response.code == 401) {
            if (refreshToken()) {
                val newToken = sessionManager.getToken() ?: throw IOException("Not authenticated")
                val retryRequest = request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
                response = execute(retryRequest)
            }
        }
        return response
    }

    private suspend fun authenticatedPost(url: String, body: JSONObject): Response {
        val token = sessionManager.getToken() ?: throw IOException("Not authenticated")
        val requestBody = body.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
        var response = execute(request)

        if (response.code == 401) {
            if (refreshToken()) {
                val newToken = sessionManager.getToken() ?: throw IOException("Not authenticated")
                val retryRequest = request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
                response = execute(retryRequest)
            }
        }
        return response
    }

    private suspend fun authenticatedPut(url: String, body: JSONObject): Response {
        val token = sessionManager.getToken() ?: throw IOException("Not authenticated")
        val requestBody = body.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .put(requestBody)
            .build()
        var response = execute(request)

        if (response.code == 401) {
            if (refreshToken()) {
                val newToken = sessionManager.getToken() ?: throw IOException("Not authenticated")
                val retryRequest = request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
                response = execute(retryRequest)
            }
        }
        return response
    }

    private suspend fun post(url: String, body: JSONObject): Response {
        val requestBody = body.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
        return execute(request)
    }

    private suspend fun execute(request: Request): Response {
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }
            })
        }
    }
}
