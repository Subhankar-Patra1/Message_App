package com.subhankar.aurachat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhankar.aurachat.ui.theme.AuraColors

/**
 * New Chat Screen — exact translation of Flutter new_chat_screen.dart.
 *
 * Preserves:
 *   • "New Message" app bar
 *   • Action items: Find by Username, New Group, New Contact, Find by Number/Email
 *   • "Contacts on Aura" section header
 *   • Contact list with lavender avatar initials
 *   • Loading/permission denied/empty states
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(
    onBackClick: () -> Unit,
    onFindByUsername: () -> Unit = {},
    onFindByIdentifier: () -> Unit = {},
    contacts: List<ContactItem> = emptyList(),
    isLoadingContacts: Boolean = false,
    permissionDenied: Boolean = false,
    onOpenSettings: () -> Unit = {},
    onContactClick: (ContactItem) -> Unit = {}
) {
    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "New Message",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.W600
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AuraColors.Background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Action items
            item {
                ActionItem(Icons.Default.AlternateEmail, "Find by Username", onFindByUsername)
            }
            item {
                ActionItem(Icons.Default.Group, "New Group") { }
            }
            item {
                ActionItem(Icons.Default.PersonAdd, "New Contact") { }
            }
            item {
                ActionItem(Icons.Default.Email, "Find by Number or Email", onFindByIdentifier)
            }

            // Section header
            item {
                Text(
                    "Contacts on Aura",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
                )
            }

            // States
            when {
                isLoadingContacts -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AuraColors.Primary)
                        }
                    }
                }
                permissionDenied -> {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Contact permission is required to sync your contacts.",
                                color = AuraColors.TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = onOpenSettings,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AuraColors.Primary,
                                    contentColor = AuraColors.Background
                                )
                            ) {
                                Text("Open Settings")
                            }
                        }
                    }
                }
                contacts.isEmpty() -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No contacts found.",
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                else -> {
                    items(contacts) { contact ->
                        ContactTile(contact = contact, onClick = { onContactClick(contact) })
                    }
                }
            }
        }
    }
}

// ─── Action Item ─────────────────────────────────────────────────

@Composable
private fun ActionItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E2025)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, title, tint = AuraColors.TextSecondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(
            title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.W500
        )
    }
}

// ─── Contact Tile ────────────────────────────────────────────────

@Composable
private fun ContactTile(contact: ContactItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(AuraColors.Primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (contact.name.isNotEmpty()) contact.name[0].uppercase() else "?",
                color = AuraColors.Primary,
                fontWeight = FontWeight.W600,
                fontSize = 16.sp
            )
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                contact.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.W500
            )
            if (contact.phone.isNotEmpty()) {
                Text(
                    contact.phone,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

data class ContactItem(
    val name: String,
    val phone: String,
    val userId: String? = null
)
