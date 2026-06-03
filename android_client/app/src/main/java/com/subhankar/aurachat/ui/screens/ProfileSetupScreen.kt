package com.subhankar.aurachat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhankar.aurachat.ui.theme.AuraColors
import com.subhankar.aurachat.ui.viewmodel.ProfileSetupUiState

/**
 * Profile Setup Screen — shown after new user registration.
 * Allows setting name, username, and optional PIN.
 */
@Composable
fun ProfileSetupScreen(
    uiState: ProfileSetupUiState,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val scrollState = rememberScrollState()
    val isValid = uiState.firstName.isNotBlank()

    Scaffold(containerColor = AuraColors.Background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 32.dp)
        ) {
            Spacer(Modifier.height(60.dp))

            // Avatar placeholder
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(100.dp).clip(CircleShape)
                        .background(AuraColors.Primary.copy(alpha = 0.15f))
                        .border(2.dp, AuraColors.Primary.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, "Avatar", tint = AuraColors.Primary, modifier = Modifier.size(48.dp))
                }
            }

            Spacer(Modifier.height(32.dp))

            Text("Set up your profile", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = (-0.5).sp)
            Spacer(Modifier.height(8.dp))
            Text("This is how others will see you on AuraChat", fontSize = 16.sp, color = AuraColors.TextSecondary)
            Spacer(Modifier.height(40.dp))

            // Name fields
            GlassTextField(
                value = uiState.firstName, onValueChange = onFirstNameChange,
                label = "First Name *", hint = "Your first name", icon = Icons.Default.Person
            )
            Spacer(Modifier.height(16.dp))
            GlassTextField(
                value = uiState.lastName, onValueChange = onLastNameChange,
                label = "Last Name", hint = "Your last name (optional)", icon = Icons.Default.Person
            )
            Spacer(Modifier.height(24.dp))

            // Username
            GlassTextField(
                value = uiState.username, onValueChange = onUsernameChange,
                label = "Username", hint = "Choose a unique username", icon = Icons.Default.AlternateEmail
            )
            // Username availability indicator
            if (uiState.username.length >= 3) {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.isCheckingUsername) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = AuraColors.Primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Checking...", color = AuraColors.TextSecondary, fontSize = 12.sp)
                    } else if (uiState.isUsernameAvailable == true) {
                        Icon(Icons.Default.CheckCircle, "Available", tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Available", color = Color(0xFF4CAF50), fontSize = 12.sp)
                    } else if (uiState.isUsernameAvailable == false) {
                        Icon(Icons.Default.Cancel, "Taken", tint = AuraColors.Error, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Already taken", color = AuraColors.Error, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Optional PIN
            GlassTextField(
                value = uiState.pin, onValueChange = onPinChange,
                label = "Discovery PIN (optional)", hint = "4-digit PIN for privacy",
                icon = Icons.Default.Pin, isPassword = true
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "If set, others will need your PIN to find you by username.",
                color = AuraColors.TextTertiary, fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp)
            )

            Spacer(Modifier.height(32.dp))

            if (uiState.errorMessage != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(AuraColors.Error.copy(alpha = 0.12f))
                        .border(1.dp, AuraColors.Error.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ErrorOutline, "Error", tint = AuraColors.Error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(uiState.errorMessage, color = AuraColors.Error, fontSize = 13.sp)
                }
                Spacer(Modifier.height(16.dp))
            }

            ShimmerButton(
                text = if (uiState.isLoading) "Setting up..." else "Continue",
                onClick = onSubmit,
                enabled = isValid && !uiState.isLoading
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}
