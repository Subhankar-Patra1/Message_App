package com.subhankar.aurachat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhankar.aurachat.R
import com.subhankar.aurachat.data.local.entity.MessageEntity
import com.subhankar.aurachat.data.local.entity.MessageStatus
import com.subhankar.aurachat.ui.theme.AuraColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Custom shape that draws smooth, pill-shaped bubbles with Telegram-styled tails
// Custom shape that draws smooth, pill-shaped bubbles with Telegram-styled tails
fun telegramBubbleShape(
    isMe: Boolean,
    isFirstInSequence: Boolean,
    isLastInSequence: Boolean,
    cornerRadius: Float = 56f, // Increased for a smooth, organic pill profile
    tailWidth: Float = 14f,    // Slightly compressed tail width to blend with the pill curve
    tailHeight: Float = 20f    // Tail height optimized for pill styling
): Shape = GenericShape { size, _ ->
    val width = size.width
    val height = size.height

    val bodyWidth = width - tailWidth
    // Constrain corner radius by height / 2f to prevent crossing paths vertically on short bubbles, while maintaining pill-like profile
    val r = minOf(cornerRadius, height / 2f).coerceAtLeast(0f)
    val r16 = minOf(16f, height / 2f).coerceAtLeast(0f)

    if (isMe && isLastInSequence) {
        // --- SENT MESSAGE PILL WITH TAIL (Bottom Right) ---
        // Start at top-left corner transition
        moveTo(r, 0f)
        
        // If it's part of a group (preceded by another sent message), use small radius for top-right corner
        val topRightRadius = if (isFirstInSequence) r else r16
        lineTo(bodyWidth - topRightRadius, 0f)
        
        // Top-Right Corner
        arcTo(
            rect = Rect(left = bodyWidth - 2 * topRightRadius, top = 0f, right = bodyWidth, bottom = 2 * topRightRadius),
            startAngleDegrees = 270f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        
        // Side wall transition blending directly into the tail
        lineTo(bodyWidth, height - tailHeight - 4f)
        
        // Elegant upper tail swoop matching pill profile
        cubicTo(
            bodyWidth, height - (tailHeight * 0.6f),
            bodyWidth + (tailWidth * 0.4f), height - (tailHeight * 0.12f),
            width, height
        )
        
        // Tail bottom swoop rounding back underneath the pill
        cubicTo(
            bodyWidth + (tailWidth * 0.4f), height,
            bodyWidth, height,
            bodyWidth - r, height
        )
        
        // Bottom wall to left arc start
        lineTo(r, height)
        
        // Left Capsule Head (Bottom-Left Corner, Vertical Line, Top-Left Corner)
        arcTo(
            rect = Rect(left = 0f, top = height - 2 * r, right = 2 * r, bottom = height),
            startAngleDegrees = 90f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        lineTo(0f, r)
        arcTo(
            rect = Rect(left = 0f, top = 0f, right = 2 * r, bottom = 2 * r),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        close()
    } else if (!isMe && isLastInSequence) {
        // --- RECEIVED MESSAGE PILL WITH TAIL (Bottom Left) ---
        // If it's part of a group (preceded by another received message), use small radius for top-left corner
        val topLeftRadius = if (isFirstInSequence) r else r16
        // Start at top-left corner
        moveTo(tailWidth + topLeftRadius, 0f)
        lineTo(width - r, 0f)
        
        // Right Capsule Head (Top-Right Corner, Vertical Line, Bottom-Right Corner)
        arcTo(
            rect = Rect(left = width - 2 * r, top = 0f, right = width, bottom = 2 * r),
            startAngleDegrees = 270f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        lineTo(width, height - r)
        arcTo(
            rect = Rect(left = width - 2 * r, top = height - 2 * r, right = width, bottom = height),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        
        // Bottom wall to tail start
        lineTo(tailWidth + r, height)
        
        // Tail bottom swoop rounding underneath the pill
        cubicTo(
            tailWidth * 0.6f, height,
            tailWidth * 0.4f, height,
            0f, height
        )
        
        // Elegant upper tail swoop matching pill profile
        cubicTo(
            tailWidth * 0.4f, height - (tailHeight * 0.12f),
            tailWidth, height - (tailHeight * 0.6f),
            tailWidth, height - tailHeight - 4f
        )
        
        // Left wall transition up
        lineTo(tailWidth, topLeftRadius)
        
        // Top-Left Corner
        arcTo(
            rect = Rect(left = tailWidth, top = 0f, right = tailWidth + 2 * topLeftRadius, bottom = 2 * topLeftRadius),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        close()
    } else {
        // --- MIDDLE/FIRST PILL MESSAGES (No Tail) ---
        val topLeft = if (!isMe && isFirstInSequence) r else if (!isMe) r16 else r
        val topRight = if (isMe && isFirstInSequence) r else if (isMe) r16 else r
        val bottomLeft = if (!isMe) r16 else r
        val bottomRight = if (isMe) r16 else r
        
        val left = if (!isMe) tailWidth else 0f
        val right = if (isMe) width - tailWidth else width
        
        // Start at top-left corner
        moveTo(left + topLeft, 0f)
        lineTo(right - topRight, 0f)
        
        // Top-Right Corner
        arcTo(
            rect = Rect(left = right - 2 * topRight, top = 0f, right = right, bottom = 2 * topRight),
            startAngleDegrees = 270f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        
        lineTo(right, height - bottomRight)
        
        // Bottom-Right Corner
        arcTo(
            rect = Rect(left = right - 2 * bottomRight, top = height - 2 * bottomRight, right = right, bottom = height),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        
        lineTo(left + bottomLeft, height)
        
        // Bottom-Left Corner
        arcTo(
            rect = Rect(left = left, top = height - 2 * bottomLeft, right = left + 2 * bottomLeft, bottom = height),
            startAngleDegrees = 90f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        
        lineTo(left, topLeft)
        
        // Top-Left Corner
        arcTo(
            rect = Rect(left = left, top = 0f, right = left + 2 * topLeft, bottom = 2 * topLeft),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        close()
    }
}


@Composable
fun MessageBubbleLayout(
    modifier: Modifier = Modifier,
    textLayoutResult: TextLayoutResult?,
    messageText: @Composable () -> Unit,
    timestamp: @Composable () -> Unit
) {
    Layout(
        modifier = modifier,
        content = {
            messageText()
            timestamp()
        }
    ) { measurables, constraints ->
        val textMeasurable = measurables[0]
        val timeMeasurable = measurables[1]

        // 1. Measure timestamp with no layout restrictions
        val timePlaceable = timeMeasurable.measure(constraints.copy(minWidth = 0, minHeight = 0))

        // 2. Measure text
        val textPlaceable = textMeasurable.measure(constraints)

        val textWidth = textPlaceable.width
        val textHeight = textPlaceable.height
        val timeWidth = timePlaceable.width
        val timeHeight = timePlaceable.height

        val lineCount = textLayoutResult?.lineCount ?: 1
        val lastLineIndex = if (lineCount > 0) lineCount - 1 else 0

        // Find how wide the last line of text is (ceil for precision)
        val lastLineWidth = textLayoutResult?.getLineRight(lastLineIndex)?.let {
            java.lang.Math.ceil(it.toDouble()).toInt()
        } ?: textWidth

        // 3. Apply the Telegram/WhatsApp multi-line decision tree
        val layoutWidth: Int
        val layoutHeight: Int
        val timeX: Int
        val timeY: Int

        // Spacing buffer between text content and the timestamp
        val timePaddingGap = 8.dp.roundToPx()

        if (lineCount == 1) {
            // CASE 1: Single line text
            val combinedWidth = textWidth + timePaddingGap + timeWidth
            if (combinedWidth <= constraints.maxWidth) {
                // Fits on a single combined horizontal line
                layoutWidth = combinedWidth
                layoutHeight = maxOf(textHeight, timeHeight)
                timeX = layoutWidth - timeWidth
                timeY = layoutHeight - timeHeight
            } else {
                // Pushes onto a structural pseudo-second row
                layoutWidth = maxOf(textWidth, timeWidth)
                layoutHeight = textHeight + timeHeight
                timeX = layoutWidth - timeWidth
                timeY = layoutHeight - timeHeight
            }
        } else {
            // CASE 2: Multi-line text. Check space available on the last line.
            val spaceRemainingOnLastLine = constraints.maxWidth - lastLineWidth
            val spaceNeededForTime = timePaddingGap + timeWidth

            if (spaceRemainingOnLastLine >= spaceNeededForTime) {
                // Time fits to the right of the final line
                layoutWidth = maxOf(textWidth, lastLineWidth + spaceNeededForTime)
                layoutHeight = textHeight
                timeX = layoutWidth - timeWidth
                timeY = layoutHeight - timeHeight
            } else {
                // Final line is crowded; stack time below
                layoutWidth = maxOf(textWidth, timeWidth)
                layoutHeight = textHeight + timeHeight
                timeX = layoutWidth - timeWidth
                timeY = layoutHeight - timeHeight
            }
        }

        // 4. Place elements
        val timeOffset = 8.dp.roundToPx()
        layout(layoutWidth, layoutHeight) {
            textPlaceable.placeRelative(0, 0)
            timePlaceable.placeRelative(timeX, timeY + timeOffset)
        }
    }
}

@Composable
fun MessageBubble(
    message: MessageEntity,
    isMe: Boolean,
    isFirstInSequence: Boolean,
    isLastInSequence: Boolean
) {
    val bubbleColor = if (isMe) AuraColors.SentBubble else AuraColors.ReceivedBubble

    // Compute the exact custom telegram vector shape
    val bubbleShape = telegramBubbleShape(
        isMe = isMe,
        isFirstInSequence = isFirstInSequence,
        isLastInSequence = isLastInSequence
    )

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val timestampText = formatTimestamp(message.serverTs, message.timestamp)

    val fileMeta = remember(message.text) { parseAuraFileUri(message.text) }
    val isImage = fileMeta != null && (fileMeta.mimeType?.startsWith("image/") == true ||
            fileMeta.name.substringAfterLast('.', "").lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif"))
    val isVideo = fileMeta != null && (fileMeta.mimeType?.startsWith("video/") == true ||
            fileMeta.name.substringAfterLast('.', "").lowercase() in listOf("mp4", "mkv", "avi", "mov", "webm"))

    val paddingStart = if (fileMeta != null && (isImage || isVideo)) 0.dp else if (!isMe && isLastInSequence) 12.dp else 8.dp
    val paddingEnd = if (fileMeta != null && (isImage || isVideo)) 0.dp else if (isMe && isLastInSequence) 12.dp else 8.dp
    val paddingTop = if (fileMeta != null && (isImage || isVideo)) 0.dp else 3.dp
    val paddingBottom = if (fileMeta != null && (isImage || isVideo)) 0.dp else 3.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (isLastInSequence) 6.dp else 2.dp)
    ) {
        Box(
            modifier = Modifier
                .align(if (isMe) Alignment.CenterEnd else Alignment.CenterStart)
                .fillMaxWidth(0.75f)
                .wrapContentWidth(if (isMe) Alignment.End else Alignment.Start)
        ) {
            // Bubble layout container
            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(bubbleColor)
                    .padding(
                        start = paddingStart,
                        end = paddingEnd,
                        top = paddingTop,
                        bottom = paddingBottom
                    )
            ) {
                if (fileMeta != null && (isImage || isVideo)) {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(fileMeta.uri)
                                .crossfade(true)
                                .precision(Precision.INEXACT)
                                .build(),
                            contentDescription = "Media File",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        
                        if (isVideo) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .align(Alignment.Center),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = com.subhankar.aurachat.R.drawable.ic_profile_camera),
                                    contentDescription = "Play Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Transparent capsule in bottom right
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 8.dp, end = 12.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = timestampText,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 10.sp
                                )
                                if (isMe) {
                                    val (iconRes, tintColor) = getStatusTickConfig(message.status)
                                    if (iconRes != null && tintColor != null) {
                                        val isPending = message.status == MessageStatus.PENDING ||
                                                message.status == MessageStatus.ENCRYPTING ||
                                                message.status == MessageStatus.RETRYING
                                        if (isPending) {
                                            CanvasClock(tintColor)
                                        } else {
                                            Icon(
                                                painter = painterResource(id = iconRes),
                                                contentDescription = message.status,
                                                tint = Color.White,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (fileMeta != null) {
                    val fileConfig = getBubbleFileTypeConfig(fileMeta.name, fileMeta.mimeType)
                    Column(
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(fileConfig.color.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = fileConfig.iconRes),
                                    contentDescription = fileMeta.name,
                                    tint = fileConfig.color,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fileMeta.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatFileSize(fileMeta.size),
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(
                            modifier = Modifier.align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = timestampText,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp
                            )
                            if (isMe) {
                                val (iconRes, tintColor) = getStatusTickConfig(message.status)
                                if (iconRes != null && tintColor != null) {
                                    val isPending = message.status == MessageStatus.PENDING ||
                                            message.status == MessageStatus.ENCRYPTING ||
                                            message.status == MessageStatus.RETRYING
                                    if (isPending) {
                                        CanvasClock(tintColor)
                                    } else {
                                        Icon(
                                            painter = painterResource(id = iconRes),
                                            contentDescription = message.status,
                                            tint = tintColor,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    MessageBubbleLayout(
                        textLayoutResult = textLayoutResult,
                        modifier = Modifier.padding(start = 6.dp, end = 0.dp, top = 2.dp, bottom = 2.dp),
                        messageText = {
                            Text(
                                text = message.text,
                                color = Color.White,
                                fontSize = 15.sp,
                                onTextLayout = { textLayoutResult = it }
                            )
                        },
                        timestamp = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = timestampText,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 10.5.sp
                                )
                                if (isMe) {
                                    val (iconRes, tintColor) = getStatusTickConfig(message.status)
                                    if (iconRes != null && tintColor != null) {
                                        val isPending = message.status == MessageStatus.PENDING ||
                                                message.status == MessageStatus.ENCRYPTING ||
                                                message.status == MessageStatus.RETRYING

                                        if (isPending) {
                                            CanvasClock(tintColor)
                                        } else {
                                            Icon(
                                                painter = painterResource(id = iconRes),
                                                contentDescription = message.status,
                                                tint = tintColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(serverTs: Long?, fallbackIso: String): String {
    if (serverTs != null) {
        try {
            val time = Instant.ofEpochMilli(serverTs)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
            return time.format(DateTimeFormatter.ofPattern("h:mm a"))
        } catch (e: Exception) {}
    }
    return try {
        val instant = Instant.parse(fallbackIso)
        val time = instant.atZone(ZoneId.systemDefault()).toLocalTime()
        time.format(DateTimeFormatter.ofPattern("h:mm a"))
    } catch (e: Exception) {
        ""
    }
}

private data class AuraFileMetadata(
    val uri: android.net.Uri,
    val name: String,
    val size: Long,
    val mimeType: String?
)

private fun parseAuraFileUri(text: String): AuraFileMetadata? {
    if (!text.startsWith("aura://file?")) return null
    try {
        val query = text.substringAfter("aura://file?")
        val params = query.split("&").associate {
            val parts = it.split("=")
            val key = parts.getOrNull(0) ?: ""
            val value = parts.getOrNull(1) ?: ""
            key to android.net.Uri.decode(value)
        }
        val uriStr = params["uri"] ?: return null
        val name = params["name"] ?: "Unknown File"
        val size = params["size"]?.toLongOrNull() ?: 0L
        val mime = params["mime"]
        return AuraFileMetadata(android.net.Uri.parse(uriStr), name, size, mime)
    } catch (e: Exception) {
        return null
    }
}

private fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private data class BubbleFileTypeConfig(
    val iconRes: Int,
    val color: Color
)

private fun getBubbleFileTypeConfig(fileName: String, mimeType: String?): BubbleFileTypeConfig {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when {
        ext == "pdf" || mimeType == "application/pdf" -> 
            BubbleFileTypeConfig(com.subhankar.aurachat.R.drawable.ic_file, Color(0xFFF44336))
        ext in listOf("doc", "docx") || mimeType?.contains("word") == true -> 
            BubbleFileTypeConfig(com.subhankar.aurachat.R.drawable.ic_file, Color(0xFF2196F3))
        ext in listOf("xls", "xlsx") || mimeType?.contains("excel") == true || mimeType?.contains("spreadsheet") == true -> 
            BubbleFileTypeConfig(com.subhankar.aurachat.R.drawable.ic_file, Color(0xFF4CAF50))
        ext in listOf("ppt", "pptx") || mimeType?.contains("powerpoint") == true || mimeType?.contains("presentation") == true -> 
            BubbleFileTypeConfig(com.subhankar.aurachat.R.drawable.ic_file, Color(0xFFFF9800))
        ext in listOf("zip", "rar", "7z", "tar", "gz") || mimeType?.contains("zip") == true || mimeType?.contains("compressed") == true -> 
            BubbleFileTypeConfig(com.subhankar.aurachat.R.drawable.ic_file, Color(0xFF9C27B0))
        mimeType?.startsWith("image/") == true || ext in listOf("jpg", "jpeg", "png", "webp", "gif") -> 
            BubbleFileTypeConfig(com.subhankar.aurachat.R.drawable.ic_gallery, Color(0xFF00BCD4))
        mimeType?.startsWith("video/") == true || ext in listOf("mp4", "mkv", "avi", "mov", "webm") -> 
            BubbleFileTypeConfig(com.subhankar.aurachat.R.drawable.ic_profile_camera, Color(0xFFFFC107))
        else -> 
            BubbleFileTypeConfig(com.subhankar.aurachat.R.drawable.ic_file, Color(0xFF9E9E9E))
    }
}

private fun getStatusTickConfig(status: String): Pair<Int?, Color?> {
    return when (status) {
        MessageStatus.READ -> Pair(
            R.drawable.ic_check_double,
            Color(0xFF53BDEB) // Blue ticks for read
        )
        MessageStatus.DELIVERED -> Pair(
            R.drawable.ic_check_double,
            Color.White.copy(alpha = 0.6f)
        )
        MessageStatus.ACKED, MessageStatus.SENT -> Pair(
            R.drawable.ic_check_single,
            Color.White.copy(alpha = 0.6f)
        )
        MessageStatus.PENDING, MessageStatus.ENCRYPTING, MessageStatus.RETRYING -> Pair(
            R.drawable.ic_clock_pending,
            Color.White.copy(alpha = 0.6f)
        )
        else -> null to null
    }
}

@Composable
private fun CanvasClock(tintColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "clockRotation")
    val minuteAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "minuteAngle"
    )
    val hourAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hourAngle"
    )

    Canvas(modifier = Modifier.size(11.dp)) {
        val center = this.center
        val radius = size.minDimension / 2

        // Draw outer ring (stroke)
        drawCircle(
            color = tintColor,
            radius = radius - 0.5.dp.toPx(),
            center = center,
            style = Stroke(width = 0.9.dp.toPx())
        )

        // Draw hour hand (shorter)
        val hourAngleRad = Math.toRadians((hourAngle - 90f).toDouble())
        val hourHandLength = radius * 0.35f
        val hourX = center.x + hourHandLength * Math.cos(hourAngleRad).toFloat()
        val hourY = center.y + hourHandLength * Math.sin(hourAngleRad).toFloat()
        drawLine(
            color = tintColor,
            start = center,
            end = Offset(hourX, hourY),
            strokeWidth = 0.9.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Draw minute hand (longer)
        val minAngleRad = Math.toRadians((minuteAngle - 90f).toDouble())
        val minHandLength = radius * 0.52f
        val minX = center.x + minHandLength * Math.cos(minAngleRad).toFloat()
        val minY = center.y + minHandLength * Math.sin(minAngleRad).toFloat()
        drawLine(
            color = tintColor,
            start = center,
            end = Offset(minX, minY),
            strokeWidth = 0.9.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

