package com.subhankar.aurachat.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhankar.aurachat.ui.theme.AuraColors
import java.io.File
import java.io.FileOutputStream

sealed class AvatarBackground {
    data class Solid(val color: Color, val defaultTextColor: Color = Color.White) : AvatarBackground()
    data class Gradient(val colors: List<Color>, val defaultTextColor: Color = Color.White) : AvatarBackground()
}

sealed class AvatarTextColor {
    object Auto : AvatarTextColor()
    data class Custom(val color: Color) : AvatarTextColor()
}

fun AvatarBackground.toBrush(): Brush {
    return when (this) {
        is AvatarBackground.Solid -> SolidColor(color)
        is AvatarBackground.Gradient -> Brush.linearGradient(colors)
    }
}

@Composable
fun TextAvatarEditScreen(
    initialText: String,
    initialBgIndex: Int,
    initialTextColorIndex: Int,
    defaultInitials: String,
    onBack: () -> Unit,
    onSave: (Uri, String, Int, Int) -> Unit
) {
    val context = LocalContext.current
    var textVal by remember { mutableStateOf(initialText) }
    
    // Beautiful background presets (Pastels, Solids and premium Gradients)
    val backgroundPresets = remember {
        listOf(
            // 12 Pastel Colors (From User's Screenshot Reference)
            AvatarBackground.Solid(Color(0xFFE2E5FF), Color(0xFF283593)), // Pastel Lavender/Indigo -> Dark Indigo
            AvatarBackground.Solid(Color(0xFFE3EDFD), Color(0xFF0D47A1)), // Pastel Soft Blue -> Dark Blue
            AvatarBackground.Solid(Color(0xFFE3F2F8), Color(0xFF006064)), // Pastel Baby Blue -> Dark Cyan
            AvatarBackground.Solid(Color(0xFFD4EBD6), Color(0xFF1B5E20)), // Pastel Green -> Dark Green
            AvatarBackground.Solid(Color(0xFFECDDF6), Color(0xFF4A148C)), // Pastel Purple -> Dark Purple
            AvatarBackground.Solid(Color(0xFFF5DCF6), Color(0xFF880E4F)), // Pastel Pink -> Dark Pink
            AvatarBackground.Solid(Color(0xFFF9DBE8), Color(0xFF880E4F)), // Pastel Rose Pink -> Dark Rose
            AvatarBackground.Solid(Color(0xFFFBE1DD), Color(0xFF990000)), // Pastel Peach Coral (Selected) -> Dark Red
            AvatarBackground.Solid(Color(0xFFFFF5CC), Color(0xFFE65100)), // Pastel Yellow -> Dark Amber
            AvatarBackground.Solid(Color(0xFFF5ECD1), Color(0xFF4E342E)), // Pastel Cream Beige -> Dark Brown
            AvatarBackground.Solid(Color(0xFFE2DDF0), Color(0xFF37474F)), // Pastel Lavender Grey -> Dark Slate
            AvatarBackground.Solid(Color(0xFFE3E3E3), Color(0xFF263238)), // Pastel Light Grey -> Dark Grey

            // Classic Solids
            AvatarBackground.Solid(Color(0xFF3F51B5), Color(0xFFE2E5FF)), // Indigo -> Light Lavender
            AvatarBackground.Solid(Color(0xFFE91E63), Color(0xFFFCE4EC)), // Pink -> Light Pink
            AvatarBackground.Solid(Color(0xFF4CAF50), Color(0xFFE2F4E3)), // Green -> Light Green
            AvatarBackground.Solid(Color(0xFFFF9800), Color(0xFF3E1F00)), // Orange -> Dark Brown/Orange
            AvatarBackground.Solid(Color(0xFF00BCD4), Color(0xFF003035)), // Teal/Cyan -> Dark Teal
            AvatarBackground.Solid(Color(0xFF9C27B0), Color(0xFFECDDF6)), // Purple -> Light Purple
            AvatarBackground.Solid(Color(0xFFF44336), Color(0xFFFBE1DD)), // Red -> Light Peach
            AvatarBackground.Solid(Color(0xFF607D8B), Color(0xFFECEFF1)), // Slate -> Light Grey
            AvatarBackground.Solid(Color(0xFFF57F17), Color(0xFF4E342E)), // Gold/Amber -> Dark Brown

            // Premium Gradients
            AvatarBackground.Gradient(listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)), Color(0xFFE2E5FF)), // Violet-Blue -> Light Lavender
            AvatarBackground.Gradient(listOf(Color(0xFFF857A6), Color(0xFFFF5858)), Color(0xFFFCE4EC)), // Pink-Red -> Light Pink
            AvatarBackground.Gradient(listOf(Color(0xFF11998E), Color(0xFF38EF7D)), Color(0xFFE2F4E3)), // Teal-Green -> Light Green
            AvatarBackground.Gradient(listOf(Color(0xFFF12711), Color(0xFFF5AF19)), Color(0xFFFBE1DD)), // Sunset -> Light Peach
            AvatarBackground.Gradient(listOf(Color(0xFF00c6ff), Color(0xFF0072ff)), Color(0xFFE3EDFD)), // Neon Blue -> Light Blue
            AvatarBackground.Gradient(listOf(Color(0xFFDA22FF), Color(0xFF9733EE)), Color(0xFFECDDF6)), // Purple-Pink -> Light Purple
            AvatarBackground.Gradient(listOf(Color(0xFFFFE259), Color(0xFFFFA751)), Color(0xFF4E342E)), // Mango -> Dark Brown
            AvatarBackground.Gradient(listOf(Color(0xFF43C6AC), Color(0xFF191654)), Color(0xFFE3F2F8)), // Deep Space -> Light Baby Blue
            AvatarBackground.Gradient(listOf(Color(0xFFEB3349), Color(0xFFF45C43)), Color(0xFFF9DBE8))  // Cherry -> Light Rose
        )
    }
    
    // Text color presets (Auto, Solids)
    val textColorPresets = remember {
        listOf(
            AvatarTextColor.Auto,
            AvatarTextColor.Custom(Color.White),
            AvatarTextColor.Custom(Color.Black),
            AvatarTextColor.Custom(Color(0xFFFFEB3B)), // Yellow
            AvatarTextColor.Custom(Color(0xFFE91E63)), // Pink
            AvatarTextColor.Custom(Color(0xFF00BCD4)), // Cyan
            AvatarTextColor.Custom(Color(0xFF8BC34A))  // Lime
        )
    }
    
    var selectedBackground by remember { 
        mutableStateOf<AvatarBackground>(
            backgroundPresets.getOrNull(initialBgIndex) ?: backgroundPresets.first()
        ) 
    }
    var selectedTextColor by remember { 
        mutableStateOf<AvatarTextColor>(
            textColorPresets.getOrNull(initialTextColorIndex) ?: textColorPresets.first()
        ) 
    }
    
    val displayText = textVal.trim()
    val isSaveEnabled = true
    
    // Resolve dynamic text preview color using explicit background defaultTextColor mappings in Auto mode
    val resolvedPreviewTextColor = remember(selectedBackground, selectedTextColor) {
        when (val textColor = selectedTextColor) {
            is AvatarTextColor.Auto -> {
                when (val bg = selectedBackground) {
                    is AvatarBackground.Solid -> bg.defaultTextColor
                    is AvatarBackground.Gradient -> bg.defaultTextColor
                }
            }
            is AvatarTextColor.Custom -> textColor.color
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraColors.Background)
            .statusBarsPadding()
    ) {
        // Custom Top Bar
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
            Text(
                text = "Text Avatar",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = {
                    if (isSaveEnabled) {
                        val uri = generateTextAvatar(context, displayText, selectedBackground, selectedTextColor)
                        if (uri != null) {
                            val bgIndex = backgroundPresets.indexOf(selectedBackground).coerceAtLeast(0)
                            val textColorIndex = textColorPresets.indexOf(selectedTextColor).coerceAtLeast(0)
                            onSave(uri, displayText, bgIndex, textColorIndex)
                        }
                    }
                },
                enabled = isSaveEnabled
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Save Text Avatar",
                    tint = if (isSaveEnabled) Color.White else Color.White.copy(alpha = 0.3f)
                )
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Preview of the Text Avatar
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(selectedBackground.toBrush())
                    .border(1.5.dp, AuraColors.Primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val fontSize = if (displayText.length <= 1) 68.sp else 50.sp
                Text(
                    text = displayText,
                    color = resolvedPreviewTextColor,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(28.dp))
            
            // Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2025))
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Text(
                        text = "Enter text / initials",
                        color = AuraColors.Primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    BasicTextField(
                        value = textVal,
                        onValueChange = { newValue ->
                            // Restrict input length to 2 code points (e.g. 2 letters or 2 emojis)
                            val codePointCount = if (newValue.isEmpty()) 0 else newValue.codePointCount(0, newValue.length)
                            if (codePointCount <= 2) {
                                textVal = newValue
                            }
                        },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = FontFamily.SansSerif
                        ),
                        cursorBrush = SolidColor(AuraColors.Primary),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 28.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (textVal.isEmpty()) {
                                    Text(
                                        text = defaultInitials.ifEmpty { "e.g. SP" },
                                        color = Color.White.copy(alpha = 0.25f),
                                        fontSize = 16.sp,
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Background Colors Selector
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Background Style",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp)
                )
                
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(backgroundPresets) { preset ->
                        val isSelected = selectedBackground == preset
                        
                        // Subtle border for light pastel colors in background selector
                        val isPresetLight = when (preset) {
                            is AvatarBackground.Solid -> preset.color.luminance() > 0.8f
                            is AvatarBackground.Gradient -> false
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(preset.toBrush())
                                .border(
                                    width = if (isSelected) 3.dp else if (isPresetLight) 1.dp else 0.dp,
                                    color = if (isSelected) {
                                        AuraColors.Primary
                                    } else if (isPresetLight) {
                                        Color.White.copy(alpha = 0.15f)
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = CircleShape
                                )
                                .clickable { selectedBackground = preset },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                val checkTint = when (preset) {
                                    is AvatarBackground.Solid -> preset.defaultTextColor
                                    is AvatarBackground.Gradient -> preset.defaultTextColor
                                }
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = checkTint,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Text Color Selector
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Text Color",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp)
                )
                
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(textColorPresets) { preset ->
                        val isSelected = selectedTextColor == preset
                        
                        // Style indicators for text circles
                        val backgroundBrush = when (preset) {
                            is AvatarTextColor.Auto -> Brush.sweepGradient(listOf(Color.White, Color.Black, Color.White))
                            is AvatarTextColor.Custom -> SolidColor(preset.color)
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(backgroundBrush)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) {
                                        AuraColors.Primary
                                    } else {
                                        // Subtle border for custom light/dark colors
                                        if (preset is AvatarTextColor.Custom && preset.color == Color.White) {
                                            Color.White.copy(alpha = 0.2f)
                                        } else if (preset is AvatarTextColor.Custom && preset.color == Color.Black) {
                                            Color.White.copy(alpha = 0.4f)
                                        } else {
                                            Color.Transparent
                                        }
                                    },
                                    shape = CircleShape
                                )
                                .clickable { selectedTextColor = preset },
                            contentAlignment = Alignment.Center
                        ) {
                            if (preset is AvatarTextColor.Auto) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(2.dp)
                                        .background(Color(0xFF2E303B), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Auto",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                            }
                            
                            if (isSelected) {
                                val checkTint = when (preset) {
                                    is AvatarTextColor.Auto -> Color.White
                                    is AvatarTextColor.Custom -> {
                                        if (preset.color.luminance() > 0.5f) Color.Black else Color.White
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = checkTint,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun generateTextAvatar(
    context: Context,
    text: String,
    background: AvatarBackground,
    textColor: AvatarTextColor
): Uri? {
    val size = 512
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // 1. Draw Background
    when (background) {
        is AvatarBackground.Solid -> {
            paint.color = background.color.toArgb()
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        }
        is AvatarBackground.Gradient -> {
            val colors = background.colors.map { it.toArgb() }.toIntArray()
            paint.shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                colors,
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
            paint.shader = null // Clear shader
        }
    }
    
    // 2. Draw Text/Initials in Center
    if (text.isNotEmpty()) {
        val argbTextColor = when (textColor) {
            is AvatarTextColor.Auto -> {
                when (background) {
                    is AvatarBackground.Solid -> background.defaultTextColor.toArgb()
                    is AvatarBackground.Gradient -> background.defaultTextColor.toArgb()
                }
            }
            is AvatarTextColor.Custom -> textColor.color.toArgb()
        }
        
        paint.color = argbTextColor
        paint.textSize = if (text.length <= 1) 220f else 160f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        
        // Calculate text baseline to center it vertically
        val textBounds = Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)
        val y = (size / 2f) - textBounds.exactCenterY()
        
        canvas.drawText(text, size / 2f, y, paint)
    }
    
    // 3. Save to file
    val file = File(context.cacheDir, "text_avatar_${System.currentTimeMillis()}.jpg")
    return try {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        bitmap.recycle()
        Uri.fromFile(file)
    } catch (e: java.lang.Exception) {
        e.printStackTrace()
        bitmap.recycle()
        null
    }
}
