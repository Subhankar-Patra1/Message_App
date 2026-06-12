package com.subhankar.aurachat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhankar.aurachat.data.local.entity.MessageEntity
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

        // Measure timestamp first
        val timePlaceable = timeMeasurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        val timeWidth = timePlaceable.width
        val timeHeight = timePlaceable.height
        val gap = 6.dp.roundToPx()

        // Measure text
        val textPlaceable = textMeasurable.measure(constraints.copy(minWidth = 0))
        val textWidth = textPlaceable.width
        val textHeight = textPlaceable.height

        val lineCount = textLayoutResult?.lineCount ?: 1
        val lastLineRightPx = textLayoutResult?.let {
            it.getLineRight(lineCount - 1)
        } ?: textWidth.toFloat()

        // Check if there is enough space on the right of the last line of text
        val fits = constraints.maxWidth - lastLineRightPx >= timeWidth + gap

        val layoutWidth: Int
        val layoutHeight: Int

        if (fits) {
            layoutWidth = maxOf(textWidth, (lastLineRightPx + gap + timeWidth).toInt())
            layoutHeight = maxOf(textHeight, timeHeight)
        } else {
            layoutWidth = maxOf(textWidth, timeWidth)
            val verticalGap = 2.dp.roundToPx()
            layoutHeight = textHeight + timeHeight + verticalGap
        }

        layout(layoutWidth, layoutHeight) {
            textPlaceable.placeRelative(0, 0)
            if (fits) {
                timePlaceable.placeRelative(
                    x = layoutWidth - timeWidth,
                    y = layoutHeight - timeHeight
                )
            } else {
                val verticalGap = 2.dp.roundToPx()
                timePlaceable.placeRelative(
                    x = layoutWidth - timeWidth,
                    y = textHeight + verticalGap
                )
            }
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
                    // Tighter padding matching authentic Telegram bubble dimensions
                    .padding(
                        start = if (!isMe && isLastInSequence) 12.dp else 8.dp,
                        end = if (isMe && isLastInSequence) 12.dp else 8.dp,
                        top = 3.dp,
                        bottom = 3.dp
                    )
            ) {
                MessageBubbleLayout(
                    textLayoutResult = textLayoutResult,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    messageText = {
                        Text(
                            text = message.text,
                            color = Color.White,
                            fontSize = 15.sp,
                            onTextLayout = { textLayoutResult = it }
                        )
                    },
                    timestamp = {
                        Text(
                            text = timestampText,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.5.sp
                        )
                    }
                )
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
