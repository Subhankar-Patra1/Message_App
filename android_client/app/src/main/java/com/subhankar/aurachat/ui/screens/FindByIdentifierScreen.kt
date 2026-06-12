package com.subhankar.aurachat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.subhankar.aurachat.ui.viewmodel.FindByIdentifierViewModel
import com.subhankar.aurachat.ui.theme.AuraColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindByIdentifierScreen(
    viewModel: FindByIdentifierViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onNavigateToChat: (recipientId: String, recipientName: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var identifierQuery by remember { mutableStateOf("") }
    val isButtonActive = identifierQuery.trim().length >= 3

    // Handle Navigation Side Effect
    LaunchedEffect(uiState.navigateToChat) {
        if (uiState.navigateToChat) {
            val user = uiState.foundUser
            if (user != null) {
                val userId = user["user_id"]?.toString() ?: ""
                val name = user["display_name"]?.toString() ?: user["username"]?.toString() ?: "Unknown"
                onNavigateToChat(userId, name)
                viewModel.onNavigatedToChat()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().zIndex(2f),
        containerColor = AuraColors.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Find by number or email", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Normal) },
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
                onClick = { if (isButtonActive && !uiState.isLoading) viewModel.searchIdentifier(identifierQuery.trim()) },
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
                value = identifierQuery,
                onValueChange = { identifierQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Phone number or email", color = Color.White.copy(alpha = 0.5f)) },
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
                "Enter a phone number or email address to find a user.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp
            )

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
        }
    }
}
