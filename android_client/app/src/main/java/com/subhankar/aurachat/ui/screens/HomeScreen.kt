package com.subhankar.aurachat.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhankar.aurachat.data.local.entity.ConversationEntity
import com.subhankar.aurachat.ui.theme.AuraColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Home Screen — exact translation of your Flutter home_screen.dart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    conversations: List<ConversationEntity>,
    onChatClick: (ConversationEntity) -> Unit,
    onNewChatClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Messages",
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.W600,
                        letterSpacing = (-0.5).sp
                    )
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Search, "Search", tint = AuraColors.TextSecondary, modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.MoreVert, "More", tint = AuraColors.TextSecondary, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AuraColors.Background.copy(alpha = 0.9f)
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewChatClick,
                containerColor = AuraColors.Primary,
                contentColor = AuraColors.Background,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.padding(bottom = 60.dp)
            ) {
                Icon(Icons.Default.Edit, "New Chat", modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "New Chat",
                    fontWeight = FontWeight.W600,
                    fontSize = 13.5.sp,
                    letterSpacing = 0.1.sp
                )
            }
        },
        bottomBar = {
            BottomNavBar(
                selectedIndex = selectedTab,
                onTabSelected = { index ->
                    if (index == 2) onProfileClick()
                    else selectedTab = index
                }
            )
        }
    ) { paddingValues ->
        if (conversations.isEmpty()) {
            // Empty state — matching Flutter
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Chat,
                        "No messages",
                        tint = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No messages yet",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                itemsIndexed(conversations) { index, chat ->
                    ChatTile(
                        conversation = chat,
                        avatarColor = AuraColors.AvatarColors[index % AuraColors.AvatarColors.size],
                        onClick = { onChatClick(chat) }
                    )
                    // Divider with left padding (matching Flutter's left: 76)
                    if (index < conversations.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 76.dp, end = 16.dp),
                            thickness = 1.dp,
                            color = AuraColors.Divider
                        )
                    }
                }
            }
        }
    }
}

// ─── Chat Tile ───────────────────────────────────────────────────

@Composable
private fun ChatTile(
    conversation: ConversationEntity,
    avatarColor: Color,
    onClick: () -> Unit
) {
    val hasUnread = conversation.unreadCount > 0
    val displayName = if (looksLikeUuid(conversation.name)) "Unknown" else conversation.name

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with unread dot
        Box {
            // Avatar circle
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(avatarColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (displayName.isNotEmpty()) displayName[0].uppercase() else "?",
                    color = avatarColor,
                    fontWeight = FontWeight.W600,
                    fontSize = 19.sp
                )
            }
            // Unread indicator dot
            if (hasUnread) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(AuraColors.Primary)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Name + message + time
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayName,
                    color = Color.White,
                    fontWeight = FontWeight.W600,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    letterSpacing = (-0.2).sp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatTime(conversation.lastMessageTime),
                    color = if (hasUnread) AuraColors.Primary else AuraColors.TextTertiary,
                    fontSize = 11.5.sp,
                    fontWeight = if (hasUnread) FontWeight.W600 else FontWeight.W400
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = conversation.lastMessage,
                color = if (hasUnread) Color.White.copy(alpha = 0.9f) else AuraColors.TextTertiary,
                fontSize = 13.5.sp,
                fontWeight = if (hasUnread) FontWeight.W500 else FontWeight.W400,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── Bottom Navigation Bar ───────────────────────────────────────

@Composable
private fun BottomNavBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    Column {
        HorizontalDivider(thickness = 1.dp, color = AuraColors.Divider)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .background(AuraColors.Background),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(0, selectedIndex, Icons.Outlined.Chat, Icons.Filled.Chat, "Chats", onTabSelected)
            NavItem(1, selectedIndex, Icons.Outlined.Phone, Icons.Filled.Phone, "Calls", onTabSelected)
            NavItem(2, selectedIndex, Icons.Outlined.Person, Icons.Filled.Person, "Profile", onTabSelected)
        }
    }
}

@Composable
private fun NavItem(
    index: Int,
    selectedIndex: Int,
    outlineIcon: ImageVector,
    filledIcon: ImageVector,
    label: String,
    onTap: (Int) -> Unit
) {
    val isSelected = index == selectedIndex
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) AuraColors.Primary else AuraColors.TextTertiary,
        animationSpec = tween(200), label = "navIconColor"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) AuraColors.Primary.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(200), label = "navBgColor"
    )

    Column(
        modifier = Modifier
            .clickable { onTap(index) }
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(bgColor)
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Icon(
                if (isSelected) filledIcon else outlineIcon,
                label,
                tint = iconColor,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = iconColor,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.W600 else FontWeight.W400
        )
        Spacer(Modifier.height(8.dp))
    }
}

// ─── Utilities ───────────────────────────────────────────────────

private fun looksLikeUuid(input: String): Boolean {
    return Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
        .matches(input)
}

private fun formatTime(isoString: String?): String {
    if (isoString.isNullOrEmpty()) return ""
    return try {
        val instant = Instant.parse(isoString)
        val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        val now = LocalDate.now()
        val diff = java.time.temporal.ChronoUnit.DAYS.between(date, now)

        when {
            diff == 0L -> {
                val time = instant.atZone(ZoneId.systemDefault()).toLocalTime()
                time.format(DateTimeFormatter.ofPattern("h:mm a"))
            }
            diff == 1L -> "Yesterday"
            diff < 7L -> date.format(DateTimeFormatter.ofPattern("EEE"))
            else -> date.format(DateTimeFormatter.ofPattern("dd/MM"))
        }
    } catch (e: Exception) {
        ""
    }
}
