package com.subhankar.aurachat.service

import android.util.Log
import com.subhankar.aurachat.crypto.CryptoEngine
import com.subhankar.aurachat.data.local.dao.ContactDao
import com.subhankar.aurachat.data.local.dao.ConversationDao
import com.subhankar.aurachat.data.local.dao.MessageDao
import com.subhankar.aurachat.data.local.entity.ConversationEntity
import com.subhankar.aurachat.data.local.entity.MessageEntity
import com.subhankar.aurachat.data.local.entity.MessageStatus
import com.subhankar.aurachat.data.local.SessionManager
import com.subhankar.aurachat.network.ApiClient
import com.subhankar.aurachat.network.AppConfig
import com.subhankar.aurachat.network.ConnectionManager
import com.subhankar.aurachat.network.PhoenixSocketManager
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chat Repository — replaces Dart chat_repository.dart.
 *
 * This is the single orchestrator that wires together:
 *   • CryptoEngine (encryption/decryption)
 *   • PhoenixSocketManager (WebSocket)
 *   • ConnectionManager (reconnection)
 *   • PendingMessageQueue (reliable delivery)
 *   • MessageSyncService (incoming messages)
 *   • ReceiptService (delivery/read receipts)
 *   • TypingService (typing indicators)
 *   • PresenceService (online/offline status)
 *
 * In Dart, the ServiceLocator created all these manually.
 * In Kotlin, Hilt handles dependency injection automatically.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val cryptoEngine: CryptoEngine,
    private val apiClient: ApiClient,
    private val socketManager: PhoenixSocketManager,
    private val connectionManager: ConnectionManager,
    private val pendingQueue: PendingMessageQueue,
    private val syncService: MessageSyncService,
    private val receiptService: ReceiptService,
    private val typingService: TypingService,
    private val presenceService: PresenceService,
    private val sessionManager: SessionManager,
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val contactDao: ContactDao
) {
    private val repositoryScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)

    companion object {
        private const val TAG = "ChatRepository"
    }

    /**
     * Initialize the entire messaging system.
     * Replaces: Dart chatRepository.initialize()
     *
     * Call this once after login/auth is complete.
     */
    suspend fun initialize() {
        // 1. Generate and register E2EE keys with the server
        val keyBundle = cryptoEngine.generateInitialKeys()
        apiClient.registerKeys(keyBundle)
        Log.d(TAG, "E2EE keys registered")

        // 2. Connect WebSocket
        val userId = sessionManager.getUserId() ?: throw IllegalStateException("Not logged in")
        val token = sessionManager.getToken() ?: throw IllegalStateException("No auth token")
        connectionManager.connect(
            socketUrl = AppConfig.socketEndpoint,
            token = token,
            userId = userId
        )

        // 3. Start all services
        syncService.start()
        receiptService.start()
        typingService.start()
        presenceService.start()

        // 4. Retry any messages that were pending from a previous session
        pendingQueue.retryAll()

        Log.d(TAG, "ChatRepository initialized — all services running")
    }

    /**
     * Send a message reliably.
     * Replaces: Dart chatRepository.enqueueMessage()
     *
     * Flow:
     *   1. Save to local DB immediately (shows in UI with "pending" clock icon)
     *   2. Enqueue for reliable delivery (encryption + send + retry)
     *   3. On ACK: update to "sent" (single tick)
     *   4. On delivery receipt: update to "delivered" (double tick)
     *   5. On read receipt: update to "read" (blue double tick)
     */
    suspend fun sendMessage(recipientId: String, plaintext: String, msgId: String? = null): String {
        val finalMsgId = msgId ?: UUID.randomUUID().toString()
        val myUserId = sessionManager.getUserId() ?: throw IllegalStateException("Not logged in")

        if (recipientId == "dummy_user_123") {
            // Save user message to local DB immediately as READ
            val message = MessageEntity(
                msgId = finalMsgId,
                chatId = recipientId,
                senderId = myUserId,
                text = plaintext,
                timestamp = Instant.now().toString(),
                isMe = true,
                status = MessageStatus.READ
            )
            messageDao.insert(message)

            // Update conversation summary
            conversationDao.upsert(
                ConversationEntity(
                    userId = recipientId,
                    name = "AuraChat Assistant",
                    avatarUrl = null,
                    lastMessage = plaintext,
                    lastMessageTime = Instant.now().toString(),
                    unreadCount = 0
                )
            )

            // Simulate reply from the assistant in the background
            /*
            repositoryScope.launch {
                try {
                    kotlinx.coroutines.delay(1000)
                    val responseText = getAssistantResponse(plaintext)
                    val responseMsgId = UUID.randomUUID().toString()
                    val responseMessage = MessageEntity(
                        msgId = responseMsgId,
                        chatId = recipientId,
                        senderId = recipientId,
                        text = responseText,
                        timestamp = Instant.now().toString(),
                        isMe = false,
                        status = MessageStatus.READ
                    )
                    messageDao.insert(responseMessage)

                    conversationDao.upsert(
                        ConversationEntity(
                            userId = recipientId,
                            name = "AuraChat Assistant",
                            avatarUrl = null,
                            lastMessage = responseText,
                            lastMessageTime = Instant.now().toString(),
                            unreadCount = 0
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            */
            return finalMsgId
        }

        // Save to local DB immediately — appears in chat with pending status
        val message = MessageEntity(
            msgId = finalMsgId,
            chatId = recipientId,
            senderId = myUserId,
            text = plaintext,
            timestamp = Instant.now().toString(),
            isMe = true,
            status = MessageStatus.PENDING
        )
        messageDao.insert(message)

        // Update conversation summary
        val contact = contactDao.getContact(recipientId)
        conversationDao.upsert(
            ConversationEntity(
                userId = recipientId,
                name = contact?.name ?: recipientId,
                avatarUrl = contact?.avatarUrl,
                lastMessage = plaintext,
                lastMessageTime = Instant.now().toString()
            )
        )

        // Enqueue for reliable delivery
        pendingQueue.enqueue(
            msgId = finalMsgId,
            recipientId = recipientId,
            senderId = myUserId,
            plaintext = plaintext
        )

        return finalMsgId
    }

    private fun getAssistantResponse(input: String): String {
        val lower = input.lowercase().trim()
        return when {
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") -> {
                "Hello there! I am your AuraChat Assistant. How can I help you customize your chat screen today?"
            }
            lower.contains("color") || lower.contains("theme") || lower.contains("custom") -> {
                "AuraChat has premium colors built-in! Tell me which colors or fonts you want to customize on the Chat screen."
            }
            lower.contains("help") -> {
                "I can assist with testing messages, checking bubble layouts, and validating styling. Just type anything here!"
            }
            else -> {
                "Awesome! You typed: \"$input\". Tell me how you want to design or customize this chat bubble interface."
            }
        }
    }

    /**
     * Called when app returns to foreground.
     */
    fun onAppResumed() {
        connectionManager.onAppResumed()
    }

    /**
     * Clean shutdown.
     */
    fun dispose() {
        syncService.dispose()
        receiptService.dispose()
        typingService.dispose()
        presenceService.dispose()
        pendingQueue.dispose()
        connectionManager.dispose()
    }
}
