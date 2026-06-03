package com.subhankar.aurachat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached contact profile — replaces Dart _contactsBoxName.
 * Used by ProfileResolver to avoid redundant API calls.
 */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "avatar_url")
    val avatarUrl: String? = null,

    /** ISO-8601 timestamp of last profile fetch */
    @ColumnInfo(name = "last_updated")
    val lastUpdated: String
)
