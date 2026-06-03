package com.subhankar.aurachat.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhankar.aurachat.ui.theme.AuraColors
import kotlinx.coroutines.launch

/**
 * Onboarding Flow — exact translation of Flutter onboarding_flow.dart.
 *
 * 3-page HorizontalPager: Permissions → Privacy → Backup
 */
@Composable
fun OnboardingFlow(
    onFinish: () -> Unit,
    onBack: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = AuraColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Pages
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> PermissionsPage()
                    1 -> PrivacyPage()
                    2 -> BackupPage()
                }
            }

            // Footer — Back/Next buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage == 0) {
                    TextButton(onClick = onBack) {
                        Text(
                            "Back",
                            color = Color(0xFF4488FF),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W600
                        )
                    }
                } else {
                    IconButton(onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = AuraColors.TextSecondary
                        )
                    }
                }

                FilledTonalButton(
                    onClick = {
                        if (pagerState.currentPage == 2) {
                            onFinish()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    contentPadding = PaddingValues(horizontal = 32.dp)
                ) {
                    Text(
                        text = if (pagerState.currentPage == 2) "Get Started" else "Next",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ─── Page 1: Permissions ─────────────────────────────────────────

@Composable
private fun PermissionsPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Allow permissions",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = (-0.5).sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "To help you message people you know, AuraChat needs access to your contacts and notifications.",
            fontSize = 16.sp,
            color = AuraColors.TextSecondary,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(48.dp))

        PermissionRow(
            Icons.Default.Notifications,
            "Notifications",
            "Get notified when you receive a message or call."
        )
        PermissionRow(
            Icons.Default.Person,
            "Contacts",
            "Find people you know and stay connected."
        )
        PermissionRow(
            Icons.Default.FolderOpen,
            "Files and media",
            "Send photos, videos, and files to your friends."
        )
        PermissionRow(
            Icons.Default.Phone,
            "Phone calls",
            "Make registering easier by automatically reading your phone number."
        )
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier.padding(bottom = 32.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, title, tint = AuraColors.TextSecondary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                title,
                fontSize = 18.sp,
                fontWeight = FontWeight.W600,
                color = Color.White
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.6f),
                lineHeight = 20.sp
            )
        }
    }
}

// ─── Page 2: Privacy ─────────────────────────────────────────────

@Composable
private fun PrivacyPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.VisibilityOff,
            "Privacy",
            tint = AuraColors.Primary,
            modifier = Modifier.size(100.dp)
        )
        Spacer(Modifier.height(48.dp))
        Text(
            "Total Privacy",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Keys stay on your device. The server sees only encrypted blobs. We cannot read your messages even if we wanted to.",
            fontSize = 16.sp,
            color = AuraColors.TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}

// ─── Page 3: Backup ──────────────────────────────────────────────

@Composable
private fun BackupPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.CloudDone,
            "Backup",
            tint = AuraColors.Primary,
            modifier = Modifier.size(100.dp)
        )
        Spacer(Modifier.height(48.dp))
        Text(
            "Secure Backup",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Lose your phone? Recover your chats with a passphrase. You can set this up after logging in.",
            fontSize = 16.sp,
            color = AuraColors.TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}
