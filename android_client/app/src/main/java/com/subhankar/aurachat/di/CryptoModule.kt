package com.subhankar.aurachat.di

import android.content.Context
import com.subhankar.aurachat.crypto.CryptoEngine
import com.subhankar.aurachat.crypto.SignalProtocolStoreImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for cryptography dependencies.
 *
 * Provides singleton instances of:
 *   • SignalProtocolStoreImpl — the encrypted key/session storage
 *   • CryptoEngine — the E2EE encrypt/decrypt engine
 */
@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {

    @Provides
    @Singleton
    fun provideSignalProtocolStore(
        @ApplicationContext context: Context
    ): SignalProtocolStoreImpl {
        return SignalProtocolStoreImpl(context)
    }

    @Provides
    @Singleton
    fun provideCryptoEngine(
        store: SignalProtocolStoreImpl
    ): CryptoEngine {
        return CryptoEngine(store)
    }
}
