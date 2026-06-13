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
import android.content.Context
import android.content.ContentUris
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.request.CachePolicy
import coil.size.Precision
import coil.request.ImageRequest
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
import com.subhankar.aurachat.ui.components.BackdropBlurView
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import coil.compose.AsyncImage
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
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
import com.vanniktech.emoji.search.SearchEmojiManager
import com.vanniktech.emoji.search.NoSearchEmoji
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing

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
    onBackClick: () -> Unit,
    onSendMedia: (List<Uri>) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var showCustomGalleryScreen by remember { mutableStateOf(false) }

    BackHandler(enabled = showCustomGalleryScreen) {
        showCustomGalleryScreen = false
    }

    // Pre-fetch MediaStore images to warm up memory cache on entering the chat room
    LaunchedEffect(Unit) {
        try {
            val storagePermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                android.Manifest.permission.READ_MEDIA_IMAGES
            } else {
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            }
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                storagePermission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (hasPermission && cachedAlbumsCache == null) {
                withContext(Dispatchers.IO) {
                    val queriedAlbums = queryMediaStoreImagesForAttachment(context)
                    cachedAlbumsCache = queriedAlbums
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    var isEmojiPickerVisible by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var emojiSearchQuery by remember { mutableStateOf("") }
    var emojiSearchResults by remember { mutableStateOf<List<com.vanniktech.emoji.Emoji>>(emptyList()) }
    val searchEmojiManager = remember { SearchEmojiManager() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val composeView = LocalView.current

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val uri = saveBitmapToMediaStore(context, bitmap)
            if (uri != null) {
                onSendMedia(listOf(uri))
            }
        }
    }

    // Camera permission request launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                cameraLauncher.launch(null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // System Picker launcher
    val systemPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onSendMedia(listOf(uri))
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().zIndex(2f),
        containerColor = AuraColors.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (!showCustomGalleryScreen) {
                ChatAppBar(
                    recipientName = recipientName,
                    recipientAvatar = recipientAvatar,
                    isTyping = isTyping,
                    isOnline = isOnline,
                    lastSeenAt = lastSeenAt,
                    onBackClick = onBackClick
                )
            }
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

        Box(modifier = Modifier.fillMaxSize()) {
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
                            },
                            onAttachClick = {
                                showAttachmentSheet = true
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
                },
                onAttachClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    showAttachmentSheet = true
                    showEmojiSearchSheet = false
                }
            )
        }

        // Attachment Bottom Sheet
        if (showAttachmentSheet) {
            AttachmentBottomSheet(
                onDismissRequest = { showAttachmentSheet = false },
                onSendMedia = { uris ->
                    onSendMedia(uris)
                    showAttachmentSheet = false
                },
                onOpenCustomGallery = {
                    showCustomGalleryScreen = true
                    showAttachmentSheet = false
                }
            )
        }

        // Custom Gallery Screen Overlay with Horizontal Slide Animation
        AnimatedVisibility(
            visible = showCustomGalleryScreen,
            enter = androidx.compose.animation.slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ),
            exit = androidx.compose.animation.slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ),
            modifier = Modifier.zIndex(10f)
        ) {
            val hasCamPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            CustomGalleryScreen(
                onBack = { showCustomGalleryScreen = false },
                onCameraClick = {
                    showCustomGalleryScreen = false
                    if (hasCamPermission) {
                        try {
                            cameraLauncher.launch(null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    }
                },
                onSystemPickerClick = {
                    systemPickerLauncher.launch("image/*")
                },
                onImageSelected = { selectedUri ->
                    onSendMedia(listOf(selectedUri))
                    showCustomGalleryScreen = false
                }
            )
        }
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
    onFocus: () -> Unit,
    onAttachClick: () -> Unit
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
                    .background(Color.Transparent)
                    .drawWithContent {
                        if (!BackdropBlurView.isCapturing) {
                            drawContent()
                        }
                    },
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
                    onClick = onAttachClick,
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
    onDismiss: () -> Unit,
    onAttachClick: () -> Unit
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
                onFocus = {},
                onAttachClick = {
                    onAttachClick()
                }
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

private var cachedAlbumsCache: List<Album>? = null

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentBottomSheet(
    onDismissRequest: () -> Unit,
    onSendMedia: (List<Uri>) -> Unit,
    onOpenCustomGallery: () -> Unit
) {
    val context = LocalContext.current
    val localAlbums = remember { mutableStateListOf<Album>() }
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val localFiles = remember { mutableStateListOf<LocalFile>() }
    var isLoadingFiles by remember { mutableStateOf(true) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var isGridView by remember { mutableStateOf(false) }
    val selectedTab = when (selectedIndex) {
        0 -> "Gallery"
        1 -> "File"
        2 -> "Contact"
        3 -> "Poll"
        else -> "Gallery"
    }

    // System Picker launcher
    val systemPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onSendMedia(listOf(uri))
        }
    }

    // Document scanner camera launcher
    val documentScanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val uri = saveBitmapToMediaStore(context, bitmap)
            if (uri != null) {
                onSendMedia(listOf(uri))
            }
        }
    }

    // Camera permission request launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                documentScanLauncher.launch(null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val hasCameraPermission = androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.CAMERA
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val uri = saveBitmapToMediaStore(context, bitmap)
            if (uri != null) {
                onSendMedia(listOf(uri))
            }
        }
    }

    // SAF File Picker Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            onSendMedia(uris)
        }
    }

    // Resolve storage permissions based on API level to check safety for MediaStore access
    val storagePermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        storagePermission
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    // Query albums when opened
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            try {
                // If cache exists, load instantly to avoid showing blank or loading screen!
                if (cachedAlbumsCache != null) {
                    localAlbums.clear()
                    localAlbums.addAll(cachedAlbumsCache!!)
                    val previousSelection = selectedAlbum?.name
                    selectedAlbum = cachedAlbumsCache!!.firstOrNull { it.name == previousSelection } 
                        ?: cachedAlbumsCache!!.firstOrNull()
                }

                // If cache is empty, we delay to let slide-in animation finish smoothly first.
                // If cache exists, we run the query in the background without delay.
                if (cachedAlbumsCache == null) {
                    delay(300)
                }

                val queriedAlbums = withContext(Dispatchers.IO) {
                    queryMediaStoreImagesForAttachment(context)
                }

                val hasChanged = cachedAlbumsCache == null ||
                                 cachedAlbumsCache!!.size != queriedAlbums.size ||
                                 cachedAlbumsCache!!.firstOrNull()?.images?.size != queriedAlbums.firstOrNull()?.images?.size

                if (hasChanged) {
                    cachedAlbumsCache = queriedAlbums
                    localAlbums.clear()
                    localAlbums.addAll(queriedAlbums)
                    val previousSelection = selectedAlbum?.name
                    selectedAlbum = queriedAlbums.firstOrNull { it.name == previousSelection } 
                        ?: queriedAlbums.firstOrNull()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    // Query files when the selectedTab becomes "File"
    LaunchedEffect(selectedTab) {
        if (selectedTab == "File") {
            isLoadingFiles = true
            try {
                val files = queryLocalFiles(context)
                localFiles.clear()
                localFiles.addAll(files)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingFiles = false
            }
        }
    }

    // Register ContentObserver to auto-refresh gallery when MediaStore updates
    DisposableEffect(hasPermission) {
        if (!hasPermission) return@DisposableEffect onDispose {}

        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val queriedAlbums = queryMediaStoreImagesForAttachment(context)
                        cachedAlbumsCache = queriedAlbums // Update the cache
                        withContext(Dispatchers.Main) {
                            localAlbums.clear()
                            localAlbums.addAll(queriedAlbums)
                            val previousSelection = selectedAlbum?.name
                            selectedAlbum = queriedAlbums.firstOrNull { it.name == previousSelection } 
                                ?: queriedAlbums.firstOrNull()
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        }

        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        onDispose {
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val isScrollEnabled = sheetState.currentValue == SheetValue.Expanded

    val windowView = androidx.compose.ui.platform.LocalView.current
    val initialWindowHeight = windowView.height.toFloat()
    val screenHeightPx = if (initialWindowHeight > 0f) initialWindowHeight else 2000f
    var sheetHeightPx by remember { mutableStateOf(screenHeightPx * 0.95f) }

    val pillTranslationY by remember(windowView) {
        derivedStateOf {
            try {
                val offset = sheetState.requireOffset()
                val windowHeight = windowView.height.toFloat()
                val activeScreenHeight = if (windowHeight > 0f) windowHeight else 2000f
                val offScreenPx = (offset + sheetHeightPx) - activeScreenHeight
                if (offScreenPx > 0f) {
                    -offScreenPx
                } else {
                    0f
                }
            } catch (e: Exception) {
                0f
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF111318),
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color(0xFF111318))
                .navigationBarsPadding()
                .onGloballyPositioned { coordinates ->
                    sheetHeightPx = coordinates.size.height.toFloat()
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag Handle inside the bordered container
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.15f))
            )

            // Content Box wrapping Header, Chips, and Photos Grid/Placeholders together
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 2.dp, end = 2.dp, bottom = 2.dp)
                    .background(Color(0xFF111318), RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (selectedTab == "Gallery") {
                    // Header Row (Album Selector Dropdown & Media Actions)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Dropdown Selector Button
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showBottomSheet = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedAlbum?.name ?: "Loading...",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Folder",
                                tint = Color.White
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = { systemPickerLauncher.launch("image/*") }) {
                                Icon(
                                    painter = painterResource(id = com.subhankar.aurachat.R.drawable.ic_system_gallery),
                                    contentDescription = "System Picker",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(onClick = {
                                try {
                                    cameraLauncher.launch(null)
                                } catch (e: Exception) {
                                    // camera preview error fallback
                                }
                            }) {
                                Icon(
                                    painter = painterResource(id = com.subhankar.aurachat.R.drawable.ic_profile_camera),
                                    contentDescription = "Open Camera",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Folder Chips Row - Always visible if albums are loaded
                    if (localAlbums.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(localAlbums) { album ->
                                val isSelected = selectedAlbum?.name == album.name
                                val backgroundColor = if (isSelected) Color(0xFF2C6BED) else Color(0xFF1E2025)
                                val textColor = Color.White
                                val countColor = if (isSelected) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.54f)

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(backgroundColor)
                                        .clickable { selectedAlbum = album }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(album.coverUri)
                                            .crossfade(true)
                                            .size(72)
                                            .allowHardware(false)
                                            .diskCachePolicy(CachePolicy.ENABLED)
                                            .memoryCachePolicy(CachePolicy.ENABLED)
                                            .memoryCacheKey("${album.coverUri}_72")
                                            .diskCacheKey("${album.coverUri}_72")
                                            .precision(Precision.INEXACT)
                                            .build(),
                                        contentDescription = album.name,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF22242A)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = album.name,
                                        color = textColor,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "(${album.images.size})",
                                        color = countColor,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                            }
                        }
                    }
                }

                // Inner Content Area with overlayed floating pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (selectedTab == "Gallery") {
                        val currentImages = selectedAlbum?.images ?: emptyList()
                        val isLoading = selectedAlbum == null && localAlbums.isEmpty()
                        val itemCount = if (isLoading) 12 else currentImages.size

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                            userScrollEnabled = isScrollEnabled
                        ) {
                            items(itemCount) { index ->
                                val itemShape = when (index) {
                                    0 -> RoundedCornerShape(topStart = 12.dp, topEnd = 4.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                                    1 -> RoundedCornerShape(4.dp)
                                    2 -> RoundedCornerShape(topEnd = 12.dp, topStart = 4.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                                    else -> RoundedCornerShape(4.dp)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(itemShape)
                                        .background(Color(0xFF22242A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = com.subhankar.aurachat.R.drawable.ic_placeholder_photo),
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.08f),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    if (!isLoading) {
                                        val galleryImage = currentImages[index]
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(galleryImage.uri)
                                                .crossfade(true)
                                                .size(220)
                                                .allowHardware(false)
                                                .diskCachePolicy(CachePolicy.ENABLED)
                                                .memoryCachePolicy(CachePolicy.ENABLED)
                                                .memoryCacheKey("${galleryImage.uri}_220")
                                                .diskCacheKey("${galleryImage.uri}_220")
                                                .precision(Precision.INEXACT)
                                                .build(),
                                            contentDescription = "Gallery Image",
                                            modifier = Modifier.fillMaxSize().clip(itemShape)
                                                .clickable { onSendMedia(listOf(galleryImage.uri)) },
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }
                    } else if (selectedTab == "File") {
                        val storageMenu = @Composable {
                            FileStorageMenu(
                                onInternalStorageClick = {
                                    try {
                                        filePickerLauncher.launch(arrayOf("*/*"))
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                                onGalleryClick = {
                                    onOpenCustomGallery()
                                },
                                onScanDocumentClick = {
                                    if (hasCameraPermission) {
                                        try {
                                            documentScanLauncher.launch(null)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    } else {
                                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                    }
                                }
                            )
                        }

                        if (isLoadingFiles) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item { storageMenu() }
                                
                                item {
                                    RecentFilesHeader(
                                        isGridView = isGridView,
                                        onToggle = { isGridView = it }
                                    )
                                }
                                
                                item {
                                    if (isGridView) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            repeat(2) { rowIndex ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    repeat(3) {
                                                        FileGridItemSkeleton(modifier = Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color(0xFF1E2025))
                                                .padding(vertical = 4.dp)
                                        ) {
                                            repeat(6) { index ->
                                                FileItemSkeleton()
                                                if (index < 5) {
                                                    Box(
                                                        modifier = Modifier
                                                            .padding(horizontal = 16.dp)
                                                            .fillMaxWidth()
                                                            .height(0.5.dp)
                                                            .background(Color.White.copy(alpha = 0.08f))
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item { storageMenu() }
                                
                                if (localFiles.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 48.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = com.subhankar.aurachat.R.drawable.ic_file),
                                                    contentDescription = "No files",
                                                    tint = Color.White.copy(alpha = 0.25f),
                                                    modifier = Modifier.size(64.dp)
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                    text = "No recent documents found",
                                                    color = Color.White.copy(alpha = 0.6f),
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    fontFamily = FontFamily.SansSerif
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    item {
                                        RecentFilesHeader(
                                            isGridView = isGridView,
                                            onToggle = { isGridView = it }
                                        )
                                    }
                                    
                                    if (isGridView) {
                                        item {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                val chunked = localFiles.chunked(3)
                                                chunked.forEach { rowItems ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        rowItems.forEach { file ->
                                                            FileGridItem(
                                                                file = file,
                                                                modifier = Modifier.weight(1f),
                                                                onClick = { onSendMedia(listOf(file.uri)) }
                                                            )
                                                        }
                                                        if (rowItems.size < 3) {
                                                            repeat(3 - rowItems.size) {
                                                                Spacer(modifier = Modifier.weight(1f))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        item {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(Color(0xFF1E2025))
                                                    .padding(vertical = 4.dp)
                                            ) {
                                                localFiles.forEachIndexed { index, file ->
                                                    FileListItem(
                                                        file = file,
                                                        onClick = { onSendMedia(listOf(file.uri)) }
                                                    )
                                                    if (index < localFiles.lastIndex) {
                                                        Box(
                                                            modifier = Modifier
                                                                .padding(horizontal = 16.dp)
                                                                .fillMaxWidth()
                                                                .height(0.5.dp)
                                                                .background(Color.White.copy(alpha = 0.08f))
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Premium Placeholders for other tabs (Contact, Poll)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 50.dp)
                                .graphicsLayer {
                                    translationY = pillTranslationY * 0.5f
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                val placeholderIcon = when(selectedTab) {
                                    "Contact" -> com.subhankar.aurachat.R.drawable.ic_contact
                                    "Poll" -> com.subhankar.aurachat.R.drawable.ic_poll
                                    else -> com.subhankar.aurachat.R.drawable.ic_gallery
                                }
                                Icon(
                                    painter = painterResource(id = placeholderIcon),
                                    contentDescription = selectedTab,
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "$selectedTab Feature Coming Soon",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                    }

                    // Floating Pill centered at the bottom of this container
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .graphicsLayer {
                                this.translationY = pillTranslationY
                            }
                    ) {
                        BottomNavBar(
                            selectedIndex = selectedIndex,
                            totalUnreadCount = 0,
                            profilePhotoPath = null,
                            translationY = pillTranslationY,
                            onTabSelected = { index ->
                                selectedIndex = index
                            }
                        )
                    }
                }
            }
        }
    }

    // Folder selection bottom sheet
    if (showBottomSheet && localAlbums.isNotEmpty()) {
        AlbumBottomSheet(
            albums = localAlbums,
            onAlbumSelected = { album ->
                selectedAlbum = album
                showBottomSheet = false
            },
            onDismissRequest = { showBottomSheet = false }
        )
    }
}

@Composable
private fun BottomNavBar(
    selectedIndex: Int,
    totalUnreadCount: Int,
    profilePhotoPath: String?,
    translationY: Float = 0f,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth()
            .height(58.dp),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        tonalElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxSize().clip(CircleShape)) {
            BackdropBlur(
                modifier = Modifier.matchParentSize(),
                customTranslationY = translationY,
                overlayColor = Color(0xFF141518).copy(alpha = 0.35f).toArgb(),
                backgroundAlpha = 1.0f
            )
            Row(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .fillMaxSize()
                    .drawWithContent {
                        if (!BackdropBlurView.isCapturing) {
                            drawContent()
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            NavItem(
                modifier = Modifier.weight(1f),
                index = 0,
                selectedIndex = selectedIndex,
                label = "Gallery",
                badgeCount = totalUnreadCount,
                onTap = onTabSelected
            ) { tint ->
                Icon(
                    painter = painterResource(id = com.subhankar.aurachat.R.drawable.ic_gallery),
                    contentDescription = "Gallery",
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            NavItem(
                modifier = Modifier.weight(1f),
                index = 1,
                selectedIndex = selectedIndex,
                label = "File",
                badgeCount = 0,
                onTap = onTabSelected
            ) { tint ->
                val fileIcon = if (selectedIndex == 1) {
                    com.subhankar.aurachat.R.drawable.ic_file_filled
                } else {
                    com.subhankar.aurachat.R.drawable.ic_file
                }
                Icon(
                    painter = painterResource(id = fileIcon),
                    contentDescription = "File",
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
            NavItem(
                modifier = Modifier.weight(1f),
                index = 2,
                selectedIndex = selectedIndex,
                label = "Contact",
                badgeCount = 0,
                onTap = onTabSelected
            ) { tint ->
                val contactIcon = if (selectedIndex == 2) {
                    com.subhankar.aurachat.R.drawable.ic_contact_filled
                } else {
                    com.subhankar.aurachat.R.drawable.ic_contact
                }
                Icon(
                    painter = painterResource(id = contactIcon),
                    contentDescription = "Contact",
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
            NavItem(
                modifier = Modifier.weight(1f),
                index = 3,
                selectedIndex = selectedIndex,
                label = "Poll",
                badgeCount = 0,
                onTap = onTabSelected
            ) { tint ->
                val pollIcon = if (selectedIndex == 3) {
                    com.subhankar.aurachat.R.drawable.ic_poll_filled
                } else {
                    com.subhankar.aurachat.R.drawable.ic_poll
                }
                Icon(
                    painter = painterResource(id = pollIcon),
                    contentDescription = "Poll",
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
    val iconColor = if (isSelected) Color(0xFF2C6BED) else Color.White
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
                                textAlign = TextAlign.Center
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

private fun queryMediaStoreImagesForAttachment(context: Context): List<Album> {
    val albumMap = mutableMapOf<String, MutableList<GalleryImage>>()
    val allPhotos = mutableListOf<GalleryImage>()

    val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED
    )
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    try {
        context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val bucketName = cursor.getString(bucketColumn) ?: "Others"
                val dateAdded = cursor.getLong(dateColumn)
                val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                val galleryImage = GalleryImage(contentUri, dateAdded)
                allPhotos.add(galleryImage)
                albumMap.getOrPut(bucketName) { mutableListOf() }.add(galleryImage)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    val albumList = mutableListOf<Album>()
    if (allPhotos.isNotEmpty()) {
        albumList.add(Album("Recent Photos", allPhotos.first().uri, allPhotos))
    }
    
    // Sort albums by name so it looks neat and professional
    val sortedAlbums = albumMap.entries.sortedBy { it.key }
    sortedAlbums.forEach { (name, images) ->
        if (images.isNotEmpty()) {
            albumList.add(Album(name, images.first().uri, images))
        }
    }
    return albumList
}

data class LocalFile(
    val id: Long,
    val name: String,
    val size: Long,
    val mimeType: String?,
    val dateModified: Long,
    val uri: Uri
)

private suspend fun queryLocalFiles(context: Context): List<LocalFile> {
    return withContext(Dispatchers.IO) {
        val files = mutableListOf<LocalFile>()
        val contentUri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        val selection = "${MediaStore.Files.FileColumns.SIZE} > 0"
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                contentUri,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext() && files.size < 50) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Unknown File"
                    val size = cursor.getLong(sizeCol)
                    val mimeType = cursor.getString(mimeCol)
                    val dateModified = cursor.getLong(dateCol)
                    val uri = ContentUris.withAppendedId(contentUri, id)

                    // Filter out folders in memory
                    if (mimeType == "vnd.android.document/directory" || mimeType == "resource/folder") {
                        continue
                    }

                    files.add(
                        LocalFile(
                            id = id,
                            name = name,
                            size = size,
                            mimeType = mimeType,
                            dateModified = dateModified,
                            uri = uri
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        files
    }
}

private fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun formatFileDate(timestampSeconds: Long): String {
    val instant = java.time.Instant.ofEpochSecond(timestampSeconds)
    val formatter = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")
        .withZone(java.time.ZoneId.systemDefault())
    return formatter.format(instant)
}

private data class FileTypeConfig(
    val iconRes: Int,
    val color: Color
)

private fun getFileTypeConfig(fileName: String, mimeType: String?): FileTypeConfig {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when {
        ext == "pdf" || mimeType == "application/pdf" -> 
            FileTypeConfig(com.subhankar.aurachat.R.drawable.ic_file, Color(0xFFF44336))
        ext in listOf("doc", "docx") || mimeType?.contains("word") == true -> 
            FileTypeConfig(com.subhankar.aurachat.R.drawable.ic_file, Color(0xFF2196F3))
        ext in listOf("xls", "xlsx") || mimeType?.contains("excel") == true || mimeType?.contains("spreadsheet") == true -> 
            FileTypeConfig(com.subhankar.aurachat.R.drawable.ic_file, Color(0xFF4CAF50))
        ext in listOf("ppt", "pptx") || mimeType?.contains("powerpoint") == true || mimeType?.contains("presentation") == true -> 
            FileTypeConfig(com.subhankar.aurachat.R.drawable.ic_file, Color(0xFFFF9800))
        ext in listOf("zip", "rar", "7z", "tar", "gz") || mimeType?.contains("zip") == true || mimeType?.contains("compressed") == true -> 
            FileTypeConfig(com.subhankar.aurachat.R.drawable.ic_file, Color(0xFF9C27B0))
        mimeType?.startsWith("image/") == true || ext in listOf("jpg", "jpeg", "png", "webp", "gif") -> 
            FileTypeConfig(com.subhankar.aurachat.R.drawable.ic_gallery, Color(0xFF00BCD4))
        mimeType?.startsWith("video/") == true || ext in listOf("mp4", "mkv", "avi", "mov", "webm") -> 
            FileTypeConfig(com.subhankar.aurachat.R.drawable.ic_profile_camera, Color(0xFFFFC107))
        else -> 
            FileTypeConfig(com.subhankar.aurachat.R.drawable.ic_file, Color(0xFF9E9E9E))
    }
}

@Composable
private fun FileStorageMenu(
    onInternalStorageClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onScanDocumentClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E2025))
            .padding(vertical = 4.dp)
    ) {
        // 1. Internal Storage
        FileMenuRow(
            iconRes = com.subhankar.aurachat.R.drawable.ic_internal_storage,
            iconBgColor = Color(0xFF4CAF50), // Green circle
            title = "Internal Storage",
            subtitle = "Browse your file system",
            onClick = onInternalStorageClick
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )
        // 2. Gallery
        FileMenuRow(
            iconRes = com.subhankar.aurachat.R.drawable.ic_gallery,
            iconBgColor = Color(0xFFFFB300), // Yellow/Orange circle
            title = "Gallery",
            subtitle = "To send images without compression",
            onClick = onGalleryClick
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )
        // 3. Scan Document
        FileMenuRow(
            iconRes = com.subhankar.aurachat.R.drawable.ic_scan_document,
            iconBgColor = Color(0xFF2196F3), // Blue circle
            title = "Scan Document",
            subtitle = "Scan documents using camera",
            onClick = onScanDocumentClick
        )
    }
}

@Composable
private fun FileMenuRow(
    iconRes: Int,
    iconBgColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                style = androidx.compose.ui.text.TextStyle(
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                        includeFontPadding = false
                    )
                )
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif,
                style = androidx.compose.ui.text.TextStyle(
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                        includeFontPadding = false
                    )
                )
            )
        }
    }
}

private fun saveBitmapToMediaStore(context: Context, bitmap: android.graphics.Bitmap): Uri? {
    val contentValues = android.content.ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "scanned_doc_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/AuraChat")
        }
    }
    
    return try {
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
            }
        }
        uri
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun getFileExtensionText(fileName: String): String {
    val cleanName = fileName.trim()
    if (cleanName.isEmpty()) return "?"
    
    // Check if there is a dot extension at the end
    val lastDotIndex = cleanName.lastIndexOf('.')
    if (lastDotIndex > 0 && lastDotIndex < cleanName.length - 1) {
        val ext = cleanName.substring(lastDotIndex + 1).lowercase()
        return if (ext.length <= 4) ext else ext.substring(0, 4)
    }
    
    // If name starts with a dot, e.g. .OplusDragAndDrop
    if (cleanName.startsWith('.')) {
        val nameAfterDot = cleanName.substring(1)
        if (nameAfterDot.isNotEmpty()) {
            val prefix = nameAfterDot.lowercase()
            return if (prefix.length <= 4) prefix else prefix.substring(0, 4)
        }
    }
    
    // No clear extension
    return "?"
}

@Composable
private fun YellowFileIcon(fileName: String) {
    val extensionText = getFileExtensionText(fileName)
    val fontSize = when (extensionText.length) {
        1 -> 18.sp
        2 -> 14.sp
        3 -> 12.sp
        else -> 9.sp
    }
    
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFEBB134)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = extensionText,
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun FileListItem(file: LocalFile, onClick: () -> Unit) {
    val isSvg = file.name.endsWith(".svg", ignoreCase = true) || file.mimeType == "image/svg+xml"
    val isImageOrVideo = (file.mimeType?.startsWith("image/") == true && !isSvg) || file.mimeType?.startsWith("video/") == true
    
    val thumbnailUri = when {
        file.mimeType?.startsWith("image/") == true -> {
            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, file.id)
        }
        file.mimeType?.startsWith("video/") == true -> {
            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, file.id)
        }
        else -> null
    }

    var isImageLoadFailed by remember { mutableStateOf(false) }
    val showThumbnail = isImageOrVideo && !isImageLoadFailed

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp),
            contentAlignment = Alignment.Center
        ) {
            if (showThumbnail && thumbnailUri != null) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(thumbnailUri)
                            .crossfade(true)
                            .allowHardware(false)
                            .build(),
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onError = {
                            isImageLoadFailed = true
                        }
                    )
                    
                    if (file.mimeType?.startsWith("video/") == true) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play Video",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            } else {
                YellowFileIcon(fileName = file.name)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = androidx.compose.ui.text.TextStyle(
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                        includeFontPadding = false
                    )
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = formatFileSize(file.size),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    style = androidx.compose.ui.text.TextStyle(
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                            includeFontPadding = false
                        )
                    )
                )
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f))
                )
                Text(
                    text = formatFileDate(file.dateModified),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    style = androidx.compose.ui.text.TextStyle(
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                            includeFontPadding = false
                        )
                    )
                )
            }
        }
    }
}

@Composable
private fun FileItemSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.05f))
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.05f))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .width(90.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.05f))
            )
        }
    }
}

@Composable
private fun ListIcon(tint: Color) {
    Column(
        modifier = Modifier.size(16.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        repeat(3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(tint)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(tint)
                )
            }
        }
    }
}

@Composable
private fun GridIcon(tint: Color) {
    Column(
        modifier = Modifier.size(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(modifier = Modifier.fillMaxHeight().weight(1f).clip(RoundedCornerShape(1.dp)).background(tint))
            Box(modifier = Modifier.fillMaxHeight().weight(1f).clip(RoundedCornerShape(1.dp)).background(tint))
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(modifier = Modifier.fillMaxHeight().weight(1f).clip(RoundedCornerShape(1.dp)).background(tint))
            Box(modifier = Modifier.fillMaxHeight().weight(1f).clip(RoundedCornerShape(1.dp)).background(tint))
        }
    }
}

@Composable
private fun RecentFilesHeader(
    isGridView: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, bottom = 8.dp, top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Recent files",
            color = Color(0xFF4C8CFF),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // List toggle button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (!isGridView) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                    .clickable { onToggle(false) },
                contentAlignment = Alignment.Center
            ) {
                ListIcon(tint = if (!isGridView) Color(0xFF4C8CFF) else Color.White.copy(alpha = 0.5f))
            }
            // Grid toggle button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isGridView) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                    .clickable { onToggle(true) },
                contentAlignment = Alignment.Center
            ) {
                GridIcon(tint = if (isGridView) Color(0xFF4C8CFF) else Color.White.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun FileGridItem(
    file: LocalFile,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isSvg = file.name.endsWith(".svg", ignoreCase = true) || file.mimeType == "image/svg+xml"
    val isImageOrVideo = (file.mimeType?.startsWith("image/") == true && !isSvg) || file.mimeType?.startsWith("video/") == true
    
    val thumbnailUri = when {
        file.mimeType?.startsWith("image/") == true -> {
            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, file.id)
        }
        file.mimeType?.startsWith("video/") == true -> {
            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, file.id)
        }
        else -> null
    }

    var isImageLoadFailed by remember { mutableStateOf(false) }
    val showThumbnail = isImageOrVideo && !isImageLoadFailed

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E2025))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)), RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (showThumbnail && thumbnailUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(thumbnailUri)
                    .crossfade(true)
                    .allowHardware(false)
                    .build(),
                contentDescription = file.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = {
                    isImageLoadFailed = true
                }
            )
            
            if (file.mimeType?.startsWith("video/") == true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Video",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        } else {
            // Document/Generic file: display yellow extension badge scaled up
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                YellowFileIcon(fileName = file.name)
            }
        }

        // Filename and size overlay at the bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Column {
                Text(
                    text = file.name,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = androidx.compose.ui.text.TextStyle(
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                            includeFontPadding = false
                        )
                    )
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = formatFileSize(file.size),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    style = androidx.compose.ui.text.TextStyle(
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                            includeFontPadding = false
                        )
                    )
                )
            }
        }
    }
}

@Composable
private fun FileGridItemSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)), RoundedCornerShape(12.dp))
    )
}
