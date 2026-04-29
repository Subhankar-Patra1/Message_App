import 'dart:async';
import 'dart:ui';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../config.dart';
import 'package:font_awesome_flutter/font_awesome_flutter.dart';
import '../auth/auth_service.dart';
import 'chat_screen.dart';
import 'home_screen.dart';
import 'onboarding/onboarding_flow.dart';
import 'package:pinput/pinput.dart';
import 'profile_setup_screen.dart';

enum AuthStep { identify, passwordInline, passwordSetup, phoneEmailCollect, otp, forgotPasswordOtp, forgotPasswordSetup }

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});
  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> with SingleTickerProviderStateMixin {
  AuthStep _currentStep = AuthStep.identify;
  bool _isLoading = false;
  String? _tempToken;
  bool _isNewUser = false;
  String? _errorMessage;

  final _identifierController = TextEditingController();
  final _passwordController = TextEditingController();
  final _otpController = TextEditingController();
  final _ipController = TextEditingController(text: AppConfig.serverIp);
  final _emailForOtpController = TextEditingController();

  bool _isPhone = false;
  bool _isInputValid = false;
  bool _obscurePassword = true;
  String _selectedCountryCode = "+91";

  // Resend OTP timer
  Timer? _resendTimer;
  int _resendCooldown = 0;

  // Rate limit cooldown
  Timer? _rateLimitTimer;
  int _rateLimitCooldown = 0;

  // Focus Nodes
  final FocusNode _otpFocusNode = FocusNode();
  final FocusNode _identifierFocusNode = FocusNode();
  final FocusNode _passwordFocusNode = FocusNode();
  final FocusNode _emailForOtpFocusNode = FocusNode();
  final FocusNode _forgotOtpFocusNode = FocusNode();
  final FocusNode _forgotPasswordFocusNode = FocusNode();

  String _resetOtpCode = "";

  bool _preferNumpad = false; // Added manual keyboard toggle state

  late AnimationController _shimmerController;
  late Animation<double> _shimmerAnimation;

  @override
  void initState() {
    super.initState();
    _shimmerController = AnimationController(vsync: this, duration: const Duration(milliseconds: 2500))..repeat();
    _shimmerAnimation = Tween<double>(begin: -1.5, end: 2.5).animate(
      CurvedAnimation(parent: _shimmerController, curve: Curves.easeInOutSine),
    );
    _identifierController.addListener(_validateInput);
    _passwordController.addListener(_validateInput);
    _otpController.addListener(_validateInput);
    _otpController.addListener(() => setState(() {}));
    _emailForOtpController.addListener(_validateInput);
  }

  void _validateInput() {
    bool isValid = false;
    bool newIsPhone = _isPhone;

    if (_currentStep == AuthStep.identify) {
      final text = _identifierController.text.trim();
      final isPhoneFormat = RegExp(r'^\+?[0-9\s\-()]+$').hasMatch(text);
      final digitCount = text.replaceAll(RegExp(r'[^0-9]'), '').length;
      newIsPhone = isPhoneFormat && digitCount == 10;
      final isEmailFormat = RegExp(r'^[\w-\.]+@([\w-]+\.)+[\w-]{2,4}$').hasMatch(text);
      isValid = newIsPhone || isEmailFormat;
    } else if (_currentStep == AuthStep.passwordInline) {
      isValid = _passwordController.text.length >= 6;
    } else if (_currentStep == AuthStep.passwordSetup) {
      isValid = _checkPasswordStrength().every((e) => e);
      // Always rebuild so individual requirement checks update on every keystroke
      setState(() { _isInputValid = isValid; _isPhone = newIsPhone; });
      return;
    } else if (_currentStep == AuthStep.phoneEmailCollect) {
      final emailText = _emailForOtpController.text.trim();
      isValid = RegExp(r'^[\w-\.]+@([\w-]+\.)+[\w-]{2,4}$').hasMatch(emailText);
    } else if (_currentStep == AuthStep.otp || _currentStep == AuthStep.forgotPasswordOtp) {
      isValid = _otpController.text.trim().length == 6;
    } else if (_currentStep == AuthStep.forgotPasswordSetup) {
      isValid = _checkPasswordStrength().every((e) => e);
      setState(() { _isInputValid = isValid; _isPhone = newIsPhone; });
      return;
    }

    if (_isInputValid != isValid || _isPhone != newIsPhone) {
      setState(() { _isInputValid = isValid; _isPhone = newIsPhone; });
    }
  }

  /// Returns [hasLength, hasUpper, hasDigit]
  List<bool> _checkPasswordStrength() {
    final pw = _passwordController.text;
    return [pw.length >= 8, RegExp(r'[A-Z]').hasMatch(pw), RegExp(r'[0-9]').hasMatch(pw)];
  }

  void _changeStep(AuthStep newStep) {
    setState(() {
      _currentStep = newStep;
    });
    
    // Request focus AFTER the AnimatedSwitcher transition completes (300ms)
    // This prevents severe jank and layout lag on Android.
    Future.delayed(const Duration(milliseconds: 350), () {
      if (!mounted) return;
      switch (_currentStep) {
        case AuthStep.identify: _identifierFocusNode.requestFocus(); break;
        case AuthStep.passwordInline:
        case AuthStep.passwordSetup: _passwordFocusNode.requestFocus(); break;
        case AuthStep.phoneEmailCollect: _emailForOtpFocusNode.requestFocus(); break;
        case AuthStep.otp: _otpFocusNode.requestFocus(); break;
        case AuthStep.forgotPasswordOtp: _forgotOtpFocusNode.requestFocus(); break;
        case AuthStep.forgotPasswordSetup: _forgotPasswordFocusNode.requestFocus(); break;
      }
    });
  }

  void _startResendTimer() {
    _resendCooldown = 60;
    _resendTimer?.cancel();
    _resendTimer = Timer.periodic(const Duration(seconds: 1), (t) {
      if (_resendCooldown <= 1) { t.cancel(); setState(() => _resendCooldown = 0); }
      else { setState(() => _resendCooldown--); }
    });
  }

  @override
  void dispose() {
    _otpFocusNode.dispose();
    _identifierFocusNode.dispose();
    _passwordFocusNode.dispose();
    _emailForOtpFocusNode.dispose();
    _forgotOtpFocusNode.dispose();
    _forgotPasswordFocusNode.dispose();
    _shimmerController.dispose();
    _identifierController.dispose();
    _passwordController.dispose();
    _otpController.dispose();
    _ipController.dispose();
    _emailForOtpController.dispose();
    _resendTimer?.cancel();
    _rateLimitTimer?.cancel();
    super.dispose();
  }

  void _startRateLimitTimer() {
    _rateLimitCooldown = 60;
    _rateLimitTimer?.cancel();
    _rateLimitTimer = Timer.periodic(const Duration(seconds: 1), (t) {
      if (_rateLimitCooldown <= 1) { t.cancel(); setState(() { _rateLimitCooldown = 0; _errorMessage = null; }); }
      else { setState(() => _rateLimitCooldown--); }
    });
  }

  Future<void> _handleContinue() async {
    if (_identifierController.text.isEmpty || _rateLimitCooldown > 0) return;
    setState(() { _isLoading = true; _errorMessage = null; });
    try {
      final response = await AuthService.identify(_identifierController.text.trim());
      setState(() {
        _isLoading = false;
        _tempToken = response['temp_token'];
        final nextStep = response['next_step'];
        if (nextStep == 'otp_input') {
          _currentStep = AuthStep.otp;
          _startResendTimer();
        } else if (nextStep == 'password_setup') {
          _currentStep = AuthStep.passwordSetup;
          _isNewUser = true;
        } else {
          _currentStep = AuthStep.passwordInline;
          _isNewUser = false;
        }
        _isInputValid = false;
      });
      HapticFeedback.mediumImpact();
    } catch (e) {
      final msg = e.toString().replaceFirst('Exception: ', '');
      final isRateLimit = msg.toLowerCase().contains('too many');
      setState(() {
        _isLoading = false;
        _errorMessage = isRateLimit ? null : msg;
      });
      if (isRateLimit) {
        _startRateLimitTimer();
      }
    }
  }

  Future<void> _handleFinalSubmit() async {
    if (_tempToken == null) return;
    setState(() { _isLoading = true; _errorMessage = null; });
    try {
      AppConfig.serverIp = _ipController.text;
      AuthResult result;
      if (_currentStep == AuthStep.passwordInline || _currentStep == AuthStep.passwordSetup) {
        result = await AuthService.verifyPassword(_tempToken!, _passwordController.text);
      } else {
        result = await AuthService.verifyOtp(_tempToken!, _otpController.text.trim());
      }
      setState(() => _isLoading = false);
      if (mounted) {
        if (result.nextStep == 'email_collect') {
          setState(() {
            _currentStep = AuthStep.phoneEmailCollect;
            _isInputValid = false;
          });
        } else if (result.nextStep == 'otp_verify') {
          setState(() {
            _currentStep = AuthStep.otp;
            _startResendTimer();
            if (result.message != null) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text(result.message!), backgroundColor: Colors.green),
              );
            }
          });
        } else if (result.isNewUser) {
          Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const ProfileSetupScreen()));
        } else if (result.requiresKeySetup) {
          Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const OnboardingFlow()));
        } else {
          Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const HomeScreen()));
        }
      }
    } catch (e) {
      setState(() {
        _isLoading = false;
        _errorMessage = e.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  Future<void> _handlePhoneEmailSubmit() async {
    if (_tempToken == null || _emailForOtpController.text.trim().isEmpty) return;
    setState(() { _isLoading = true; _errorMessage = null; });
    try {
      final result = await AuthService.sendPhoneOtp(_tempToken!, _emailForOtpController.text.trim());
      setState(() => _isLoading = false);
      if (mounted) {
        if (result.nextStep == 'otp_verify') {
          _changeStep(AuthStep.otp);
          _startResendTimer();
          if (result.message != null) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text(result.message!), backgroundColor: Colors.green),
            );
          }
        }
      }
    } catch (e) {
      final msg = e.toString().replaceFirst('Exception: ', '');
      final isSessionExpired = msg.toLowerCase().contains('session has expired');
      setState(() {
        _isLoading = false;
        _errorMessage = msg;
      });
      if (isSessionExpired && mounted) {
        // Auto-navigate back after a brief delay so user can read the message
        Future.delayed(const Duration(seconds: 2), () {
          if (!mounted) return;
          setState(() {
            _passwordController.clear();
            _otpController.clear();
            _emailForOtpController.clear();
            _errorMessage = null;
            _tempToken = null;
          });
          _changeStep(AuthStep.identify);
        });
      }
    }
  }

  Future<void> _handleGoogleLogin() async {
    setState(() { _isLoading = true; _errorMessage = null; });
    try {
      final result = await AuthService.signInWithGoogle();
      setState(() => _isLoading = false);
      if (result != null && mounted) {
        if (result.isNewUser) {
          Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const ProfileSetupScreen()));
        } else if (result.requiresKeySetup) {
          Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const OnboardingFlow()));
        } else {
          Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const HomeScreen()));
        }
      }
    } catch (e) {
      setState(() {
        _isLoading = false;
        _errorMessage = e.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  Future<void> _handleResendOtp() async {
    if (_resendCooldown > 0 || _tempToken == null) return;
    final email = _isPhone ? _emailForOtpController.text.trim() : _identifierController.text.trim();
    if (email.isEmpty) return;
    try {
      await AuthService.resendOtp(_tempToken!, email);
      _startResendTimer();
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("New code sent!")));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
    }
  }

  Future<void> _handleForgotPassword() async {
    final email = _identifierController.text.trim();
    if (email.isEmpty) return;
    setState(() { _isLoading = true; _errorMessage = null; });
    try {
      await AuthService.forgotPassword(email);
      setState(() => _isLoading = false);
      _otpController.clear();
      _changeStep(AuthStep.forgotPasswordOtp);
      _startResendTimer();
    } catch (e) {
      setState(() {
        _isLoading = false;
        _errorMessage = e.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  void _handleForgotOtpSubmit() {
    if (_otpController.text.length == 6) {
      _resetOtpCode = _otpController.text;
      _passwordController.clear();
      _errorMessage = null;
      _changeStep(AuthStep.forgotPasswordSetup);
    }
  }

  Future<void> _handleForgotPasswordReset() async {
    final email = _identifierController.text.trim();
    final newPassword = _passwordController.text;
    if (email.isEmpty || newPassword.isEmpty) return;
    setState(() { _isLoading = true; _errorMessage = null; });
    try {
      await AuthService.resetPassword(email, _resetOtpCode, newPassword);
      // Auto-login
      final result = await AuthService.identify(email);
      if (result['temp_token'] != null) {
        final authResult = await AuthService.verifyPassword(result['temp_token'] as String, newPassword);
        setState(() => _isLoading = false);
        if (mounted) {
          if (authResult.isNewUser) {
             Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const ProfileSetupScreen()));
          } else if (authResult.requiresKeySetup) {
             Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const OnboardingFlow()));
          } else {
             Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const HomeScreen()));
          }
        }
      } else {
        throw Exception("Failed to auto-login. Please login manually.");
      }
    } catch (e) {
      setState(() {
        _isLoading = false;
        _errorMessage = e.toString().replaceFirst('Exception: ', '');
        // If the reset code was invalid, send them back to the OTP screen
        if (_errorMessage!.contains("invalid_reset_code") || _errorMessage!.contains("expired")) {
           _changeStep(AuthStep.forgotPasswordOtp);
        }
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle.light,
      child: Scaffold(
        resizeToAvoidBottomInset: false,
        backgroundColor: Theme.of(context).scaffoldBackgroundColor,
        body: LayoutBuilder(
          builder: (context, constraints) {
            return SingleChildScrollView(
              child: ConstrainedBox(
                constraints: BoxConstraints(minHeight: constraints.maxHeight),
                child: IntrinsicHeight(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 32),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        SizedBox(height: MediaQuery.of(context).padding.top + 20),
                        // Header
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            if (_currentStep != AuthStep.identify)
                              IconButton(
                                icon: const Icon(Icons.arrow_back_ios_new_rounded, color: Colors.white, size: 20),
                                onPressed: () {
                                  setState(() {
                                    _passwordController.clear();
                                    _otpController.clear();
                                    _isInputValid = false;
                                    _errorMessage = null;
                                    _resendTimer?.cancel();
                                  });
                                  _changeStep(AuthStep.identify);
                                },
                              )
                            else const SizedBox(width: 48),
                            if (kDebugMode)
                              IconButton(
                                icon: Icon(Icons.settings_rounded, color: Colors.white.withOpacity(0.3), size: 20),
                                onPressed: _showServerSettings,
                              )
                            else const SizedBox(width: 48),
                          ],
                        ),
                        const SizedBox(height: 20),
                        // Title
                        Text(
                          _currentStep == AuthStep.identify ? "Welcome" :
                          _currentStep == AuthStep.passwordSetup ? "Create Password" :
                          _currentStep == AuthStep.passwordInline ? "Welcome back" : 
                          _currentStep == AuthStep.phoneEmailCollect ? "Verify Your Number" :
                          _currentStep == AuthStep.forgotPasswordOtp ? "Reset Password" :
                          _currentStep == AuthStep.forgotPasswordSetup ? "New Password" : "Verification Code",
                          style: const TextStyle(color: Colors.white, fontSize: 32, fontWeight: FontWeight.w800, letterSpacing: -1),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          _currentStep == AuthStep.identify ? "Sign in or create your account" :
                          _currentStep == AuthStep.passwordSetup ? "Choose a strong password for your account" :
                          _currentStep == AuthStep.passwordInline ? "Enter your password to continue" : 
                          _currentStep == AuthStep.phoneEmailCollect ? "Enter your email so we can send you a verification code" :
                          _currentStep == AuthStep.forgotPasswordOtp ? "Enter the reset code sent to ${_identifierController.text}" :
                          _currentStep == AuthStep.forgotPasswordSetup ? "Create a new strong password" :
                          _isPhone 
                            ? "Enter the code we sent to ${_identifierController.text}\n(Code sent to ${(_emailForOtpController.text.isNotEmpty ? _emailForOtpController.text : "your email")})"
                            : "Enter the code we sent to ${_identifierController.text}",
                          style: TextStyle(color: Colors.white.withOpacity(0.5), fontSize: 16, height: 1.4),
                        ),
                        const SizedBox(height: 40),

                        // Form
                        AnimatedSwitcher(
                          duration: const Duration(milliseconds: 300),
                          child: _buildFormContent(),
                        ),

                        const SizedBox(height: 32),

                        if (_currentStep == AuthStep.identify) ...[
                          Row(children: [
                            Expanded(child: Divider(color: Colors.white.withOpacity(0.1))),
                            Padding(padding: const EdgeInsets.symmetric(horizontal: 16),
                              child: Text("or continue with", style: TextStyle(color: Colors.white.withOpacity(0.6), fontSize: 12))),
                            Expanded(child: Divider(color: Colors.white.withOpacity(0.1))),
                          ]),
                          const SizedBox(height: 32),
                          Column(children: [
                            SizedBox(width: double.infinity,
                              child: _buildSocialButton(
                                icon: const FaIcon(FontAwesomeIcons.google, color: Colors.white, size: 20),
                                label: "Continue with Google",
                                color: Colors.white.withOpacity(0.05),
                                onPressed: _handleGoogleLogin,
                              )),
                            const SizedBox(height: 16),
                            SizedBox(width: double.infinity,
                              child: _buildSocialButton(icon: const FaIcon(FontAwesomeIcons.github, color: Colors.white, size: 20), label: "Continue with GitHub", color: Colors.white.withOpacity(0.05),
                                onPressed: () { ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("GitHub login coming soon!"))); })),
                          ]),
                        ],

                        const Spacer(),
                        Center(child: Text("By continuing, you agree to our Terms & Privacy Policy",
                          style: TextStyle(color: Colors.white.withOpacity(0.6), fontSize: 12))),
                        const SizedBox(height: 24),
                      ],
                    ),
                  ),
                ),
              ),
            );
          },
        ),
      ),
    );
  }

  Widget _buildFormContent() {
    switch (_currentStep) {
      case AuthStep.identify:
        return Column(key: const ValueKey("id_form"), children: [
          if (_isPhone)
            Padding(
              padding: const EdgeInsets.only(left: 4, bottom: 8),
              child: Text(
                "Email or Phone Number",
                style: const TextStyle(
                  color: Color(0xFF8e9099),
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  fontFamily: 'Plus Jakarta Sans',
                  letterSpacing: 0.5,
                ),
              ),
            ),
          Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
            if (_isPhone)
              AnimatedContainer(
                duration: const Duration(milliseconds: 300), 
                height: 58,
                margin: const EdgeInsets.only(right: 12),
                padding: const EdgeInsets.symmetric(horizontal: 16),
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [
                      Colors.white.withOpacity(0.08),
                      Colors.white.withOpacity(0.03),
                    ],
                  ),
                  borderRadius: BorderRadius.circular(18),
                  border: Border.all(
                    color: Colors.white.withOpacity(0.1),
                    width: 1,
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withOpacity(0.15),
                      blurRadius: 12,
                      offset: const Offset(0, 4),
                    ),
                  ],
                ),
                child: Center(
                  child: Text(
                    _selectedCountryCode, 
                    style: const TextStyle(
                      color: Colors.white, 
                      fontSize: 16, 
                      fontWeight: FontWeight.w700,
                      fontFamily: 'Plus Jakarta Sans',
                    )
                  )
                ),
              ),
            Expanded(child: _buildGlassTextField(
              controller: _identifierController, 
              focusNode: _identifierFocusNode,
              label: "Email or Phone Number",
              hint: _isPhone ? "Enter your phone number" : "name@example.com",
              showLabel: !_isPhone,
              icon: _isPhone ? Icons.phone_android_rounded : Icons.email_outlined,
              keyboardType: _preferNumpad ? TextInputType.phone : TextInputType.emailAddress,
              customSuffix: IconButton(
                icon: Icon(_preferNumpad ? Icons.keyboard_alt_outlined : Icons.dialpad_rounded, 
                  color: Colors.white.withOpacity(0.4), size: 22),
                onPressed: () {
                  setState(() {
                    _preferNumpad = !_preferNumpad;
                    _identifierFocusNode.unfocus();
                    Future.delayed(const Duration(milliseconds: 50), () {
                      if (mounted) _identifierFocusNode.requestFocus(); 
                    });
                  });
                },
              ),
            )),
          ]),
          const SizedBox(height: 16),
          if (_errorMessage != null) _buildErrorBanner(),
          if (_rateLimitCooldown > 0)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 14),
                decoration: BoxDecoration(
                  color: Colors.orange.withOpacity(0.12),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: Colors.orange.withOpacity(0.3)),
                ),
                child: Row(children: [
                  Icon(Icons.timer_outlined, color: Colors.orange.shade300, size: 18),
                  const SizedBox(width: 8),
                  Expanded(child: Text("Too many attempts. Try again in ${_rateLimitCooldown}s",
                    style: TextStyle(color: Colors.orange.shade300, fontSize: 13))),
                ]),
              ),
            ),
          _buildShimmerButton(
            _isLoading ? "Connecting..." : (_rateLimitCooldown > 0 ? "Wait ${_rateLimitCooldown}s" : "Continue"),
            _handleContinue,
            loading: _isLoading,
            enabled: _isInputValid && _rateLimitCooldown == 0,
          ),
        ]);

      case AuthStep.passwordInline:
        return Column(key: const ValueKey("pw_inline"), children: [
          // Show identifier (read-only)
          _buildGlassTextField(controller: _identifierController, label: "Email", icon: Icons.email_outlined, enabled: false),
          const SizedBox(height: 16),
          _buildGlassTextField(controller: _passwordController, focusNode: _passwordFocusNode, label: "Password", hint: "Enter your password", icon: Icons.lock_outline, isPassword: true),
          Align(alignment: Alignment.centerRight, child: TextButton(
            onPressed: _handleForgotPassword,
            child: Text("Forgot password?", style: TextStyle(color: const Color(0xFF6C5CE7).withOpacity(0.8), fontSize: 13)))),
          const SizedBox(height: 8),
          if (_errorMessage != null) _buildErrorBanner(),
          _buildShimmerButton("Login", _handleFinalSubmit, loading: _isLoading, enabled: _isInputValid),
        ]);

      case AuthStep.passwordSetup:
        final checks = _checkPasswordStrength();
        final allMet = checks.every((e) => e);
        return Column(key: const ValueKey("pw_setup"), children: [
          _buildGlassTextField(controller: _passwordController, focusNode: _passwordFocusNode, label: "Create Password", hint: "Enter a strong password", icon: Icons.lock_outline, isPassword: true),
          const SizedBox(height: 16),
          // Password requirements
          _buildRequirement("At least 8 characters", checks[0]),
          _buildRequirement("Contains an uppercase letter", checks[1]),
          _buildRequirement("Contains a number", checks[2]),
          const SizedBox(height: 24),
          if (_errorMessage != null) _buildErrorBanner(),
          _buildShimmerButton("Create Account", _handleFinalSubmit, loading: _isLoading, enabled: _isInputValid),
        ]);

      case AuthStep.phoneEmailCollect:
        return Column(key: const ValueKey("phone_email_form"), children: [
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: const Color(0xFF1e2025),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: const Color(0xFF8e9099).withOpacity(0.2)),
            ),
            child: Row(children: [
              const Icon(Icons.info_outline_rounded, color: Color(0xFFc3c0ff), size: 20),
              const SizedBox(width: 12),
              Expanded(child: Text(
                "We'll send a verification code to your email to verify your phone number.",
                style: const TextStyle(color: Color(0xFFe2e2e6), fontSize: 13, fontFamily: 'Plus Jakarta Sans'),
              )),
            ]),
          ),
          const SizedBox(height: 20),
          _buildGlassTextField(
            controller: _emailForOtpController,
            focusNode: _emailForOtpFocusNode,
            label: "Your email address",
            hint: "yourname@gmail.com",
            icon: Icons.email_outlined,
            keyboardType: TextInputType.emailAddress,
          ),
          const SizedBox(height: 20),
          if (_errorMessage != null) _buildErrorBanner(),
          _buildShimmerButton("Send Verification Code", _handlePhoneEmailSubmit, loading: _isLoading, enabled: _isInputValid),
        ]);

      case AuthStep.otp:
        final identifier = _identifierController.text.trim();
        final email = _isPhone ? _emailForOtpController.text.trim() : identifier;
        
        return Column(key: const ValueKey("otp_form"), children: [
          // Custom OTP Input (6 boxes)
          _buildOtpInput(),
          
          const SizedBox(height: 8),
          
          // Row for links below OTP input
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              // "Wrong number?" or "Wrong email?" link
              TextButton(
                onPressed: () {
                  setState(() {
                    _otpController.clear();
                    _errorMessage = null;
                  });
                  _changeStep(AuthStep.identify);
                },
                child: Text(
                  _isPhone ? "Wrong number?" : "Wrong email?",
                  style: const TextStyle(color: Color(0xFF6C5CE7), fontSize: 13, fontWeight: FontWeight.w600),
                ),
              ),
              
              // Resend timer
              _resendCooldown > 0
                ? Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Text("Resend code in ${_resendCooldown}s", style: TextStyle(color: Colors.white.withOpacity(0.4), fontSize: 13)),
                  )
                : TextButton(
                    onPressed: _handleResendOtp,
                    child: const Text("Resend code", style: TextStyle(color: Color(0xFF6C5CE7), fontSize: 13, fontWeight: FontWeight.w600)),
                  ),
            ],
          ),
          
          const SizedBox(height: 12),
          if (_errorMessage != null) _buildErrorBanner(),
          if (_errorMessage != null) const SizedBox(height: 12),
          
          // Only show Verify button if there's an error or it's currently loading
          if (_errorMessage != null || _isLoading)
            _buildShimmerButton("Verify", _handleFinalSubmit, loading: _isLoading, enabled: _isInputValid),
        ]);

      case AuthStep.forgotPasswordOtp:
        return Column(key: const ValueKey("forgot_otp_form"), children: [
          _buildOtpInput(isForgotFlow: true),
          const SizedBox(height: 8),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              TextButton(
                onPressed: () {
                  setState(() { _otpController.clear(); _errorMessage = null; });
                  _changeStep(AuthStep.identify);
                },
                child: const Text("Wrong email?", style: TextStyle(color: Color(0xFF6C5CE7), fontSize: 13, fontWeight: FontWeight.w600)),
              ),
              _resendCooldown > 0
                ? Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Text("Resend code in ${_resendCooldown}s", style: TextStyle(color: Colors.white.withOpacity(0.4), fontSize: 13)),
                  )
                : TextButton(
                    onPressed: _handleForgotPassword, // Resend reset code
                    child: const Text("Resend code", style: TextStyle(color: Color(0xFF6C5CE7), fontSize: 13, fontWeight: FontWeight.w600)),
                  ),
            ],
          ),
          const SizedBox(height: 12),
          if (_errorMessage != null) _buildErrorBanner(),
          if (_errorMessage != null) const SizedBox(height: 12),
          if (_errorMessage != null || _isLoading)
            _buildShimmerButton("Verify", _handleForgotOtpSubmit, loading: _isLoading, enabled: _isInputValid),
        ]);

      case AuthStep.forgotPasswordSetup:
        final checks = _checkPasswordStrength();
        final allMet = checks.every((e) => e);
        return Column(key: const ValueKey("forgot_pw_setup"), children: [
          _buildGlassTextField(controller: _passwordController, focusNode: _forgotPasswordFocusNode, label: "New Password", hint: "Enter new password", icon: Icons.lock_outline, isPassword: true),
          const SizedBox(height: 16),
          _buildRequirement("At least 8 characters", checks[0]),
          _buildRequirement("One uppercase letter", checks[1]),
          _buildRequirement("One number", checks[2]),
          const SizedBox(height: 24),
          if (_errorMessage != null) _buildErrorBanner(),
          _buildShimmerButton("Reset & Login", _handleForgotPasswordReset, loading: _isLoading, enabled: _isInputValid && allMet),
        ]);
    }
  }

  Widget _buildOtpInput({bool isForgotFlow = false}) {
    const primary = Color(0xFFc3c0ff);
    const onSurface = Color(0xFFe2e2e6);

    final defaultPinTheme = PinTheme(
      width: 52,
      height: 64,
      textStyle: const TextStyle(
        fontFamily: 'Plus Jakarta Sans',
        fontSize: 28, 
        color: onSurface, 
        fontWeight: FontWeight.w700,
      ),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.03),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.white.withOpacity(0.08), width: 1),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.2),
            blurRadius: 8,
            offset: const Offset(0, 4),
          )
        ],
      ),
    );

    final focusedPinTheme = defaultPinTheme.copyDecorationWith(
      color: Colors.white.withOpacity(0.06),
      border: Border.all(color: primary.withOpacity(0.8), width: 2),
      boxShadow: [
        BoxShadow(
          color: primary.withOpacity(0.25),
          blurRadius: 16,
          spreadRadius: 2,
        )
      ],
    );

    final errorPinTheme = defaultPinTheme.copyDecorationWith(
      border: Border.all(color: Colors.redAccent.withOpacity(0.8), width: 2),
      boxShadow: [
        BoxShadow(
          color: Colors.redAccent.withOpacity(0.2),
          blurRadius: 16,
          spreadRadius: 2,
        )
      ],
    );

    return Pinput(
      length: 6,
      controller: _otpController,
      focusNode: _otpFocusNode,
      defaultPinTheme: defaultPinTheme,
      focusedPinTheme: focusedPinTheme,
      errorPinTheme: errorPinTheme,
      forceErrorState: _errorMessage != null,
      separatorBuilder: (index) {
        if (index == 2) {
          return const Padding(
            padding: EdgeInsets.symmetric(horizontal: 6),
            child: Text("-", style: TextStyle(color: Colors.white24, fontSize: 24)),
          );
        }
        return const SizedBox(width: 6);
      },
      showCursor: true,
      cursor: Container(
        width: 2,
        height: 24,
        color: primary,
      ),
      onCompleted: (pin) {
        if (_isInputValid && !_isLoading) {
          if (isForgotFlow) {
            _handleForgotOtpSubmit();
          } else {
            _handleFinalSubmit();
          }
        }
      },
    );
  }

  Widget _buildRequirement(String text, bool met) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(children: [
        Icon(met ? Icons.check_circle_rounded : Icons.radio_button_unchecked,
          color: met ? const Color(0xFF00D2A0) : Colors.white.withOpacity(0.3), size: 18),
        const SizedBox(width: 10),
        Text(text, style: TextStyle(color: met ? const Color(0xFF00D2A0) : Colors.white.withOpacity(0.4), fontSize: 13)),
      ]),
    );
  }

  Widget _buildErrorBanner() {
    String friendlyMsg = _errorMessage!;
    final lowerMsg = friendlyMsg.toLowerCase();
    
    if (lowerMsg.contains('session has expired')) {
      friendlyMsg = 'Your session expired. Taking you back...';
    } else if (lowerMsg.contains('invalid_otp')) {
      friendlyMsg = 'The code you entered is incorrect. Please try again.';
    } else if (lowerMsg.contains('invalid_password') || lowerMsg.contains('invalid_credentials')) {
      friendlyMsg = 'Incorrect password. Please try again.';
    } else if (lowerMsg.contains('not_found')) {
      friendlyMsg = 'Account not found. Please check your details.';
    } else if (friendlyMsg.contains('_')) {
      friendlyMsg = friendlyMsg.replaceAll('_', ' ');
      friendlyMsg = friendlyMsg[0].toUpperCase() + friendlyMsg.substring(1);
    }

    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 14),
        decoration: BoxDecoration(
          color: Colors.red.withOpacity(0.12),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: Colors.red.withOpacity(0.3)),
        ),
        child: Row(children: [
          Icon(Icons.error_outline_rounded, color: Colors.red.shade300, size: 18),
          const SizedBox(width: 8),
          Expanded(child: Text(friendlyMsg, style: TextStyle(color: Colors.red.shade300, fontSize: 13))),
        ]),
      ),
    );
  }

  Widget _buildGlassTextField({
    required TextEditingController controller, required String label, required IconData icon,
    bool isPassword = false, TextInputType keyboardType = TextInputType.text, bool autoFocus = false, bool enabled = true,
    Widget? customSuffix, FocusNode? focusNode, bool showLabel = true, String? hint,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (showLabel)
          Padding(
            padding: const EdgeInsets.only(left: 8, bottom: 8),
            child: Text(
              label,
              style: TextStyle(
                color: Colors.white.withOpacity(0.5),
                fontSize: 12,
                fontWeight: FontWeight.w600,
                fontFamily: 'Plus Jakarta Sans',
                letterSpacing: 0.5,
              ),
            ),
          ),
        Container(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(18),
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [
                Colors.white.withOpacity(0.08),
                Colors.white.withOpacity(0.03),
              ],
            ),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.1),
                blurRadius: 10,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: TextField(
            controller: controller, 
            obscureText: isPassword ? _obscurePassword : false,
            keyboardType: keyboardType, 
            autofocus: autoFocus, 
            enabled: enabled,
            focusNode: focusNode,
            style: TextStyle(
              color: enabled ? const Color(0xFFe2e2e6) : const Color(0xFFe2e2e6).withOpacity(0.5), 
              fontSize: 16, 
              fontFamily: 'Plus Jakarta Sans',
              fontWeight: FontWeight.w500,
            ),
            decoration: InputDecoration(
              hintText: hint ?? (!showLabel ? label : null),
              hintStyle: TextStyle(color: Colors.white.withOpacity(0.15), fontSize: 15),
              prefixIcon: Icon(icon, color: Colors.white.withOpacity(0.3), size: 22),
              suffixIcon: isPassword 
                ? IconButton(
                    icon: Icon(_obscurePassword ? Icons.visibility_off_rounded : Icons.visibility_rounded,
                      color: Colors.white.withOpacity(0.3), size: 20),
                    onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                  ) 
                : customSuffix,
              filled: false,
              contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 18),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(18), 
                borderSide: BorderSide(color: Colors.white.withOpacity(0.1), width: 1),
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(18), 
                borderSide: BorderSide(color: Colors.white.withOpacity(0.08), width: 1),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(18), 
                borderSide: const BorderSide(color: Color(0xFFc3c0ff), width: 1.5),
              ),
              disabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(18), 
                borderSide: BorderSide(color: Colors.white.withOpacity(0.03), width: 1),
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildShimmerButton(String text, VoidCallback onPressed, {bool loading = false, bool enabled = true}) {
    return AnimatedOpacity(
      duration: const Duration(milliseconds: 300), opacity: enabled ? 1.0 : 0.5,
      child: SizedBox(width: double.infinity, height: 56,
        child: ClipRRect(borderRadius: BorderRadius.circular(28),
          child: Stack(alignment: Alignment.center, children: [
            Container(color: const Color(0xFFc3c0ff)),
            if (!loading && enabled)
              AnimatedBuilder(animation: _shimmerAnimation, builder: (context, child) {
                return Container(decoration: BoxDecoration(gradient: LinearGradient(
                  begin: Alignment(_shimmerAnimation.value - 1, -1.0), end: Alignment(_shimmerAnimation.value + 1, 1.0),
                  colors: [Colors.white.withOpacity(0.0), Colors.white.withOpacity(0.3), Colors.white.withOpacity(0.0)],
                  stops: const [0.3, 0.5, 0.7])));
              }),
            SizedBox(width: double.infinity, height: 56,
              child: ElevatedButton(
                onPressed: (loading || !enabled) ? null : onPressed,
                style: ElevatedButton.styleFrom(backgroundColor: Colors.transparent, shadowColor: Colors.transparent,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(28))),
                child: loading
                  ? const SizedBox(height: 24, width: 24, child: CircularProgressIndicator(color: Color(0xFF1e2025), strokeWidth: 2))
                  : Text(text, style: const TextStyle(fontSize: 16, fontFamily: 'Plus Jakarta Sans', fontWeight: FontWeight.bold, color: Color(0xFF1e2025))),
              )),
          ]))));
  }

  Widget _buildSocialButton({required Widget icon, required String label, required Color color, required VoidCallback onPressed}) {
    return SizedBox(height: 60, child: ElevatedButton(onPressed: onPressed,
      style: ElevatedButton.styleFrom(backgroundColor: color, elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(30), side: BorderSide(color: Colors.white.withOpacity(0.1)))),
      child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
        icon, const SizedBox(width: 12),
        Text(label, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
      ])));
  }

  void _showServerSettings() {
    showModalBottomSheet(context: context, backgroundColor: const Color(0xFF1E1E3F),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
      builder: (context) => Padding(
        padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom, left: 24, right: 24, top: 24),
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text("Advanced Server Setup", style: TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.bold)),
          const SizedBox(height: 24),
          _buildGlassTextField(controller: _ipController, label: "Server IP Address", hint: "e.g. 192.168.1.1", icon: Icons.dns_rounded),
          const SizedBox(height: 24),
          SizedBox(width: double.infinity, height: 50,
            child: ElevatedButton(
              style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF6C5CE7),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(25))),
              onPressed: () => Navigator.pop(context),
              child: const Text("Save Configuration", style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)))),
          const SizedBox(height: 40),
        ])));
  }
}
