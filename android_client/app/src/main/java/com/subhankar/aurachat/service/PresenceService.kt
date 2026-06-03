package com.subhankar.aurachat.service

import android.util.Log
import com.subhankar.aurachat.network.ApiClient
import com.subhankar.aurachat.network.PhoenixSocketManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Presence Service — replaces Dart presence_service.dart.
 *
 * Tracks online/offline status of contacts via:
 *   1. Real-time Phoenix Presence diffs (joins/leaves)
 *   2. REST API fallback for last_seen_at timestamp
 *   3. In-memory cache for instant UI reads
 */
@Singleton
class PresenceService @Inject constructor(
    private val socketManager: PhoenixSocketManager,
    private val apiClient: ApiClient
) {
    companion object {
        private const val TAG = "PresenceService"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var collectJob: Job? = null

    private val _presenceState = MutableSharedFlow<PresenceEvent>(extraBufferCapacity = 64)
    val presenceState: SharedFlow<PresenceEvent> = _presenceState

    // In-memory cache
    private val presenceCache = mutableMapOf<String, PresenceEvent>()

    fun start() {
        collectJob?.cancel()
        collectJob = scope.launch {
            socketManager.presenceEvents.collect { payload ->
                val joins = payload["joins"] as? Map<*, *>
                val leaves = payload["leaves"] as? Map<*, *>

                joins?.keys?.forEach { key ->
                    val userId = key as? String ?: return@forEach
                    val event = PresenceEvent(userId, online = true, lastSeenAt = null)
                    presenceCache[userId] = event
                    _presenceState.emit(event)
                }

                leaves?.keys?.forEach { key ->
                    val userId = key as? String ?: return@forEach
                    // Delay 1s to let server write disconnect timestamp (matching Dart)
                    scope.launch {
                        delay(1_000)
                        checkStatusFallback(userId)
                    }
                }
            }
        }
    }

    private suspend fun checkStatusFallback(userId: String) {
        try {
            val data = apiClient.fetchUserStatus(userId)
            val event = PresenceEvent(
                userId = userId,
                online = data["online"] == true,
                lastSeenAt = data["last_seen_at"] as? String
            )
            presenceCache[userId] = event
            _presenceState.emit(event)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch status for $userId: ${e.message}")
            val event = PresenceEvent(userId, online = false, lastSeenAt = null)
            presenceCache[userId] = event
            _presenceState.emit(event)
        }
    }

    /**
     * Get cached presence, with background refresh if offline.
     * Replaces: Dart presenceService.getPresence()
     */
    suspend fun getPresence(userId: String): PresenceEvent {
        val cached = presenceCache[userId]
        if (cached != null) {
            if (!cached.online) {
                // Kick off async refresh
                scope.launch { checkStatusFallback(userId) }
            }
            return cached
        }
        // First fetch
        checkStatusFallback(userId)
        return presenceCache[userId] ?: PresenceEvent(userId, false, null)
    }

    fun stop() { collectJob?.cancel() }
    fun dispose() { stop(); scope.cancel() }
}

data class PresenceEvent(
    val userId: String,
    val online: Boolean,
    val lastSeenAt: String?
)
