package com.subhankar.aurachat.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhankar.aurachat.ui.theme.AuraColors

/**
 * Welcome Screen — exact translation of Flutter welcome_screen.dart.
 *
 * Preserves:
 *   • Chat bubble graphic with frosted glass lock icon
 *   • "Take privacy with you." headline
 *   • "Be yourself in every message." subtitle
 *   • Shimmer "Get Started" button with animated gradient sweep
 *   • Terms & Privacy Policy link
 */
@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit
) {
    Scaffold(
        containerColor = AuraColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(3f))

            // Abstract Chat Bubbles Graphic
            ChatBubbleGraphic()

            Spacer(Modifier.height(48.dp))

            // Headline
            Text(
                "Take privacy with you.",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            // Subtitle
            Text(
                "Be yourself in every message.",
                fontSize = 16.sp,
                color = AuraColors.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.weight(4f))

            // Privacy Policy link
            TextButton(onClick = { }) {
                Text(
                    "Terms & Privacy Policy",
                    color = AuraColors.Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W500
                )
            }

            Spacer(Modifier.height(32.dp))

            // Shimmer "Get Started" button
            ShimmerButton(
                text = "Get Started",
                onClick = onGetStarted
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─── Chat Bubble Graphic ─────────────────────────────────────────

@Composable
private fun ChatBubbleGraphic() {
    Box(
        modifier = Modifier.size(200.dp, 160.dp),
        contentAlignment = Alignment.Center
    ) {
        // Back-left teal bubble
        Box(
            modifier = Modifier
                .offset(x = (-30).dp, y = (-20).dp)
                .size(110.dp, 80.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF6C9E95))
        )

        // Front-right lavender bubble
        Box(
            modifier = Modifier
                .offset(x = 30.dp, y = 20.dp)
                .size(120.dp, 90.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFE3E8FF))
        )

        // Center frosted glass lock
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.2f))
                .border(
                    width = 1.5.dp,
                    color = Color.White.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Lock,
                "Privacy",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

// ─── Shimmer Button ──────────────────────────────────────────────

@Composable
fun ShimmerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // Animate the shimmer position from -1.5 to 2.5 (matching your Dart code)
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerPosition by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerPos"
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(26.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = AuraColors.Primary,
            disabledContainerColor = AuraColors.Primary.copy(alpha = 0.4f)
        ),
        contentPadding = PaddingValues()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    // Draw the shimmer gradient on top
                    if (enabled) {
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0f),
                                    Color.White.copy(alpha = 0.3f),
                                    Color.White.copy(alpha = 0f),
                                ),
                                start = Offset(
                                    x = size.width * (shimmerPosition - 1),
                                    y = 0f
                                ),
                                end = Offset(
                                    x = size.width * (shimmerPosition + 1),
                                    y = size.height
                                )
                            )
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = if (enabled) AuraColors.Background else AuraColors.Background.copy(alpha = 0.5f),
                fontSize = 16.sp,
                fontWeight = FontWeight.W600
            )
        }
    }
}
