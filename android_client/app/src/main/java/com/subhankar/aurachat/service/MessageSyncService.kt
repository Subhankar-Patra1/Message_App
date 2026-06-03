package com.subhankar.aurachat.service

import android.util.Log
import com.subhankar.aurachat.crypto.CryptoEngine
import com.subhankar.aurachat.data.local.dao.ConversationDao
import com.subhankar.aurachat.data.local.dao.ContactDao
import com.subhankar.aurachat.data.local.dao.MessageDao
import com.subhankar.aurachat.data.local.entity.ConversationEntity
import com.subhankar.aurachat.data.local.entity.MessageEntity
import com.subhankar.aurachat.data.local.entity.MessageStatus
import com.subhankar.aurachat.network.PhoenixSocketManager
import kotlinx.coroutines.*
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Message Sync Service — replaces Dart message_sync_service.dart.
 *
 * Listens to incoming encrypted messages from the Phoenix WebSocket,
 * then:
 *   1. Checks for duplicates (dedup via msg_id)
 *   2. Decrypts the ciphertext using CryptoEngine
 *   3. Saves the plaintext to the Room database
 *   4. Updates the conversation summary
 *   5. Sends a delivery receipt back to the sender
 */
@Singleton
class MessageSyncService @Inject constructor(
    private val socketManager: PhoenixSocketManager,
    private val cryptoEngine: CryptoEngine,
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val contactDao: ContactDao
) {
    companion object {
        private const val TAG = "MessageSync"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var collectJob: Job? = null

    /**
     * Start listening for incoming messages.
     * Call this after the socket connects.
     */
    fun start() {
        collectJob?.cancel()
        collectJob = scope.launch {
            socketManager.incomingMessages.collect { payload ->
                try {
                    handleIncomingMessage(payload)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process incoming message: ${e.message}", e)
                }
            }
        }
    }

    private suspend fun handleIncomingMessage(payload: Map<String, Any?>) {
        val msgId = payload["msg_id"] as? String ?: return
        val senderId = payload["sender_id"] as? String ?: return
        val type = payload["type"]

        // Check if this is a group message
        if (type == "group_message") {
            handleGroupMessage(payload)
            return
        }

        // ── Deduplication check ──
        if (messageDao.messageExists(msgId) > 0) {
            Log.d(TAG, "Duplicate message $msgId — skipping")
            // Still send delivery receipt (in case the first one was lost)
            socketManager.sendDeliveryReceipt(senderId, msgId)
            return
        }

        // ── Decrypt ──
        val encryptedPayload = mapOf(
            "type" to (payload["type"] as? Number)?.toInt(),
            "ciphertext" to payload["ciphertext"]
        )

        val plaintext: String = try {
            cryptoEngine.decryptMessage(senderId, encryptedPayload)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed for $msgId from $senderId", e)
            // SECURITY: Never log the ciphertext or keys
            return
        }

        // ── Extract server metadata ──
        val serverTs = (payload["server_ts"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val seq = (payload["seq"] as? Number)?.toInt()

        // ── Save to database ──
        val message = MessageEntity(
            msgId = msgId,
            chatId = senderId,
            senderId = senderId,
            text = plaintext,
            timestamp = Instant.ofEpochMilli(serverTs).toString(),
            isMe = false,
            seq = seq,
            serverTs = serverTs,
            status = MessageStatus.DELIVERED
        )
        messageDao.insert(message)

        // ── Update conversation summary ──
        val contact = contactDao.getContact(senderId)
        conversationDao.upsert(
            ConversationEntity(
                userId = senderId,
                name = contact?.name ?: senderId,
                avatarUrl = contact?.avatarUrl,
                lastMessage = plaintext,
                lastMessageTime = Instant.ofEpochMilli(serverTs).toString(),
                unreadCount = (conversationDao.getConversation(senderId)?.unreadCount ?: 0) + 1
            )
        )

        // ── Send delivery receipt ──
        socketManager.sendDeliveryReceipt(senderId, msgId)
        Log.d(TAG, "Message received and saved: $msgId from $senderId")
    }

    private suspend fun handleGroupMessage(payload: Map<String, Any?>) {
        val msgId = payload["msg_id"] as? String ?: return
        val senderId = payload["sender_id"] as? String ?: return
        val groupId = payload["group_id"] as? String ?: return

        if (messageDao.messageExists(msgId) > 0) return

        val plaintext = try {
            cryptoEngine.decryptGroupMessage(groupId, senderId, payload["ciphertext"] as String)
        } catch (e: Exception) {
            Log.e(TAG, "Group decryption failed for $msgId", e)
            return
        }

        val serverTs = (payload["server_ts"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val seq = (payload["seq"] as? Number)?.toInt()

        val message = MessageEntity(
            msgId = msgId,
            chatId = groupId,
            senderId = senderId,
            text = plaintext,
            timestamp = Instant.ofEpochMilli(serverTs).toString(),
            isMe = false,
            seq = seq,
            serverTs = serverTs,
            status = MessageStatus.DELIVERED
        )
        messageDao.insert(message)
    }

    fun stop() {
        collectJob?.cancel()
    }

    fun dispose() {
        stop()
        scope.cancel()
    }
}
