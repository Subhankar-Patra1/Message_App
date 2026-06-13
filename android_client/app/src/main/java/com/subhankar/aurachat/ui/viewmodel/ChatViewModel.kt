package com.subhankar.aurachat.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subhankar.aurachat.data.local.dao.MessageDao
import com.subhankar.aurachat.data.local.entity.MessageEntity
import com.subhankar.aurachat.service.ChatRepository
import com.subhankar.aurachat.service.ReceiptService
import com.subhankar.aurachat.service.TypingService
import com.subhankar.aurachat.service.PresenceService
import com.subhankar.aurachat.service.PresenceEvent
import com.subhankar.aurachat.service.TypingEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ChatViewModel — connects ChatScreen to all messaging services.
 *
 * In Flutter, you had ~6 StreamSubscriptions manually managed in
 * initState/dispose. Here, the ViewModel handles lifecycle automatically.
 *
 * Wires:
 *   • Messages (from Room, auto-updating)
 *   • Receipt updates (delivery/read ticks)
 *   • Typing indicators
 *   • Presence (online/offline/last seen)
 *   • Send message (via ChatRepository)
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageDao: MessageDao,
    private val chatRepository: ChatRepository,
    private val receiptService: ReceiptService,
    private val typingService: TypingService,
    private val presenceService: PresenceService
) : ViewModel() {

    val recipientId: String = savedStateHandle["recipientId"] ?: ""

    // Messages — auto-updating from Room database
    val messages: StateFlow<List<MessageEntity>> =
        messageDao.getMessagesForChat(recipientId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    // Typing state for this specific recipient
    val isTyping: StateFlow<Boolean> =
        typingService.typingState
            .filter { it.userId == recipientId }
            .map { it.isTyping }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // Presence state
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline

    private val _lastSeenAt = MutableStateFlow<String?>(null)
    val lastSeenAt: StateFlow<String?> = _lastSeenAt

    // Input text
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText

    init {
        // Observe presence for this recipient
        viewModelScope.launch {
            presenceService.presenceState
                .filter { it.userId == recipientId }
                .collect { event ->
                    _isOnline.value = event.online
                    _lastSeenAt.value = event.lastSeenAt
                }
        }

        // Initial presence fetch
        viewModelScope.launch {
            val presence = presenceService.getPresence(recipientId)
            _isOnline.value = presence.online
            _lastSeenAt.value = presence.lastSeenAt
        }
    }

    fun updateInput(text: String) {
        _inputText.value = text
        if (text.isNotEmpty()) {
            typingService.sendTyping(recipientId)
        }
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return
        _inputText.value = ""

        viewModelScope.launch {
            chatRepository.sendMessage(recipientId, text)
        }
    }

    fun sendReadReceipt(msgId: String, seq: Int) {
        receiptService.sendReadReceipt(recipientId, msgId)
        receiptService.sendReadCursor(recipientId, seq)
    }

    fun sendMediaMessage(uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val name = getFileName(context, uri)
            val size = getFileSize(context, uri)
            val mime = context.contentResolver.getType(uri) ?: ""
            val text = "aura://file?uri=${android.net.Uri.encode(uri.toString())}&name=${android.net.Uri.encode(name)}&size=$size&mime=${android.net.Uri.encode(mime)}"
            chatRepository.sendMessage(recipientId, text)
        }
    }

    private fun getFileName(context: android.content.Context, uri: android.net.Uri): String {
        var name = "Unknown File"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    name = cursor.getString(nameIndex) ?: name
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return name
    }

    private fun getFileSize(context: android.content.Context, uri: android.net.Uri): Long {
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex != -1) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return size
    }
}

