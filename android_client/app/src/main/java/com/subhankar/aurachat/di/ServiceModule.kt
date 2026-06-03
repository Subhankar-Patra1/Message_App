package com.subhankar.aurachat.di

import com.subhankar.aurachat.crypto.CryptoEngine
import com.subhankar.aurachat.data.local.SessionManager
import com.subhankar.aurachat.data.local.dao.*
import com.subhankar.aurachat.network.ApiClient
import com.subhankar.aurachat.network.ConnectionManager
import com.subhankar.aurachat.network.PhoenixSocketManager
import com.subhankar.aurachat.service.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for all service layer dependencies.
 * Provides singleton instances of the message delivery services.
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun providePendingMessageQueue(
        pendingDao: PendingMessageDao,
        messageDao: MessageDao,
        cryptoEngine: CryptoEngine,
        socketManager: PhoenixSocketManager,
        apiClient: ApiClient
    ): PendingMessageQueue {
        return PendingMessageQueue(pendingDao, messageDao, cryptoEngine, socketManager, apiClient)
    }

    @Provides
    @Singleton
    fun provideMessageSyncService(
        socketManager: PhoenixSocketManager,
        cryptoEngine: CryptoEngine,
        messageDao: MessageDao,
        conversationDao: ConversationDao,
        contactDao: ContactDao
    ): MessageSyncService {
        return MessageSyncService(socketManager, cryptoEngine, messageDao, conversationDao, contactDao)
    }

    @Provides
    @Singleton
    fun provideReceiptService(
        socketManager: PhoenixSocketManager,
        messageDao: MessageDao,
        conversationDao: ConversationDao
    ): ReceiptService {
        return ReceiptService(socketManager, messageDao, conversationDao)
    }

    @Provides
    @Singleton
    fun provideTypingService(
        socketManager: PhoenixSocketManager
    ): TypingService {
        return TypingService(socketManager)
    }

    @Provides
    @Singleton
    fun providePresenceService(
        socketManager: PhoenixSocketManager,
        apiClient: ApiClient
    ): PresenceService {
        return PresenceService(socketManager, apiClient)
    }

    @Provides
    @Singleton
    fun provideChatRepository(
        cryptoEngine: CryptoEngine,
        apiClient: ApiClient,
        socketManager: PhoenixSocketManager,
        connectionManager: ConnectionManager,
        pendingQueue: PendingMessageQueue,
        syncService: MessageSyncService,
        receiptService: ReceiptService,
        typingService: TypingService,
        presenceService: PresenceService,
        sessionManager: SessionManager,
        messageDao: MessageDao,
        conversationDao: ConversationDao,
        contactDao: ContactDao
    ): ChatRepository {
        return ChatRepository(
            cryptoEngine, apiClient, socketManager, connectionManager,
            pendingQueue, syncService, receiptService, typingService,
            presenceService, sessionManager, messageDao, conversationDao, contactDao
        )
    }
}
