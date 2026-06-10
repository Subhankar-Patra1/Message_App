package com.subhankar.aurachat.data.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure session storage — replaces Dart session_manager.dart.
 *
 * Uses Android's EncryptedSharedPreferences backed by the Hardware Keystore.
 * All stored values (JWT, refresh token, etc.) are encrypted at rest.
 *
 * This class is thread-safe and can be injected anywhere via Hilt.
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "aurachat_session"
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_IS_ONBOARDED = "is_onboarded"
        private const val KEY_BIO = "profile_bio"
        private const val KEY_PHONE = "profile_phone"
        private const val KEY_BIRTHDAY = "profile_birthday"
        private const val KEY_PROFILE_PHOTO_PATH = "profile_photo_path"
        private const val KEY_TEXT_AVATAR_TEXT = "text_avatar_text"
        private const val KEY_TEXT_AVATAR_BG_INDEX = "text_avatar_bg_index"
        private const val KEY_TEXT_AVATAR_TEXT_COLOR_INDEX = "text_avatar_text_color_index"
    }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ─── Save ────────────────────────────────────────────────────

    fun saveSession(
        token: String,
        userId: String,
        deviceId: String,
        refreshToken: String? = null
    ) {
        prefs.edit().apply {
            putString(KEY_TOKEN, token)
            putString(KEY_USER_ID, userId)
            putString(KEY_DEVICE_ID, deviceId)
            if (refreshToken != null) {
                putString(KEY_REFRESH_TOKEN, refreshToken)
            }
            apply()
        }
    }

    // ─── Read ────────────────────────────────────────────────────

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun getDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)

    fun isLoggedIn(): Boolean {
        val token = getToken()
        return token != null && token.isNotEmpty()
    }

    // ─── Bio, Phone, Birthday ────────────────────────────────────

    fun getBio(): String? = prefs.getString(KEY_BIO, null)

    fun saveBio(bio: String) {
        prefs.edit().putString(KEY_BIO, bio).apply()
    }

    fun getPhone(): String? = prefs.getString(KEY_PHONE, null)

    fun savePhone(phone: String) {
        prefs.edit().putString(KEY_PHONE, phone).apply()
    }

    fun getBirthday(): String? = prefs.getString(KEY_BIRTHDAY, null)

    fun saveBirthday(birthday: String) {
        prefs.edit().putString(KEY_BIRTHDAY, birthday).apply()
    }

    fun getProfilePhotoPath(): String? = prefs.getString(KEY_PROFILE_PHOTO_PATH, null)

    fun saveProfilePhotoPath(path: String?) {
        if (path != null) {
            prefs.edit().putString(KEY_PROFILE_PHOTO_PATH, path).apply()
        } else {
            prefs.edit().remove(KEY_PROFILE_PHOTO_PATH).apply()
        }
    }

    fun getTextAvatarText(): String? = prefs.getString(KEY_TEXT_AVATAR_TEXT, null)

    fun getTextAvatarBgIndex(): Int? {
        val value = prefs.getInt(KEY_TEXT_AVATAR_BG_INDEX, -1)
        return if (value == -1) null else value
    }

    fun getTextAvatarTextColorIndex(): Int? {
        val value = prefs.getInt(KEY_TEXT_AVATAR_TEXT_COLOR_INDEX, -1)
        return if (value == -1) null else value
    }

    fun saveTextAvatarState(text: String?, bgIndex: Int?, textColorIndex: Int?) {
        prefs.edit().apply {
            if (text != null) putString(KEY_TEXT_AVATAR_TEXT, text) else remove(KEY_TEXT_AVATAR_TEXT)
            if (bgIndex != null) putInt(KEY_TEXT_AVATAR_BG_INDEX, bgIndex) else remove(KEY_TEXT_AVATAR_BG_INDEX)
            if (textColorIndex != null) putInt(KEY_TEXT_AVATAR_TEXT_COLOR_INDEX, textColorIndex) else remove(KEY_TEXT_AVATAR_TEXT_COLOR_INDEX)
            apply()
        }
    }

    // ─── Onboarding ──────────────────────────────────────────────

    fun isOnboarded(): Boolean = prefs.getBoolean(KEY_IS_ONBOARDED, false)

    fun setOnboarded() {
        prefs.edit().putBoolean(KEY_IS_ONBOARDED, true).apply()
    }

    // ─── Clear ───────────────────────────────────────────────────

    fun clearSession() {
        prefs.edit().apply {
            remove(KEY_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_USER_ID)
            remove(KEY_DEVICE_ID)
            remove(KEY_BIO)
            remove(KEY_PHONE)
            remove(KEY_BIRTHDAY)
            remove(KEY_PROFILE_PHOTO_PATH)
            remove(KEY_TEXT_AVATAR_TEXT)
            remove(KEY_TEXT_AVATAR_BG_INDEX)
            remove(KEY_TEXT_AVATAR_TEXT_COLOR_INDEX)
            apply()
        }
    }

    /**
     * Update just the tokens (used after token refresh).
     */
    fun updateTokens(token: String, refreshToken: String) {
        prefs.edit().apply {
            putString(KEY_TOKEN, token)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            apply()
        }
    }
}
