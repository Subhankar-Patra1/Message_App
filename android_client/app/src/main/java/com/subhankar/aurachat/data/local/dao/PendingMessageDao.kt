package com.subhankar.aurachat.data.local.dao

import androidx.room.*
import com.subhankar.aurachat.data.local.entity.PendingMessageEntity

/**
 * Pending Message DAO — replaces Dart _pendingMessagesBoxName operations.
 * Powers the exponential backoff retry queue.
 */
@Dao
interface PendingMessageDao {

    /**
     * Get all pending messages that are NOT failed (for queue processing).
     * Replaces: DatabaseService.getPendingMessages() + the filter in _processQueue()
     */
    @Query("SELECT * FROM pending_messages WHERE state != 'failed' ORDER BY created_at ASC")
    suspend fun getRetryableMessages(): List<PendingMessageEntity>

    /**
     * Get ALL pending messages (including failed, for UI display).
     */
    @Query("SELECT * FROM pending_messages ORDER BY created_at ASC")
    suspend fun getAllPending(): List<PendingMessageEntity>

    @Query("SELECT * FROM pending_messages WHERE msg_id = :msgId LIMIT 1")
    suspend fun getByMsgId(msgId: String): PendingMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: PendingMessageEntity)

    /**
     * Remove a message from the pending queue (after successful ACK).
     * Replaces: DatabaseService.removePendingMessage()
     */
    @Query("DELETE FROM pending_messages WHERE msg_id = :msgId")
    suspend fun delete(msgId: String)

    /**
     * Reset all retrying/pending messages for immediate reprocessing.
     * Replaces: PendingMessageQueue.retryAll()
     */
    @Query("""
        UPDATE pending_messages 
        SET next_retry_at = :now 
        WHERE state IN ('pending', 'retrying')
    """)
    suspend fun resetAllRetryTimes(now: String)
}
