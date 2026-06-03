package com.subhankar.aurachat.service

import com.subhankar.aurachat.network.PhoenixSocketManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Typing Service — replaces Dart typing_service.dart.
 *
 * Handles:
 *   • Incoming typing indicators with auto-clear after 7 seconds
 *   • Outbound typing throttle (max once every 3 seconds per recipient)
 */
@Singleton
class TypingService @Inject constructor(
    private val socketManager: PhoenixSocketManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var collectJob: Job? = null

    // Emits typing state changes for UI
    private val _typingState = MutableSharedFlow<TypingEvent>(extraBufferCapacity = 64)
    val typingState: SharedFlow<TypingEvent> = _typingState

    // Auto-clear timers per user
    private val activeTypers = mutableMapOf<String, Job>()

    // Outbound throttle tracking
    private val lastSentTyping = mutableMapOf<String, Long>()

    fun start() {
        collectJob?.cancel()
        collectJob = scope.launch {
            socketManager.typingEvents.collect { payload ->
                val event = payload["event"] as? String ?: return@collect
                val userId = payload["user_id"] as? String ?: return@collect

                when (event) {
                    "typing_start" -> {
                        _typingState.emit(TypingEvent(userId, true))

                        // Auto-clear after 7 seconds (matching Dart)
                        activeTypers[userId]?.cancel()
                        activeTypers[userId] = scope.launch {
                            delay(7_000)
                            _typingState.emit(TypingEvent(userId, false))
                            activeTypers.remove(userId)
                        }
                    }
                    "typing_stop" -> {
                        activeTypers[userId]?.cancel()
                        activeTypers.remove(userId)
                        _typingState.emit(TypingEvent(userId, false))
                    }
                }
            }
        }
    }

    /**
     * Send a typing indicator, throttled to once every 3 seconds.
     * Replaces: Dart typingService.sendTyping()
     */
    fun sendTyping(recipientId: String) {
        val now = System.currentTimeMillis()
        val lastSent = lastSentTyping[recipientId] ?: 0

        if (now - lastSent >= 3_000) {
            socketManager.sendTyping(recipientId)
            lastSentTyping[recipientId] = now
        }
    }

    fun sendStopTyping(recipientId: String) {
        socketManager.sendStopTyping(recipientId)
        lastSentTyping.remove(recipientId)
    }

    fun stop() { collectJob?.cancel() }
    fun dispose() {
        stop()
        activeTypers.values.forEach { it.cancel() }
        activeTypers.clear()
        scope.cancel()
    }
}

data class TypingEvent(val userId: String, val isTyping: Boolean)
