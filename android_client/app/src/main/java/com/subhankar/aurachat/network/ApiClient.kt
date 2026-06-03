package com.subhankar.aurachat.network

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * REST API client — replaces Dart api_client.dart.
 *
 * Handles all HTTP requests to the Phoenix backend:
 *   • Key registration (E2EE pre-keys)
 *   • Pre-key fetching (for session establishment)
 *   • Profile lookups
 *   • Media upload/download URLs
 *
 * Uses OkHttp for HTTP (same library as our WebSocket client).
 * Includes automatic JWT token refresh on 401.
 */
class ApiClient(
    private val baseUrl: String,
    private var tokenProvider: suspend () -> String,
    private val authService: AuthService? = null
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // ─── Key Management ──────────────────────────────────────────

    /**
     * Register E2EE keys with the server.
     * Replaces: Dart apiClient.registerKeys()
     */
    suspend fun registerKeys(payload: Map<String, Any?>) {
        val body = JSONObject(payload).apply {
            put("version", "1.0")
            put("device_id", UUID.randomUUID().toString())
        }

        val response = authenticatedPost(
            url = "$baseUrl/api/v1/keys/register",
            body = body,
            extraHeaders = mapOf("Idempotency-Key" to UUID.randomUUID().toString())
        )

        if (response.code != 201) {
            throw IOException("Failed to register keys: ${response.body?.string()}")
        }
    }

    /**
     * Fetch pre-keys for a recipient (by user ID).
     * Replaces: Dart apiClient.fetchPreKeys()
     */
    suspend fun fetchPreKeys(recipientId: String): Map<String, Any?> {
        val response = authenticatedGet("$baseUrl/api/v1/pre_keys?recipient_id=$recipientId")
        if (response.code == 200) {
            return JSONObject(response.body!!.string()).toMap()
        } else {
            throw IOException("Failed to fetch pre keys: ${response.body?.string()}")
        }
    }

    /**
     * Fetch pre-keys by username (for new chat flow).
     * Replaces: Dart apiClient.fetchPreKeysByUsername()
     */
    suspend fun fetchPreKeysByUsername(username: String, pin: String? = null): Map<String, Any?> {
        val query = if (pin != null) "?pin=$pin" else ""
        val response = authenticatedGet("$baseUrl/api/v1/keys/fetch_by_username/$username$query")
        if (response.code == 200) {
            return JSONObject(response.body!!.string()).toMap()
        } else {
            throw IOException("Failed to fetch keys for $username: ${response.code} - ${response.body?.string()}")
        }
    }

    /**
     * Fetch pre-keys by identifier (username or user ID).
     * Replaces: Dart apiClient.fetchPreKeysByIdentifier()
     */
    suspend fun fetchPreKeysByIdentifier(identifier: String): Map<String, Any?> {
        val encoded = java.net.URLEncoder.encode(identifier, "UTF-8")
        val response = authenticatedGet("$baseUrl/api/v1/keys/fetch_by_identifier?identifier=$encoded")
        if (response.code == 200) {
            return JSONObject(response.body!!.string()).toMap()
        } else {
            throw IOException("${response.code} - ${response.body?.string()}")
        }
    }

    // ─── Profile & Status ────────────────────────────────────────

    /**
     * Fetch a user's online/offline status.
     * Replaces: Dart apiClient.fetchUserStatus()
     */
    suspend fun fetchUserStatus(userId: String): Map<String, Any?> {
        val response = authenticatedGet("$baseUrl/api/v1/account/status/$userId")
        if (response.code == 200) {
            return JSONObject(response.body!!.string()).toMap()
        } else {
            throw IOException("Failed to fetch status: ${response.body?.string()}")
        }
    }

    /**
     * Fetch a user's public profile (name, avatar).
     * Replaces: Dart apiClient.fetchPublicProfile()
     */
    suspend fun fetchPublicProfile(userId: String): Map<String, Any?> {
        val response = authenticatedGet("$baseUrl/api/v1/account/public_profile/$userId")
        if (response.code == 200) {
            return JSONObject(response.body!!.string()).toMap()
        } else {
            throw IOException("Failed to fetch profile: ${response.body?.string()}")
        }
    }

    // ─── Media ───────────────────────────────────────────────────

    /**
     * Get a pre-signed upload URL for media.
     * Replaces: Dart apiClient.getUploadUrl()
     */
    suspend fun getUploadUrl(fileSize: Int, mimeType: String, recipientId: String): Map<String, Any?> {
        val body = JSONObject().apply {
            put("file_size", fileSize)
            put("mime_type", mimeType)
            put("recipient_id", recipientId)
        }
        val response = authenticatedPost("$baseUrl/api/v1/media/upload_url", body)
        if (response.code == 200) {
            return JSONObject(response.body!!.string()).toMap()
        } else {
            throw IOException("Failed to get upload URL: ${response.body?.string()}")
        }
    }

    /**
     * Get a pre-signed download URL for media.
     * Replaces: Dart apiClient.getDownloadUrl()
     */
    suspend fun getDownloadUrl(s3Key: String): Map<String, Any?> {
        val body = JSONObject().apply { put("s3_key", s3Key) }
        val response = authenticatedPost("$baseUrl/api/v1/media/download_url", body)
        if (response.code == 200) {
            return JSONObject(response.body!!.string()).toMap()
        } else {
            throw IOException("Failed to get download URL: ${response.body?.string()}")
        }
    }

    // ─── Internal HTTP helpers with auto-refresh ─────────────────

    private suspend fun authenticatedGet(url: String): Response {
        val token = tokenProvider()
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        var response = execute(request)

        // Auto-refresh on 401
        if (response.code == 401 && authService != null) {
            if (authService.refreshToken()) {
                val newToken = tokenProvider()
                val retryRequest = request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
                response = execute(retryRequest)
            }
        }
        return response
    }

    private suspend fun authenticatedPost(
        url: String,
        body: JSONObject,
        extraHeaders: Map<String, String> = emptyMap()
    ): Response {
        val token = tokenProvider()
        val requestBody = body.toString().toRequestBody(jsonMediaType)
        val builder = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)

        extraHeaders.forEach { (k, v) -> builder.addHeader(k, v) }
        var response = execute(builder.build())

        // Auto-refresh on 401
        if (response.code == 401 && authService != null) {
            if (authService.refreshToken()) {
                val newToken = tokenProvider()
                val retryBuilder = builder.header("Authorization", "Bearer $newToken")
                response = execute(retryBuilder.build())
            }
        }
        return response
    }

    /**
     * Execute an OkHttp request on a coroutine-friendly thread.
     */
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
