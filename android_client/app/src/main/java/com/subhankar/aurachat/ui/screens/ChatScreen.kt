package com.subhankar.aurachat.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhankar.aurachat.data.local.entity.MessageEntity
import com.subhankar.aurachat.data.local.entity.MessageStatus
import com.subhankar.aurachat.ui.theme.AuraColors
import androidx.compose.ui.res.painterResource
import com.subhankar.aurachat.R
import androidx.compose.ui.graphics.toArgb
import com.subhankar.aurachat.ui.components.BackdropBlur
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.vanniktech.emoji.EmojiView
import com.vanniktech.emoji.EmojiTheming
import com.vanniktech.emoji.EmojiManager
import com.vanniktech.emoji.ios.IosEmojiProvider
import com.vanniktech.emoji.listeners.OnEmojiClickListener
import com.vanniktech.emoji.listeners.OnEmojiBackspaceClickListener
import androidx.compose.ui.platform.LocalView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import com.vanniktech.emoji.search.SearchEmojiManager
import com.vanniktech.emoji.search.NoSearchEmoji
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
    var isEmojiPickerVisible by remember { mutableStateOf(false) }
    var emojiSearchQuery by remember { mutableStateOf("") }
    var emojiSearchResults by remember { mutableStateOf<List<com.vanniktech.emoji.Emoji>>(emptyList()) }
    val searchEmojiManager = remember { SearchEmojiManager() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val composeView = LocalView.current

    Scaffold(
        modifier = Modifier.fillMaxSize().zIndex(2f),
        containerColor = AuraColors.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
        val density = LocalDensity.current
        var showEmojiSearchSheet by remember { mutableStateOf(false) }
        val bottomInset = if (showEmojiSearchSheet) {
            WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
        } else {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
        }
        val bottomInsetDp = with(density) { bottomInset.getBottom(density).toDp() }
        val emojiPickerHeight = 260.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Message list (reversed — newest at bottom)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(
                    start = 5.dp,
                    end = 5.dp,
                    top = 48.dp, // room for floating Date chip
                    bottom = bottomInsetDp + (if (isEmojiPickerVisible) emojiPickerHeight else 0.dp) + 50.dp
                )
            ) {
                itemsIndexed(messages) { index, msg ->
                    val isMe = msg.isMe
                    val currentTs = msg.serverTs ?: try {
                        Instant.parse(msg.timestamp).toEpochMilli()
                    } catch (e: Exception) {
                        0L
                    }

                    val isLastInSequence = if (index == 0) {
                        true
                    } else {
                        val nextMsg = messages[index - 1]
                        val nextTs = nextMsg.serverTs ?: try {
                            Instant.parse(nextMsg.timestamp).toEpochMilli()
                        } catch (e: Exception) {
                            0L
                        }
                        nextMsg.senderId != msg.senderId || java.lang.Math.abs(nextTs - currentTs) > 5 * 60 * 1000L
                    }

                    val isFirstInSequence = if (index == messages.lastIndex) {
                        true
                    } else {
                        val prevMsg = messages[index + 1]
                        val prevTs = prevMsg.serverTs ?: try {
                            Instant.parse(prevMsg.timestamp).toEpochMilli()
                        } catch (e: Exception) {
                            0L
                        }
                        prevMsg.senderId != msg.senderId || java.lang.Math.abs(currentTs - prevTs) > 5 * 60 * 1000L
                    }

                    MessageBubble(
                        message = msg,
                        isMe = isMe,
                        isFirstInSequence = isFirstInSequence,
                        isLastInSequence = isLastInSequence
                    )
                }
            }

            // Date chip — "Today" floating at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
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

            // Outer wrapper for the bottom input area and backgrounds
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                // Solid background filling the navigation bar or keyboard space
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .windowInsetsBottomHeight(
                            if (showEmojiSearchSheet) WindowInsets.navigationBars else WindowInsets.safeDrawing
                        )
                        .background(AuraColors.Background)
                )

                // Input bar and picker container, pushed up above the bottom safe drawing inset
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(
                            if (showEmojiSearchSheet) {
                                WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                            } else {
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                            }
                        )
                ) {
                    // ChatInputBar container with a vertical gradient shadow behind it
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    0.0f to Color.Transparent,
                                    0.75f to Color.Transparent,
                                    0.9f to AuraColors.Background,
                                    1.0f to AuraColors.Background
                                )
                            )
                    ) {
                        ChatInputBar(
                            text = inputText,
                            onTextChange = onInputChange,
                            onSendClick = onSendClick,
                            onEmojiClick = {
                                if (isEmojiPickerVisible) {
                                    isEmojiPickerVisible = false
                                    emojiSearchQuery = ""
                                    emojiSearchResults = emptyList()
                                } else {
                                    isEmojiPickerVisible = true
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }
                            },
                            onFocus = {
                                isEmojiPickerVisible = false
                                emojiSearchQuery = ""
                                emojiSearchResults = emptyList()
                            }
                        )
                    }

                    // Emoji Picker sits underneath
                    if (isEmojiPickerVisible) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(emojiPickerHeight)
                                .background(AuraColors.Background)
                        ) {
                            // Search icon row at the top of picker
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .clickable { showEmojiSearchSheet = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search Emoji",
                                        tint = AuraColors.TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            AndroidView(
                                factory = { context ->
                                    val themedContext = ContextThemeWrapper(context, R.style.Theme_AuraChat)
                                    EmojiView(themedContext).apply {
                                        setBackgroundColor(AuraColors.Background.toArgb())
                                        try {
                                            EmojiManager.install(IosEmojiProvider())
                                        } catch (e: Exception) {
                                            // already installed
                                        }
                                        setUp(
                                            rootView = composeView,
                                            onEmojiClickListener = { emoji ->
                                                onInputChange(inputText + emoji.unicode)
                                            },
                                            onEmojiBackspaceClickListener = {
                                                onInputChange(deleteLastCodePoint(inputText))
                                            },
                                            editText = null,
                                            theming = EmojiTheming(
                                                backgroundColor = AuraColors.Background.toArgb(),
                                                primaryColor = AuraColors.TextSecondary.toArgb(),
                                                secondaryColor = AuraColors.Primary.toArgb(),
                                                dividerColor = AuraColors.Divider.toArgb(),
                                                textColor = AuraColors.TextPrimary.toArgb(),
                                                textSecondaryColor = AuraColors.TextSecondary.toArgb()
                                            ),
                                            searchEmoji = NoSearchEmoji
                                        )

                                        // Move category bar to the bottom by reordering children
                                        val viewsList = ArrayList<android.view.View>()
                                        for (i in 0 until childCount) {
                                            viewsList.add(getChildAt(i))
                                        }
                                        val viewPager = viewsList.find { 
                                            it is androidx.viewpager.widget.ViewPager || 
                                            it is androidx.viewpager2.widget.ViewPager2 || 
                                            it.javaClass.name.contains("ViewPager", ignoreCase = true) 
                                        }
                                        if (viewPager != null) {
                                            val remainingViews = viewsList.filter { it != viewPager }
                                            removeAllViews()
                                            
                                            val vpParams = viewPager.layoutParams as? android.widget.LinearLayout.LayoutParams
                                                ?: android.widget.LinearLayout.LayoutParams(
                                                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                                    0
                                                )
                                            vpParams.height = 0
                                            vpParams.weight = 1f
                                            viewPager.layoutParams = vpParams
                                            addView(viewPager, vpParams)
                                            
                                            for (v in remainingViews.reversed()) {
                                                val params = v.layoutParams as? android.widget.LinearLayout.LayoutParams
                                                    ?: android.widget.LinearLayout.LayoutParams(
                                                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                                    )
                                                params.weight = 0f
                                                v.layoutParams = params
                                                addView(v, params)
                                            }
                                        } else {
                                            removeAllViews()
                                            for (v in viewsList.reversed()) {
                                                val params = v.layoutParams as? android.widget.LinearLayout.LayoutParams
                                                    ?: android.widget.LinearLayout.LayoutParams(
                                                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                                    )
                                                params.weight = 0f
                                                v.layoutParams = params
                                                addView(v, params)
                                            }
                                        }

                                        // Reduce the touch effect (ripple) size on category tabs
                                        val emojisTab = findViewById<android.widget.LinearLayout>(com.vanniktech.emoji.R.id.emojiViewTab)
                                        if (emojisTab != null) {
                                            val density = context.resources.displayMetrics.density
                                            val targetHeight = (density * 36).toInt()
                                            val circleSize = (density * 30).toInt()
                                            val highlightColor = android.graphics.Color.argb(32, 255, 255, 255)
                                            
                                            for (i in 0 until emojisTab.childCount) {
                                                val tabView = emojisTab.getChildAt(i)
                                                
                                                val params = tabView.layoutParams as? android.widget.LinearLayout.LayoutParams
                                                if (params != null) {
                                                    params.height = targetHeight
                                                    params.gravity = android.view.Gravity.CENTER_VERTICAL
                                                    tabView.layoutParams = params
                                                }
                                                
                                                val circle = android.graphics.drawable.GradientDrawable().apply {
                                                    shape = android.graphics.drawable.GradientDrawable.OVAL
                                                    setColor(highlightColor)
                                                    setSize(circleSize, circleSize)
                                                }
                                                val layers = arrayOf<android.graphics.drawable.Drawable>(circle)
                                                val layerDrawable = android.graphics.drawable.LayerDrawable(layers).apply {
                                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                                        setLayerGravity(0, android.view.Gravity.CENTER)
                                                    }
                                                }
                                                
                                                val stateList = android.graphics.drawable.StateListDrawable().apply {
                                                    addState(intArrayOf(android.R.attr.state_selected), layerDrawable)
                                                    addState(intArrayOf(android.R.attr.state_pressed), layerDrawable)
                                                    addState(intArrayOf(), android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                                                }
                                                
                                                tabView.background = stateList
                                            }
                                        }
                                    }
                                },
                                onRelease = { emojiView ->
                                    emojiView.tearDown()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Emoji Search Bottom Sheet
        if (showEmojiSearchSheet) {
            EmojiSearchBottomSheet(
                searchEmojiManager = searchEmojiManager,
                inputText = inputText,
                onInputChange = onInputChange,
                onSendClick = onSendClick,
                onEmojiClick = { emoji ->
                    onInputChange(inputText + emoji.unicode)
                },
                onDismiss = {
                    showEmojiSearchSheet = false
                    emojiSearchQuery = ""
                    emojiSearchResults = emptyList()
                }
            )
        }
    }
}

private val RobotoFontFamily = FontFamily(
    Font(com.subhankar.aurachat.R.font.roboto_regular, FontWeight.Normal),
    Font(com.subhankar.aurachat.R.font.roboto_medium, FontWeight.Medium),
    Font(com.subhankar.aurachat.R.font.roboto_medium, FontWeight.SemiBold),
    Font(com.subhankar.aurachat.R.font.roboto_bold, FontWeight.Bold)
)

// ─── App Bar ─────────────────────────────────────────────────────

@Composable
private fun ChatAppBar(
    recipientName: String,
    recipientAvatar: String?,
    isTyping: Boolean,
    isOnline: Boolean,
    lastSeenAt: String?,
    onBackClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF232325))
            .statusBarsPadding()
            .height(54.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back navigation button
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                "Back",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        // Avatar + Info block
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(AuraColors.ReceivedBubble),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (recipientName.isNotEmpty()) recipientName[0].uppercase() else "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = recipientName.ifEmpty { "Contact" },
                    color = Color(0xFFFAFAFA),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = RobotoFontFamily,
                    lineHeight = 18.sp,
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
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = RobotoFontFamily,
                        fontStyle = if (isTyping) FontStyle.Italic else FontStyle.Normal
                    )
                }
            }
        }

        // Action icons
        IconButton(
            onClick = { },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.MoreVert,
                "More",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// MessageBubble and ReceiptIcon are now implemented in MessageBubble.kt

// ─── Input Bar ───────────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onEmojiClick: () -> Unit,
    onFocus: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(start = 10.dp, end = 10.dp, bottom = 6.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Input field container (floating pill with backdrop blur)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), CircleShape)
        ) {
            // Background blur layer (Method A / Method B)
            BackdropBlur(
                modifier = Modifier.matchParentSize(),
                overlayColor = Color(0xFF141518).copy(alpha = 0.85f).toArgb()
            )

            // Content row (transparent background)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .background(Color.Transparent),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Emoji button on the left
                IconButton(
                    onClick = onEmojiClick,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_mood_smile),
                        contentDescription = "Emoji",
                        tint = AuraColors.TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Text input
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                onFocus()
                            }
                        }
                        .padding(start = 8.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(AuraColors.Primary),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (text.isNotEmpty()) {
                                onSendClick()
                            }
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty()) {
                                Text("Message", color = AuraColors.TextHint, fontSize = 15.sp)
                            }
                            innerTextField()
                        }
                    }
                )

                // Attachment button
                IconButton(
                    onClick = { /* Attachment action */ },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.attach_file_24),
                        contentDescription = "Attach",
                        tint = AuraColors.TextSecondary,
                        modifier = Modifier.size(25.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Dynamic Mic/Send button inside a filled circle
                val isNotEmpty = text.isNotEmpty()
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AuraColors.SentBubble)
                        .clickable {
                            if (isNotEmpty) {
                                onSendClick()
                            } else {
                                /* Mic action */
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isNotEmpty) R.drawable.ic_send_24 else R.drawable.ic_mic_24
                        ),
                        contentDescription = if (isNotEmpty) "Send" else "Mic",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// ─── Utilities ───────────────────────────────────────────────────



private fun formatTimeShort(isoString: String): String {
    return try {
        val instant = Instant.parse(isoString)
        val time = instant.atZone(ZoneId.systemDefault()).toLocalTime()
        time.format(DateTimeFormatter.ofPattern("h:mm a"))
    } catch (e: Exception) {
        ""
    }
}

private fun deleteLastCodePoint(text: String): String {
    if (text.isEmpty()) return text
    return try {
        val lastCodepointIndex = text.offsetByCodePoints(text.length, -1)
        text.substring(0, lastCodepointIndex)
    } catch (e: Exception) {
        text.dropLast(1)
    }
}

// ─── Emoji Search Bottom Sheet ───────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmojiSearchBottomSheet(
    searchEmojiManager: SearchEmojiManager,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onEmojiClick: (com.vanniktech.emoji.Emoji) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<com.vanniktech.emoji.Emoji>>(emptyList()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }

    // Auto-focus the search field when sheet is open (after the slide-up animation completes)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400)
        focusRequester.requestFocus()
    }

    // Temporarily switch to adjustNothing so the background content doesn't shift
    // when the search keyboard opens inside the bottom sheet
    val activity = LocalView.current.context as? android.app.Activity
    DisposableEffect(Unit) {
        activity?.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )
        onDispose {
            @Suppress("DEPRECATION")
            activity?.window?.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AuraColors.Background,
        contentColor = Color.White,
        dragHandle = {
            // Custom drag handle
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            )
        },
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
        ) {
            // Message box
            ChatInputBar(
                text = inputText,
                onTextChange = onInputChange,
                onSendClick = {
                    onSendClick()
                    onDismiss()
                },
                onEmojiClick = {
                    onDismiss()
                },
                onFocus = {}
            )

            // Search bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)), CircleShape)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = AuraColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { newQuery ->
                        query = newQuery
                        results = if (newQuery.isEmpty()) {
                            emptyList()
                        } else {
                            searchEmojiManager.search(newQuery).map { it.emoji }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(AuraColors.Primary),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text("Search emoji...", color = AuraColors.TextHint, fontSize = 15.sp)
                            }
                            innerTextField()
                        }
                    }
                )
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            query = ""
                            results = emptyList()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = AuraColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Results grid
            if (query.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Type to search emojis",
                        color = AuraColors.TextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                EmojiSearchResultsGrid(
                    emojis = results,
                    onEmojiClick = onEmojiClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EmojiSearchResultsGrid(
    emojis: List<com.vanniktech.emoji.Emoji>,
    onEmojiClick: (com.vanniktech.emoji.Emoji) -> Unit,
    modifier: Modifier = Modifier
) {
    if (emojis.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No emojis found",
                color = AuraColors.TextSecondary,
                fontSize = 14.sp
            )
        }
    } else {
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

        val isScrollInProgress = gridState.isScrollInProgress
        LaunchedEffect(isScrollInProgress) {
            if (isScrollInProgress) {
                focusManager.clearFocus()
                keyboardController?.hide()
            }
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 44.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(emojis.size) { index ->
                val emoji = emojis[index]
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onEmojiClick(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    // Use EmojiTextView to render iOS Apple-style emojis consistently
                    AndroidView(
                        factory = { ctx ->
                            com.vanniktech.emoji.EmojiTextView(ctx).apply {
                                setPadding(0, 0, 0, 0)
                                val sizeInPx = (ctx.resources.displayMetrics.density * 32).toInt()
                                setEmojiSize(sizeInPx)
                                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 32f)
                                gravity = android.view.Gravity.CENTER
                            }
                        },
                        update = { view ->
                            view.text = emoji.unicode
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
