package com.subhankar.aurachat.data.local.dao

import androidx.room.*
import com.subhankar.aurachat.data.local.entity.ContactEntity

/**
 * Contact DAO — replaces Dart _contactsBoxName operations.
 */
@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts WHERE user_id = :userId LIMIT 1")
    suspend fun getContact(userId: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity)

    @Query("SELECT * FROM contacts")
    suspend fun getAllContacts(): List<ContactEntity>

    @Query("DELETE FROM contacts WHERE user_id = :userId")
    suspend fun delete(userId: String)
}
