package com.subhankar.aurachat.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.camera.core.ImageCaptureException
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.FileOutputStream
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume
import androidx.compose.ui.draw.blur
import androidx.compose.ui.zIndex
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.camera.core.CameraControl
import androidx.camera.core.FocusMeteringAction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.subhankar.aurachat.ui.theme.AuraColors

enum class ProfileDialogType {
    PHONE, BIRTHDAY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profileData: Map<String, String?>? = null,
    isLoading: Boolean = false,
    error: String? = null,
    firstName: String = "",
    lastName: String = "",
    bio: String = "",
    phone: String = "",
    username: String = "",
    birthday: String = "",
    profilePhotoUri: String? = null,
    textAvatarText: String? = null,
    textAvatarBgIndex: Int? = null,
    textAvatarTextColorIndex: Int? = null,
    isSaving: Boolean = false,
    isLoggedOut: Boolean = false,
    hasChanges: Boolean = false,
    onFirstNameChange: (String) -> Unit = {},
    onLastNameChange: (String) -> Unit = {},
    onBioChange: (String) -> Unit = {},
    onPhoneChange: (String) -> Unit = {},
    onUsernameChange: (String) -> Unit = {},
    onBirthdayChange: (String) -> Unit = {},
    onProfilePhotoChange: (String?) -> Unit = {},
    onTextAvatarStateChange: (String?, Int?, Int?) -> Unit = { _, _, _ -> },
    onLogoutClick: () -> Unit = {},
    onLogoutSuccess: () -> Unit = {},
    onSaveClick: (onSuccess: () -> Unit) -> Unit = {},
    onDiscardClick: () -> Unit = {},
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var activeDialog by remember { mutableStateOf<ProfileDialogType?>(null) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }
    var showSavedText by remember { mutableStateOf(false) }
    var isEditingPhoto by remember { mutableStateOf(false) }
    var isPhotoFullScreen by remember { mutableStateOf(false) }
    var fullScaleState by remember { mutableStateOf(1f) }
    var fullOffsetState by remember { mutableStateOf(Offset.Zero) }
    var zoomAnimationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var imageWidth by remember { mutableStateOf(1) }
    var imageHeight by remember { mutableStateOf(1) }
 
    LaunchedEffect(isPhotoFullScreen) {
        if (isPhotoFullScreen) {
            fullScaleState = 1f
            fullOffsetState = Offset.Zero
            zoomAnimationJob?.cancel()
            zoomAnimationJob = null
            imageWidth = 1
            imageHeight = 1
        }
    }

    var showCameraView by remember { mutableStateOf(false) }
    var showUsernameEditScreen by remember { mutableStateOf(false) }
    var showCustomGalleryScreen by remember { mutableStateOf(false) }
    var showTextAvatarScreen by remember { mutableStateOf(false) }
    var showPhotoEditor by remember { mutableStateOf(false) }
    var photoEditorSourceUri by remember { mutableStateOf<String?>(null) }
    
    val profileOffset by animateDpAsState(
        targetValue = if (showUsernameEditScreen) (-100).dp else 0.dp,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "ProfileOffset"
    )
    val profileAlpha by animateFloatAsState(
        targetValue = if (showUsernameEditScreen) 0.5f else 1f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "ProfileAlpha"
    )

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableStateOf(false) } // false = off, true = on

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                showCameraView = true
            } else {
                Toast.makeText(context, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
            }
        }
    )

    val systemPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val photoFile = File(
                            context.cacheDir,
                            "profile_photo_temp_${System.currentTimeMillis()}.jpg"
                        )
                        photoFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                        
                        launch(kotlinx.coroutines.Dispatchers.Main) {
                            val savedUri = android.net.Uri.fromFile(photoFile)
                            photoEditorSourceUri = savedUri.toString()
                            showPhotoEditor = true
                            showCustomGalleryScreen = false
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    launch(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val currentUsername = username
    val cardBgColor = Color(0xFF1E2025)

    // Intercept back presses
    if (showCameraView) {
        BackHandler {
            showCameraView = false
        }
    } else if (showPhotoEditor) {
        BackHandler {
            showPhotoEditor = false
        }
    } else if (showCustomGalleryScreen) {
        BackHandler {
            showCustomGalleryScreen = false
        }
    } else if (showTextAvatarScreen) {
        BackHandler {
            showTextAvatarScreen = false
        }
    } else if (showUsernameEditScreen) {
        BackHandler {
            showUsernameEditScreen = false
        }
    } else if (isEditingPhoto) {
        BackHandler {
            isEditingPhoto = false
        }
    } else if (hasChanges) {
        BackHandler {
            showDiscardConfirmDialog = true
        }
    }

    LaunchedEffect(isLoggedOut) {
        if (isLoggedOut) {
            onLogoutSuccess()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().zIndex(2f),
        containerColor = AuraColors.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AuraColors.Primary)
                    }
                }
                showCameraView -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    val lifecycleOwner = LocalLifecycleOwner.current
                    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
                    val currentFlashMode by rememberUpdatedState(flashMode)
                    var isSwitchingCamera by remember { mutableStateOf(false) }
                    var isCapturingPhoto by remember { mutableStateOf(false) }
                    var isTakingPicture by remember { mutableStateOf(false) }

                    val flashAlpha by animateFloatAsState(
                        targetValue = if (isCapturingPhoto) 0.85f else 0f,
                        animationSpec = tween(durationMillis = 150),
                        label = "ShutterFlashAlpha"
                    )

                    val cameraFlipScale by animateFloatAsState(
                        targetValue = if (isSwitchingCamera) 0.90f else 1f,
                        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                        label = "CameraScreenScale"
                    )
                    val cameraBlurRadius by animateFloatAsState(
                        targetValue = if (isSwitchingCamera) 16f else 0f,
                        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                        label = "CameraScreenBlur"
                    )

                    // Create and remember PreviewView
                    val previewView = remember {
                        PreviewView(context).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    }

                    var cameraControl: CameraControl? by remember { mutableStateOf(null) }
                    var activeCamera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
                    var zoomState by remember { mutableStateOf<androidx.camera.core.ZoomState?>(null) }
                    
                    DisposableEffect(activeCamera) {
                        val cameraInfo = activeCamera?.cameraInfo
                        val observer = androidx.lifecycle.Observer<androidx.camera.core.ZoomState> { state ->
                            zoomState = state
                        }
                        cameraInfo?.zoomState?.observeForever(observer)
                        onDispose {
                            cameraInfo?.zoomState?.removeObserver(observer)
                        }
                    }

                    var tapPosition by remember { mutableStateOf<Offset?>(null) }
                    var focusRingTrigger by remember { mutableStateOf(0) }
                    
                    val focusRingAlpha = remember { Animatable(0f) }
                    val focusRingScale = remember { Animatable(1.5f) }
                    
                    val captureInteractionSource = remember { MutableInteractionSource() }
                    val isCapturePressed by captureInteractionSource.collectIsPressedAsState()
                    val captureButtonScale by animateFloatAsState(
                        targetValue = if (isCapturePressed) 0.85f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "CaptureButtonScale"
                    )
                    
                    LaunchedEffect(focusRingTrigger) {
                        if (focusRingTrigger > 0) {
                            focusRingScale.snapTo(1.5f)
                            focusRingAlpha.snapTo(1f)
                            launch {
                                focusRingScale.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(durationMillis = 300)
                                )
                            }
                            launch {
                                delay(800)
                                focusRingAlpha.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(durationMillis = 300)
                                )
                            }
                        }
                    }

                    // Handle CameraX binding inside a LaunchedEffect keyed on lensFacing
                    LaunchedEffect(lensFacing) {
                        isSwitchingCamera = true
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                        val cameraProvider = suspendCoroutine<ProcessCameraProvider> { continuation ->
                            cameraProviderFuture.addListener({
                                continuation.resume(cameraProviderFuture.get())
                            }, ContextCompat.getMainExecutor(context))
                        }

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .setFlashMode(if (currentFlashMode) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                            .build()
                        imageCapture = capture

                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(lensFacing)
                            .build()

                        try {
                            cameraProvider.unbindAll()
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                capture
                            )
                            cameraControl = camera.cameraControl
                            activeCamera = camera
                            delay(300) // Brief delay for sensor warm-up/exposure adjustment
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            isSwitchingCamera = false
                        }
                    }

                    // AndroidView wrapping the remembered PreviewView
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(activeCamera) {
                                detectTransformGestures { _, _, zoomFactor, _ ->
                                    val state = zoomState ?: return@detectTransformGestures
                                    val currentRatio = state.zoomRatio
                                    val minRatio = state.minZoomRatio
                                    val maxRatio = state.maxZoomRatio
                                    val newRatio = (currentRatio * zoomFactor).coerceIn(minRatio, maxRatio)
                                    cameraControl?.setZoomRatio(newRatio)
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    tapPosition = offset
                                    focusRingTrigger++
                                    
                                    val factory = previewView.meteringPointFactory
                                    val point = factory.createPoint(offset.x, offset.y)
                                    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                                        .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                                        .build()
                                    cameraControl?.startFocusAndMetering(action)
                                }
                            }
                            .graphicsLayer {
                                scaleX = cameraFlipScale
                                scaleY = cameraFlipScale
                                cameraDistance = 12f * density
                            }
                            .blur(cameraBlurRadius.dp)
                    )

                    // Focus Ring Overlay
                    if (focusRingAlpha.value > 0f && tapPosition != null) {
                        val density = LocalDensity.current
                        Box(
                            modifier = Modifier
                                .absoluteOffset {
                                    val offsetPx = with(density) { 24.dp.toPx() }
                                    IntOffset(
                                        (tapPosition!!.x - offsetPx).toInt(),
                                        (tapPosition!!.y - offsetPx).toInt()
                                    )
                                }
                                .size(48.dp)
                                .graphicsLayer {
                                    scaleX = focusRingScale.value
                                    scaleY = focusRingScale.value
                                    alpha = focusRingAlpha.value
                                }
                                .border(1.5.dp, Color.White, CircleShape)
                        )
                    }

                    // 2. Close Button (Top Right)
                    IconButton(
                        onClick = { showCameraView = false },
                        enabled = !isTakingPicture,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(16.dp)
                            .size(48.dp)
                            .background(
                                color = if (isTakingPicture) Color.Black.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                            .align(Alignment.TopEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Camera",
                            tint = if (isTakingPicture) Color.White.copy(alpha = 0.5f) else Color.White
                        )
                    }

                    // 4. Capture & Flip Controls at the bottom
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val currentLinearZoom = zoomState?.linearZoom ?: 0f
                        val currentZoomRatio = zoomState?.zoomRatio ?: 1f
                        val minRatio = zoomState?.minZoomRatio ?: 1f
                        val maxRatio = zoomState?.maxZoomRatio ?: 8f

                        ZoomSlider(
                            linearZoom = currentLinearZoom,
                            zoomRatio = currentZoomRatio,
                            minZoomRatio = minRatio,
                            maxZoomRatio = maxRatio,
                            onZoomChange = { newLinear ->
                                cameraControl?.setLinearZoom(newLinear)
                            },
                            onZoomRatioChange = { newRatio ->
                                cameraControl?.setZoomRatio(newRatio)
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Flash icon (Bottom Left)
                            if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                IconButton(
                                    onClick = {
                                        flashMode = !flashMode
                                        imageCapture?.flashMode = if (!flashMode) ImageCapture.FLASH_MODE_OFF else ImageCapture.FLASH_MODE_ON
                                    },
                                    enabled = !isTakingPicture,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = if (flashMode) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                        contentDescription = "Toggle Flash",
                                        tint = if (isTakingPicture) Color.White.copy(alpha = 0.5f) else Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(48.dp))
                            }

                            // Capture Button: large empty white circle with border
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .border(4.dp, Color.White, CircleShape)
                                    .clip(CircleShape)
                                    .background(Color.Transparent)
                                    .clickable(
                                        enabled = !isTakingPicture,
                                        interactionSource = captureInteractionSource,
                                        indication = null
                                    ) {
                                        val capture = imageCapture
                                        if (capture != null) {
                                            isTakingPicture = true
                                            isCapturingPhoto = true
                                            coroutineScope.launch {
                                                delay(150)
                                                isCapturingPhoto = false
                                            }
                                            val photoFile = File(
                                                context.cacheDir,
                                                "profile_photo_${System.currentTimeMillis()}.jpg"
                                            )
                                            val metadata = ImageCapture.Metadata()
                                            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
                                                .setMetadata(metadata)
                                                .build()
                                            capture.takePicture(
                                                outputOptions,
                                                ContextCompat.getMainExecutor(context),
                                                object : ImageCapture.OnImageSavedCallback {
                                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                            try {
                                                                val isFront = lensFacing == CameraSelector.LENS_FACING_FRONT
                                                                val exifInterface = android.media.ExifInterface(photoFile.absolutePath)
                                                                val orientation = exifInterface.getAttributeInt(
                                                                    android.media.ExifInterface.TAG_ORIENTATION,
                                                                    android.media.ExifInterface.ORIENTATION_NORMAL
                                                                )
                                                                val degrees = when (orientation) {
                                                                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                                                                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                                                                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                                                                    else -> 0
                                                                }

                                                                val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                                                                if (bitmap != null) {
                                                                    if (degrees != 0 || isFront) {
                                                                        val matrix = Matrix().apply {
                                                                            postRotate(degrees.toFloat())
                                                                            if (isFront) {
                                                                                postScale(-1f, 1f)
                                                                            }
                                                                        }
                                                                        val rotatedBitmap = Bitmap.createBitmap(
                                                                            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                                                                        )
                                                                        FileOutputStream(photoFile).use { out ->
                                                                            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                                                        }
                                                                        if (rotatedBitmap != bitmap) {
                                                                            rotatedBitmap.recycle()
                                                                        }
                                                                        
                                                                        try {
                                                                            val newExif = android.media.ExifInterface(photoFile.absolutePath)
                                                                            newExif.setAttribute(
                                                                                android.media.ExifInterface.TAG_ORIENTATION,
                                                                                android.media.ExifInterface.ORIENTATION_NORMAL.toString()
                                                                            )
                                                                            newExif.saveAttributes()
                                                                        } catch (e: Exception) {
                                                                            e.printStackTrace()
                                                                        }
                                                                    }
                                                                    bitmap.recycle()
                                                                }
                                                                // Save to public gallery so it displays in device's albums!
                                                                saveImageToPublicGallery(
                                                                    context = context,
                                                                    file = photoFile,
                                                                    displayName = "profile_captured_${System.currentTimeMillis()}.jpg"
                                                                )
                                                            } catch (e: Exception) {
                                                                e.printStackTrace()
                                                            }
                                                            
                                                            launch(kotlinx.coroutines.Dispatchers.Main) {
                                                                val savedUri = android.net.Uri.fromFile(photoFile)
                                                                photoEditorSourceUri = savedUri.toString()
                                                                showPhotoEditor = true
                                                                showCameraView = false
                                                                isTakingPicture = false
                                                            }
                                                        }
                                                    }

                                                    override fun onError(exception: ImageCaptureException) {
                                                        isTakingPicture = false
                                                        Toast.makeText(
                                                            context,
                                                            "Failed to capture photo: ${exception.message}",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            )
                                        } else {
                                            Toast.makeText(context, "Camera is not ready", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                // Inner solid white circle that scales instantly on tap for a mechanical click feel
                                val innerCircleScale = if (isCapturePressed) 0.65f else 0.75f
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize(innerCircleScale)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }

                            // Switch Camera Button (Front/Back)
                            IconButton(
                                onClick = {
                                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                        CameraSelector.LENS_FACING_FRONT
                                    } else {
                                        CameraSelector.LENS_FACING_BACK
                                    }
                                },
                                enabled = !isTakingPicture,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FlipCameraAndroid,
                                    contentDescription = "Switch Camera",
                                    tint = if (isTakingPicture) Color.White.copy(alpha = 0.5f) else Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Text Instruction: "Tap for photo"
                        Text(
                            text = "Tap for photo",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    // Camera switch transition overlay
                    val overlayAlpha by animateFloatAsState(
                        targetValue = if (isSwitchingCamera) 0.8f else 0f,
                        animationSpec = tween(durationMillis = 300),
                        label = "CameraSwitchOverlayAlpha"
                    )
                    
                    if (overlayAlpha > 0f) {
                        val rotationAngle by animateFloatAsState(
                            targetValue = if (isSwitchingCamera) 180f else 0f,
                            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                            label = "CameraSwitchRotateAngle"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = overlayAlpha)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlipCameraAndroid,
                                contentDescription = "Switching Camera",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .size(64.dp)
                                    .graphicsLayer(rotationZ = rotationAngle)
                            )
                        }
                    }

                    // Shutter screen flash overlay
                    if (flashAlpha > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = flashAlpha))
                        )
                    }
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error, color = AuraColors.Error, modifier = Modifier.padding(16.dp))
                        Button(
                            onClick = onBackClick,
                            colors = ButtonDefaults.buttonColors(containerColor = AuraColors.Primary)
                        ) {
                            Text("Go Back", color = Color.Black)
                        }
                    }
                }
            }
            isEditingPhoto -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AuraColors.Background)
                ) {
                    // Sticky top bar for editing photo
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { isEditingPhoto = false }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Edit Photo",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    // Content below top bar
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Large circular avatar image
                            Box(
                                modifier = Modifier
                                    .size(180.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.5.dp, AuraColors.Primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (profilePhotoUri != null) {
                                    AsyncImage(
                                        model = profilePhotoUri,
                                        contentDescription = "Profile Photo",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable { isPhotoFullScreen = true },
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(id = com.subhankar.aurachat.R.drawable.ic_profile_placeholder),
                                        contentDescription = "Profile Photo",
                                        tint = AuraColors.Primary,
                                        modifier = Modifier.size(90.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(60.dp))

                            // Row of three action buttons: Camera, Photo, Text
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Camera option
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF2E303B))
                                            .clickable {
                                                val hasPermission = ContextCompat.checkSelfPermission(
                                                    context,
                                                    android.Manifest.permission.CAMERA
                                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                                if (hasPermission) {
                                                    showCameraView = true
                                                } else {
                                                    permissionLauncher.launch(android.Manifest.permission.CAMERA)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = com.subhankar.aurachat.R.drawable.ic_profile_camera),
                                            contentDescription = "Camera",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Camera",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }

                                // Photo option
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF2E303B))
                                            .clickable {
                                                showCustomGalleryScreen = true
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = com.subhankar.aurachat.R.drawable.ic_profile_photo),
                                            contentDescription = "Photo",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Photo",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }

                                // Text option
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF2E303B))
                                            .clickable {
                                                showTextAvatarScreen = true
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Aa",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Normal,
                                            fontFamily = FontFamily.SansSerif
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Text",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // Save Button in the lower right corner (aligned to BottomEnd)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(AuraColors.Primary)
                                .clickable {
                                    if (hasChanges) {
                                        onSaveClick {
                                            coroutineScope.launch {
                                                showSavedText = true
                                                delay(1200)
                                                showSavedText = false
                                                isEditingPhoto = false
                                            }
                                        }
                                    } else {
                                        isEditingPhoto = false
                                    }
                                }
                                .padding(horizontal = 28.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Save",
                                color = Color.Black,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(x = profileOffset)
                        .graphicsLayer { alpha = profileAlpha }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (hasChanges) {
                                    showDiscardConfirmDialog = true
                                } else {
                                    onBackClick()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Account",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (showSavedText) {
                            Text(
                                text = "Saved",
                                color = AuraColors.Primary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        } else if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = AuraColors.Primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        } else if (hasChanges) {
                            IconButton(
                                onClick = {
                                    onSaveClick {
                                        coroutineScope.launch {
                                            showSavedText = true
                                            delay(1200)
                                            showSavedText = false
                                            onBackClick()
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Save Changes",
                                    tint = AuraColors.Primary
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                    // Profile Photo (Centered at the top)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.5.dp, AuraColors.Primary, CircleShape)
                                .clickable {
                                    if (profilePhotoUri == null) {
                                        isEditingPhoto = true
                                    } else {
                                        isPhotoFullScreen = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (profilePhotoUri != null) {
                                AsyncImage(
                                    model = profilePhotoUri,
                                    contentDescription = "Profile Photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = com.subhankar.aurachat.R.drawable.ic_profile_placeholder),
                                    contentDescription = "Profile Photo",
                                    tint = AuraColors.Primary,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF2C2E35))
                                .clickable {
                                    isEditingPhoto = true
                                }
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Edit photo",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Card 1: Your name
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = com.subhankar.aurachat.R.drawable.ic_profile_header),
                                    contentDescription = "Your name icon",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Your name",
                                    color = AuraColors.Primary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            
                            // First Name
                            BasicTextField(
                                value = firstName,
                                onValueChange = onFirstNameChange,
                                textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Normal),
                                cursorBrush = SolidColor(Color.White),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 24.dp),
                                decorationBox = { innerTextField ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (firstName.isEmpty()) {
                                            Text("First Name", color = Color.White.copy(alpha = 0.25f), fontSize = 16.sp)
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Last Name
                            BasicTextField(
                                value = lastName,
                                onValueChange = onLastNameChange,
                                textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Normal),
                                cursorBrush = SolidColor(Color.White),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 24.dp),
                                decorationBox = { innerTextField ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (lastName.isEmpty()) {
                                            Text("Last Name", color = Color.White.copy(alpha = 0.25f), fontSize = 16.sp)
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Card 2: Bio
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                            Text(
                                text = "Bio",
                                color = AuraColors.Primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Box(modifier = Modifier.fillMaxWidth()) {
                                BasicTextField(
                                    value = bio,
                                    onValueChange = onBioChange,
                                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal),
                                    cursorBrush = SolidColor(Color.White),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .padding(bottom = 12.dp, end = 32.dp),
                                    decorationBox = { innerTextField ->
                                        Box {
                                            if (bio.isEmpty()) {
                                                Text("A few Words About You.", color = Color.White.copy(alpha = 0.3f), fontSize = 15.sp)
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                                
                                Text(
                                    text = "${140 - bio.length}",
                                    color = Color.White.copy(alpha = 0.54f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.align(Alignment.BottomEnd)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    // Card 3: Your Info
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor)
                    ) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Text(
                                text = "Your Info",
                                color = AuraColors.Primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            InfoItem(
                                title = if (phone.isNotEmpty()) phone else "+91 8609351025",
                                subtext = "Tap to change phone number",
                                onClick = { activeDialog = ProfileDialogType.PHONE },
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                leading = {
                                    Icon(
                                        painter = painterResource(id = com.subhankar.aurachat.R.drawable.ic_profile_call),
                                        contentDescription = "Call Icon",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            )
                            
                            InfoItem(
                                title = if (currentUsername.isNotEmpty()) "@$currentUsername" else "Set Username",
                                subtext = if (currentUsername.isNotEmpty()) "Username" else null,
                                onClick = { showUsernameEditScreen = true },
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = if (currentUsername.isNotEmpty()) 8.dp else 14.dp),
                                leading = {
                                    Icon(
                                        painter = painterResource(id = com.subhankar.aurachat.R.drawable.ic_profile_username),
                                        contentDescription = "Username Icon",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            )
                            
                            InfoItem(
                                title = if (birthday.isNotEmpty()) birthday else "Add Birthday",
                                subtext = if (birthday.isNotEmpty()) "Birthday" else null,
                                onClick = { activeDialog = ProfileDialogType.BIRTHDAY },
                                modifier = Modifier.padding(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = if (birthday.isNotEmpty()) 8.dp else 14.dp,
                                    bottom = 18.dp
                                ),
                                leading = {
                                    Icon(
                                        painter = painterResource(id = com.subhankar.aurachat.R.drawable.ic_profile_birthday),
                                        contentDescription = "Birthday Icon",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    // Card 4: QR Code
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        Toast.makeText(context, "QR code coming soon!", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = com.subhankar.aurachat.R.drawable.ic_profile_qrcode),
                                    contentDescription = "QR Code Icon",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "QR Code",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.W400
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            DashedDivider(
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        Toast.makeText(context, "Links coming soon!", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = com.subhankar.aurachat.R.drawable.link_24),
                                    contentDescription = "Links Icon",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "Links",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.W400
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Card 5: Actions
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor)
                    ) {
                        Column {
                            ActionItem(
                                text = "Add Account",
                                onClick = {
                                    Toast.makeText(context, "Multi-account support is coming soon!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
                                leading = {
                                    Icon(
                                        painter = painterResource(id = com.subhankar.aurachat.R.drawable.ic_profile_add_account),
                                        contentDescription = "Add Account Icon",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            )
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            ActionItem(
                                text = "Logout",
                                onClick = onLogoutClick,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 18.dp),
                                leading = {
                                    Icon(
                                        painter = painterResource(id = com.subhankar.aurachat.R.drawable.ic_profile_logout),
                                        contentDescription = "Logout Icon",
                                        tint = Color(0xFFFF4D4D),
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                textColor = Color(0xFFFF4D4D)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
        }
            
            // Username Edit Screen Overlay with Horizontal Slide Animation
            AnimatedVisibility(
                visible = showUsernameEditScreen,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                )
            ) {
                UsernameEditScreen(
                    initialUsername = username,
                    onBack = { showUsernameEditScreen = false },
                    onSave = { newUsername ->
                        onUsernameChange(newUsername)
                        showUsernameEditScreen = false
                    }
                )
            }
            
            // Custom Gallery Screen Overlay with Horizontal Slide Animation
            AnimatedVisibility(
                visible = showCustomGalleryScreen,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                )
            ) {
                CustomGalleryScreen(
                    onBack = { showCustomGalleryScreen = false },
                    onCameraClick = {
                        showCustomGalleryScreen = false
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.CAMERA
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            showCameraView = true
                        } else {
                            permissionLauncher.launch(android.Manifest.permission.CAMERA)
                        }
                    },
                    onSystemPickerClick = {
                        systemPickerLauncher.launch("image/*")
                    },
                    onImageSelected = { selectedUri ->
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                context.contentResolver.openInputStream(selectedUri)?.use { inputStream ->
                                    val photoFile = File(
                                        context.cacheDir,
                                        "profile_photo_temp_${System.currentTimeMillis()}.jpg"
                                    )
                                    photoFile.outputStream().use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                    
                                    launch(kotlinx.coroutines.Dispatchers.Main) {
                                        val savedUri = android.net.Uri.fromFile(photoFile)
                                        photoEditorSourceUri = savedUri.toString()
                                        showPhotoEditor = true
                                        showCustomGalleryScreen = false
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                launch(kotlinx.coroutines.Dispatchers.Main) {
                                    Toast.makeText(context, "Failed to load image from gallery", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
            }
            
            // Text Avatar Screen Overlay with Horizontal Slide Animation
            AnimatedVisibility(
                visible = showTextAvatarScreen,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                )
            ) {
                val defaultInitials = remember(firstName, lastName) {
                    val first = firstName.firstOrNull()?.toString() ?: ""
                    val last = lastName.firstOrNull()?.toString() ?: ""
                    (first + last).uppercase()
                }
                val currentTextVal = textAvatarText ?: defaultInitials
                val currentBgIndex = textAvatarBgIndex ?: 7 // Default to peach coral index
                val currentTextColorIndex = textAvatarTextColorIndex ?: 0 // Default to Auto index
                
                TextAvatarEditScreen(
                    initialText = currentTextVal,
                    initialBgIndex = currentBgIndex,
                    initialTextColorIndex = currentTextColorIndex,
                    defaultInitials = defaultInitials,
                    onBack = { showTextAvatarScreen = false },
                    onSave = { selectedUri, text, bgIdx, textColIdx ->
                        onProfilePhotoChange(selectedUri.toString())
                        onTextAvatarStateChange(text, bgIdx, textColIdx)
                        showTextAvatarScreen = false
                    }
                )
            }

            // Photo Editor Screen Overlay with Horizontal Slide Animation
            AnimatedVisibility(
                visible = showPhotoEditor && photoEditorSourceUri != null,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                )
            ) {
                PhotoEditorScreen(
                    imageUriString = photoEditorSourceUri!!,
                    onBack = { showPhotoEditor = false },
                    onSave = { editedUriString ->
                        onProfilePhotoChange(editedUriString)
                        onTextAvatarStateChange(null, null, null)
                        showPhotoEditor = false
                        isEditingPhoto = false
                    }
                )
            }

            // Full Screen Photo Overlay (drawn on top of the other overlays)
            if (isPhotoFullScreen && profilePhotoUri != null) {
                BackHandler {
                    isPhotoFullScreen = false
                }
                
                var showMenu by remember { mutableStateOf(false) }
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = profilePhotoUri,
                        contentDescription = "Full Screen Profile Photo",
                        onSuccess = { state ->
                            val drawable = state.result.drawable
                            if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                                imageWidth = drawable.intrinsicWidth
                                imageHeight = drawable.intrinsicHeight
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    zoomAnimationJob?.cancel()
                                    val newScale = (fullScaleState * zoom).coerceIn(1f, 4f)
                                    val newOffset = if (newScale == 1f) {
                                        Offset.Zero
                                    } else {
                                        fullOffsetState + pan
                                    }
                                    fullScaleState = newScale
                                    
                                    val imgW = if (imageWidth > 0) imageWidth.toFloat() else 1f
                                    val imgH = if (imageHeight > 0) imageHeight.toFloat() else 1f
                                    val imageAspectRatio = imgW / imgH
                                    val screenAspectRatio = if (size.height > 0) size.width.toFloat() / size.height.toFloat() else 1f

                                    val (visualW, visualH) = if (imageAspectRatio > screenAspectRatio) {
                                        size.width.toFloat() to (size.width.toFloat() / imageAspectRatio)
                                    } else {
                                        (size.height.toFloat() * imageAspectRatio) to size.height.toFloat()
                                    }

                                    val limitX = if (visualW * newScale > size.width) {
                                        (visualW * newScale - size.width) / 2f
                                    } else {
                                        0f
                                    }
                                    val limitY = if (visualH * newScale > size.height) {
                                        (visualH * newScale - size.height) / 2f
                                    } else {
                                        0f
                                    }
                                    
                                    fullOffsetState = Offset(
                                        x = newOffset.x.coerceIn(-limitX, limitX),
                                        y = newOffset.y.coerceIn(-limitY, limitY)
                                    )
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = { centroid ->
                                        zoomAnimationJob?.cancel()
                                        val targetScale = when {
                                            fullScaleState < 2f -> 2.2f
                                            fullScaleState >= 2f && fullScaleState < 3.2f -> 3.5f
                                            else -> 1f
                                        }
                                        
                                        val targetOffset = if (targetScale == 1f) {
                                            Offset.Zero
                                        } else {
                                            val screenCenter = Offset(size.width / 2f, size.height / 2f)
                                            val tapPos = centroid - screenCenter
                                            val proposedOffset = fullOffsetState - tapPos * (targetScale - fullScaleState) / fullScaleState
                                            
                                            val imgW = if (imageWidth > 0) imageWidth.toFloat() else 1f
                                            val imgH = if (imageHeight > 0) imageHeight.toFloat() else 1f
                                            val imageAspectRatio = imgW / imgH
                                            val screenAspectRatio = if (size.height > 0) size.width.toFloat() / size.height.toFloat() else 1f

                                            val (visualW, visualH) = if (imageAspectRatio > screenAspectRatio) {
                                                size.width.toFloat() to (size.width.toFloat() / imageAspectRatio)
                                            } else {
                                                (size.height.toFloat() * imageAspectRatio) to size.height.toFloat()
                                            }

                                            val limitX = if (visualW * targetScale > size.width) {
                                                (visualW * targetScale - size.width) / 2f
                                            } else {
                                                0f
                                            }
                                            val limitY = if (visualH * targetScale > size.height) {
                                                (visualH * targetScale - size.height) / 2f
                                            } else {
                                                0f
                                            }
                                            
                                            Offset(
                                                x = proposedOffset.x.coerceIn(-limitX, limitX),
                                                y = proposedOffset.y.coerceIn(-limitY, limitY)
                                            )
                                        }
                                        
                                        zoomAnimationJob = coroutineScope.launch {
                                            val startScale = fullScaleState
                                            val startOffset = fullOffsetState
                                            animate(
                                                initialValue = 0f,
                                                targetValue = 1f,
                                                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                                            ) { fraction, _ ->
                                                fullScaleState = startScale + (targetScale - startScale) * fraction
                                                fullOffsetState = Offset(
                                                    x = startOffset.x + (targetOffset.x - startOffset.x) * fraction,
                                                    y = startOffset.y + (targetOffset.y - startOffset.y) * fraction
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                            .graphicsLayer {
                                scaleX = fullScaleState
                                scaleY = fullScaleState
                                translationX = fullOffsetState.x
                                translationY = fullOffsetState.y
                            },
                        contentScale = ContentScale.Fit
                    )

                    // Telegram-Style Header Navigation Bar (drawn on top of the image)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { isPhotoFullScreen = false },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        val displayName = remember(firstName, lastName) {
                            val name = "$firstName $lastName".trim()
                            if (name.isEmpty()) "User" else name
                        }
                        Text(
                            text = displayName,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        )
                        
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(42.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,



                                    





                                    


                                    


                                    




                                    
                                    
                                    

                                    
                                    


                                    


                                    



                                    


                                    



                                    


                                





                                    




                                    



                                    
                                    







                                    
                                    
                                    
                                    
                                    


                                    
                                    
                                    
                                    
                                    

                                    







                                    

                                    
                                    
                                    

                            

                                    


                                    



                                    
                                    
                                    

                                    





                                    

                                    



                                    

 

                                


                            
                                    

                                




                                    

                                    
                                



                                    
                                        
                                    


                                    
                                    
                                    



                                    



 

                                    




                                

                                    
                            





                                    

                                    
                                    

                                                


                                


                                            

                                    
                                


                                    




                                    









                                    
                                    



                            

                                    


                                            
                                
 

    


                                    







        



                                    





                





            
                                    
                                
                



 
 

        

                


                
                


 



                





                        

                                        

                    

                        




                


 
                                            

 


                                        

                            


        

 



                                
                                    

 
                
 
                                        
                                


                            

                                    
                            

                






                            



                        


    


                    






                        
        
                        


 


            






                            
        





                                

                            
 




                            
 


                

        


    
                    









        



    
    
                                    






        










        


            
        

                    
    



        
                    
 
 

                






    


 








        

            
 



            
            





            
                
















    
 
            


 
    


            
 




                
                

 





    


 






                    
    

 
                

            
        



                





 
        
            
 
 
                



                    


            

 




                

            
 





                        

                                    




                                    

                                    
                                    
                                    







                                    

                                    

                                    
                                    
                                    

                                    

                                    





                                    





                                    



                                    




                                    





                                    


                                    

                                    







                                    
                            

                                    

                                    
                                    
                                    
                                            
                                    


                                    
 



                                

                                        

                                                




                                





                                    



                            
                                    










                            
                                    

                                                
                                    



                                    
                                



                                    
                                
                                                
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier
                                        .size(22.dp)
                                        .wrapContentSize(Alignment.Center)
                                ) {
                                    repeat(3) {
                                        Box(
                                            modifier = Modifier
                                                .size(3.dp)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                        )
                                    }
                                }
                            }
                            
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(Color(0xFF2C2E35))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Save to Gallery", color = Color.White) },
                                    onClick = {
                                        showMenu = false
                                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            try {
                                                val uri = android.net.Uri.parse(profilePhotoUri)
                                                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                                    val tempFile = File(
                                                        context.cacheDir,
                                                        "profile_downloaded_${System.currentTimeMillis()}.jpg"
                                                    )
                                                    tempFile.outputStream().use { outputStream ->
                                                        inputStream.copyTo(outputStream)
                                                    }
                                                    saveImageToPublicGallery(
                                                        context = context,
                                                        file = tempFile,
                                                        displayName = "profile_photo_${System.currentTimeMillis()}.jpg"
                                                    )
                                                    launch(kotlinx.coroutines.Dispatchers.Main) {
                                                        Toast.makeText(context, "Saved to Gallery!", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                launch(kotlinx.coroutines.Dispatchers.Main) {
                                                    Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                )
                                
                                DropdownMenuItem(
                                    text = { Text("Share", color = Color.White) },
                                    onClick = {
                                        showMenu = false
                                        try {
                                            val shareIntent = android.content.Intent().apply {
                                                action = android.content.Intent.ACTION_SEND
                                                putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.parse(profilePhotoUri))
                                                type = "image/*"
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share profile photo"))
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            Toast.makeText(context, "Failed to share photo", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Edit Photo", color = Color.White) },
                                    onClick = {
                                        showMenu = false
                                        isPhotoFullScreen = false
                                        isEditingPhoto = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Active Edit Dialogs
    when (activeDialog) {
        ProfileDialogType.PHONE -> {
            ProfileEditDialog(
                title = "Change Phone Number",
                initialValue = phone,
                label = "Phone Number",
                onDismiss = { activeDialog = null },
                onSave = onPhoneChange
            )
        }
        ProfileDialogType.BIRTHDAY -> {
            BirthdayBottomSheet(
                initialValue = birthday,
                onDismiss = { activeDialog = null },
                onSave = { selectedDate ->
                    onBirthdayChange(selectedDate)
                    activeDialog = null
                }
            )
        }
        null -> {}
    }

    if (showDiscardConfirmDialog) {
        Dialog(
            onDismissRequest = { showDiscardConfirmDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2025))
            ) {
                Column(
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 12.dp)
                ) {
                    Text(
                        text = "Unsaved Changes",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You have unsaved changes. Do you want to save them before leaving?",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cancel",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable {
                                    showDiscardConfirmDialog = false
                                    onDiscardClick()
                                    onBackClick()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Save",
                            color = AuraColors.Primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    showDiscardConfirmDialog = false
                                    onSaveClick {
                                        coroutineScope.launch {
                                            showSavedText = true
                                            delay(1200)
                                            showSavedText = false
                                            onBackClick()
                                        }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InfoIconBox() {
    Box(
        modifier = Modifier
            .size(24.dp)
            .border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
    )
}

@Composable
fun InfoItem(
    title: String,
    subtext: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = { InfoIconBox() }
) {
    val finalModifier = remember(subtext, modifier) {
        if (modifier == Modifier) {
            Modifier
                .padding(horizontal = 20.dp)
                .padding(vertical = if (subtext == null) 14.dp else 8.dp)
        } else {
            modifier
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(finalModifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.W400
            )
            if (subtext != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtext,
                    color = Color.White.copy(alpha = 0.54f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun DashedDivider(
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.35f),
    thickness: Dp = 1.dp,
    dashLength: Dp = 6.dp,
    gapLength: Dp = 4.dp
) {
    Canvas(modifier = modifier.fillMaxWidth().height(thickness)) {
        val dashLengthPx = dashLength.toPx()
        val gapLengthPx = gapLength.toPx()
        val thicknessPx = thickness.toPx()
        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLengthPx, gapLengthPx), 0f)
        
        drawLine(
            color = color,
            start = Offset(0f, thicknessPx / 2),
            end = Offset(size.width, thicknessPx / 2),
            strokeWidth = thicknessPx,
            pathEffect = pathEffect
        )
    }
}

@Composable
fun ActionIconCircle() {
    Box(
        modifier = Modifier
            .size(24.dp)
            .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
    )
}

@Composable
fun ActionItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = { ActionIconCircle() },
    textColor: Color = Color.White
) {
    val finalModifier = remember(modifier) {
        if (modifier == Modifier) {
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        } else {
            modifier
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(finalModifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.W400
        )
    }
}

@Composable
fun ProfileEditDialog(
    title: String,
    initialValue: String,
    label: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var textVal by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2025),
        title = {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            OutlinedTextField(
                value = textVal,
                onValueChange = { textVal = it },
                label = { Text(label, color = Color.White.copy(alpha = 0.5f)) },
                textStyle = TextStyle(color = Color.White),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AuraColors.Primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    cursorColor = AuraColors.Primary
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(textVal)
                    onDismiss()
                }
            ) {
                Text("Save", color = AuraColors.Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.6f))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdayBottomSheet(
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    val days = (1..31).map { it.toString() }
    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val years = (1875..currentYear).map { it.toString() } + "—"

    // Parse initial state with current date fallback
    val calendar = java.util.Calendar.getInstance()
    var parsedDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)
    var parsedMonthIndex = calendar.get(java.util.Calendar.MONTH) // 0-based
    var parsedYearString = currentYear.toString()
    
    if (initialValue.isNotEmpty()) {
        try {
            val parts = initialValue.split(" ")
            if (parts.size >= 1) {
                parsedDay = parts[0].toIntOrNull() ?: parsedDay
            }
            if (parts.size >= 2) {
                val mIndex = months.indexOfFirst { it.equals(parts[1], ignoreCase = true) }
                if (mIndex != -1) {
                    parsedMonthIndex = mIndex
                }
            }
            if (parts.size >= 3) {
                parsedYearString = parts[2]
            }
        } catch (e: Exception) {
            // fallback
        }
    }

    var selectedDay by remember { mutableStateOf(parsedDay.toString()) }
    var selectedMonth by remember { mutableStateOf(months[parsedMonthIndex]) }
    var selectedYear by remember { mutableStateOf(parsedYearString) }

    val initialDayIndex = days.indexOf(selectedDay).coerceAtLeast(0)
    val initialMonthIndex = parsedMonthIndex.coerceIn(0, months.size - 1)
    val initialYearIndex = years.indexOf(selectedYear).coerceAtLeast(0)

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E2025),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 20.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Birthday",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Columns and selected highlight
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                // Two parallel horizontal lines for selection (above and below the 40.dp selected row)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    HorizontalDivider(color = AuraColors.Primary, thickness = 1.dp)
                    HorizontalDivider(color = AuraColors.Primary, thickness = 1.dp)
                }

                Row(
                    modifier = Modifier.width(340.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    WheelColumn(
                        items = days,
                        initialIndex = initialDayIndex,
                        onItemSelected = { selectedDay = it },
                        modifier = Modifier.weight(1f)
                    )
                    WheelColumn(
                        items = months,
                        initialIndex = initialMonthIndex,
                        onItemSelected = { selectedMonth = it },
                        modifier = Modifier.weight(1.2f)
                    )
                    WheelColumn(
                        items = years,
                        initialIndex = initialYearIndex,
                        onItemSelected = { selectedYear = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save Button
            Button(
                onClick = {
                    val result = if (selectedYear == "—") {
                        "$selectedDay $selectedMonth"
                    } else {
                        "$selectedDay $selectedMonth $selectedYear"
                    }
                    onSave(result)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuraColors.Primary,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = "Save",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Remove Button
            TextButton(
                onClick = {
                    onSave("") // empty string to clear/remove birthday
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = "Remove",
                    color = AuraColors.Primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelColumn(
    items: List<String>,
    initialIndex: Int,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val displayDensity = context.resources.displayMetrics.density
    val itemHeight = 40.dp
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState)
    
    val selectedIndex by remember {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (visibleItemsInfo.isEmpty()) {
                0
            } else {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                visibleItemsInfo.minByOrNull { 
                    val diff = (it.offset + it.size / 2) - viewportCenter
                    if (diff < 0) -diff else diff
                }?.index ?: 0
            }
        }
    }
    
    // Propagate selection changes
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in items.indices) {
            onItemSelected(items[selectedIndex])
        }
    }
    
    LazyColumn(
        state = lazyListState,
        flingBehavior = flingBehavior,
        modifier = modifier
            .height(220.dp)
            .fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 90.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(items.size) { index ->
            val item = items[index]
            val isSelected = index == selectedIndex
            val itemInfo = remember {
                derivedStateOf {
                    lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                }
            }.value
            
            Box(
                modifier = Modifier
                    .height(itemHeight)
                    .fillMaxWidth()
                    .graphicsLayer {
                        if (itemInfo != null) {
                            val viewportCenter = (lazyListState.layoutInfo.viewportStartOffset + lazyListState.layoutInfo.viewportEndOffset) / 2f
                            val itemCenter = itemInfo.offset + itemInfo.size / 2f
                            val maxDistance = 100f * displayDensity
                            if (maxDistance > 0f) {
                                val progress = ((itemCenter - viewportCenter) / maxDistance).coerceIn(-1.3f, 1.3f)
                                
                                // Angle on the cylinder
                                val maxAngle = 1.2f
                                val theta = progress * maxAngle
                                
                                // Cylindrical 3D rotation around X-axis
                                rotationX = progress * -55f
                                
                                // Mathematical translation adjustment to project onto cylinder
                                translationY = maxDistance * (kotlin.math.sin(theta) - theta)
                                
                                // Scale down items away from center
                                val absProgress = if (progress < 0f) -progress else progress
                                val scale = 1f - (absProgress * 0.15f)
                                scaleX = scale
                                scaleY = scale
                                
                                // Smooth alpha fade out (fades exactly at edges)
                                alpha = kotlin.math.cos(theta).coerceAtLeast(0f)
                                
                                cameraDistance = 8f * displayDensity
                            }
                        } else {
                            rotationX = 0f
                            translationY = 0f
                            scaleX = 0.7f
                            scaleY = 0.7f
                            alpha = 0f
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.4f),
                    fontSize = if (isSelected) 18.sp else 15.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun ZoomSlider(
    linearZoom: Float,
    zoomRatio: Float,
    minZoomRatio: Float,
    maxZoomRatio: Float,
    onZoomChange: (Float) -> Unit,
    onZoomRatioChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    
    // Internal animatable to smoothly handle drag snap animations
    val animatedZoom = remember { Animatable(linearZoom) }
    
    // Sync animatedZoom with external linearZoom updates (like pinch-to-zoom or button tap)
    LaunchedEffect(linearZoom) {
        val diff = abs(animatedZoom.value - linearZoom)
        if (diff > 0.05f) {
            animatedZoom.animateTo(
                linearZoom,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
            )
        } else if (diff > 0.001f) {
            animatedZoom.snapTo(linearZoom)
        }
    }
    
    val notchCount = 51 // 0 to 50 notches
    val spacingDp = 8.dp
    val spacingPx = with(density) { spacingDp.toPx() }
    val totalRangePx = (notchCount - 1) * spacingPx
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Circular Zoom Value Indicator Badge (above the slider)
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                .clickable {
                    // Cycles zoom ratio: 1.0x -> 1.5x -> 2.0x -> 4.0x -> 8.0x -> 1.0x
                    val targetRatio = when {
                        zoomRatio < 1.0f -> 1.0f
                        zoomRatio >= 1.0f && zoomRatio < 1.49f -> 1.5f
                        zoomRatio >= 1.49f && zoomRatio < 1.99f -> 2.0f
                        zoomRatio >= 1.99f && zoomRatio < 3.99f -> 4.0f
                        zoomRatio >= 3.99f && zoomRatio < 7.99f -> 8.0f
                        else -> 1.0f
                    }.coerceIn(minZoomRatio, maxZoomRatio)
                    
                    onZoomRatioChange(targetRatio)
                },
            contentAlignment = Alignment.Center
        ) {
            // Draw circular progress arc around the text
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 2.dp.toPx()
                // Background circle track
                drawCircle(
                    color = Color.White.copy(alpha = 0.15f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                )
                // Active arc representing current zoom fraction
                drawArc(
                    color = Color(0xFF10B981), // AuraColors.Primary
                    startAngle = -90f,
                    sweepAngle = linearZoom * 360f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                )
            }
            
            // Format zoom ratio text e.g. "1.0x", "2.4x"
            val formattedText = remember(zoomRatio) {
                val ratioInt10 = (zoomRatio * 10).toInt()
                val dec = ratioInt10 % 10
                val whole = ratioInt10 / 10
                "${whole}.${dec}x"
            }
            
            Text(
                text = formattedText,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 2. Horizontally Draggable Notch Ruler Slider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .pointerInput(totalRangePx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            // Spring-snap to the nearest 2% step (notch spacing snap)
                            val nearestStep = (animatedZoom.value * (notchCount - 1)).roundToInt()
                            val snappedValue = nearestStep.toFloat() / (notchCount - 1)
                            coroutineScope.launch {
                                animatedZoom.animateTo(
                                    snappedValue,
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                                )
                                onZoomChange(snappedValue)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val delta = -dragAmount / totalRangePx
                            val newValue = (animatedZoom.value + delta).coerceIn(0f, 1f)
                            coroutineScope.launch {
                                animatedZoom.snapTo(newValue)
                            }
                            onZoomChange(newValue)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                
                // Offset calculation based on the current animated value
                val offset = -animatedZoom.value * totalRangePx
                
                val maxDistance = 120.dp.toPx()
                
                // Draw Notch Marks
                for (i in 0 until notchCount) {
                    val notchX = centerX + (i * spacingPx) + offset
                    
                    // Only draw notches inside the visible bounds
                    if (notchX >= 0f && notchX <= size.width) {
                        val distance = abs(notchX - centerX)
                        if (distance <= maxDistance) {
                            // Proximity height and opacity
                            val proximity = (1f - (distance / maxDistance)).coerceIn(0f, 1f)
                            val notchHeight = 6.dp.toPx() + (proximity * 16.dp.toPx())
                            val notchAlpha = 0.2f + (proximity * 0.8f)
                            
                            // Highlight every 5th or 10th notch with a slightly wider/taller line
                            val isMajor = i % 5 == 0
                            val strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx()
                            val color = if (isMajor) Color.White else Color.White.copy(alpha = 0.6f)
                            
                            drawLine(
                                color = color.copy(alpha = notchAlpha),
                                start = Offset(notchX, centerY - notchHeight / 2f),
                                end = Offset(notchX, centerY + notchHeight / 2f),
                                strokeWidth = strokeWidth
                            )
                        }
                    }
                }
                
                // 3. Central Fixed Ticker (Draw vertical accent colored pointer line)
                val tickerHeight = 28.dp.toPx()
                drawLine(
                    color = Color(0xFF10B981), // AuraColors.Primary
                    start = Offset(centerX, centerY - tickerHeight / 2f),
                    end = Offset(centerX, centerY + tickerHeight / 2f),
                    strokeWidth = 2.5.dp.toPx()
                )
            }
        }
    }
}

internal fun saveImageToPublicGallery(context: android.content.Context, file: File, displayName: String) {
    try {
        val resolver = context.contentResolver
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/AuraChat")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                contentValues.clear()
                contentValues.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(it, contentValues, null, null)
            }
        } else {
            val publicDir = File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),
                "AuraChat"
            )
            if (!publicDir.exists()) {
                publicDir.mkdirs()
            }
            val publicFile = File(publicDir, displayName)
            file.inputStream().use { inputStream ->
                publicFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(publicFile.absolutePath),
                arrayOf("image/jpeg"),
                null
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

