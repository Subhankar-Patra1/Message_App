package com.subhankar.aurachat.network

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.LinkedList
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * ConnectionManager — replaces Dart connection_manager.dart.
 *
 * Handles:
 *   • Automatic reconnection with exponential backoff + jitter
 *   • "Reconnect storm" detection (>5 attempts in 60s → forced 60s cooldown)
 *   • App lifecycle awareness (reconnect when app returns to foreground)
 *
 * In Dart, this used WidgetsBindingObserver. In Android, the Activity
 * will call onResume()/onPause() which triggers reconnection.
 */
class ConnectionManager(
    private val socketManager: PhoenixSocketManager
) {
    companion object {
        private const val TAG = "ConnectionManager"
        private const val MAX_BACKOFF_SECONDS = 30
        private const val STORM_THRESHOLD = 5
        private const val STORM_WINDOW_SECONDS = 60
        private const val STORM_COOLDOWN_SECONDS = 60L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private val recentReconnects = LinkedList<Long>()  // timestamps in millis
    private var isDisposed = false

    // Store connection params for reconnection
    private var socketUrl: String = ""
    private var token: String = ""
    private var userId: String = ""

    /**
     * Initial connection. Stores params for future reconnections.
     */
    fun connect(socketUrl: String, token: String, userId: String) {
        if (isDisposed) return
        this.socketUrl = socketUrl
        this.token = token
        this.userId = userId

        // Listen to connection state for auto-reconnect
        scope.launch {
            socketManager.connectionState.collect { state ->
                when (state) {
                    ConnectionState.CONNECTED -> {
                        reconnectAttempts = 0
                        recentReconnects.clear()
                        Log.d(TAG, "Connected — backoff reset")
                    }
                    ConnectionState.DISCONNECTED -> {
                        // Auto-reconnect unless we intentionally disconnected
                        if (!isDisposed) {
                            Log.d(TAG, "Disconnected — scheduling reconnect")
                            scheduleReconnect()
                        }
                    }
                    ConnectionState.CONNECTING -> { /* no-op */ }
                }
            }
        }

        doConnect()
    }

    /**
     * Called when the app returns to the foreground.
     * Replaces: Dart didChangeAppLifecycleState(AppLifecycleState.resumed)
     */
    fun onAppResumed() {
        if (!socketManager.isConnected && !isDisposed) {
            reconnect()
        }
    }

    private fun reconnect() {
        if (socketManager.isConnected || isDisposed) return

        // Storm detection: if >5 reconnects in 60s, force 60s backoff
        val now = System.currentTimeMillis()
        recentReconnects.removeAll { now - it > STORM_WINDOW_SECONDS * 1000 }
        recentReconnects.add(now)

        if (recentReconnects.size > STORM_THRESHOLD) {
            Log.w(TAG, "Reconnect storm detected. Backing off for ${STORM_COOLDOWN_SECONDS}s.")
            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(STORM_COOLDOWN_SECONDS * 1000)
                doConnect()
            }
            return
        }

        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()

        // Exponential backoff: 1s, 2s, 4s, 8s, max 30s
        val delaySeconds = min(MAX_BACKOFF_SECONDS, 2.0.pow(reconnectAttempts).toInt())
        // Jitter: 0-500ms
        val jitterMs = Random.nextInt(500)

        reconnectAttempts++
        Log.d(TAG, "Reconnecting in ${delaySeconds}s (+${jitterMs}ms jitter), attempt #$reconnectAttempts")

        reconnectJob = scope.launch {
            delay(delaySeconds * 1000L + jitterMs)
            doConnect()
        }
    }

    private fun doConnect() {
        if (isDisposed || socketUrl.isEmpty()) return
        try {
            socketManager.connect(socketUrl, token, userId)
        } catch (e: Exception) {
            Log.e(TAG, "Connect failed: ${e.message}")
            scheduleReconnect()
        }
    }

    fun dispose() {
        isDisposed = true
        reconnectJob?.cancel()
        socketManager.disconnect()
        scope.cancel()
    }
}
