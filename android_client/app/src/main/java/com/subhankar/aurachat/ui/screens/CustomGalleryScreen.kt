package com.subhankar.aurachat.ui.screens

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import androidx.compose.ui.res.painterResource
import coil.request.ImageRequest
import coil.request.CachePolicy
import coil.size.Precision
import com.subhankar.aurachat.ui.theme.AuraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset

data class GalleryImage(
    val uri: Uri,
    val dateAdded: Long
)

data class Album(
    val name: String,
    val coverUri: Uri,
    val images: List<GalleryImage>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomGalleryScreen(
    onBack: () -> Unit,
    onCameraClick: () -> Unit,
    onSystemPickerClick: () -> Unit,
    onImageSelected: (Uri) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var permissionsGranted by remember { mutableStateOf(false) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val lazyGridState = rememberLazyGridState()
    var showDateBadge by remember { mutableStateOf(false) }

    val totalGridItems by remember {
        derivedStateOf {
            selectedAlbum?.images?.size?.let { size ->
                size + (if (albums.isNotEmpty()) 1 else 0)
            } ?: 0
        }
    }

    val scrollProgress by remember {
        derivedStateOf {
            val layoutInfo = lazyGridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems <= 1) 0f
            else {
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) 0f
                else {
                    val firstIndex = visibleItems.first().index
                    val progress = firstIndex.toFloat() / (totalItems - visibleItems.size).coerceAtLeast(1)
                    progress.coerceIn(0f, 1f)
                }
            }
        }
    }

    var isDraggingHandle by remember { mutableStateOf(false) }
    var dragYOffset by remember { mutableStateOf(0f) }
    var lastScrolledIndex by remember { mutableStateOf(-1) }
    var dragJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val firstVisibleIndex by remember {
        derivedStateOf { lazyGridState.firstVisibleItemIndex }
    }

    val currentMonthYear by remember {
        derivedStateOf {
            val images = selectedAlbum?.images
            if (images != null && images.isNotEmpty()) {
                val photoIndex = (firstVisibleIndex - 1).coerceIn(0, images.size - 1)
                val timestampSeconds = images[photoIndex].dateAdded
                formatMonthYear(timestampSeconds)
            } else {
                ""
            }
        }
    }

    LaunchedEffect(firstVisibleIndex, lazyGridState.isScrollInProgress, isDraggingHandle) {
        if ((lazyGridState.isScrollInProgress || isDraggingHandle) && currentMonthYear.isNotEmpty()) {
            showDateBadge = true
        } else {
            delay(1200)
            showDateBadge = false
        }
    }

    // Resolve storage permissions based on API level
    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionsGranted = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Storage permission is required to select photos", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    // Check permission on launch
    LaunchedEffect(Unit) {
        val isGranted = ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED
        permissionsGranted = isGranted
        if (!isGranted) {
            launcher.launch(storagePermission)
        }
    }

    // Query images when permission is granted
    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted) {
            isLoading = true
            delay(350)
            withContext(Dispatchers.IO) {
                val queriedAlbums = queryMediaStoreImages(context)
                withContext(Dispatchers.Main) {
                    albums = queriedAlbums
                    selectedAlbum = queriedAlbums.firstOrNull()
                    isLoading = false
                }
            }
        }
    }

    // Register ContentObserver to auto-refresh gallery when MediaStore updates
    DisposableEffect(permissionsGranted) {
        if (!permissionsGranted) return@DisposableEffect onDispose {}

        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                coroutineScope.launch(Dispatchers.IO) {
                    val queriedAlbums = queryMediaStoreImages(context)
                    withContext(Dispatchers.Main) {
                        albums = queriedAlbums
                        val previousSelection = selectedAlbum?.name
                        selectedAlbum = queriedAlbums.firstOrNull { it.name == previousSelection } 
                            ?: queriedAlbums.firstOrNull()
                    }
                }
            }
        }

        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )

        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraColors.Background)
            .statusBarsPadding()
    ) {
        // Sticky Header / Custom Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(4.dp))

            // Dropdown Selector Button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showBottomSheet = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedAlbum?.name ?: "Recent Photos",
                    color = Color.White,
                    fontSize = 19.sp,
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
            
            Spacer(modifier = Modifier.weight(1f))
            
            IconButton(onClick = onSystemPickerClick) {
                Icon(
                    painter = painterResource(id = com.subhankar.aurachat.R.drawable.ic_system_gallery),
                    contentDescription = "System Picker",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            IconButton(onClick = onCameraClick) {
                Icon(
                    painter = painterResource(id = com.subhankar.aurachat.R.drawable.ic_profile_camera),
                    contentDescription = "Open Camera",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    val transition = rememberInfiniteTransition(label = "shimmer")
                    val translateAnim by transition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1000f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 1200, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "shimmer_translate"
                    )

                    val shimmerColors = listOf(
                        Color(0xFF22242A),
                        Color(0xFF2D3139),
                        Color(0xFF22242A)
                    )

                    val brush = Brush.linearGradient(
                        colors = shimmerColors,
                        start = Offset(translateAnim - 300f, translateAnim - 300f),
                        end = Offset(translateAnim, translateAnim)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        userScrollEnabled = false
                    ) {
                        items(30) { index ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(brush = brush),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = com.subhankar.aurachat.R.drawable.ic_placeholder_photo),
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.08f),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
                selectedAlbum == null || selectedAlbum!!.images.isEmpty() -> {
                    Text(
                        text = "No photos found on device",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
                else -> {
                    // Lazy vertical grid for photos
                    LazyVerticalGrid(
                        state = lazyGridState,
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Horizontal list of albums (Folder chips) as the first item (spans all 3 columns)
                        if (albums.isNotEmpty()) {
                            item(
                                key = "album_selector_row",
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(
                                        items = albums,
                                        key = { it.name }
                                    ) { album ->
                                        val isSelected = selectedAlbum?.name == album.name
                                        val backgroundColor = if (isSelected) AuraColors.Primary else Color(0xFF1E2025)
                                        val textColor = if (isSelected) Color(0xFF111318) else Color.White
                                        val countColor = if (isSelected) Color(0xFF111318).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.54f)

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

                        items(
                            items = selectedAlbum!!.images,
                            key = { it.uri.toString() }
                        ) { galleryImage ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF22242A))
                                    .clickable { onImageSelected(galleryImage.uri) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = com.subhankar.aurachat.R.drawable.ic_placeholder_photo),
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.12f),
                                    modifier = Modifier.size(32.dp)
                                )
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(galleryImage.uri)
                                        .crossfade(true)
                                        .size(220)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .memoryCachePolicy(CachePolicy.ENABLED)
                                        .memoryCacheKey("${galleryImage.uri}_220")
                                        .diskCacheKey("${galleryImage.uri}_220")
                                        .precision(Precision.INEXACT)
                                        .build(),
                                    contentDescription = "Gallery Image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }

                    // Floating Date Scroll Indicator & Fast Scroll Drag Handle
                    val trackTopPadding = 56.dp
                    val trackBottomPadding = 16.dp
                    val heightPx = with(LocalDensity.current) { (maxHeight - trackTopPadding - trackBottomPadding).toPx() }
                    val handleSize = 40.dp
                    val handleSizePx = with(LocalDensity.current) { handleSize.toPx() }
                    val maxScrollY = (heightPx - handleSizePx).coerceAtLeast(0f)

                    val handleYOffset by remember {
                        derivedStateOf {
                            if (isDraggingHandle) {
                                dragYOffset
                            } else {
                                scrollProgress * maxScrollY
                            }
                        }
                    }
                    val handleYOffsetDp = with(LocalDensity.current) { handleYOffset.toDp() }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(top = trackTopPadding, bottom = trackBottomPadding)
                            .width(180.dp)
                            .align(Alignment.TopEnd)
                    ) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 12.dp, y = handleYOffsetDp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Floating Date Badge
                            androidx.compose.animation.AnimatedVisibility(
                                visible = (showDateBadge || isDraggingHandle) && currentMonthYear.isNotEmpty(),
                                enter = fadeIn(animationSpec = tween(250)) + slideInHorizontally(
                                    initialOffsetX = { it / 2 },
                                    animationSpec = tween(250, easing = FastOutSlowInEasing)
                                ),
                                exit = fadeOut(animationSpec = tween(300)) + slideOutHorizontally(
                                    targetOffsetX = { it / 2 },
                                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                                )
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xFF1E2025), // Dark background matching the screenshot
                                    tonalElevation = 6.dp,
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                                ) {
                                    Text(
                                        text = currentMonthYear,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.SansSerif,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            // 2. Scroll Handle Button (Up/Down Arrow)
                            Surface(
                                modifier = Modifier
                                    .size(handleSize)
                                    .pointerInput(maxScrollY, totalGridItems) {
                                        detectVerticalDragGestures(
                                            onDragStart = { offset ->
                                                isDraggingHandle = true
                                                lastScrolledIndex = -1
                                                dragYOffset = (scrollProgress * maxScrollY).coerceIn(0f, maxScrollY)
                                            },
                                            onDragEnd = {
                                                isDraggingHandle = false
                                                dragJob?.cancel()
                                            },
                                            onDragCancel = {
                                                isDraggingHandle = false
                                                dragJob?.cancel()
                                            },
                                            onVerticalDrag = { change, dragAmount ->
                                                change.consume()
                                                dragYOffset = (dragYOffset + dragAmount).coerceIn(0f, maxScrollY)
                                                val dragFraction = if (maxScrollY > 0f) dragYOffset / maxScrollY else 0f
                                                val targetIndex = (dragFraction * (totalGridItems - 1)).roundToInt().coerceIn(0, totalGridItems - 1)
                                                
                                                if (targetIndex != lastScrolledIndex) {
                                                    lastScrolledIndex = targetIndex
                                                    dragJob?.cancel()
                                                    dragJob = coroutineScope.launch {
                                                        lazyGridState.scrollToItem(targetIndex)
                                                    }
                                                }
                                            }
                                        )
                                    },
                                shape = CircleShape,
                                color = Color.White,
                                tonalElevation = 8.dp,
                                shadowElevation = 4.dp
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = Color(0xFF1E2025),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .graphicsLayer(rotationZ = 180f)
                                    )
                                    Spacer(modifier = Modifier.height((-4).dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = Color(0xFF1E2025),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Bottom Sheet for Album Selection
    if (showBottomSheet && albums.isNotEmpty()) {
        AlbumBottomSheet(
            albums = albums,
            onAlbumSelected = { album ->
                selectedAlbum = album
                showBottomSheet = false
            },
            onDismissRequest = { showBottomSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumBottomSheet(
    albums: List<Album>,
    onAlbumSelected: (Album) -> Unit,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = Color(0xFF1E2025), // Dark surface/card color
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Select Folder",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = albums,
                    key = { it.name }
                ) { album ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onAlbumSelected(album)
                                onDismissRequest()
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(album.coverUri)
                                .crossfade(true)
                                .size(120)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .memoryCacheKey("${album.coverUri}_120")
                                .diskCacheKey("${album.coverUri}_120")
                                .precision(Precision.INEXACT)
                                .build(),
                            contentDescription = album.name,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF22242A)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = album.name,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${album.images.size} photos",
                                color = Color.White.copy(alpha = 0.54f),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatMonthYear(seconds: Long): String {
    if (seconds <= 0) return ""
    return try {
        val date = java.util.Date(seconds * 1000)
        val sdf = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
        sdf.format(date)
    } catch (e: Exception) {
        ""
    }
}

private fun queryMediaStoreImages(context: Context): List<Album> {
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
