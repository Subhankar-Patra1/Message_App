package com.subhankar.aurachat.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * AuraChat Design Tokens — exact match of your Flutter color palette.
 *
 * From your Flutter code:
 *   _bgColor = Color(0xFF111318)
 *   _primary = Color(0xFFC3C0FF)
 *   _receivedBubbleColor = Color(0xFF212121)
 *   _sentBubbleColor = Color(0xFF2c6bed)
 */
object AuraColors {
    // Core background
    val Background = Color(0xFF141518)
    val Surface = Color(0xFF1A1B21)
    val SurfaceVariant = Color(0xFF212121)
    val AppBarBackground = Color(0xFF141518)

    // Primary accent (lavender)
    val Primary = Color(0xFFC3C0FF)
    val PrimaryDim = Color(0xFF8A87CC)

    // Chat bubbles
    val SentBubble = Color(0xFF2C6BED)
    val LightBlue = Color(0xFF4C8CFF)
    val ReceivedBubble = Color(0xFF212121)

    // Text
    val TextPrimary = Color.White
    val TextSecondary = Color(0xB3FFFFFF)   // ~70% white
    val TextTertiary = Color(0x66FFFFFF)    // ~40% white
    val TextHint = Color(0x8AFFFFFF)        // ~54% white

    // Receipt icons
    val ReadBlue = Color(0xFF4FC3F7)

    // Avatar palette (matching your _avatarColors)
    val AvatarColors = listOf(
        Color(0xFFC3C0FF),
        Color(0xFFFFC0C0),
        Color(0xFFC0FFC3),
        Color(0xFFFFF6C0),
        Color(0xFFC0F4FF),
    )

    // Misc
    val Divider = Color(0x0DFFFFFF)        // ~5% white
    val Error = Color(0xFFFF5252)
}
