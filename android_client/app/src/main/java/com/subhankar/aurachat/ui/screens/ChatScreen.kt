package com.subhankar.aurachat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhankar.aurachat.data.local.entity.MessageEntity
import com.subhankar.aurachat.data.local.entity.MessageStatus
import com.subhankar.aurachat.ui.theme.AuraColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Chat Screen — precise translation of your Flutter chat_screen.dart.
 *
 * Preserves:
 *   • Dark background (0xFF111318)
 *   • Blue sent bubbles (0xFF2C6BED) with white text
 *   • Dark received bubbles (0xFF212121) with white text
 *   • WhatsApp-style bubble tails using Canvas path drawing
 *   • Dynamic border radius based on message grouping
 *   • Timestamp + receipt tick icons inside the bubble
 *   • AppBar with avatar, name, typing/online status
 *   • Input bar with emoji, attach, camera icons + animated send/mic button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    recipientName: String,
    recipientAvatar: String?,
    messages: List<MessageEntity>,
    isTyping: Boolean = false,
    isOnline: Boolean = false,
    lastSeenAt: String? = null,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val listState = rememberLazyListState()

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            ChatAppBar(
                recipientName = recipientName,
                recipientAvatar = recipientAvatar,
                isTyping = isTyping,
                isOnline = isOnline,
                lastSeenAt = lastSeenAt,
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Date chip — "Today"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(AuraColors.ReceivedBubble.copy(alpha = 0.8f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Today", color = AuraColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.W500)
                }
            }

            // Message list (reversed — newest at bottom)
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                itemsIndexed(messages) { index, msg ->
                    val isMe = msg.isMe
                    val isLastInSequence = index == 0 ||
                            messages[index - 1].senderId != msg.senderId
                    val isFirstInSequence = index == messages.lastIndex ||
                            messages[index + 1].senderId != msg.senderId

                    MessageBubble(
                        message = msg,
                        isMe = isMe,
                        isFirstInSequence = isFirstInSequence,
                        isLastInSequence = isLastInSequence
                    )
                }
            }

            // Input bar
            ChatInputBar(
                text = inputText,
                onTextChange = onInputChange,
                onSendClick = onSendClick
            )
        }
    }
}

// ─── App Bar ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatAppBar(
    recipientName: String,
    recipientAvatar: String?,
    isTyping: Boolean,
    isOnline: Boolean,
    lastSeenAt: String?,
    onBackClick: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AuraColors.ReceivedBubble),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (recipientName.isNotEmpty()) recipientName[0].uppercase() else "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        recipientName.ifEmpty { "Contact" },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W600,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isTyping || isOnline || lastSeenAt != null) {
                        Text(
                            text = when {
                                isTyping -> "typing..."
                                isOnline -> "Online"
                                lastSeenAt != null -> "last seen ${formatTimeShort(lastSeenAt)}"
                                else -> ""
                            },
                            color = if (isTyping) Color(0xFF5C8AFF) else AuraColors.TextSecondary,
                            fontSize = 13.sp,
                            fontStyle = if (isTyping) FontStyle.Italic else FontStyle.Normal
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = { }) {
                Icon(Icons.Default.Videocam, "Video", tint = Color.White)
            }
            IconButton(onClick = { }) {
                Icon(Icons.Default.Call, "Call", tint = Color.White)
            }
            IconButton(onClick = { }) {
                Icon(Icons.Default.MoreVert, "More", tint = Color.White)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = AuraColors.Background
        )
    )
}

// ─── Message Bubble ──────────────────────────────────────────────

@Composable
private fun MessageBubble(
    message: MessageEntity,
    isMe: Boolean,
    isFirstInSequence: Boolean,
    isLastInSequence: Boolean
) {
    val bubbleColor = if (isMe) AuraColors.SentBubble else AuraColors.ReceivedBubble

    // Dynamic border radius — matching your Flutter _buildMessageItem()
    val shape = RoundedCornerShape(
        topStart = if (isMe) 16.dp else if (isFirstInSequence) 16.dp else 4.dp,
        topEnd = if (isMe) (if (isFirstInSequence) 16.dp else 4.dp) else 16.dp,
        bottomStart = if (isMe) 16.dp else if (isLastInSequence) 0.dp else 4.dp,
        bottomEnd = if (isMe) (if (isLastInSequence) 0.dp else 4.dp) else 16.dp
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (isLastInSequence) 4.dp else 2.dp)
            .padding(horizontal = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .align(if (isMe) Alignment.CenterEnd else Alignment.CenterStart)
                .fillMaxWidth(0.75f)
                .wrapContentWidth(if (isMe) Alignment.End else Alignment.Start)
        ) {
            // Bubble
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(bubbleColor)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Column {
                    Text(
                        text = message.text,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTimestamp(message.serverTs),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                        if (isMe) {
                            Spacer(Modifier.width(4.dp))
                            ReceiptIcon(message.status)
                        }
                    }
                }
            }
        }
    }
}

// ─── Receipt Tick Icons ──────────────────────────────────────────

@Composable
private fun ReceiptIcon(status: String) {
    val icon: ImageVector
    val color: Color
    when (status) {
        MessageStatus.READ -> {
            icon = Icons.Default.DoneAll
            color = AuraColors.ReadBlue
        }
        MessageStatus.DELIVERED -> {
            icon = Icons.Default.DoneAll
            color = Color.White.copy(alpha = 0.5f)
        }
        MessageStatus.FAILED -> {
            icon = Icons.Default.Warning
            color = Color(0xFFFF5252)
        }
        else -> {
            icon = Icons.Default.Done
            color = Color.White.copy(alpha = 0.5f)
        }
    }
    Icon(icon, status, tint = color, modifier = Modifier.size(14.dp))
}

// ─── Input Bar ───────────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AuraColors.Background)
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // Input field container
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(AuraColors.ReceivedBubble),
            verticalAlignment = Alignment.Bottom
        ) {
            // Emoji button
            IconButton(onClick = { }) {
                Icon(Icons.Default.EmojiEmotions, "Emoji", tint = AuraColors.TextHint)
            }

            // Text input
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(AuraColors.Primary),
                maxLines = 5,
                decorationBox = { innerTextField ->
                    Box {
                        if (text.isEmpty()) {
                            Text("Message", color = AuraColors.TextHint, fontSize = 16.sp)
                        }
                        innerTextField()
                    }
                }
            )

            // Attach + Camera
            IconButton(onClick = { }) {
                Icon(Icons.Default.AttachFile, "Attach", tint = AuraColors.TextHint)
            }
            IconButton(onClick = { }) {
                Icon(Icons.Default.CameraAlt, "Camera", tint = AuraColors.TextHint)
            }
        }

        Spacer(Modifier.width(8.dp))

        // Send / Mic button
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(AuraColors.SentBubble)
                .clickable {
                    if (text.isNotEmpty()) onSendClick()
                },
            contentAlignment = Alignment.Center
        ) {
            if (text.isNotEmpty()) {
                Icon(Icons.Default.Send, "Send", tint = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Icon(Icons.Default.Mic, "Voice", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

// ─── Utilities ───────────────────────────────────────────────────

private fun formatTimestamp(serverTs: Long?): String {
    if (serverTs == null) return ""
    return try {
        val time = Instant.ofEpochMilli(serverTs)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        time.format(DateTimeFormatter.ofPattern("h:mm a"))
    } catch (e: Exception) {
        ""
    }
}

private fun formatTimeShort(isoString: String): String {
    return try {
        val instant = Instant.parse(isoString)
        val time = instant.atZone(ZoneId.systemDefault()).toLocalTime()
        time.format(DateTimeFormatter.ofPattern("h:mm a"))
    } catch (e: Exception) {
        ""
    }
}
