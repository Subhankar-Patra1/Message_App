package com.subhankar.aurachat.ui.screens

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhankar.aurachat.network.AppConfig
import com.subhankar.aurachat.ui.theme.AuraColors
import com.subhankar.aurachat.ui.viewmodel.AuthStep
import com.subhankar.aurachat.ui.viewmodel.LoginUiState

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    identifier: String,
    emailForOtp: String,
    onIdentify: (String) -> Unit,
    onPasswordSubmit: (String) -> Unit,
    onOtpSubmit: (String) -> Unit,
    onPhoneEmailSubmit: (String) -> Unit,
    onResendOtp: () -> Unit,
    onForgotPassword: () -> Unit,
    onForgotOtpSubmit: (String) -> Unit,
    onForgotPasswordReset: (String) -> Unit,
    onGoogleSignIn: (Activity) -> Unit,
    onGoBack: () -> Unit,
    onUpdateServerIp: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    var identifierText by remember { mutableStateOf("") }
    var passwordText by remember { mutableStateOf("") }
    var otpText by remember { mutableStateOf("") }
    var emailText by remember { mutableStateOf("") }
    var showServerDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Reset fields on step change
    LaunchedEffect(uiState.currentStep) {
        if (uiState.currentStep == AuthStep.IDENTIFY) {
            passwordText = ""
            otpText = ""
            emailText = ""
        }
    }

    Scaffold(containerColor = AuraColors.Background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 32.dp)
        ) {
            Spacer(Modifier.height(48.dp))

            // Header with back button and settings
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (uiState.currentStep != AuthStep.IDENTIFY) {
                    IconButton(onClick = onGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                } else Spacer(Modifier.width(48.dp))

                IconButton(onClick = { showServerDialog = true }) {
                    Icon(Icons.Default.Settings, "Settings", tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            // Title
            Text(
                text = when (uiState.currentStep) {
                    AuthStep.IDENTIFY -> "Welcome"
                    AuthStep.PASSWORD_SETUP -> "Create Password"
                    AuthStep.PASSWORD_INLINE -> "Welcome back"
                    AuthStep.PHONE_EMAIL_COLLECT -> "Verify Your Number"
                    AuthStep.FORGOT_PASSWORD_OTP -> "Reset Password"
                    AuthStep.FORGOT_PASSWORD_SETUP -> "New Password"
                    AuthStep.OTP -> "Verification Code"
                },
                fontSize = 32.sp, fontWeight = FontWeight.W800,
                color = Color.White, letterSpacing = (-1).sp
            )

            Spacer(Modifier.height(8.dp))

            // Subtitle
            Text(
                text = when (uiState.currentStep) {
                    AuthStep.IDENTIFY -> "Sign in or create your account"
                    AuthStep.PASSWORD_SETUP -> "Choose a strong password for your account"
                    AuthStep.PASSWORD_INLINE -> "Enter your password to continue"
                    AuthStep.PHONE_EMAIL_COLLECT -> "Enter your email so we can send you a verification code"
                    AuthStep.FORGOT_PASSWORD_OTP -> "Enter the reset code sent to $identifier"
                    AuthStep.FORGOT_PASSWORD_SETUP -> "Create a new strong password"
                    AuthStep.OTP -> if (uiState.isPhone) "Enter the code sent to your email" else "Enter the code we sent to $identifier"
                },
                fontSize = 16.sp, color = Color.White.copy(alpha = 0.5f), lineHeight = 22.sp
            )

            Spacer(Modifier.height(40.dp))

            // Form content based on step
            AnimatedContent(
                targetState = uiState.currentStep,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "auth_step"
            ) { step ->
                Column {
                    when (step) {
                        AuthStep.IDENTIFY -> IdentifyForm(
                            identifierText = identifierText,
                            onIdentifierChange = { identifierText = it },
                            isLoading = uiState.isLoading,
                            errorMessage = uiState.errorMessage,
                            rateLimitCooldown = uiState.rateLimitCooldown,
                            onContinue = { onIdentify(identifierText) },
                            onGoogleSignIn = { onGoogleSignIn(context as Activity) }
                        )
                        AuthStep.PASSWORD_INLINE -> PasswordInlineForm(
                            identifier = identifier,
                            passwordText = passwordText,
                            onPasswordChange = { passwordText = it },
                            isLoading = uiState.isLoading,
                            errorMessage = uiState.errorMessage,
                            onSubmit = { onPasswordSubmit(passwordText) },
                            onForgotPassword = onForgotPassword
                        )
                        AuthStep.PASSWORD_SETUP -> PasswordSetupForm(
                            passwordText = passwordText,
                            onPasswordChange = { passwordText = it },
                            isLoading = uiState.isLoading,
                            errorMessage = uiState.errorMessage,
                            onSubmit = { onPasswordSubmit(passwordText) }
                        )
                        AuthStep.PHONE_EMAIL_COLLECT -> PhoneEmailForm(
                            emailText = emailText,
                            onEmailChange = { emailText = it },
                            isLoading = uiState.isLoading,
                            errorMessage = uiState.errorMessage,
                            onSubmit = { onPhoneEmailSubmit(emailText) }
                        )
                        AuthStep.OTP -> OtpForm(
                            otpText = otpText,
                            onOtpChange = { if (it.length <= 6) otpText = it },
                            isLoading = uiState.isLoading,
                            errorMessage = uiState.errorMessage,
                            resendCooldown = uiState.resendCooldown,
                            isPhone = uiState.isPhone,
                            onSubmit = { onOtpSubmit(otpText) },
                            onResend = onResendOtp,
                            onWrongIdentifier = onGoBack
                        )
                        AuthStep.FORGOT_PASSWORD_OTP -> OtpForm(
                            otpText = otpText,
                            onOtpChange = { if (it.length <= 6) otpText = it },
                            isLoading = uiState.isLoading,
                            errorMessage = uiState.errorMessage,
                            resendCooldown = uiState.resendCooldown,
                            isPhone = false,
                            onSubmit = { onForgotOtpSubmit(otpText) },
                            onResend = onForgotPassword,
                            onWrongIdentifier = onGoBack
                        )
                        AuthStep.FORGOT_PASSWORD_SETUP -> PasswordSetupForm(
                            passwordText = passwordText,
                            onPasswordChange = { passwordText = it },
                            isLoading = uiState.isLoading,
                            errorMessage = uiState.errorMessage,
                            onSubmit = { onForgotPasswordReset(passwordText) },
                            buttonText = "Reset & Login"
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Footer
            Text(
                "By continuing, you agree to our Terms & Privacy Policy",
                color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    // Server IP dialog
    if (showServerDialog) {
        ServerSettingsDialog(
            currentIp = AppConfig.serverIp,
            onDismiss = { showServerDialog = false },
            onSave = { ip -> onUpdateServerIp(ip); showServerDialog = false }
        )
    }
}

// ─── Identify Form ───────────────────────────────────────────────

@Composable
private fun IdentifyForm(
    identifierText: String,
    onIdentifierChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    rateLimitCooldown: Int,
    onContinue: () -> Unit,
    onGoogleSignIn: () -> Unit
) {
    val isValid = identifierText.trim().let { text ->
        val isEmail = Regex("^[\\w\\-.]+@([\\w\\-]+\\.)+[\\w\\-]{2,4}$").matches(text)
        val isPhone = Regex("^\\+?[0-9\\s\\-()]+$").matches(text) && text.replace(Regex("[^0-9]"), "").length >= 10
        isEmail || isPhone
    }

    Column {
        GlassTextField(
            value = identifierText, onValueChange = onIdentifierChange,
            label = "Email or Phone Number", hint = "name@example.com",
            icon = Icons.Default.Email, keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done, onImeAction = { if (isValid) onContinue() }
        )
        Spacer(Modifier.height(16.dp))

        if (errorMessage != null) ErrorBanner(errorMessage)
        if (rateLimitCooldown > 0) RateLimitBanner(rateLimitCooldown)

        ShimmerButton(
            text = if (isLoading) "Connecting..." else if (rateLimitCooldown > 0) "Wait ${rateLimitCooldown}s" else "Continue",
            onClick = onContinue,
            enabled = isValid && !isLoading && rateLimitCooldown == 0
        )

        Spacer(Modifier.height(32.dp))

        // Divider
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = Color.White.copy(alpha = 0.1f))
            Text("or continue with", Modifier.padding(horizontal = 16.dp), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            HorizontalDivider(Modifier.weight(1f), color = Color.White.copy(alpha = 0.1f))
        }

        Spacer(Modifier.height(32.dp))

        // Google button
        OutlinedButton(
            onClick = onGoogleSignIn,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = 0.05f))
        ) {
            Icon(Icons.Default.AccountCircle, "Google", tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("Continue with Google", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.W500)
        }
    }
}

// ─── Password Inline Form ────────────────────────────────────────

@Composable
private fun PasswordInlineForm(
    identifier: String,
    passwordText: String,
    onPasswordChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit
) {
    val isValid = passwordText.length >= 6

    Column {
        GlassTextField(value = identifier, onValueChange = {}, label = "Email", icon = Icons.Default.Email, enabled = false)
        Spacer(Modifier.height(16.dp))
        GlassTextField(
            value = passwordText, onValueChange = onPasswordChange,
            label = "Password", hint = "Enter your password",
            icon = Icons.Default.Lock, isPassword = true,
            imeAction = ImeAction.Done, onImeAction = { if (isValid) onSubmit() }
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onForgotPassword) {
                Text("Forgot password?", color = Color(0xFF6C5CE7), fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(8.dp))
        if (errorMessage != null) ErrorBanner(errorMessage)
        ShimmerButton("Login", onSubmit, enabled = isValid && !isLoading)
    }
}

// ─── Password Setup Form ─────────────────────────────────────────

@Composable
private fun PasswordSetupForm(
    passwordText: String,
    onPasswordChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onSubmit: () -> Unit,
    buttonText: String = "Create Account"
) {
    val hasLength = passwordText.length >= 8
    val hasUpper = Regex("[A-Z]").containsMatchIn(passwordText)
    val hasDigit = Regex("[0-9]").containsMatchIn(passwordText)
    val isValid = hasLength && hasUpper && hasDigit

    Column {
        GlassTextField(
            value = passwordText, onValueChange = onPasswordChange,
            label = "Create Password", hint = "Enter a strong password",
            icon = Icons.Default.Lock, isPassword = true,
            imeAction = ImeAction.Done, onImeAction = { if (isValid) onSubmit() }
        )
        Spacer(Modifier.height(16.dp))
        PasswordRequirement("At least 8 characters", hasLength)
        PasswordRequirement("Contains an uppercase letter", hasUpper)
        PasswordRequirement("Contains a number", hasDigit)
        Spacer(Modifier.height(24.dp))
        if (errorMessage != null) ErrorBanner(errorMessage)
        ShimmerButton(buttonText, onSubmit, enabled = isValid && !isLoading)
    }
}

// ─── Phone Email Form ────────────────────────────────────────────

@Composable
private fun PhoneEmailForm(
    emailText: String,
    onEmailChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onSubmit: () -> Unit
) {
    val isValid = Regex("^[\\w\\-.]+@([\\w\\-]+\\.)+[\\w\\-]{2,4}$").matches(emailText.trim())

    Column {
        // Info banner
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1e2025)).border(1.dp, Color(0xFF8e9099).copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Info, "Info", tint = AuraColors.Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("We'll send a verification code to your email to verify your phone number.",
                color = Color(0xFFe2e2e6), fontSize = 13.sp)
        }
        Spacer(Modifier.height(20.dp))
        GlassTextField(
            value = emailText, onValueChange = onEmailChange,
            label = "Your email address", hint = "yourname@gmail.com",
            icon = Icons.Default.Email, keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done, onImeAction = { if (isValid) onSubmit() }
        )
        Spacer(Modifier.height(20.dp))
        if (errorMessage != null) ErrorBanner(errorMessage)
        ShimmerButton("Send Verification Code", onSubmit, enabled = isValid && !isLoading)
    }
}

// ─── OTP Form ────────────────────────────────────────────────────

@Composable
private fun OtpForm(
    otpText: String,
    onOtpChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    resendCooldown: Int,
    isPhone: Boolean,
    onSubmit: () -> Unit,
    onResend: () -> Unit,
    onWrongIdentifier: () -> Unit
) {
    // Auto-submit when 6 digits entered
    LaunchedEffect(otpText) {
        if (otpText.length == 6 && errorMessage == null) {
            onSubmit()
        }
    }

    Column {
        // OTP boxes
        OtpInput(value = otpText, onValueChange = onOtpChange)

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onWrongIdentifier) {
                Text(if (isPhone) "Wrong number?" else "Wrong email?",
                    color = Color(0xFF6C5CE7), fontSize = 13.sp, fontWeight = FontWeight.W600)
            }
            if (resendCooldown > 0) {
                Text("Resend in ${resendCooldown}s", Modifier.padding(16.dp),
                    color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
            } else {
                TextButton(onClick = onResend) {
                    Text("Resend code", color = Color(0xFF6C5CE7), fontSize = 13.sp, fontWeight = FontWeight.W600)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (errorMessage != null) {
            ErrorBanner(errorMessage)
            Spacer(Modifier.height(12.dp))
            ShimmerButton("Verify", onSubmit, enabled = otpText.length == 6 && !isLoading)
        }
    }
}

// ─── OTP Input Boxes ─────────────────────────────────────────────

@Composable
private fun OtpInput(value: String, onValueChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Hidden text field
        BasicTextField(
            value = value, onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onValueChange(it) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            modifier = Modifier.focusRequester(focusRequester).size(1.dp).background(Color.Transparent),
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent)
        )

        // Visual boxes
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            repeat(6) { i ->
                val char = value.getOrNull(i)?.toString() ?: ""
                val isFocused = value.length == i
                Box(
                    Modifier.size(52.dp, 64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(1.dp, if (isFocused) AuraColors.Primary else Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(char, fontSize = 28.sp, fontWeight = FontWeight.W700, color = Color(0xFFe2e2e6))
                }
            }
        }
    }
}

// ─── Shared Components ───────────────────────────────────────────

@Composable
fun GlassTextField(
    value: String, onValueChange: (String) -> Unit,
    label: String, hint: String = "", icon: ImageVector = Icons.Default.Edit,
    isPassword: Boolean = false, enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next, onImeAction: () -> Unit = {}
) {
    var obscured by remember { mutableStateOf(isPassword) }
    val keyboard = LocalSoftwareKeyboardController.current

    Column {
        Text(label, Modifier.padding(start = 4.dp, bottom = 8.dp),
            color = Color(0xFF8e9099), fontSize = 13.sp, fontWeight = FontWeight.W600, letterSpacing = 0.5.sp)

        Box(
            Modifier.fillMaxWidth().height(58.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.03f))))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(icon, label, tint = Color(0xFF8e9099), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = value, onValueChange = if (enabled) onValueChange else { _ -> },
                    enabled = enabled,
                    singleLine = true,
                    textStyle = TextStyle(color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f), fontSize = 16.sp, fontWeight = FontWeight.W500),
                    cursorBrush = SolidColor(AuraColors.Primary),
                    visualTransformation = if (obscured) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType, imeAction = imeAction),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); onImeAction() }, onNext = { onImeAction() }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.isEmpty()) Text(hint, color = Color.White.copy(alpha = 0.25f), fontSize = 16.sp)
                            innerTextField()
                        }
                    }
                )
                if (isPassword) {
                    IconButton(onClick = { obscured = !obscured }, modifier = Modifier.size(32.dp)) {
                        Icon(if (obscured) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            "Toggle", tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordRequirement(text: String, met: Boolean) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (met) Icons.Default.CheckCircle else Icons.Default.Cancel,
            text, tint = if (met) Color(0xFF4CAF50) else Color(0xFF666666),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = if (met) Color(0xFF4CAF50) else Color(0xFF666666), fontSize = 14.sp)
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AuraColors.Error.copy(alpha = 0.12f))
            .border(1.dp, AuraColors.Error.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.ErrorOutline, "Error", tint = AuraColors.Error, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(message, color = AuraColors.Error, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RateLimitBanner(cooldown: Int) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFF9800).copy(alpha = 0.12f))
            .border(1.dp, Color(0xFFFF9800).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Timer, "Timer", tint = Color(0xFFFFB74D), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Too many attempts. Try again in ${cooldown}s", color = Color(0xFFFFB74D), fontSize = 13.sp)
    }
}

@Composable
private fun ServerSettingsDialog(currentIp: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var ip by remember { mutableStateOf(currentIp) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server Settings") },
        text = {
            OutlinedTextField(value = ip, onValueChange = { ip = it }, label = { Text("Server IP") }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = { onSave(ip) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
