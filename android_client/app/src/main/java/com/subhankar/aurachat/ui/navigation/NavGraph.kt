package com.subhankar.aurachat.ui.navigation

import android.app.Activity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost






















import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import com.subhankar.aurachat.ui.theme.AuraColors
import com.subhankar.aurachat.data.local.SessionManager
import com.subhankar.aurachat.ui.screens.*
import com.subhankar.aurachat.ui.viewmodel.*

/**
 * App navigation graph — all routes declared in one place.
 *
 * Flow:
 *   If logged in:  Home → Chat/NewChat/Profile
 *   If not:        Welcome → Login → (ProfileSetup) → Onboarding → Home
 */
object Routes {
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val PROFILE_SETUP = "profile_setup"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CHAT = "chat/{recipientId}/{recipientName}"
    const val NEW_CHAT = "new_chat"
    const val PROFILE = "profile"
    const val FIND_BY_USERNAME = "find_by_username"
    const val FIND_BY_IDENTIFIER = "find_by_identifier"

    fun chat(recipientId: String, recipientName: String): String {
        val safeName = recipientName.replace("/", "%2F")
        return "chat/$recipientId/$safeName"
    }
}

@Composable
fun AuraChatNavGraph(
    sessionManager: SessionManager
) {
    val navController = rememberNavController()

    // Determine start destination based on auth state
    val startDestination = remember {
        if (sessionManager.isLoggedIn()) Routes.HOME else Routes.WELCOME
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.background(AuraColors.Background),
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(220)
            )
        },
        exitTransition = {
            ExitTransition.None
        },
        popEnterTransition = {
            EnterTransition.None
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(220)
            )
        }
    ) {
        // ─── Welcome Screen ──────────────────────────────────
        composable(Routes.WELCOME) {
            WelcomeScreen(
                onGetStarted = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                }
            )
        }

        // ─── Login Screen ────────────────────────────────────
        composable(Routes.LOGIN) {
            val viewModel: LoginViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            val navEvent by viewModel.navEvent.collectAsState()

            // Handle navigation events
            LaunchedEffect(navEvent) {
                when (navEvent) {
                    LoginNavEvent.NavigateToHome -> {
                        viewModel.clearNavEvent()
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                    LoginNavEvent.NavigateToProfileSetup -> {
                        viewModel.clearNavEvent()
                        navController.navigate(Routes.PROFILE_SETUP) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                    LoginNavEvent.NavigateToOnboarding -> {
                        viewModel.clearNavEvent()
                        navController.navigate(Routes.ONBOARDING) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                    null -> {}
                }
            }

            LoginScreen(
                uiState = uiState,
                identifier = viewModel.identifier,
                emailForOtp = viewModel.emailForOtp,
                onIdentify = { viewModel.handleIdentify(it) },
                onPasswordSubmit = { viewModel.handlePasswordSubmit(it) },
                onOtpSubmit = { viewModel.handleOtpSubmit(it) },
                onPhoneEmailSubmit = { viewModel.handlePhoneEmailSubmit(it) },
                onResendOtp = { viewModel.handleResendOtp() },
                onForgotPassword = { viewModel.handleForgotPassword() },
                onForgotOtpSubmit = { viewModel.handleForgotOtpSubmit(it) },
                onForgotPasswordReset = { viewModel.handleForgotPasswordReset(it) },
                onGoogleSignIn = { activity -> viewModel.handleGoogleSignIn(activity) },
                onGoBack = { viewModel.goBack() },
                onUpdateServerIp = { viewModel.updateServerIp(it) }
            )
        }

        // ─── Profile Setup ───────────────────────────────────
        composable(Routes.PROFILE_SETUP) {
            val viewModel: ProfileSetupViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            // Navigate when profile setup is complete
            LaunchedEffect(uiState.isComplete) {
                if (uiState.isComplete) {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.PROFILE_SETUP) { inclusive = true }
                    }
                }
            }

            ProfileSetupScreen(
                uiState = uiState,
                onFirstNameChange = { viewModel.updateFirstName(it) },
                onLastNameChange = { viewModel.updateLastName(it) },
                onUsernameChange = { viewModel.updateUsername(it) },
                onPinChange = { viewModel.updatePin(it) },
                onSubmit = { viewModel.submit() }
            )
        }

        // ─── Onboarding Flow ─────────────────────────────────
        composable(Routes.ONBOARDING) {
            OnboardingFlow(
                onFinish = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onBack = {
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        // ─── Home Screen ─────────────────────────────────────
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = hiltViewModel()
            val conversations by viewModel.conversations.collectAsState()

            // Initialize chat services on first launch
            LaunchedEffect(Unit) {
                viewModel.initialize()
            }

            var profilePhotoPath by remember { mutableStateOf(sessionManager.getProfilePhotoPath()) }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        profilePhotoPath = sessionManager.getProfilePhotoPath()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            HomeScreen(
                conversations = conversations,
                profilePhotoPath = profilePhotoPath,
                onChatClick = { chat ->
                    viewModel.markAsRead(chat.userId)
                    if (navController.currentDestination?.route == Routes.HOME) {
                        navController.navigate(Routes.chat(chat.userId, chat.name)) {
                            launchSingleTop = true
                        }
                    }
                },
                onNewChatClick = {
                    if (navController.currentDestination?.route == Routes.HOME) {
                        navController.navigate(Routes.NEW_CHAT) {
                            launchSingleTop = true
                        }
                    }
                },
                onProfileClick = {
                    if (navController.currentDestination?.route == Routes.HOME) {
                        navController.navigate(Routes.PROFILE) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }

        // ─── Chat Screen ─────────────────────────────────────
        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("recipientId") { type = NavType.StringType },
                navArgument("recipientName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val recipientName = backStackEntry.arguments?.getString("recipientName") ?: ""

            val viewModel: ChatViewModel = hiltViewModel()
            val messages by viewModel.messages.collectAsState()
            val isTyping by viewModel.isTyping.collectAsState()
            val isOnline by viewModel.isOnline.collectAsState()
            val lastSeenAt by viewModel.lastSeenAt.collectAsState()
            val inputText by viewModel.inputText.collectAsState()

            val context = LocalContext.current
            ChatScreen(
                recipientName = recipientName,
                recipientAvatar = null,
                messages = messages,
                isTyping = isTyping,
                isOnline = isOnline,
                lastSeenAt = lastSeenAt,
                inputText = inputText,
                onInputChange = { viewModel.updateInput(it) },
                onSendClick = { viewModel.sendMessage() },
                onBackClick = { navController.popBackStack() },
                onSendMedia = { uris ->
                    uris.forEach { uri ->
                        viewModel.sendMediaMessage(uri, context)
                    }
                }
            )
        }

        // ─── New Chat Screen ─────────────────────────────────
        composable(Routes.NEW_CHAT) {
            NewChatScreen(
                onBackClick = { navController.popBackStack() },
                onFindByUsername = {
                    if (navController.currentDestination?.route == Routes.NEW_CHAT) {
                        navController.navigate(Routes.FIND_BY_USERNAME)
                    }
                },
                onFindByIdentifier = {
                    if (navController.currentDestination?.route == Routes.NEW_CHAT) {
                        navController.navigate(Routes.FIND_BY_IDENTIFIER)
                    }
                }
            )
        }

        // ─── Find By Username Screen ─────────────────────────
        composable(Routes.FIND_BY_USERNAME) {
            FindByUsernameScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToChat = { recipientId, recipientName ->
                    if (navController.currentDestination?.route == Routes.FIND_BY_USERNAME) {
                        navController.navigate(Routes.chat(recipientId, recipientName)) {
                            popUpTo(Routes.NEW_CHAT) { inclusive = true }
                        }
                    }
                }
            )
        }

        // ─── Find By Identifier Screen ───────────────────────
        composable(Routes.FIND_BY_IDENTIFIER) {
            FindByIdentifierScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToChat = { recipientId, recipientName ->
                    if (navController.currentDestination?.route == Routes.FIND_BY_IDENTIFIER) {
                        navController.navigate(Routes.chat(recipientId, recipientName)) {
                            popUpTo(Routes.NEW_CHAT) { inclusive = true }
                        }
                    }
                }
            )
        }

        // ─── Profile Screen ──────────────────────────────────
        composable(Routes.PROFILE) {
            val viewModel: com.subhankar.aurachat.ui.viewmodel.ProfileViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            ProfileScreen(
                profileData = uiState.profileData,
                isLoading = uiState.isLoading,
                error = uiState.error,
                firstName = uiState.firstName,
                lastName = uiState.lastName,
                bio = uiState.bio,
                phone = uiState.phone,
                username = uiState.username,
                birthday = uiState.birthday,
                profilePhotoUri = uiState.profilePhotoUri,
                textAvatarText = uiState.textAvatarText,
                textAvatarBgIndex = uiState.textAvatarBgIndex,
                textAvatarTextColorIndex = uiState.textAvatarTextColorIndex,
                isSaving = uiState.isSaving,
                isLoggedOut = uiState.isLoggedOut,
                hasChanges = viewModel.hasChanges(),
                onFirstNameChange = { viewModel.updateFirstName(it) },
                onLastNameChange = { viewModel.updateLastName(it) },
                onBioChange = { viewModel.updateBio(it) },
                onPhoneChange = { viewModel.updatePhone(it) },
                onUsernameChange = { viewModel.updateUsername(it) },
                onBirthdayChange = { viewModel.updateBirthday(it) },
                onProfilePhotoChange = { viewModel.updateProfilePhotoUri(it) },
                onTextAvatarStateChange = { text, bgIdx, textColIdx ->
                    viewModel.updateTextAvatarState(text, bgIdx, textColIdx)
                },
                onLogoutClick = { viewModel.logout() },
                onLogoutSuccess = {
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onSaveClick = { onSuccess -> viewModel.saveAllChanges(onSuccess) },
                onDiscardClick = { viewModel.discardChanges() },
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
