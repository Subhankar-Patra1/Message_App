package com.subhankar.aurachat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Core message entity — mirrors the Dart DatabaseService.saveMessage() schema.
 *
 * Indexes:
 *   • (chatId, seq)  — for ordered chat loading (like your Dart sort-by-seq logic)
 *   • (msgId)        — for O(1) dedup checks (replaces Dart messageExists())
 *   • (chatId, status) — for bulk status updates (bulkUpdateMessagesRead)
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["chat_id", "seq"]),
        Index(value = ["msg_id"], unique = true),
        Index(value = ["chat_id", "status"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Unique message ID (UUID v4), matches msg['msg_id'] in Dart */
    @ColumnInfo(name = "msg_id")
    val msgId: String,

    /** The "other" user in this conversation.
     *  For sent messages: recipientId. For received: senderId.
     *  This replaces the Dart _messagesBoxPrefix pattern. */
    @ColumnInfo(name = "chat_id")
    val chatId: String,

    /** Who actually sent this message */
    @ColumnInfo(name = "sender_id")
    val senderId: String,

    /** Decrypted plaintext content */
    @ColumnInfo(name = "text")
    val text: String,

    /** ISO-8601 timestamp — updated to server_ts upon ACK (like your Dart logic) */
    @ColumnInfo(name = "timestamp")
    val timestamp: String,

    /** true = I sent this, false = I received this */
    @ColumnInfo(name = "is_me")
    val isMe: Boolean,

    /** Server-assigned sequence number for ordering & read cursors */
    @ColumnInfo(name = "seq")
    val seq: Int? = null,

    /** Server timestamp in millis, set on ACK */
    @ColumnInfo(name = "server_ts")
    val serverTs: Long? = null,

    /** Message delivery state: pending → encrypting → sent → acked → delivered → read
     *  Maps directly to your Dart MessageState class */
    @ColumnInfo(name = "status")
    val status: String = MessageStatus.PENDING
)

/**
 * Kotlin equivalent of your Dart MessageState class.
 * Preserves the exact same state machine and weight system.
 */
object MessageStatus {
    const val PENDING = "pending"
    const val ENCRYPTING = "encrypting"
    const val SENT = "sent"
    const val ACKED = "acked"
    const val RETRYING = "retrying"
    const val DELIVERED = "delivered"
    const val READ = "read"
    const val FAILED = "failed"

    /** Valid transitions — exact copy of your Dart allowedTransitions map */
    private val allowedTransitions = mapOf(
        PENDING to listOf(ENCRYPTING),
        ENCRYPTING to listOf(SENT, FAILED),
        SENT to listOf(ACKED, RETRYING),
        RETRYING to listOf(SENT, FAILED),
        ACKED to listOf(DELIVERED, READ),
        DELIVERED to listOf(READ),
        READ to emptyList(),
        FAILED to listOf(PENDING)
    )

    fun isValidTransition(current: String, next: String): Boolean {
        if (current == next) return true
        return allowedTransitions[current]?.contains(next) == true
    }

    /** Numeric weight for visual sorting — matches your Dart getWeight() */
    fun getWeight(status: String): Int = when (status) {
        PENDING -> 0
        ENCRYPTING -> 1
        RETRYING -> 2
        SENT -> 3
        ACKED -> 4
        DELIVERED -> 5
        READ -> 6
        FAILED -> -1
        else -> 0
    }
}
