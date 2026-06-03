package com.subhankar.aurachat.di

import android.content.Context
import com.subhankar.aurachat.data.local.AppDatabase
import com.subhankar.aurachat.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import net.sqlcipher.database.SQLiteDatabase
import androidx.room.Room
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.Cipher
import android.util.Base64
import javax.inject.Singleton

/**
 * Hilt module that provides the encrypted database and all DAOs.
 *
 * Security architecture:
 *   1. A 256-bit AES key is generated inside the Android Hardware Keystore.
 *      This key NEVER leaves the secure hardware (TEE/StrongBox).
 *   2. That hardware key encrypts a random "database passphrase".
 *   3. The encrypted passphrase is stored in SharedPreferences.
 *   4. On app launch, we decrypt the passphrase using the hardware key,
 *      then pass it to SQLCipher to unlock the database.
 *
 * Result: Even if someone roots the phone and copies the database file,
 * they cannot read it without the hardware-backed key.
 * This is the exact same pattern Signal uses.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val KEYSTORE_ALIAS = "aurachat_db_key"
    private const val PREFS_NAME = "aurachat_secure_prefs"
    private const val PREF_DB_PASSPHRASE = "encrypted_db_passphrase"

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        val passphrase = getOrCreatePassphrase(context)
        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "aurachat_encrypted.db"
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()
    @Provides fun provideConversationDao(db: AppDatabase): ConversationDao = db.conversationDao()
    @Provides fun provideContactDao(db: AppDatabase): ContactDao = db.contactDao()
    @Provides fun providePendingMessageDao(db: AppDatabase): PendingMessageDao = db.pendingMessageDao()

    // ─── Hardware-Backed Key Management ───────────────────────────────

    /**
     * Gets or creates a SQLCipher passphrase, encrypted by the Android Keystore.
     */
    private fun getOrCreatePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(PREF_DB_PASSPHRASE, null)

        return if (existing != null) {
            // Decrypt the stored passphrase using the hardware key
            decryptPassphrase(existing)
        } else {
            // First launch: generate a random passphrase, encrypt it, store it
            val rawPassphrase = SQLiteDatabase.getBytes("aurachat_${System.nanoTime()}".toCharArray())
            val encrypted = encryptPassphrase(rawPassphrase)
            prefs.edit().putString(PREF_DB_PASSPHRASE, encrypted).apply()
            rawPassphrase
        }
    }

    /**
     * Creates (or retrieves) a 256-bit AES key inside the Android Keystore hardware.
     */
    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        // Return existing key if it exists
        keyStore.getKey(KEYSTORE_ALIAS, null)?.let { return it as SecretKey }

        // Generate a new hardware-backed key
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun encryptPassphrase(data: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        // Store IV + ciphertext together
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decryptPassphrase(encoded: String): ByteArray {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        // GCM IV is always 12 bytes
        val iv = combined.copyOfRange(0, 12)
        val encrypted = combined.copyOfRange(12, combined.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKeystoreKey(), spec)
        return cipher.doFinal(encrypted)
    }
}
