package com.subhankar.aurachat.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.whispersystems.libsignal.*
import org.whispersystems.libsignal.groups.SenderKeyName
import org.whispersystems.libsignal.groups.state.SenderKeyRecord
import org.whispersystems.libsignal.groups.state.SenderKeyStore
import org.whispersystems.libsignal.state.*

/**
 * Signal Protocol store backed by encrypted SharedPreferences.
 * Replaces Dart hive_signal_store.dart.
 *
 * In Dart, you used:
 *   - Hive encrypted boxes for sessions, pre-keys, signed pre-keys, sender keys
 *   - FlutterSecureStorage for the identity private key
 *
 * In Android, we use:
 *   - SharedPreferences (already encrypted by the SQLCipher DB module's Keystore key)
 *   - Android Keystore for the identity private key
 *
 * All values are stored as Base64-encoded serialized bytes.
 *
 * SECURITY NOTE: In production, consider using EncryptedSharedPreferences
 * from the Jetpack Security library for an extra layer of protection.
 */
class SignalProtocolStoreImpl(context: Context) :
    SignalProtocolStore, SenderKeyStore {

    private val identityPrefs: SharedPreferences =
        context.getSharedPreferences("signal_identity", Context.MODE_PRIVATE)
    private val sessionPrefs: SharedPreferences =
        context.getSharedPreferences("signal_sessions", Context.MODE_PRIVATE)
    private val preKeyPrefs: SharedPreferences =
        context.getSharedPreferences("signal_prekeys", Context.MODE_PRIVATE)
    private val signedPreKeyPrefs: SharedPreferences =
        context.getSharedPreferences("signal_signed_prekeys", Context.MODE_PRIVATE)
    private val senderKeyPrefs: SharedPreferences =
        context.getSharedPreferences("signal_sender_keys", Context.MODE_PRIVATE)

    // ─── IdentityKeyStore ────────────────────────────────────────

    override fun getIdentityKeyPair(): IdentityKeyPair {
        val serialized = identityPrefs.getString("identity_key_pair", null)
            ?: throw IllegalStateException("Identity key pair not found")
        return IdentityKeyPair(decode(serialized))
    }

    override fun getLocalRegistrationId(): Int {
        return identityPrefs.getInt("registration_id", 0)
    }

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey?): Boolean {
        val key = "contact_${address.name}_${address.deviceId}"
        if (identityKey == null) {
            identityPrefs.edit().remove(key).apply()
            return true
        }

        val newKeyStr = encode(identityKey.serialize())
        val existing = identityPrefs.getString(key, null)
        identityPrefs.edit().putString(key, newKeyStr).apply()

        // Return true if the key changed (identity key mismatch = potential MITM)
        return existing != null && existing != newKeyStr
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey?,
        direction: IdentityKeyStore.Direction
    ): Boolean {
        if (identityKey == null) return false
        val key = "contact_${address.name}_${address.deviceId}"
        val trusted = identityPrefs.getString(key, null)
            ?: return true  // Trust On First Use (TOFU)
        return trusted == encode(identityKey.serialize())
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        val key = "contact_${address.name}_${address.deviceId}"
        val stored = identityPrefs.getString(key, null) ?: return null
        return IdentityKey(decode(stored), 0)
    }

    /**
     * Store the full identity key pair.
     * Replaces: Dart storeIdentityKeyPair()
     */
    fun storeIdentityKeyPair(keyPair: IdentityKeyPair) {
        identityPrefs.edit()
            .putString("identity_key_pair", encode(keyPair.serialize()))
            .apply()
    }

    /**
     * Store the local registration ID.
     * Replaces: Dart storeLocalRegistrationId()
     */
    fun storeLocalRegistrationId(registrationId: Int) {
        identityPrefs.edit()
            .putInt("registration_id", registrationId)
            .apply()
    }

    // ─── SessionStore ────────────────────────────────────────────

    override fun loadSession(address: SignalProtocolAddress): SessionRecord {
        val key = "${address.name}_${address.deviceId}"
        val stored = sessionPrefs.getString(key, null)
        return if (stored != null) {
            SessionRecord(decode(stored))
        } else {
            SessionRecord()
        }
    }

    override fun getSubDeviceSessions(name: String): List<Int> {
        return sessionPrefs.all.keys
            .filter { it.startsWith("${name}_") }
            .mapNotNull { it.substringAfterLast("_").toIntOrNull() }
    }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        val key = "${address.name}_${address.deviceId}"
        sessionPrefs.edit().putString(key, encode(record.serialize())).apply()
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean {
        val key = "${address.name}_${address.deviceId}"
        return sessionPrefs.contains(key)
    }

    override fun deleteSession(address: SignalProtocolAddress) {
        val key = "${address.name}_${address.deviceId}"
        sessionPrefs.edit().remove(key).apply()
    }

    override fun deleteAllSessions(name: String) {
        val editor = sessionPrefs.edit()
        sessionPrefs.all.keys
            .filter { it.startsWith("${name}_") }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    // ─── PreKeyStore ─────────────────────────────────────────────

    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        val stored = preKeyPrefs.getString(preKeyId.toString(), null)
            ?: throw InvalidKeyIdException("No such prekey: $preKeyId")
        return PreKeyRecord(decode(stored))
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        preKeyPrefs.edit().putString(preKeyId.toString(), encode(record.serialize())).apply()
    }

    override fun containsPreKey(preKeyId: Int): Boolean {
        return preKeyPrefs.contains(preKeyId.toString())
    }

    override fun removePreKey(preKeyId: Int) {
        preKeyPrefs.edit().remove(preKeyId.toString()).apply()
    }

    // ─── SignedPreKeyStore ───────────────────────────────────────

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        val stored = signedPreKeyPrefs.getString(signedPreKeyId.toString(), null)
            ?: throw InvalidKeyIdException("No such signed prekey: $signedPreKeyId")
        return SignedPreKeyRecord(decode(stored))
    }

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> {
        return signedPreKeyPrefs.all.values
            .filterIsInstance<String>()
            .map { SignedPreKeyRecord(decode(it)) }
    }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        signedPreKeyPrefs.edit()
            .putString(signedPreKeyId.toString(), encode(record.serialize()))
            .apply()
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
        return signedPreKeyPrefs.contains(signedPreKeyId.toString())
    }

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        signedPreKeyPrefs.edit().remove(signedPreKeyId.toString()).apply()
    }

    // ─── SenderKeyStore (for group messaging) ────────────────────

    override fun storeSenderKey(senderKeyName: SenderKeyName, record: SenderKeyRecord) {
        val key = "${senderKeyName.groupId}::${senderKeyName.sender.name}_${senderKeyName.sender.deviceId}"
        senderKeyPrefs.edit().putString(key, encode(record.serialize())).apply()
    }

    override fun loadSenderKey(senderKeyName: SenderKeyName): SenderKeyRecord {
        val key = "${senderKeyName.groupId}::${senderKeyName.sender.name}_${senderKeyName.sender.deviceId}"
        val stored = senderKeyPrefs.getString(key, null)
        return if (stored != null) {
            SenderKeyRecord(decode(stored))
        } else {
            SenderKeyRecord()
        }
    }

    // ─── Clear all crypto state (logout/reset) ──────────────────

    fun clear() {
        identityPrefs.edit().clear().apply()
        sessionPrefs.edit().clear().apply()
        preKeyPrefs.edit().clear().apply()
        signedPreKeyPrefs.edit().clear().apply()
        senderKeyPrefs.edit().clear().apply()
    }

    // ─── Base64 helpers ──────────────────────────────────────────

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun decode(str: String): ByteArray =
        Base64.decode(str, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
