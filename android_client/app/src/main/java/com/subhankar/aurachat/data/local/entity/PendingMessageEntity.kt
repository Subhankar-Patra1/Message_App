package com.subhankar.aurachat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Pending outgoing message — replaces Dart _pendingMessagesBoxName.
 * Persists messages that haven't been ACKed by the server yet,
 * enabling the exponential backoff retry logic from PendingMessageQueue.
 */
@Entity(tableName = "pending_messages")
data class PendingMessageEntity(
    /** UUID v4 message ID */
    @PrimaryKey
    @ColumnInfo(name = "msg_id")
    val msgId: String,

    @ColumnInfo(name = "recipient_id")
    val recipientId: String,

    @ColumnInfo(name = "sender_id")
    val senderId: String,

    /** Original plaintext — encrypted on-the-fly before each send attempt */
    @ColumnInfo(name = "plaintext")
    val plaintext: String,

    /** Current queue state: pending, encrypting, retrying, failed */
    @ColumnInfo(name = "state")
    val state: String = MessageStatus.PENDING,

    /** Number of send attempts so far */
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,

    /** ISO-8601 timestamp when this message was first enqueued */
    @ColumnInfo(name = "created_at")
    val createdAt: String,

    /** ISO-8601 timestamp for the next retry attempt (exponential backoff) */
    @ColumnInfo(name = "next_retry_at")
    val nextRetryAt: String? = null
)
