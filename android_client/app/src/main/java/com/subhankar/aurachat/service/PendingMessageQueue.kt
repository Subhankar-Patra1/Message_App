package com.subhankar.aurachat.service

import android.util.Log
import com.subhankar.aurachat.data.local.dao.MessageDao
import com.subhankar.aurachat.data.local.dao.PendingMessageDao
import com.subhankar.aurachat.data.local.entity.MessageEntity
import com.subhankar.aurachat.data.local.entity.MessageStatus
import com.subhankar.aurachat.data.local.entity.PendingMessageEntity
import com.subhankar.aurachat.crypto.CryptoEngine
import com.subhankar.aurachat.network.PhoenixSocketManager
import com.subhankar.aurachat.network.AppConfig
import com.subhankar.aurachat.network.ApiClient
import kotlinx.coroutines.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

/**
 * Pending Message Queue — replaces Dart pending_message_queue.dart.
 *
 * Guarantees message delivery using:
 *   1. Persistent storage (Room) — survives app kill
 *   2. Exponential backoff retries (5s → 10s → 20s → 40s → 60s)
 *   3. Max 5 attempts before marking as FAILED
 *
 * Uses Kotlin Coroutines instead of Dart Timers.
 * Much more powerful: structured concurrency, cancellation, thread safety.
 */
@Singleton
class PendingMessageQueue @Inject constructor(
    private val pendingDao: PendingMessageDao,
    private val messageDao: MessageDao,
    private val cryptoEngine: CryptoEngine,
    private val socketManager: PhoenixSocketManager,
    private val apiClient: ApiClient
) {
    companion object {
        private const val TAG = "PendingQueue"
        private const val MAX_ATTEMPTS = 5
        private const val PROCESS_DELAY_MS = 500L
        private const val RECHECK_INTERVAL_MS = 5_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var processJob: Job? = null

    /**
     * Enqueue a new message for reliable delivery.
     * Replaces: Dart pendingQueue.enqueue(msg)
     */
    suspend fun enqueue(msgId: String, recipientId: String, senderId: String, plaintext: String) {
        val pending = PendingMessageEntity(
            msgId = msgId,
            recipientId = recipientId,
            senderId = senderId,
            plaintext = plaintext,
            state = MessageStatus.PENDING,
            attemptCount = 0,
            createdAt = Instant.now().toString(),
            nextRetryAt = null
        )
        pendingDao.upsert(pending)
        scheduleProcess()
    }

    /**
     * Handle a successful server ACK.
     * Replaces: Dart pendingQueue.onAck(msgId, serverTs, seq)
     */
    suspend fun onAck(msgId: String, serverTs: Long, seq: Int) {
        val pending = pendingDao.getByMsgId(msgId) ?: return

        // Remove from pending queue
        pendingDao.delete(msgId)

        // Update the main message with server-assigned timestamp and seq
        val existing = messageDao.getByMsgId(msgId)
        if (existing != null) {
            messageDao.update(
                existing.copy(
                    status = MessageStatus.SENT,
                    serverTs = serverTs,
                    seq = seq,
                    timestamp = Instant.ofEpochMilli(serverTs).toString()
                )
            )
        }
        Log.d(TAG, "ACK received for $msgId (seq=$seq)")
    }

    /**
     * Handle a send timeout/failure.
     * Replaces: Dart pendingQueue.onTimeout(msgId)
     */
    suspend fun onTimeout(msgId: String) {
        val pending = pendingDao.getByMsgId(msgId) ?: return
        val attempts = pending.attemptCount + 1

        if (attempts >= MAX_ATTEMPTS) {
            // Mark as permanently failed
            pendingDao.upsert(pending.copy(state = MessageStatus.FAILED, attemptCount = attempts))
            messageDao.updateStatus(msgId, MessageStatus.FAILED)
            Log.w(TAG, "Message $msgId FAILED after $MAX_ATTEMPTS attempts")
        } else {
            // Schedule retry with exponential backoff
            val nextRetry = Instant.now().plus(getRetryDelaySeconds(attempts), ChronoUnit.SECONDS)
            pendingDao.upsert(
                pending.copy(
                    state = MessageStatus.RETRYING,
                    attemptCount = attempts,
                    nextRetryAt = nextRetry.toString()
                )
            )
            Log.d(TAG, "Message $msgId retry #$attempts scheduled at $nextRetry")
            scheduleProcess()
        }
    }

    /**
     * Force-retry all pending/retrying messages immediately.
     * Replaces: Dart pendingQueue.retryAll()
     */
    suspend fun retryAll() {
        pendingDao.resetAllRetryTimes(Instant.now().toString())
        scheduleProcess()
    }

    /**
     * Exponential backoff delays — matches your Dart _getRetryDelay().
     */
    private fun getRetryDelaySeconds(attempt: Int): Long = when (attempt) {
        1 -> 5L
        2 -> 10L
        3 -> 20L
        4 -> 40L
        else -> 60L
    }

    // ─── Queue Processing Loop ───────────────────────────────────

    private fun scheduleProcess() {
        processJob?.cancel()
        processJob = scope.launch {
            delay(PROCESS_DELAY_MS)
            processQueue()
        }
    }

    private suspend fun processQueue() {
        if (!socketManager.isConnected) return

        val pending = pendingDao.getRetryableMessages()
        val now = Instant.now()

        for (msg in pending) {
            // Check if it's time to retry
            val nextRetryAt = msg.nextRetryAt?.let { Instant.parse(it) } ?: now
            if (now.isBefore(nextRetryAt)) continue

            // Update state to encrypting
            pendingDao.upsert(msg.copy(state = MessageStatus.ENCRYPTING))

            try {
                // Encrypt and send
                var encryptedPayload: Map<String, Any?>? = null
                
                try {
                    encryptedPayload = cryptoEngine.encryptMessage(msg.recipientId, msg.plaintext)
                } catch (e: Exception) {
                    // No session — fetch bundle and establish
                    Log.d(TAG, "No session for ${msg.recipientId}, fetching keys to establish session...")
                    val bundle = apiClient.fetchPreKeys(msg.recipientId)
                    cryptoEngine.establishSession(msg.recipientId, bundle)
                    
                    // Retry encryption immediately
                    encryptedPayload = cryptoEngine.encryptMessage(msg.recipientId, msg.plaintext)
                }

                val payload = encryptedPayload!!.toMutableMap()
                payload["sender_id"] = msg.senderId

                val response = socketManager.sendMessage(msg.recipientId, msg.msgId, payload)

                // Success! Process ACK
                val serverTs = (response["server_ts"] as? Number)?.toLong() ?: System.currentTimeMillis()
                val seq = (response["seq"] as? Number)?.toInt() ?: 0
                onAck(msg.msgId, serverTs, seq)

            } catch (e: Exception) {
                Log.e(TAG, "Send failed for ${msg.msgId}: ${e.message}")
                onTimeout(msg.msgId)
            }
        }

        // Re-schedule if there are still pending items
        val stillPending = pendingDao.getRetryableMessages()
        if (stillPending.isNotEmpty()) {
            processJob = scope.launch {
                delay(RECHECK_INTERVAL_MS)
                processQueue()
            }
        }
    }

    fun dispose() {
        processJob?.cancel()
        scope.cancel()
    }
}
