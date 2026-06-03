package com.subhankar.aurachat.crypto

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.whispersystems.libsignal.*
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.groups.GroupCipher
import org.whispersystems.libsignal.groups.GroupSessionBuilder
import org.whispersystems.libsignal.groups.SenderKeyName
import org.whispersystems.libsignal.protocol.CiphertextMessage
import org.whispersystems.libsignal.protocol.PreKeySignalMessage
import org.whispersystems.libsignal.protocol.SenderKeyDistributionMessage
import org.whispersystems.libsignal.protocol.SignalMessage
import org.whispersystems.libsignal.state.PreKeyBundle
import org.whispersystems.libsignal.util.KeyHelper
import java.nio.charset.StandardCharsets

/**
 * CryptoEngine — the heart of E2EE.
 * Direct translation of your Dart crypto_engine.dart.
 *
 * This class handles:
 *   1. Key generation (identity key, signed pre-key, one-time pre-keys)
 *   2. Session establishment (X3DH via PreKeyBundle)
 *   3. Message encryption/decryption (Double Ratchet via SessionCipher)
 *   4. Group messaging (Sender Key protocol via GroupCipher)
 *
 * All heavy crypto runs on Dispatchers.Default (background thread pool),
 * which is the Kotlin equivalent of Dart's compute() isolate.
 */
class CryptoEngine(private val store: SignalProtocolStoreImpl) {

    companion object {
        private const val TAG = "CryptoEngine"
    }

    // ─── Key Generation ──────────────────────────────────────────

    /**
     * Generate initial E2EE keys and return the public bundle
     * to be registered with the server.
     *
     * Replaces: Dart generateInitialKeys()
     *
     * The returned map matches the exact JSON structure your Elixir
     * backend expects at POST /api/v1/keys/register
     */
    suspend fun generateInitialKeys(): Map<String, Any?> = withContext(Dispatchers.Default) {
        // Get or generate identity key pair
        val identityKeyPair: IdentityKeyPair
        val registrationId: Int

        try {
            identityKeyPair = store.getIdentityKeyPair()
            registrationId = store.getLocalRegistrationId()
        } catch (e: Exception) {
            // First launch — generate fresh keys
            val newKeyPair = KeyHelper.generateIdentityKeyPair()
            val newRegId = KeyHelper.generateRegistrationId(false)
            store.storeIdentityKeyPair(newKeyPair)
            store.storeLocalRegistrationId(newRegId)
            return@withContext generateInitialKeysInternal(newKeyPair, newRegId)
        }

        generateInitialKeysInternal(identityKeyPair, registrationId)
    }

    private fun generateInitialKeysInternal(
        identityKeyPair: IdentityKeyPair,
        registrationId: Int
    ): Map<String, Any?> {
        // Generate Signed PreKey
        val signedPreKey = KeyHelper.generateSignedPreKey(identityKeyPair, 1)
        store.storeSignedPreKey(signedPreKey.id, signedPreKey)

        // Generate One-Time PreKeys (10, matching your Dart code)
        val preKeys = KeyHelper.generatePreKeys(0, 10)
        for (pk in preKeys) {
            store.storePreKey(pk.id, pk)
        }

        // Self-sign the identity key (matching your Dart logic)
        val identityPublic = identityKeyPair.publicKey.serialize()
        val identitySignature = Curve.calculateSignature(
            identityKeyPair.privateKey,
            identityPublic.copyOfRange(1, identityPublic.size)  // Strip type byte
        )

        return mapOf(
            "identity_key" to mapOf(
                "public" to stripTypeByte(identityPublic),
                "signature" to encodeNoPad(identitySignature)
            ),
            "signed_pre_key" to mapOf(
                "key_id" to signedPreKey.id,
                "public" to stripTypeByte(signedPreKey.keyPair.publicKey.serialize()),
                "signature" to encodeNoPad(signedPreKey.signature)
            ),
            "pre_keys" to preKeys.map { pk ->
                mapOf(
                    "key_id" to pk.id,
                    "public" to stripTypeByte(pk.keyPair.publicKey.serialize())
                )
            }
        )
    }

    // ─── Session Establishment ───────────────────────────────────

    /**
     * Establish an encrypted session with a recipient using their pre-key bundle.
     * This is the X3DH handshake.
     *
     * Replaces: Dart establishSession()
     */
    suspend fun establishSession(recipientId: String, bundle: Map<String, Any?>) =
        withContext(Dispatchers.Default) {
            val address = SignalProtocolAddress(recipientId, 1)

            // Parse identity key
            val identityKeyBytes = decodeWithPrefix(bundle.nested("identity_key", "public"))
            val identityKey = IdentityKey(identityKeyBytes, 0)

            // Parse signed pre-key
            val signedPreKeyMap = bundle["signed_pre_key"] as? Map<*, *>
                ?: throw IllegalArgumentException("Missing signed_pre_key")
            val signedPreKeyPublic = Curve.decodePoint(
                decodeWithPrefix(signedPreKeyMap["public"] as String), 0
            )
            val signedPreKeyId = (signedPreKeyMap["key_id"] as Number).toInt()
            val signedPreKeySignature = decodePadded(signedPreKeyMap["signature"] as String)

            // Parse one-time pre-key (optional)
            var preKeyPublic: org.whispersystems.libsignal.ecc.ECPublicKey? = null
            var preKeyId = -1
            val preKeysList = bundle["pre_keys"] as? List<*>
            if (!preKeysList.isNullOrEmpty()) {
                val pk = preKeysList[0] as Map<*, *>
                preKeyId = (pk["key_id"] as Number).toInt()
                preKeyPublic = Curve.decodePoint(decodeWithPrefix(pk["public"] as String), 0)
            }

            // Build the PreKeyBundle
            val preKeyBundle = PreKeyBundle(
                0,                      // registrationId (server doesn't send this, use 0)
                1,                      // deviceId
                preKeyId,               // preKeyId (-1 if none)
                preKeyPublic,           // preKeyPublic (null if none)
                signedPreKeyId,         // signedPreKeyId
                signedPreKeyPublic,     // signedPreKeyPublic
                signedPreKeySignature,  // signedPreKeySignature
                identityKey             // identityKey
            )

            // Process the bundle → creates the session
            val sessionBuilder = SessionBuilder(store, address)
            sessionBuilder.process(preKeyBundle)
            Log.d(TAG, "Session established with $recipientId")
        }

    // ─── 1:1 Message Encryption ──────────────────────────────────

    /**
     * Encrypt a plaintext message for a recipient.
     *
     * Replaces: Dart encryptMessage()
     *
     * Returns a map with:
     *   "type": CiphertextMessage type (3 = PreKey, 1 = Signal)
     *   "ciphertext": Base64URL-encoded ciphertext (no padding)
     *   "sender_id": our user ID (added by ChatRepository before sending)
     */
    suspend fun encryptMessage(recipientId: String, plaintext: String): Map<String, Any?> =
        withContext(Dispatchers.Default) {
            val address = SignalProtocolAddress(recipientId, 1)
            val sessionCipher = SessionCipher(store, address)

            val ciphertextMessage = sessionCipher.encrypt(
                plaintext.toByteArray(StandardCharsets.UTF_8)
            )

            mapOf(
                "type" to ciphertextMessage.type,
                "ciphertext" to encodeNoPad(ciphertextMessage.serialize())
            )
        }

    // ─── 1:1 Message Decryption ──────────────────────────────────

    /**
     * Decrypt an incoming encrypted message.
     *
     * Replaces: Dart decryptMessage()
     *
     * Handles both:
     *   - PreKeySignalMessage (first message in a session, type=3)
     *   - SignalMessage (subsequent messages, type=1)
     */
    suspend fun decryptMessage(senderId: String, encryptedPayload: Map<String, Any?>): String =
        withContext(Dispatchers.Default) {
            val address = SignalProtocolAddress(senderId, 1)
            val type = (encryptedPayload["type"] as Number).toInt()
            val ciphertextBytes = decodePadded(encryptedPayload["ciphertext"] as String)

            val sessionCipher = SessionCipher(store, address)

            val plaintext: ByteArray = if (type == CiphertextMessage.PREKEY_TYPE) {
                // First message — includes key exchange material
                sessionCipher.decrypt(PreKeySignalMessage(ciphertextBytes))
            } else {
                // Normal ratcheted message
                sessionCipher.decrypt(SignalMessage(ciphertextBytes))
            }

            String(plaintext, StandardCharsets.UTF_8)
        }

    // ─── Group Messaging (Sender Key Protocol) ───────────────────

    /**
     * Generate a SenderKeyDistributionMessage for a group.
     * Must be sent to all group members before you can encrypt group messages.
     *
     * Replaces: Dart generateSenderKeyDistributionMessage()
     */
    suspend fun generateSenderKeyDistributionMessage(
        groupId: String,
        senderId: String
    ): String = withContext(Dispatchers.Default) {
        val senderKeyName = SenderKeyName(groupId, SignalProtocolAddress(senderId, 1))
        val builder = GroupSessionBuilder(store)
        val distributionMessage = builder.create(senderKeyName)
        encodeNoPad(distributionMessage.serialize())
    }

    /**
     * Process a received SenderKeyDistributionMessage from another group member.
     *
     * Replaces: Dart processSenderKeyDistribution()
     */
    suspend fun processSenderKeyDistribution(
        groupId: String,
        senderId: String,
        distributionMessageB64: String
    ) = withContext(Dispatchers.Default) {
        val senderKeyName = SenderKeyName(groupId, SignalProtocolAddress(senderId, 1))
        val builder = GroupSessionBuilder(store)
        val bytes = decodePadded(distributionMessageB64)
        val distributionMessage = SenderKeyDistributionMessage(bytes)
        builder.process(senderKeyName, distributionMessage)
    }

    /**
     * Rotate the sender key for a group (e.g., after a member is removed).
     *
     * Replaces: Dart rotateSenderKey()
     */
    suspend fun rotateSenderKey(groupId: String, senderId: String): String =
        withContext(Dispatchers.Default) {
            val senderKeyName = SenderKeyName(groupId, SignalProtocolAddress(senderId, 1))
            // Overwrite with empty record to force new key generation
            store.storeSenderKey(senderKeyName, org.whispersystems.libsignal.groups.state.SenderKeyRecord())
            // Generate fresh distribution message
            val builder = GroupSessionBuilder(store)
            val distributionMessage = builder.create(senderKeyName)
            encodeNoPad(distributionMessage.serialize())
        }

    /**
     * Encrypt a message for a group.
     *
     * Replaces: Dart encryptGroupMessage()
     */
    suspend fun encryptGroupMessage(
        groupId: String,
        senderId: String,
        plaintext: String
    ): String = withContext(Dispatchers.Default) {
        val senderKeyName = SenderKeyName(groupId, SignalProtocolAddress(senderId, 1))
        val cipher = GroupCipher(store, senderKeyName)
        val ciphertextBytes = cipher.encrypt(plaintext.toByteArray(StandardCharsets.UTF_8))
        encodeNoPad(ciphertextBytes)
    }

    /**
     * Decrypt a group message.
     *
     * Replaces: Dart decryptGroupMessage()
     */
    suspend fun decryptGroupMessage(
        groupId: String,
        senderId: String,
        ciphertextB64: String
    ): String = withContext(Dispatchers.Default) {
        val senderKeyName = SenderKeyName(groupId, SignalProtocolAddress(senderId, 1))
        val cipher = GroupCipher(store, senderKeyName)
        val bytes = decodePadded(ciphertextB64)
        val plaintextBytes = cipher.decrypt(bytes)
        String(plaintextBytes, StandardCharsets.UTF_8)
    }

    // ─── Encoding Helpers ────────────────────────────────────────

    /**
     * Strip the 0x05 type byte from a Curve25519 public key and encode.
     * Matches: Dart stripTypeByte()
     */
    private fun stripTypeByte(bytes: ByteArray): String {
        return encodeNoPad(bytes.copyOfRange(1, bytes.size))
    }

    /**
     * Decode a Base64URL string and prepend the 0x05 type byte.
     * Matches: Dart _decodeWithPrefix()
     */
    private fun decodeWithPrefix(b64: String): ByteArray {
        val decoded = decodePadded(b64)
        val prefixed = ByteArray(33)
        prefixed[0] = 0x05
        System.arraycopy(decoded, 0, prefixed, 1, 32)
        return prefixed
    }

    /**
     * Encode bytes to Base64URL without padding.
     * Matches: Dart base64UrlEncode(...).replaceAll('=', '')
     */
    private fun encodeNoPad(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Decode a Base64URL string, automatically handling missing padding.
     * Matches: Dart _padBase64()
     */
    private fun decodePadded(b64: String): ByteArray {
        return Base64.decode(b64, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /** Helper to read nested map values */
    private fun Map<String, Any?>.nested(key1: String, key2: String): String {
        val inner = this[key1] as? Map<*, *>
            ?: throw IllegalArgumentException("Missing key: $key1")
        return inner[key2] as? String
            ?: throw IllegalArgumentException("Missing key: $key1.$key2")
    }
}
