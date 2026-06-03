package com.subhankar.aurachat.data.local.dao

import androidx.room.*
import com.subhankar.aurachat.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Conversation DAO — replaces Dart _conversationsBox operations.
 *
 * The Flow return type means the home screen's conversation list
 * will auto-update the instant a new message arrives — no manual refresh.
 */
@Dao
interface ConversationDao {

    /**
     * Live stream of all conversations, sorted newest-first.
     * Replaces: DatabaseService.getAllConversations()
     * The ORDER BY replaces your Dart manual sort.
     */
    @Query("SELECT * FROM conversations ORDER BY last_message_time DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    /**
     * Get a single conversation (one-shot).
     */
    @Query("SELECT * FROM conversations WHERE user_id = :userId LIMIT 1")
    suspend fun getConversation(userId: String): ConversationEntity?

    /**
     * Insert or update a conversation summary.
     * Replaces: DatabaseService.updateConversation()
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    /**
     * Reset unread count to 0 when user opens a chat.
     * Replaces: DatabaseService.markAsRead()
     */
    @Query("UPDATE conversations SET unread_count = 0 WHERE user_id = :userId")
    suspend fun markAsRead(userId: String)

    /**
     * Increment unread count by 1 (called when a new message arrives
     * and the user is NOT currently viewing that chat).
     */
    @Query("UPDATE conversations SET unread_count = unread_count + 1 WHERE user_id = :userId")
    suspend fun incrementUnread(userId: String)

    /**
     * Delete a conversation (swipe-to-delete on home screen).
     */
    @Query("DELETE FROM conversations WHERE user_id = :userId")
    suspend fun delete(userId: String)
}
