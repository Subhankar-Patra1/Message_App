package com.subhankar.aurachat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.subhankar.aurachat.ui.viewmodel.FindByUsernameViewModel
import com.subhankar.aurachat.ui.theme.AuraColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindByUsernameScreen(
    viewModel: FindByUsernameViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onNavigateToChat: (recipientId: String, recipientName: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var usernameQuery by remember { mutableStateOf("") }
    val isButtonActive = usernameQuery.trim().length >= 3

    // Pin Dialog State
    var showPinDialog by remember { mutableStateOf(false) }
    var pinValue by remember { mutableStateOf("") }
    
    // Handle Navigation Side Effect
    LaunchedEffect(uiState.navigateToChat) {
        if (uiState.navigateToChat) {
            val user = uiState.foundUser
            if (user != null) {
                val userId = user["user_id"]?.toString() ?: ""
                val name = user["display_name"]?.toString() ?: user["username"]?.toString() ?: "Unknown"
                onNavigateToChat(userId, name)
                viewModel.onNavigatedToChat()
                showPinDialog = false
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().zIndex(2f),
        containerColor = AuraColors.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Find by username", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Normal) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AuraColors.AppBarBackground,
                    navigationIconContentColor = Color.White
                ),
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (isButtonActive && !uiState.isLoading) viewModel.searchUsername(usernameQuery.trim()) },
                containerColor = if (isButtonActive) Color(0xFFC3C0FF) else Color(0xFF22242A),
                contentColor = if (isButtonActive) Color(0xFF111318) else Color.White.copy(alpha = 0.3f),
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(color = Color(0xFF111318), modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Search")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = usernameQuery,
                onValueChange = { usernameQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Username", color = Color.White.copy(alpha = 0.5f)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF22242A),
                    unfocusedContainerColor = Color(0xFF22242A),
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Enter a username to chat with that person.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Scan QR
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Surface(
                    color = Color(0xFF22242A),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.clickable { /* Future feature */ }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Scan QR code", color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Error state
            if (uiState.error != null) {
                Text(
                    uiState.error!!,
                    color = Color.Red.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            // Search Result
            val user = uiState.foundUser
            if (user != null) {
                Text(
                    "SEARCH RESULT",
                    color = Color.White.copy(alpha = 0.38f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22242A)),
                        contentAlignment = Alignment.Center
                    ) {
                        // For now we use placeholder icon
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    val displayName = user["display_name"]?.toString() ?: user["username"]?.toString() ?: "Unknown"
                    val username = user["username"]?.toString()

                    Column(modifier = Modifier.weight(1f)) {
                        Text(displayName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        if (username != null) {
                            Text("@$username", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                        }
                    }

                    Button(
                        onClick = {
                            val pinRequired = user["pin_required"] as? Boolean ?: false
                            val hasKeys = user["pre_keys"] != null
                            
                            if (pinRequired && !hasKeys) {
                                showPinDialog = true
                            } else {
                                val userId = user["user_id"]?.toString() ?: ""
                                onNavigateToChat(userId, displayName)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC3C0FF), contentColor = Color(0xFF111318)),
                        shape = CircleShape
                    ) {
                        Text("Message", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // PIN Dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { if (!uiState.isPinLoading) showPinDialog = false },
            containerColor = Color(0xFF2D2F33),
            title = {
                Text("PIN Required", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Column {
                    Text("Enter PIN for @${uiState.foundUser?.get("username")}", color = Color.White.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pinValue,
                        onValueChange = { if (it.length <= 4) pinValue = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFC3C0FF),
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color(0xFF1E2025),
                            unfocusedContainerColor = Color(0xFF1E2025)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        isError = uiState.pinError != null,
                        supportingText = if (uiState.pinError != null) { { Text(uiState.pinError!!, color = Color.Red) } } else null
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.verifyPinAndFetchKeys(uiState.foundUser?.get("username").toString(), pinValue) },
                    enabled = pinValue.length == 4 && !uiState.isPinLoading
                ) {
                    if (uiState.isPinLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFFC3C0FF), strokeWidth = 2.dp)
                    } else {
                        Text("Verify", color = if (pinValue.length == 4) Color(0xFFC3C0FF) else Color.White.copy(alpha = 0.3f))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPinDialog = false },
                    enabled = !uiState.isPinLoading
                ) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.5f))
                }
            }
        )
    }
}
