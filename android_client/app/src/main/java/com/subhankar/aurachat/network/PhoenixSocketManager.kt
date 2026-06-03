package com.subhankar.aurachat.network

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Phoenix Channel WebSocket client — replaces Dart SocketClient + phoenix_socket.
 *
 * Since there is no mature "Phoenix Channels for Kotlin" library,
 * we implement the Phoenix Channel protocol directly over OkHttp WebSocket.
 * This gives us full control over reconnection, heartbeat, and event routing.
 *
 * Phoenix Channel Wire Protocol (JSON):
 *   [join_ref, ref, topic, event, payload]
 *
 * Events we handle:
 *   • "receive_message"  → incoming encrypted message
 *   • "receipt"           → delivery/read receipt for a single message
 *   • "read_cursor"       → bulk read receipt up to a sequence number
 *   • "typing_start/stop" → typing indicators
 *   • "presence_diff"     → online/offline status changes
 *   • "sync_complete"     → offline queue drain finished
 */
class PhoenixSocketManager {

    companion object {
        private const val TAG = "PhoenixSocket"
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val REPLY_TIMEOUT_MS = 10_000L
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)  // No read timeout for WebSocket
        .pingInterval(25, TimeUnit.SECONDS)      // OkHttp-level ping/pong
        .build()

    private var webSocket: WebSocket? = null
    private val refCounter = AtomicInteger(0)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Track the user ID we connected with
    private var connectedUserId: String = ""

    // Pending reply callbacks: ref → CompletableDeferred<JSONObject>
    private val pendingReplies = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    // Joined channel tracking
    private var chatJoinRef: String? = null
    private val groupJoinRefs = ConcurrentHashMap<String, String>()

    // ─── Public Event Flows (replace Dart StreamControllers) ───────

    private val _incomingMessages = MutableSharedFlow<Map<String, Any?>>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<Map<String, Any?>> = _incomingMessages

    private val _receiptEvents = MutableSharedFlow<Map<String, Any?>>(extraBufferCapacity = 64)
    val receiptEvents: SharedFlow<Map<String, Any?>> = _receiptEvents

    private val _typingEvents = MutableSharedFlow<Map<String, Any?>>(extraBufferCapacity = 64)
    val typingEvents: SharedFlow<Map<String, Any?>> = _typingEvents

    private val _presenceEvents = MutableSharedFlow<Map<String, Any?>>(extraBufferCapacity = 64)
    val presenceEvents: SharedFlow<Map<String, Any?>> = _presenceEvents

    private val _syncCompleteEvents = MutableSharedFlow<Map<String, Any?>>(extraBufferCapacity = 64)
    val syncCompleteEvents: SharedFlow<Map<String, Any?>> = _syncCompleteEvents

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    val isConnected: Boolean get() = _connectionState.value == ConnectionState.CONNECTED

    // ─── Connect ──────────────────────────────────────────────────

    fun connect(socketUrl: String, token: String, myUserId: String) {
        if (isConnected) return

        _connectionState.value = ConnectionState.CONNECTING
        val url = "$socketUrl?token=$token&vsn=2.0.0"

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                _connectionState.value = ConnectionState.CONNECTED
                connectedUserId = myUserId
                startHeartbeat()
                joinChannel("encrypted_chat:$myUserId")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                _connectionState.value = ConnectionState.DISCONNECTED
                stopHeartbeat()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                _connectionState.value = ConnectionState.DISCONNECTED
                stopHeartbeat()
            }
        })
    }

    // ─── Phoenix Channel Protocol ────────────────────────────────

    /**
     * Join a Phoenix channel.
     * Wire format: [joinRef, ref, topic, "phx_join", {}]
     */
    private fun joinChannel(topic: String): String {
        val joinRef = nextRef()
        val ref = nextRef()

        if (topic.startsWith("encrypted_chat:")) {
            chatJoinRef = joinRef
        } else if (topic.startsWith("group_chat:")) {
            groupJoinRefs[topic] = joinRef
        }

        sendRaw(joinRef, ref, topic, "phx_join", JSONObject())
        return joinRef
    }

    /**
     * Join a group channel (public API).
     */
    fun joinGroupChannel(groupId: String) {
        val topic = "group_chat:$groupId"
        if (groupJoinRefs.containsKey(topic)) return
        joinChannel(topic)
    }

    /**
     * Push an event on the chat channel and wait for a reply.
     * This is the Kotlin equivalent of Dart's `_chatChannel!.push(event, payload).future`.
     */
    suspend fun pushAndWait(event: String, payload: JSONObject, topic: String? = null): JSONObject {
        val actualTopic = topic ?: "encrypted_chat:$connectedUserId"
        val joinRef = if (actualTopic.startsWith("group_chat:")) {
            groupJoinRefs[actualTopic] ?: throw IllegalStateException("Not joined to $actualTopic")
        } else {
            chatJoinRef ?: throw IllegalStateException("Chat channel not joined")
        }

        val ref = nextRef()
        val deferred = CompletableDeferred<JSONObject>()
        pendingReplies[ref] = deferred

        sendRaw(joinRef, ref, actualTopic, event, payload)

        return withTimeout(REPLY_TIMEOUT_MS) {
            deferred.await()
        }
    }

    /**
     * Push an event without waiting for a reply (fire-and-forget).
     * Used for receipts, typing indicators, etc.
     */
    fun pushFireAndForget(event: String, payload: JSONObject, topic: String? = null) {
        val actualTopic = topic ?: "encrypted_chat:$connectedUserId"
        val joinRef = if (actualTopic.startsWith("group_chat:")) {
            groupJoinRefs[actualTopic]
        } else {
            chatJoinRef
        } ?: return

        val ref = nextRef()
        sendRaw(joinRef, ref, actualTopic, event, payload)
    }

    // ─── Convenience methods (mirror Dart SocketClient API) ──────

    /**
     * Send an encrypted message and wait for ACK with server_ts and seq.
     * Replaces: Dart socketClient.sendMessage()
     */
    suspend fun sendMessage(recipientId: String, msgId: String, encryptedPayload: Map<String, Any?>): Map<String, Any?> {
        val json = JSONObject().apply {
            put("recipient_user_id", recipientId)
            put("msg_id", msgId)
            encryptedPayload.forEach { (k, v) -> put(k, v) }
        }
        val reply = pushAndWait("send_message", json)
        return reply.toMap()
    }

    /**
     * Send an encrypted group message.
     * Replaces: Dart socketClient.sendGroupMessage()
     */
    suspend fun sendGroupMessage(groupId: String, msgId: String, encryptedPayload: Map<String, Any?>): Map<String, Any?> {
        val json = JSONObject().apply {
            put("msg_id", msgId)
            encryptedPayload.forEach { (k, v) -> put(k, v) }
        }
        val reply = pushAndWait("send_group_message", json, topic = "group_chat:$groupId")
        return reply.toMap()
    }

    fun sendDeliveryReceipt(senderId: String, msgId: String) {
        pushFireAndForget("delivery_receipt", JSONObject().apply {
            put("msg_id", msgId)
            put("sender_user_id", senderId)
        })
    }

    fun sendReadReceipt(senderId: String, msgId: String) {
        pushFireAndForget("read_receipt", JSONObject().apply {
            put("msg_id", msgId)
            put("sender_user_id", senderId)
        })
    }

    fun sendReadCursor(senderId: String, lastReadSeq: Int) {
        pushFireAndForget("read_cursor", JSONObject().apply {
            put("sender_id", senderId)
            put("last_read_seq", lastReadSeq)
        })
    }

    fun sendTyping(recipientId: String) {
        pushFireAndForget("typing", JSONObject().apply {
            put("recipient_id", recipientId)
        })
    }

    fun sendStopTyping(recipientId: String) {
        pushFireAndForget("stop_typing", JSONObject().apply {
            put("recipient_id", recipientId)
        })
    }

    fun sendSyncAck() {
        pushFireAndForget("sync_ack", JSONObject())
    }

    // ─── Message Handling ────────────────────────────────────────

    private fun handleMessage(text: String) {
        try {
            val arr = JSONArray(text)
            // Phoenix v2 wire format: [joinRef, ref, topic, event, payload]
            val joinRef = arr.optString(0)
            val ref = arr.optString(1)
            val topic = arr.getString(2)
            val event = arr.getString(3)
            val payload = arr.optJSONObject(4) ?: JSONObject()

            when (event) {
                "phx_reply" -> {
                    // Reply to a push we sent
                    pendingReplies.remove(ref)?.let { deferred ->
                        val response = payload.optJSONObject("response") ?: JSONObject()
                        val status = payload.optString("status")
                        if (status == "ok") {
                            deferred.complete(response)
                        } else {
                            deferred.completeExceptionally(
                                Exception("Phoenix reply error: $status — $response")
                            )
                        }
                    }
                }
                "phx_error" -> {
                    Log.e(TAG, "Channel error on $topic: $payload")
                }
                "phx_close" -> {
                    Log.d(TAG, "Channel closed: $topic")
                }
                "receive_message" -> {
                    scope.launch { _incomingMessages.emit(payload.toMap()) }
                }
                "receive_group_message" -> {
                    val map = payload.toMap().toMutableMap()
                    map["type"] = "group_message"
                    scope.launch { _incomingMessages.emit(map) }
                }
                "receipt" -> {
                    scope.launch { _receiptEvents.emit(payload.toMap()) }
                }
                "read_cursor" -> {
                    val map = payload.toMap().toMutableMap()
                    map["isCursor"] = true
                    scope.launch { _receiptEvents.emit(map) }
                }
                "typing_start", "typing_stop" -> {
                    val map = payload.toMap().toMutableMap()
                    map["event"] = event
                    scope.launch { _typingEvents.emit(map) }
                }
                "presence_diff" -> {
                    val map = payload.toMap().toMutableMap()
                    map["event"] = event
                    scope.launch { _presenceEvents.emit(map) }
                }
                "sync_complete" -> {
                    scope.launch { _syncCompleteEvents.emit(payload.toMap()) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message: $text", e)
        }
    }

    // ─── Heartbeat ───────────────────────────────────────────────

    private var heartbeatJob: Job? = null

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                sendRaw(null, nextRef(), "phoenix", "heartbeat", JSONObject())
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    // ─── Low-Level Send ──────────────────────────────────────────

    private fun sendRaw(joinRef: String?, ref: String, topic: String, event: String, payload: JSONObject) {
        val arr = JSONArray().apply {
            put(joinRef)
            put(ref)
            put(topic)
            put(event)
            put(payload)
        }
        val text = arr.toString()
        Log.d(TAG, ">>> $text")
        webSocket?.send(text)
    }

    private fun nextRef(): String = refCounter.incrementAndGet().toString()

    // ─── Disconnect ──────────────────────────────────────────────

    fun disconnect() {
        stopHeartbeat()
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        chatJoinRef = null
        groupJoinRefs.clear()
        pendingReplies.values.forEach {
            it.completeExceptionally(Exception("Disconnected"))
        }
        pendingReplies.clear()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }
}

// ─── Helper Extensions ───────────────────────────────────────────

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

/** Convert a JSONObject to a Kotlin Map */
fun JSONObject.toMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    val keys = this.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val value = this.get(key)
        map[key] = when (value) {
            is JSONObject -> value.toMap()
            is JSONArray -> value.toList()
            JSONObject.NULL -> null
            else -> value
        }
    }
    return map
}

/** Convert a JSONArray to a Kotlin List */
fun JSONArray.toList(): List<Any?> {
    val list = mutableListOf<Any?>()
    for (i in 0 until this.length()) {
        val value = this.get(i)
        list.add(when (value) {
            is JSONObject -> value.toMap()
            is JSONArray -> value.toList()
            JSONObject.NULL -> null
            else -> value
        })
    }
    return list
}
