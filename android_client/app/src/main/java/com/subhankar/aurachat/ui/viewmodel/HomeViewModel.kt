package com.subhankar.aurachat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subhankar.aurachat.data.local.dao.ConversationDao
import com.subhankar.aurachat.data.local.entity.ConversationEntity
import com.subhankar.aurachat.service.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * HomeViewModel — connects HomeScreen to Room database.
 *
 * In Flutter you used Hive.box('conversations').listenable() inside the widget.
 * In Android, the ViewModel exposes a Flow that auto-updates the UI when
 * the database changes. Much cleaner separation of concerns.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val conversationDao: ConversationDao,
    private val chatRepository: ChatRepository
) : ViewModel() {

    val conversations: StateFlow<List<ConversationEntity>> =
        conversationDao.getAllConversations()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    fun markAsRead(userId: String) {
        viewModelScope.launch {
            conversationDao.markAsRead(userId)
        }
    }

    fun initialize() {
        viewModelScope.launch {
            try {
                chatRepository.initialize()
            } catch (e: Exception) {
                // Handle initialization error
            }
        }
    }
}
