package com.subhankar.aurachat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Conversation summary — replaces the Dart _conversationsBox.
 * Each row = one chat thread shown on the home screen.
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    /** The other user's ID — primary key, one conversation per user */
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,

    /** Display name of the contact */
    @ColumnInfo(name = "name")
    val name: String,

    /** Avatar URL (nullable) */
    @ColumnInfo(name = "avatar_url")
    val avatarUrl: String? = null,

    /** Preview of the last message sent/received */
    @ColumnInfo(name = "last_message")
    val lastMessage: String,

    /** ISO-8601 timestamp of the last message — used for sorting */
    @ColumnInfo(name = "last_message_time")
    val lastMessageTime: String,

    /** Unread message count badge */
    @ColumnInfo(name = "unread_count")
    val unreadCount: Int = 0
)
