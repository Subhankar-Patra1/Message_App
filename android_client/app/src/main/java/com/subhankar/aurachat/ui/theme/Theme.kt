package com.subhankar.aurachat.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * AuraChat Theme — always dark, matching your Flutter design.
 * Uses your exact color palette instead of Material You dynamic colors.
 */
private val AuraDarkColorScheme = darkColorScheme(
    primary = AuraColors.Primary,
    onPrimary = AuraColors.Background,
    secondary = AuraColors.PrimaryDim,
    surface = AuraColors.Surface,
    onSurface = AuraColors.TextPrimary,
    background = AuraColors.Background,
    onBackground = AuraColors.TextPrimary,
    surfaceVariant = AuraColors.SurfaceVariant,
    error = AuraColors.Error,
)

@Composable
fun AuraChatTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = AuraDarkColorScheme

    // Make the system bars transparent for edge-to-edge
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = AuraColors.Background.toArgb()
            window.navigationBarColor = AuraColors.Background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}