package com.subhankar.aurachat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhankar.aurachat.ui.theme.AuraColors

@Composable
fun UsernameEditScreen(
    initialUsername: String,
    onBack: () -> Unit,
    onSave: (String) -> Unit
) {
    var usernameVal by remember { mutableStateOf(initialUsername) }
    
    val isValid = usernameVal.isEmpty() || (usernameVal.length >= 5 && usernameVal.all { 
        it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' 
    })
    
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
                text = "Username",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = { if (isValid) onSave(usernameVal) },
                enabled = isValid
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Save Username",
                    tint = if (isValid) Color.White else Color.White.copy(alpha = 0.3f)
                )
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            // Card containing the input field
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2025))
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(
                        text = "Set username",
                        color = AuraColors.Primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    BasicTextField(
                        value = usernameVal,
                        onValueChange = { newValue ->
                            // Restrict to letters, digits, and underscores
                            if (newValue.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }) {
                                usernameVal = newValue
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
                                if (usernameVal.isEmpty()) {
                                    Text(
                                        text = "username",
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
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Helpful Description text below the card
            Text(
                text = buildAnnotatedString {
                    append("You can choose a username on ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("AuraChat")
                    }
                    append(". If you do, people will be able to find you by this username and contact you without needing your phone number.")
                },
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontFamily = FontFamily.SansSerif
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = buildAnnotatedString {
                    append("You can use ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("a-z")
                    }
                    append(", ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("0-9")
                    }
                    append(" and ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("underscores")
                    }
                    append(".\nMinimum length is ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("5 characters")
                    }
                    append(".")
                },
                color = if (usernameVal.isNotEmpty() && usernameVal.length < 5) AuraColors.Error else Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}
