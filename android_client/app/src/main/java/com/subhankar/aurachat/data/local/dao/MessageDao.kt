package com.subhankar.aurachat.data.local.dao

import androidx.room.*
import com.subhankar.aurachat.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Message DAO — replaces the Dart DatabaseService message operations.
 *
 * Key differences from Dart:
 *   • Flow<List<MessageEntity>> = Dart Stream, but Room auto-emits on DB change
 *   • No manual box.toMap() iteration — SQL does the work
 *   • Dedup check is a simple SELECT instead of box.values.any()
 */
@Dao
interface MessageDao {

    /**
     * Live stream of messages for a chat, ordered by seq then timestamp.
     * Replaces: DatabaseService.getMessages(userId)
     *
     * Room will automatically re-emit whenever the messages table changes.
     * This is MUCH more efficient than Dart's manual sorting.
     */
    @Query("""
        SELECT * FROM messages 
        WHERE chat_id = :chatId 
        ORDER BY 
            CASE WHEN seq IS NOT NULL THEN seq ELSE 999999999 END DESC, 
            timestamp DESC
    """)
    fun getMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    /**
     * One-shot fetch (non-reactive) for background processing.
     */
    @Query("SELECT * FROM messages WHERE chat_id = :chatId ORDER BY seq ASC, timestamp ASC")
    suspend fun getMessagesForChatOnce(chatId: String): List<MessageEntity>

    /**
     * Deduplication check — replaces: DatabaseService.messageExists()
     * Returns 1 if exists, 0 if not. Much faster than Dart's linear scan.
     */
    @Query("SELECT COUNT(*) FROM messages WHERE msg_id = :msgId")
    suspend fun messageExists(msgId: String): Int

    /**
     * Get a single message by its unique ID.
     * Used for status updates and ACK processing.
     */
    @Query("SELECT * FROM messages WHERE msg_id = :msgId LIMIT 1")
    suspend fun getByMsgId(msgId: String): MessageEntity?

    /**
     * Insert a new message. OnConflictStrategy.IGNORE prevents crashes
     * if a duplicate msgId somehow arrives (belt-and-suspenders dedup).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    /**
     * Update an existing message (e.g., status change, seq assignment).
     */
    @Update
    suspend fun update(message: MessageEntity)

    /**
     * Upsert: insert or replace. Used when we receive a server ACK
     * and need to overwrite the pending message with server_ts and seq.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    /**
     * Update message status by msgId.
     * Replaces: DatabaseService.updateMessageStatus()
     */
    @Query("UPDATE messages SET status = :status WHERE msg_id = :msgId")
    suspend fun updateStatus(msgId: String, status: String)

    /**
     * Bulk mark messages as read up to a sequence number.
     * Replaces: DatabaseService.bulkUpdateMessagesRead()
     *
     * This single SQL statement replaces your Dart loop that iterated
     * over every message in the box — orders of magnitude faster.
     */
    @Query("""
        UPDATE messages 
        SET status = 'read' 
        WHERE chat_id = :chatId 
          AND is_me = 1 
          AND seq IS NOT NULL 
          AND seq <= :upToSeq 
          AND status != 'read'
    """)
    suspend fun bulkMarkRead(chatId: String, upToSeq: Int)

    /**
     * Delete all messages for a chat (used for "Clear Chat" feature).
     */
    @Query("DELETE FROM messages WHERE chat_id = :chatId")
    suspend fun deleteAllForChat(chatId: String)
}
