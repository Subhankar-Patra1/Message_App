package com.subhankar.aurachat

import android.app.Application
import com.vanniktech.emoji.EmojiManager
import com.vanniktech.emoji.ios.IosEmojiProvider
import dagger.hilt.android.HiltAndroidApp

/**
 * The Hilt application entry point.
 * This annotation tells Hilt to generate the dependency injection
 * container at compile time.
 */
@HiltAndroidApp
class AuraChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        EmojiManager.install(IosEmojiProvider())
    }
}
