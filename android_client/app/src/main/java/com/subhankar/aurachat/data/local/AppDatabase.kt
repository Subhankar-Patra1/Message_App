package com.subhankar.aurachat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.subhankar.aurachat.data.local.dao.*
import com.subhankar.aurachat.data.local.entity.*

/**
 * The AuraChat encrypted database.
 *
 * This is the Room equivalent of your entire Dart DatabaseService + Hive setup.
 * The actual encryption (SQLCipher) is configured in the Hilt DatabaseModule,
 * not here — this class only defines the schema.
 *
 * When you add a new table, bump the version number and add a migration.
 */
@Database(
    entities = [
        MessageEntity::class,
        ConversationEntity::class,
        ContactEntity::class,
        PendingMessageEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun contactDao(): ContactDao
    abstract fun pendingMessageDao(): PendingMessageDao
}
