package com.subhankar.aurachat.service

import android.util.Log
import com.subhankar.aurachat.data.local.dao.ConversationDao
import com.subhankar.aurachat.data.local.dao.MessageDao
import com.subhankar.aurachat.data.local.entity.MessageStatus
import com.subhankar.aurachat.network.PhoenixSocketManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Receipt Service — replaces Dart receipt_service.dart.
 *
 * Handles two types of receipts:
 *   1. Single message receipt: { msg_id, status, by }
 *   2. Read cursor (bulk): { isCursor, reader_id, last_read_seq }
 *
 * Updates the local database and emits events for UI refresh.
 */
@Singleton
class ReceiptService @Inject constructor(
    private val socketManager: PhoenixSocketManager,
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao
) {
    companion object {
        private const val TAG = "ReceiptService"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var collectJob: Job? = null

    // UI can observe this for real-time tick updates
    private val _receiptUpdates = MutableSharedFlow<Map<String, Any?>>(extraBufferCapacity = 64)
    val receiptUpdates: SharedFlow<Map<String, Any?>> = _receiptUpdates

    fun start() {
        collectJob?.cancel()
        collectJob = scope.launch {
            socketManager.receiptEvents.collect { payload ->
                try {
                    handleReceipt(payload)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process receipt: ${e.message}", e)
                }
            }
        }
    }

    private suspend fun handleReceipt(payload: Map<String, Any?>) {
        val isCursor = payload["isCursor"] == true

        if (isCursor) {
            // Bulk read cursor — marks all messages up to seq as read
            val readerId = payload["reader_id"] as? String ?: return
            val seq = (payload["last_read_seq"] as? Number)?.toInt() ?: return

            messageDao.bulkMarkRead(readerId, seq)
            _receiptUpdates.emit(mapOf(
                "isCursor" to true,
                "reader_id" to readerId,
                "seq" to seq,
                "status" to MessageStatus.READ
            ))
            Log.d(TAG, "Bulk read cursor: $readerId up to seq $seq")
        } else {
            // Single message receipt
            val msgId = payload["msg_id"] as? String ?: return
            val status = payload["status"] as? String ?: return  // "delivered" or "read"
            val senderId = payload["by"] as? String ?: return

            // Validate state transition before updating
            val existing = messageDao.getByMsgId(msgId)
            if (existing != null) {
                val currentWeight = MessageStatus.getWeight(existing.status)
                val newWeight = MessageStatus.getWeight(status)
                if (newWeight > currentWeight) {
                    messageDao.updateStatus(msgId, status)
                    Log.d(TAG, "Receipt: $msgId → $status (from $senderId)")
                }
            }

            _receiptUpdates.emit(mapOf(
                "msg_id" to msgId,
                "status" to status
            ))
        }
    }

    fun sendReadCursor(senderId: String, lastReadSeq: Int) {
        socketManager.sendReadCursor(senderId, lastReadSeq)
    }

    fun sendReadReceipt(senderId: String, msgId: String) {
        socketManager.sendReadReceipt(senderId, msgId)
    }

    fun stop() { collectJob?.cancel() }
    fun dispose() { stop(); scope.cancel() }
}
