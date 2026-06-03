package com.subhankar.aurachat.di

import com.subhankar.aurachat.data.local.SessionManager
import com.subhankar.aurachat.network.ApiClient
import com.subhankar.aurachat.network.AppConfig
import com.subhankar.aurachat.network.AuthService
import com.subhankar.aurachat.network.ConnectionManager
import com.subhankar.aurachat.network.PhoenixSocketManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for network dependencies.
 * Provides singleton instances of the socket manager, connection manager,
 * API client, and auth service.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun providePhoenixSocketManager(): PhoenixSocketManager {
        return PhoenixSocketManager()
    }

    @Provides
    @Singleton
    fun provideConnectionManager(socketManager: PhoenixSocketManager): ConnectionManager {
        return ConnectionManager(socketManager)
    }

    @Provides
    @Singleton
    fun provideAuthService(sessionManager: SessionManager): AuthService {
        return AuthService(sessionManager)
    }

    @Provides
    @Singleton
    fun provideApiClient(sessionManager: SessionManager, authService: AuthService): ApiClient {
        return ApiClient(
            baseUrl = AppConfig.apiBaseUrl,
            tokenProvider = { sessionManager.getToken() ?: "" },
            authService = authService
        )
    }
}
