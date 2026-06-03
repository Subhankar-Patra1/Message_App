package com.subhankar.aurachat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhankar.aurachat.ui.theme.AuraColors

/**
 * Profile Screen — exact translation of Flutter profile_screen.dart.
 *
 * Preserves:
 *   • Dark background with "Profile" app bar
 *   • Large circular avatar with "Edit photo" button
 *   • Name, About, Username, QR code rows with icons
 *   • Privacy explanation text sections
 *   • Dividers matching Flutter's white10
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profileData: Map<String, String?>? = null,
    isLoading: Boolean = false,
    error: String? = null,
    onBackClick: () -> Unit
) {
    val surfaceColor = Color(0xFF1E2025)

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Profile", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.W500)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AuraColors.Background)
            )
        }
    ) { padding ->
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
            error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(error, color = AuraColors.Error)
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(20.dp))

                    // Avatar Section
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Avatar circle
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(surfaceColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                "Avatar",
                                tint = Color.White.copy(alpha = 0.24f),
                                modifier = Modifier.size(60.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        // Edit photo button
                        Button(
                            onClick = { },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = surfaceColor,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(50),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text("Edit photo", fontSize = 14.sp)
                        }
                    }

                    Spacer(Modifier.height(40.dp))

                    // Profile Info
                    val fullName = listOfNotNull(
                        profileData?.get("first_name"),
                        profileData?.get("last_name")
                    ).joinToString(" ").ifEmpty { "No name set" }

                    ProfileItem(Icons.Outlined.Person, fullName) { }
                    ProfileItem(Icons.Outlined.Edit, "About") { }

                    // Info text
                    Text(
                        "Your profile and changes to it will be visible to people you message, contacts, and groups.",
                        color = AuraColors.TextHint,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(start = 72.dp, top = 16.dp, end = 24.dp, bottom = 16.dp)
                    )

                    HorizontalDivider(
                        thickness = 1.dp,
                        color = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    // Username & QR
                    ProfileItem(
                        Icons.Outlined.AlternateEmail,
                        profileData?.get("username") ?: "No username set"
                    ) { }
                    ProfileItem(Icons.Outlined.QrCode, "QR code or link") { }

                    // Info text
                    Text(
                        "Your username, QR code and link aren't visible on your profile. Only share your username with people you trust.",
                        color = AuraColors.TextHint,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(start = 72.dp, top = 16.dp, end = 24.dp, bottom = 32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, title, tint = AuraColors.TextSecondary, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(24.dp))
        Text(
            title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.W400,
            modifier = Modifier.weight(1f)
        )
    }
}
