package com.subhankar.aurachat.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhankar.aurachat.data.local.entity.ConversationEntity
import com.subhankar.aurachat.ui.theme.AuraColors
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * Home Screen — exact translation of your Flutter home_screen.dart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    conversations: List<ConversationEntity>,
    profilePhotoPath: String?,
    onChatClick: (ConversationEntity) -> Unit,
    onNewChatClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize().zIndex(1f),
        containerColor = AuraColors.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Messages",
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.W600,
                        fontFamily = RobotoFontFamily,
                        style = LocalTextStyle.current.copy(
                            fontFamily = RobotoFontFamily
                        ),
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
                    containerColor = AuraColors.AppBarBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewChatClick,
                containerColor = AuraColors.LightBlue,
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                modifier = Modifier
                    .padding(bottom = 8.dp, end = 12.dp)
                    .size(48.dp)
            ) {
                Icon(
                    painter = painterResource(id = com.subhankar.aurachat.R.drawable.ic_new_message),
                    contentDescription = "New Message",
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 6.dp, start = 12.dp, end = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                BottomNavBar(
                    selectedIndex = selectedTab,
                    totalUnreadCount = conversations.sumOf { it.unreadCount },
                    profilePhotoPath = profilePhotoPath,
                    onTabSelected = { index ->
                        if (index == 3) onProfileClick()
                        else selectedTab = index
                    }
                )
            }
        }
    ) { paddingValues ->
        if (conversations.isEmpty()) {
            // Empty state — matching Flutter
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding(), bottom = 90.dp),
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = 100.dp
                )
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    var isClickHighlighted by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    val showHighlight = isPressed || isClickHighlighted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (showHighlight) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    isClickHighlighted = true
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(20)
                        isClickHighlighted = false
                    }
                    onClick()
                }
            )
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
    totalUnreadCount: Int,
    profilePhotoPath: String?,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth()
            .height(58.dp),
        shape = CircleShape,
        color = Color(0xFF141518).copy(alpha = 0.9f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(
                modifier = Modifier.weight(1f),
                index = 0,
                selectedIndex = selectedIndex,
                label = "Chats",
                badgeCount = totalUnreadCount,
                onTap = onTabSelected
            ) { tint ->
                Icon(
                    painter = painterResource(id = com.subhankar.aurachat.R.drawable.chatbubbles),
                    contentDescription = "Chats",
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
            NavItem(
                modifier = Modifier.weight(1f),
                index = 1,
                selectedIndex = selectedIndex,
                label = "Moments",
                badgeCount = 0,
                onTap = onTabSelected
            ) { tint ->
                val momentsIcon = if (selectedIndex == 1) {
                    com.subhankar.aurachat.R.drawable.ic_moments_filled
                } else {
                    com.subhankar.aurachat.R.drawable.ic_moments_outlined
                }
                Icon(
                    painter = painterResource(id = momentsIcon),
                    contentDescription = "Moments",
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
            NavItem(
                modifier = Modifier.weight(1f),
                index = 2,
                selectedIndex = selectedIndex,
                label = "Calls",
                badgeCount = 0,
                onTap = onTabSelected
            ) { tint ->
                val callsIcon = if (selectedIndex == 2) {
                    com.subhankar.aurachat.R.drawable.ic_calls_filled
                } else {
                    com.subhankar.aurachat.R.drawable.ic_calls_outlined
                }
                Icon(
                    painter = painterResource(id = callsIcon),
                    contentDescription = "Calls",
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
            NavItem(
                modifier = Modifier.weight(1f),
                index = 3,
                selectedIndex = selectedIndex,
                label = "Profile",
                badgeCount = 0,
                onTap = onTabSelected
            ) { tint ->
                if (profilePhotoPath != null) {
                    AsyncImage(
                        model = profilePhotoPath,
                        contentDescription = "Profile",
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .border(
                                width = 1.dp,
                                color = if (selectedIndex == 3) Color(0xFF2C6BED) else Color.White.copy(alpha = 0.4f),
                                shape = CircleShape
                            ),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (selectedIndex == 3) Icons.Filled.Person else Icons.Outlined.Person,
                        contentDescription = "Profile",
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    modifier: Modifier = Modifier,
    index: Int,
    selectedIndex: Int,
    label: String,
    badgeCount: Int,
    onTap: (Int) -> Unit,
    icon: @Composable (tint: Color) -> Unit
) {
    val isSelected = index == selectedIndex
    val iconColor = if (isSelected) Color(0xFF2C6BED) else Color.White.copy(alpha = 0.5f)
    val textColor = if (isSelected) Color(0xFF2C6BED) else Color.White
    val bgColor = if (isSelected) Color(0xFF2C6BED).copy(alpha = 0.12f) else Color.Transparent
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .height(48.dp) 
            .clip(CircleShape)
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onTap(index) }
            .padding(top = 4.dp, bottom = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            icon(iconColor)
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 7.dp, y = (-2.5).dp)
                        .size(16.5.dp)
                        .background(Color(0xFF141518), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.5.dp)
                            .background(Color(0xFF2C6BED), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badgeCount.toString(),
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = RobotoFontFamily,
                            style = TextStyle(
                                platformStyle = PlatformTextStyle(
                                    includeFontPadding = false
                                ),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        )
                    }
                }
            }
        }
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RobotoFontFamily,
            style = LocalTextStyle.current.copy(
                fontFamily = RobotoFontFamily,
                platformStyle = PlatformTextStyle(
                    includeFontPadding = false
                )
            ),
            modifier = Modifier
                .wrapContentHeight(unbounded = true)
                .offset(y = (-3).dp)
        )
    }
}

private val RobotoFontFamily = FontFamily(
    Font(com.subhankar.aurachat.R.font.roboto_regular, FontWeight.Normal),
    Font(com.subhankar.aurachat.R.font.roboto_medium, FontWeight.Medium),
    Font(com.subhankar.aurachat.R.font.roboto_medium, FontWeight.SemiBold),
    Font(com.subhankar.aurachat.R.font.roboto_bold, FontWeight.Bold)
)

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
