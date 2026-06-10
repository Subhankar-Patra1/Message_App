package com.subhankar.aurachat.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.subhankar.aurachat.ui.theme.AuraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

fun clampOffset(
    proposed: Offset,
    scale: Float,
    rotationDeg: Float,
    baseW: Float,
    baseH: Float,
    circleRad: Float
): Offset {
    val phiRad = Math.toRadians(rotationDeg.toDouble())
    val cosVal = Math.cos(-phiRad).toFloat()
    val sinVal = Math.sin(-phiRad).toFloat()

    val localX = proposed.x * cosVal - proposed.y * sinVal
    val localY = proposed.x * sinVal + proposed.y * cosVal

    val limitX = (baseW * scale) / 2f - circleRad
    val limitY = (baseH * scale) / 2f - circleRad

    val clampedLocalX = localX.coerceIn(-max(0f, limitX), max(0f, limitX))
    val clampedLocalY = localY.coerceIn(-max(0f, limitY), max(0f, limitY))

    val cosBack = Math.cos(phiRad).toFloat()
    val sinBack = Math.sin(phiRad).toFloat()

    return Offset(
        x = clampedLocalX * cosBack - clampedLocalY * sinBack,
        y = clampedLocalX * sinBack + clampedLocalY * cosBack
    )
}

@Composable
fun PhotoEditorScreen(
    imageUriString: String,
    onBack: () -> Unit,
    onSave: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }

    // Original bitmap dimensions
    var originalWidth by remember { mutableStateOf(1) }
    var originalHeight by remember { mutableStateOf(1) }

    // User transformation states
    val scaleState = remember { mutableStateOf(1f) }
    val offsetState = remember { mutableStateOf(Offset.Zero) }
    
    val rotationAngleState = remember { mutableStateOf(0f) }

    val rotationAngle by remember {
        derivedStateOf { rotationAngleState.value }
    }

    val isFlippedState = remember { mutableStateOf(false) }

    // Discard dialog state
    var showDiscardDialog by remember { mutableStateOf(false) }

    var isInitialized by remember { mutableStateOf(false) }

    // Load original dimensions
    LaunchedEffect(imageUriString) {
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(imageUriString)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(inputStream, null, options)
                    originalWidth = if (options.outWidth > 0) options.outWidth else 1
                    originalHeight = if (options.outHeight > 0) options.outHeight else 1
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Container size tracking
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    // Viewport coordinates state in pixels (fixed circular viewport)
    val cropLeftState = remember { mutableStateOf(0f) }
    val cropTopState = remember { mutableStateOf(0f) }
    val cropRightState = remember { mutableStateOf(0f) }
    val cropBottomState = remember { mutableStateOf(0f) }

    // Aspect ratio of the original image
    val originalAspectRatio = originalWidth.toFloat() / originalHeight.toFloat()

    // Calculate baseWidth and baseHeight of the image layout
    val baseWidth by remember(containerSize, originalAspectRatio) {
        derivedStateOf {
            if (containerSize.width <= 0) 1f else {
                with(density) {
                    val refW = (containerSize.width.toFloat() - 48.dp.toPx()).coerceAtLeast(100.dp.toPx())
                    if (originalAspectRatio > 1f) refW * originalAspectRatio else refW
                }
            }
        }
    }

    val baseHeight by remember(containerSize, originalAspectRatio) {
        derivedStateOf {
            if (containerSize.width <= 0) 1f else {
                with(density) {
                    val refW = (containerSize.width.toFloat() - 48.dp.toPx()).coerceAtLeast(100.dp.toPx())
                    if (originalAspectRatio > 1f) refW else refW / originalAspectRatio
                }
            }
        }
    }

    // Circle size: min(width, height) - 48.dp
    val circleSize by remember(containerSize) {
        derivedStateOf {
            if (containerSize.width <= 0) 0f else {
                with(density) {
                    (min(containerSize.width.toFloat(), containerSize.height.toFloat()) - 48.dp.toPx())
                        .coerceAtLeast(100.dp.toPx())
                }
            }
        }
    }

    val circleRadius by remember(circleSize) {
        derivedStateOf { circleSize / 2f }
    }

    val minScale by remember(circleSize, baseWidth, baseHeight) {
        derivedStateOf {
            if (circleSize <= 0f) 1f else {
                max(circleSize / baseWidth, circleSize / baseHeight)
            }
        }
    }

    // Track whether user has made any changes
    val hasChanges by remember(minScale, isInitialized) {
        derivedStateOf {
            isInitialized && (
                abs(scaleState.value - minScale) > 0.001f ||
                offsetState.value != Offset.Zero ||
                rotationAngleState.value != 0f ||
                isFlippedState.value
            )
        }
    }

    // Initialize crop coordinates and scale once containerSize is known
    LaunchedEffect(containerSize, circleSize, minScale) {
        if (containerSize.width > 0 && circleSize > 0f) {
            val circleCenterX = containerSize.width.toFloat() / 2f
            val circleCenterY = containerSize.height.toFloat() / 2f

            cropLeftState.value = circleCenterX - circleRadius
            cropTopState.value = circleCenterY - circleRadius
            cropRightState.value = circleCenterX + circleRadius
            cropBottomState.value = circleCenterY + circleRadius

            scaleState.value = minScale
            offsetState.value = Offset.Zero
            isInitialized = true
        }
    }

    // Compute visual width and height after applying 90-degree step rotations
    val isRotated90or270 by remember(rotationAngle) {
        derivedStateOf { (rotationAngle / 90f).roundToInt() % 2 != 0 }
    }
    val visualWidth by remember(baseWidth, baseHeight, isRotated90or270) {
        derivedStateOf { if (isRotated90or270) baseHeight else baseWidth }
    }
    val visualHeight by remember(baseWidth, baseHeight, isRotated90or270) {
        derivedStateOf { if (isRotated90or270) baseWidth else baseHeight }
    }

    // ════════════════════════════════════════════════
    //  DISCARD CHANGES DIALOG
    // ════════════════════════════════════════════════
    if (showDiscardDialog) {
        Dialog(
            onDismissRequest = { showDiscardDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Discard changes?",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Your edits will be lost.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cancel button
                        OutlinedButton(
                            onClick = { showDiscardDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.3f))
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            )
                        ) {
                            Text("Cancel", fontWeight = FontWeight.Medium)
                        }

                        // Discard button
                        Button(
                            onClick = {
                                showDiscardDialog = false
                                onBack()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE53935),
                                contentColor = Color.White
                            )
                        ) {
                            Text("Discard", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    // ════════════════════════════════════════════════
    //  MAIN LAYOUT
    // ════════════════════════════════════════════════
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .systemBarsPadding()
            .onSizeChanged { containerSize = it }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {


            // ────────────────────────────────────────────
            //  IMAGE  +  CROP OVERLAY
            // ────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                if (containerSize.width > 0 && cropRightState.value > 0f) {
                    // Transformed image
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .requiredSize(
                                    width = with(density) { baseWidth.toDp() },
                                    height = with(density) { baseHeight.toDp() }
                                )
                                .graphicsLayer {
                                    scaleX = scaleState.value * (if (isFlippedState.value) -1f else 1f)
                                    scaleY = scaleState.value
                                    rotationZ = rotationAngle
                                    translationX = offsetState.value.x
                                    translationY = offsetState.value.y
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(imageUriString)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .build(),
                                contentDescription = "Editing image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    // Top-most touch and draw Canvas
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(alpha = 0.99f)
                            .pointerInput(baseWidth, baseHeight) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        down.consume()

                                        fun dist(pt1: Offset, pt2: Offset): Float {
                                            val dx = pt1.x - pt2.x
                                            val dy = pt1.y - pt2.y
                                            return sqrt(dx * dx + dy * dy)
                                        }

                                        var lastCentroid = down.position
                                        var lastDistance = 0f
                                        var lastActivePointerCount = 1

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val activePointers = event.changes.filter { it.pressed }

                                            if (activePointers.isEmpty()) {
                                                break
                                            }

                                            // Calculate current radius and minScale
                                            val currentCircleRadius = circleRadius
                                            val currentMinScale = minScale

                                            val centroid = if (activePointers.size == 1) {
                                                activePointers[0].position
                                            } else {
                                                val sum = activePointers.fold(Offset.Zero) { acc, p -> acc + p.position }
                                                sum / activePointers.size.toFloat()
                                            }

                                            // Reset baseline values on pointer count transition to avoid jumps
                                            if (activePointers.size != lastActivePointerCount) {
                                                lastCentroid = centroid
                                                if (activePointers.size >= 2) {
                                                    lastDistance = dist(activePointers[0].position, activePointers[1].position)
                                                }
                                                lastActivePointerCount = activePointers.size
                                                activePointers.forEach { it.consume() }
                                                continue
                                            }

                                            if (activePointers.size >= 2) {
                                                // Multi-touch Zoom/Pan/Rotate
                                                val p1 = activePointers[0].position
                                                val p2 = activePointers[1].position
                                                val currentDist = dist(p1, p2)
                                                val zoom = if (lastDistance > 0f) currentDist / lastDistance else 1f
                                                val pan = centroid - lastCentroid

                                                // Calculate angle delta for two-finger rotation
                                                val prevP1 = activePointers[0].previousPosition
                                                val prevP2 = activePointers[1].previousPosition
                                                
                                                var deltaRotationDeg = 0f
                                                val prevVec = prevP2 - prevP1
                                                val currVec = p2 - p1
                                                if (prevVec.getDistanceSquared() > 0f && currVec.getDistanceSquared() > 0f) {
                                                    val prevAngle = Math.atan2(prevVec.y.toDouble(), prevVec.x.toDouble())
                                                    val currAngle = Math.atan2(currVec.y.toDouble(), currVec.x.toDouble())
                                                    var diff = Math.toDegrees(currAngle - prevAngle).toFloat()
                                                    if (diff > 180f) diff -= 360f
                                                    if (diff < -180f) diff += 360f
                                                    deltaRotationDeg = diff
                                                }

                                                // Update scale
                                                val currentScale = scaleState.value
                                                val newScale = (currentScale * zoom).coerceIn(currentMinScale, currentMinScale * 5f)
                                                scaleState.value = newScale

                                                // Update rotation state directly
                                                val proposedTotalAngle = rotationAngleState.value + deltaRotationDeg
                                                val newRotationAngle = (proposedTotalAngle % 360f + 360f) % 360f
                                                rotationAngleState.value = newRotationAngle

                                                // Update offset
                                                val zoomFactor = newScale / currentScale
                                                val screenCenter = Offset(size.width / 2f, size.height / 2f)
                                                val relativeCentroid = centroid - screenCenter

                                                val currentOffset = offsetState.value
                                                val proposedOffsetX = currentOffset.x * zoomFactor + pan.x + relativeCentroid.x * (1f - zoomFactor)
                                                val proposedOffsetY = currentOffset.y * zoomFactor + pan.y + relativeCentroid.y * (1f - zoomFactor)

                                                offsetState.value = clampOffset(
                                                    proposed = Offset(proposedOffsetX, proposedOffsetY),
                                                    scale = newScale,
                                                    rotationDeg = newRotationAngle,
                                                    baseW = baseWidth,
                                                    baseH = baseHeight,
                                                    circleRad = currentCircleRadius
                                                )

                                                lastCentroid = centroid
                                                lastDistance = currentDist
                                                activePointers.forEach { it.consume() }
                                            } else {
                                                // Single touch pan
                                                val pan = centroid - lastCentroid

                                                val currentOffset = offsetState.value
                                                val proposedOffsetX = currentOffset.x + pan.x
                                                val proposedOffsetY = currentOffset.y + pan.y

                                                offsetState.value = clampOffset(
                                                    proposed = Offset(proposedOffsetX, proposedOffsetY),
                                                    scale = scaleState.value,
                                                    rotationDeg = rotationAngle,
                                                    baseW = baseWidth,
                                                    baseH = baseHeight,
                                                    circleRad = currentCircleRadius
                                                )
                                                lastCentroid = centroid
                                                activePointers.forEach { it.consume() }
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        val canvasW = size.width
                        val canvasH = size.height
                        val centerVal = Offset(canvasW / 2f, canvasH / 2f)
                        val radiusVal = circleRadius

                        // 1) Dim entire canvas
                        drawRect(color = Color.Black.copy(alpha = 0.55f))

                        // 2) Cut out the crop circle (transparent window)
                        drawCircle(
                            color = Color.Transparent,
                            radius = radiusVal,
                            center = centerVal,
                            blendMode = BlendMode.Clear
                        )

                        // 3) Draw a thin white circular outline around the viewport
                        drawCircle(
                            color = Color.White.copy(alpha = 0.8f),
                            radius = radiusVal,
                            center = centerVal,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    // Top Bar Overlay
                    androidx.compose.animation.AnimatedVisibility(
                        visible = hasChanges,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Undo / reset circle
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.4f))
                                    .clickable(enabled = !isProcessing) {
                                        scaleState.value = minScale
                                        offsetState.value = Offset.Zero
                                        rotationAngleState.value = 0f
                                        isFlippedState.value = false
                                        
                                        val circleCenterX = containerSize.width.toFloat() / 2f
                                        val circleCenterY = containerSize.height.toFloat() / 2f
                                        cropLeftState.value = circleCenterX - circleRadius
                                        cropTopState.value = circleCenterY - circleRadius
                                        cropRightState.value = circleCenterX + circleRadius
                                        cropBottomState.value = circleCenterY + circleRadius
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "Undo all",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Clear all pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.Black.copy(alpha = 0.4f))
                                    .clickable(enabled = !isProcessing) {
                                        scaleState.value = minScale
                                        offsetState.value = Offset.Zero
                                        rotationAngleState.value = 0f
                                        isFlippedState.value = false
                                        
                                        val circleCenterX = containerSize.width.toFloat() / 2f
                                        val circleCenterY = containerSize.height.toFloat() / 2f
                                        cropLeftState.value = circleCenterX - circleRadius
                                        cropTopState.value = circleCenterY - circleRadius
                                        cropRightState.value = circleCenterX + circleRadius
                                        cropBottomState.value = circleCenterY + circleRadius
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Clear all",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // ────────────────────────────────────────────
            //  ROTATION RULER (Interactive)
            // ────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Current angle label
                val displayAngle = "${(rotationAngleState.value.roundToInt() % 360 + 360) % 360}°"
                Text(
                    text = displayAngle,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Tick-mark ruler
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .padding(horizontal = 24.dp)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                val deltaAngle = -dragAmount * 0.15f
                                val newAngle = ((rotationAngleState.value + deltaAngle) % 360f + 360f) % 360f
                                rotationAngleState.value = newAngle
                                
                                offsetState.value = clampOffset(
                                    proposed = offsetState.value,
                                    scale = scaleState.value,
                                    rotationDeg = newAngle,
                                    baseW = baseWidth,
                                    baseH = baseHeight,
                                    circleRad = circleRadius
                                )
                            }
                        }
                ) {
                    val pixelsPerDegree = size.width / 90f // 90 degrees total visible window
                    
                    // Draw ticks every 5 degrees
                    for (deg in 0 until 360 step 5) {
                        var diff = deg.toFloat() - rotationAngleState.value
                        while (diff > 180f) diff -= 360f
                        while (diff < -180f) diff += 360f
                        
                        val x = size.width / 2f + diff * pixelsPerDegree
                        if (x in 0f..size.width) {
                            val isZero = deg == 0
                            val isMajor = deg % 45 == 0 // Major tick every 45 degrees
                            
                            val tickH = if (isZero) size.height * 0.85f else if (isMajor) size.height * 0.6f else size.height * 0.35f
                            val tickColor = if (isZero) Color.White else Color.White.copy(alpha = 0.3f)
                            val tickWidth = if (isZero) 1.5.dp.toPx() else 1.dp.toPx()

                            drawLine(
                                color = tickColor,
                                start = Offset(x, size.height - tickH),
                                end = Offset(x, size.height),
                                strokeWidth = tickWidth,
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // Static center red cursor marker
                    drawLine(
                        color = Color(0xFFE53935),
                        start = Offset(size.width / 2f, 0f),
                        end = Offset(size.width / 2f, size.height),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // ────────────────────────────────────────────
            //  BOTTOM TOOLBAR  —  ✕  ↻  ⇆  ✓
            // ────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Cancel (✕)
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C2C2C))
                        .clickable(enabled = !isProcessing) {
                            if (hasChanges) {
                                showDiscardDialog = true
                            } else {
                                onBack()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Rotate 90°
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !isProcessing) {
                            val newAngle = (rotationAngleState.value + 90f) % 360f
                            rotationAngleState.value = newAngle
                            
                            val currentMinScale = minScale
                            if (scaleState.value < currentMinScale) {
                                scaleState.value = currentMinScale
                            }
                            
                            offsetState.value = clampOffset(
                                proposed = offsetState.value,
                                scale = scaleState.value,
                                rotationDeg = newAngle,
                                baseW = baseWidth,
                                baseH = baseHeight,
                                circleRad = circleRadius
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RotateRight,
                        contentDescription = "Rotate",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Flip horizontal
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !isProcessing) { isFlippedState.value = !isFlippedState.value },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Flip,
                        contentDescription = "Flip",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Confirm (✓)
                if (isProcessing) {
                    Box(
                        modifier = Modifier.size(46.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = AuraColors.Primary,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2C2C2C))
                            .clickable {
                                isProcessing = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val uri = Uri.parse(imageUriString)
                                        val inputStream = context.contentResolver.openInputStream(uri)
                                        val originalBitmap = BitmapFactory.decodeStream(inputStream)
                                        inputStream?.close()

                                        if (originalBitmap == null) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Failed to decode photo", Toast.LENGTH_SHORT).show()
                                                isProcessing = false
                                            }
                                            return@launch
                                        }

                                        // 1. Calculate scale factor from screen to original bitmap
                                        val scaleFactor = originalBitmap.width.toFloat() / baseWidth

                                        val cropLeft = cropLeftState.value
                                        val cropTop = cropTopState.value
                                        val cropRight = cropRightState.value
                                        val cropBottom = cropBottomState.value

                                        var cropWidthPx = ((cropRight - cropLeft) * scaleFactor).roundToInt().coerceAtLeast(1)
                                        var cropHeightPx = ((cropBottom - cropTop) * scaleFactor).roundToInt().coerceAtLeast(1)

                                        val maxDimension = 2048
                                        val ratio = if (cropWidthPx > maxDimension || cropHeightPx > maxDimension) {
                                            maxDimension.toFloat() / max(cropWidthPx, cropHeightPx).toFloat()
                                        } else {
                                            1f
                                        }

                                        if (ratio < 1f) {
                                            cropWidthPx = (cropWidthPx * ratio).roundToInt().coerceAtLeast(1)
                                            cropHeightPx = (cropHeightPx * ratio).roundToInt().coerceAtLeast(1)
                                        }

                                        val finalScale = scaleFactor * ratio

                                        // 2. Create the target cropped bitmap
                                        val croppedBitmap = Bitmap.createBitmap(cropWidthPx, cropHeightPx, Bitmap.Config.ARGB_8888)
                                        val canvas = android.graphics.Canvas(croppedBitmap)
                                        canvas.drawColor(android.graphics.Color.BLACK)

                                        // 3. Construct transformation matrix matching Compose UI
                                        val matrix = Matrix()
                                        // Translate original bitmap center to (0,0) at visual scale
                                        matrix.postTranslate(-originalBitmap.width / 2f, -originalBitmap.height / 2f)
                                        matrix.postScale(baseWidth / originalBitmap.width.toFloat(), baseHeight / originalBitmap.height.toFloat())

                                        // Apply user transformations
                                        if (isFlippedState.value) {
                                            matrix.postScale(-1f, 1f)
                                        }
                                        matrix.postScale(scaleState.value, scaleState.value)
                                        matrix.postRotate(rotationAngle)
                                        matrix.postTranslate(offsetState.value.x, offsetState.value.y)

                                        // Map crop box top-left to canvas (0,0)
                                        val cx = containerSize.width / 2f
                                        val cy = containerSize.height / 2f
                                        val cropLeftRelative = cropLeft - cx
                                        val cropTopRelative = cropTop - cy
                                        matrix.postTranslate(-cropLeftRelative, -cropTopRelative)

                                        // Scale up to final output size
                                        matrix.postScale(finalScale, finalScale)

                                        // 4. Draw original bitmap with the matrix
                                        val paint = android.graphics.Paint().apply {
                                            isFilterBitmap = true
                                        }
                                        canvas.drawBitmap(originalBitmap, matrix, paint)

                                        // 5. Save to cache
                                        val cacheFile = File(
                                            context.cacheDir,
                                            "profile_photo_edited_${System.currentTimeMillis()}.jpg"
                                        )
                                        FileOutputStream(cacheFile).use { outStream ->
                                            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outStream)
                                        }

                                        // 4. Save to public gallery
                                        saveImageToPublicGallery(
                                            context = context,
                                            file = cacheFile,
                                            displayName = "profile_edited_${System.currentTimeMillis()}.jpg"
                                        )

                                        // Recycle bitmaps
                                        originalBitmap.recycle()
                                        croppedBitmap.recycle()

                                        withContext(Dispatchers.Main) {
                                            isProcessing = false
                                            onSave(Uri.fromFile(cacheFile).toString())
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Error saving photo: ${e.message}", Toast.LENGTH_SHORT).show()
                                            isProcessing = false
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Apply",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
