import 'dart:async';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../auth/auth_service.dart';
import 'initialization_screen.dart';

class UsernameSetupScreen extends StatefulWidget {
  const UsernameSetupScreen({super.key});

  @override
  State<UsernameSetupScreen> createState() => _UsernameSetupScreenState();
}

class _UsernameSetupScreenState extends State<UsernameSetupScreen> {
  final _usernameController = TextEditingController();
  final _pinController = TextEditingController();
  
  final _usernameFocusNode = FocusNode();
  final _pinFocusNode = FocusNode();
  
  bool _isLoading = false;
  bool _isCheckingAvailability = false;
  String? _usernameError;
  Timer? _debounce;
  String _lastCheckedUsername = '';

  @override
  void initState() {
    super.initState();
    _usernameController.addListener(_validateUsername);
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _usernameController.dispose();
    _pinController.dispose();
    _usernameFocusNode.dispose();
    _pinFocusNode.dispose();
    super.dispose();
  }

  void _validateUsername() {
    final text = _usernameController.text;

    // Only run validation if the actual text changed (ignore cursor movements/taps)
    if (text == _lastCheckedUsername) return;
    _lastCheckedUsername = text;

    // Cancel any previous validation timer
    if (_debounce?.isActive ?? false) _debounce!.cancel();

    if (text.isEmpty) {
      if (_usernameError != null) setState(() => _usernameError = null);
      if (_isCheckingAvailability) setState(() => _isCheckingAvailability = false);
      return;
    }

    String? error;
    if (text.length < 3 || text.length > 35) {
      error = "Must be between 3 and 35 characters";
    } else if (!RegExp(r'^[a-z0-9._]+$').hasMatch(text)) {
      error = "Only a-z, 0-9, periods, and underscores allowed";
    } else if (!RegExp(r'[a-z]').hasMatch(text)) {
      error = "Must contain at least one letter";
    } else if (text.startsWith('www.')) {
      error = "Cannot start with www.";
    } else if (text.endsWith('.com') || text.endsWith('.net')) {
      error = "Cannot end with .com or .net";
    }

    // If local validation fails, set the error immediately and return
    if (error != null) {
      if (_usernameError != error) setState(() => _usernameError = error);
      if (_isCheckingAvailability) setState(() => _isCheckingAvailability = false);
      return;
    }

    // Local validation passed. Clear error and show loading indicator
    if (_usernameError != null) setState(() => _usernameError = null);
    setState(() => _isCheckingAvailability = true);

    // Debounce the backend request to avoid spamming the server
    _debounce = Timer(const Duration(milliseconds: 500), () async {
      try {
        final isAvailable = await AuthService.checkUsername(text);
        if (!mounted) return;
        
        // Ensure text hasn't changed since the request started
        if (_usernameController.text == text) {
          setState(() {
            _isCheckingAvailability = false;
            if (!isAvailable) {
              _usernameError = "This username is already taken";
            }
          });
        }
      } catch (e) {
        if (!mounted) return;
        setState(() => _isCheckingAvailability = false);
        // On network error during typing, we ignore it to not interrupt.
        // The final continue button will catch it.
      }
    });
  }

  Future<void> _handleNext() async {
    final username = _usernameController.text.trim();
    
    // If username is empty, they are skipping the setup
    if (username.isEmpty) {
      _navigateToNext();
      return;
    }

    if (_usernameError != null) {
      HapticFeedback.heavyImpact();
      return;
    }

    final pin = _pinController.text.trim();
    if (pin.isNotEmpty && pin.length != 4) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text("PIN must be exactly 4 digits"),
          backgroundColor: Colors.redAccent,
        ),
      );
      return;
    }

    HapticFeedback.mediumImpact();
    setState(() => _isLoading = true);
    
    try {
      await AuthService.setUsername(
        username: username,
        pin: pin.isNotEmpty ? pin : null,
      );
      _navigateToNext();
    } catch (e) {
      if (!mounted) return;
      final errorMessage = e.toString().replaceFirst('Exception: ', '');
      
      // If it's a validation error about the username, we can display it below the text field too
      if (errorMessage.toLowerCase().contains('taken') || errorMessage.toLowerCase().contains('already')) {
        setState(() => _usernameError = "This username is already taken");
        HapticFeedback.heavyImpact();
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(errorMessage),
            backgroundColor: Colors.redAccent,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  void _navigateToNext() {
    if (!mounted) return;
    Navigator.of(context).pushReplacement(
      MaterialPageRoute(builder: (context) => const InitializationScreen()),
    );
  }

  @override
  Widget build(BuildContext context) {
    const bgColor = Color(0xFF111318);
    const surfaceContainer = Color(0xFF1e2025);
    const primary = Color(0xFFc3c0ff);
    const outline = Color(0xFF8e9099);
    const onSurface = Color(0xFFe2e2e6);

    final isUsernameValid = _usernameController.text.isNotEmpty && _usernameError == null && !_isCheckingAvailability;
    final isSkipping = _usernameController.text.isEmpty;

    return Scaffold(
      backgroundColor: bgColor,
      appBar: PreferredSize(
        preferredSize: const Size.fromHeight(64),
        child: ClipRRect(
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
            child: Container(
              decoration: BoxDecoration(
                color: bgColor.withOpacity(0.8),
              ),
              child: SafeArea(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Row(
                    children: [
                      IconButton(
                        icon: const Icon(Icons.arrow_back, color: primary),
                        onPressed: () => Navigator.of(context).pop(),
                        splashRadius: 24,
                      ),
                      const Expanded(
                        child: Text(
                          "Create Username",
                          style: TextStyle(
                            fontFamily: 'Plus Jakarta Sans',
                            color: onSurface,
                            fontSize: 22,
                            fontWeight: FontWeight.w700,
                            letterSpacing: -0.5,
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ),
                      const SizedBox(width: 48),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
      body: SafeArea(
        child: GestureDetector(
          behavior: HitTestBehavior.translucent,
          onTap: () => FocusScope.of(context).unfocus(),
          child: CustomScrollView(
            physics: const BouncingScrollPhysics(),
            keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
            slivers: [
              SliverPadding(
                padding: const EdgeInsets.symmetric(horizontal: 24),
                sliver: SliverFillRemaining(
                  hasScrollBody: false,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const SizedBox(height: 32),
                      
                      const Text(
                        "Let friends find you",
                        style: TextStyle(
                          fontFamily: 'Plus Jakarta Sans',
                          color: Colors.white,
                          fontSize: 24,
                          fontWeight: FontWeight.w700,
                          letterSpacing: -0.5,
                        ),
                      ),
                      const SizedBox(height: 8),
                      const Text(
                        "Choose a username so people can message you without needing your phone number. This is entirely optional.",
                        style: TextStyle(
                          fontFamily: 'Plus Jakarta Sans',
                          color: outline,
                          fontSize: 14,
                          height: 1.4,
                        ),
                      ),
                      
                      const SizedBox(height: 48),
                      
                      // Username Field
                      TextField(
                        controller: _usernameController,
                        focusNode: _usernameFocusNode,
                        style: const TextStyle(color: onSurface, fontSize: 16),
                        textInputAction: TextInputAction.next,
                        onSubmitted: (_) => FocusScope.of(context).requestFocus(_pinFocusNode),
                        decoration: InputDecoration(
                          labelText: "Username (optional)",
                          labelStyle: const TextStyle(color: outline, fontFamily: 'Plus Jakarta Sans'),
                          floatingLabelStyle: TextStyle(
                            color: _usernameError != null ? Colors.redAccent : primary, 
                            fontWeight: FontWeight.w600
                          ),
                          hintText: "e.g. alex_rivera",
                          hintStyle: TextStyle(color: outline.withOpacity(0.3)),
                          filled: true,
                          fillColor: surfaceContainer,
                          contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 18),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(16),
                            borderSide: BorderSide.none,
                          ),
                          focusedBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(16),
                            borderSide: BorderSide(
                              color: _usernameError != null ? Colors.redAccent : primary, 
                              width: 1.5
                            ),
                          ),
                          errorMaxLines: 3,
                          errorText: _usernameError,
                          errorStyle: const TextStyle(
                            fontFamily: 'Plus Jakarta Sans',
                            color: Colors.redAccent,
                            fontWeight: FontWeight.w500,
                          ),
                          prefixIcon: const Icon(Icons.alternate_email, color: outline),
                          suffixIcon: _isCheckingAvailability
                              ? const Padding(
                                  padding: EdgeInsets.all(14.0),
                                  child: SizedBox(
                                    width: 16,
                                    height: 16,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                      valueColor: AlwaysStoppedAnimation<Color>(primary),
                                    ),
                                  ),
                                )
                              : (isUsernameValid
                                  ? const Icon(Icons.check_circle, color: Colors.greenAccent)
                                  : null),
                        ),
                      ),
                      
                      const SizedBox(height: 24),
                      
                      // PIN Field
                      AnimatedOpacity(
                        opacity: _usernameController.text.isNotEmpty ? 1.0 : 0.4,
                        duration: const Duration(milliseconds: 300),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              "Username PIN (Optional Anti-Spam)",
                              style: TextStyle(
                                fontFamily: 'Plus Jakarta Sans',
                                color: outline,
                                fontSize: 13,
                                fontWeight: FontWeight.w600,
                                letterSpacing: 0.5,
                              ),
                            ),
                            const SizedBox(height: 8),
                            TextField(
                              controller: _pinController,
                              focusNode: _pinFocusNode,
                              enabled: _usernameController.text.isNotEmpty,
                              keyboardType: TextInputType.number,
                              inputFormatters: [
                                FilteringTextInputFormatter.digitsOnly,
                                LengthLimitingTextInputFormatter(4),
                              ],
                              obscureText: true,
                              style: const TextStyle(color: onSurface, fontSize: 24, letterSpacing: 8),
                              textAlign: TextAlign.center,
                              textInputAction: TextInputAction.done,
                              onSubmitted: (_) => _handleNext(),
                              decoration: InputDecoration(
                                hintText: "••••",
                                hintStyle: TextStyle(color: outline.withOpacity(0.3), letterSpacing: 8),
                                filled: true,
                                fillColor: surfaceContainer,
                                contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 18),
                                border: OutlineInputBorder(
                                  borderRadius: BorderRadius.circular(16),
                                  borderSide: BorderSide.none,
                                ),
                                focusedBorder: OutlineInputBorder(
                                  borderRadius: BorderRadius.circular(16),
                                  borderSide: const BorderSide(color: primary, width: 1.5),
                                ),
                              ),
                            ),
                            const SizedBox(height: 8),
                            const Text(
                              "If you set a 4-digit PIN, strangers must know it to start a chat with you.",
                              style: TextStyle(
                                fontFamily: 'Plus Jakarta Sans',
                                color: outline,
                                fontSize: 12,
                                height: 1.3,
                              ),
                            ),
                          ],
                        ),
                      ),
                      
                      const Spacer(),
                      const SizedBox(height: 32),
                      
                      // Action Button
                      SizedBox(
                        width: double.infinity,
                        height: 56,
                        child: ElevatedButton(
                          onPressed: _isLoading ? null : _handleNext,
                          style: ElevatedButton.styleFrom(
                            backgroundColor: (isUsernameValid || isSkipping) 
                                ? primary 
                                : surfaceContainer,
                            foregroundColor: (isUsernameValid || isSkipping) 
                                ? const Color(0xFF111318) 
                                : outline,
                            elevation: 0,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(28),
                            ),
                          ),
                          child: _isLoading
                              ? const SizedBox(
                                  height: 24,
                                  width: 24,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    valueColor: AlwaysStoppedAnimation<Color>(Color(0xFF111318)),
                                  ),
                                )
                              : Text(
                                  isSkipping ? "Skip" : "Continue",
                                  style: const TextStyle(
                                    fontFamily: 'Plus Jakarta Sans',
                                    fontSize: 16,
                                    fontWeight: FontWeight.w700,
                                    letterSpacing: 0.5,
                                  ),
                                ),
                        ),
                      ),
                      const SizedBox(height: 32),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
