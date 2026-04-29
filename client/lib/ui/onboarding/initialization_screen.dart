import 'dart:ui';
import 'package:flutter/material.dart';
import '../../auth/session_manager.dart';
import '../../crypto/crypto_engine.dart';
import '../../crypto/hive_signal_store.dart';
import '../../network/api_client.dart';
import '../../config.dart';
import '../chat_screen.dart';
import '../home_screen.dart';

class InitializationScreen extends StatefulWidget {
  const InitializationScreen({Key? key}) : super(key: key);

  @override
  State<InitializationScreen> createState() => _InitializationScreenState();
}

class _InitializationScreenState extends State<InitializationScreen> with TickerProviderStateMixin {
  late AnimationController _spinController1;
  late AnimationController _spinController2;
  late AnimationController _spinController3;
  late AnimationController _pulseController;
  
  int _currentStep = 0;
  double _progressWidth = 0.0;

  @override
  void initState() {
    super.initState();
    
    // Setup rotation animations
    _spinController1 = AnimationController(vsync: this, duration: const Duration(seconds: 3))..repeat();
    _spinController2 = AnimationController(vsync: this, duration: const Duration(seconds: 2))..repeat(reverse: false);
    _spinController3 = AnimationController(vsync: this, duration: const Duration(milliseconds: 1500))..repeat();
    
    // Setup pulse animation
    _pulseController = AnimationController(vsync: this, duration: const Duration(milliseconds: 1000))..repeat(reverse: true);

    _generateAndRegisterKeys();
  }

  Future<void> _generateAndRegisterKeys() async {
    try {
      // Setup engine variables
      final store = HiveSignalStore();
      final apiClient = ApiClient(baseUrl: AppConfig.apiBaseUrl);

      // ---------------------------------------------------------
      // REAL STEP 1: "Creating secure vault..."
      // ---------------------------------------------------------
      Future.microtask(() {
        if (mounted) setState(() { _currentStep = 0; _progressWidth = 0.3; });
      });
      
      // Initialize the offline database
      await store.init();
      final cryptoEngine = CryptoEngine(store);
      
      // Actually generate the Identity and Pre-Keys locally on the CPU
      final payload = await cryptoEngine.generateInitialKeys();

      // ---------------------------------------------------------
      // REAL STEP 2: "Registering public keys..."
      // ---------------------------------------------------------
      if (!mounted) return;
      setState(() {
        _currentStep = 1;
        _progressWidth = 0.65;
      });

      // Actually upload the keys over the network to the Elixir backend
      // If the network is slow, the UI will pulse "In Progress" right here until it finishes!
      await apiClient.registerKeys(payload);

      // ---------------------------------------------------------
      // REAL STEP 3: "Syncing handshake protocols..."
      // ---------------------------------------------------------
      if (!mounted) return;
      setState(() {
        _currentStep = 2;
        _progressWidth = 0.90;
      });

      // Prepare any final session states, verify network token, etc.
      // We add a tiny 600ms buffer just so the user can see the final checkmark appear
      // before they are instantly teleported to the Chat screen.
      await Future.delayed(const Duration(milliseconds: 600));
      
      if (!mounted) return;
      setState(() => _progressWidth = 1.0);
      await Future.delayed(const Duration(milliseconds: 200));

      if (!mounted) return;
      _finishOnboarding();
    } catch (e) {
      debugPrint("Error initializing keys: $e");
      // Optionally show an error dialog here
    }
  }

  Future<void> _finishOnboarding() async {
    await SessionManager.setOnboarded();
    if (mounted) {
      Navigator.of(context).pushReplacement(
        PageRouteBuilder(
          transitionDuration: const Duration(milliseconds: 800),
          pageBuilder: (context, animation, secondaryAnimation) => const _WelcomeSuccessScreen(),
          transitionsBuilder: (context, animation, secondaryAnimation, child) {
            return FadeTransition(opacity: animation, child: child);
          },
        ),
      );
    }
  }

  @override
  void dispose() {
    _spinController1.dispose();
    _spinController2.dispose();
    _spinController3.dispose();
    _pulseController.dispose();
    super.dispose();
  }

  Widget _buildRotatingRing({
    required Animation<double> animation,
    required Color color,
    required double size,
    required double opacity,
    bool reverse = false,
  }) {
    return AnimatedBuilder(
      animation: animation,
      builder: (context, child) {
        final angle = (reverse ? -1 : 1) * animation.value * 2 * 3.14159265359;
        return Transform.rotate(
          angle: angle,
          child: Container(
            width: size,
            height: size,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              border: Border(
                top: BorderSide(color: color.withOpacity(opacity), width: 2),
                right: const BorderSide(color: Colors.transparent, width: 2),
                bottom: const BorderSide(color: Colors.transparent, width: 2),
                left: const BorderSide(color: Colors.transparent, width: 2),
              ),
            ),
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    const bgColor = Color(0xFF111318);
    const primary = Color(0xFFC3C0FF);
    const success = Color(0xFF4ade80);
    const textGrey = Color(0xFF879393);

    return Scaffold(
      backgroundColor: bgColor,
      body: Stack(
        children: [
          // Background Glows
          Positioned(
            top: -100,
            right: -100,
            child: Container(
              width: 300,
              height: 300,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: primary.withOpacity(0.05),
              ),
              child: BackdropFilter(
                filter: ImageFilter.blur(sigmaX: 120, sigmaY: 120),
                child: Container(),
              ),
            ),
          ),
          Positioned(
            bottom: -50,
            left: -50,
            child: Container(
              width: 250,
              height: 250,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: primary.withOpacity(0.05),
              ),
              child: BackdropFilter(
                filter: ImageFilter.blur(sigmaX: 100, sigmaY: 100),
                child: Container(),
              ),
            ),
          ),
          
          SafeArea(
            child: Center(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 24),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    // Spinning Logo
                    SizedBox(
                      width: 128,
                      height: 128,
                      child: Stack(
                        alignment: Alignment.center,
                        children: [
                          _buildRotatingRing(
                            animation: _spinController1,
                            color: primary,
                            size: 128,
                            opacity: 0.4,
                          ),
                          _buildRotatingRing(
                            animation: _spinController2,
                            color: Colors.white,
                            size: 112,
                            opacity: 0.6,
                            reverse: true,
                          ),
                          _buildRotatingRing(
                            animation: _spinController3,
                            color: primary,
                            size: 96,
                            opacity: 0.8,
                          ),
                          const Icon(
                            Icons.security_rounded,
                            color: primary,
                            size: 40,
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 48),
                    
                    // Typography
                    const Text(
                      "Initializing...",
                      style: TextStyle(
                        fontFamily: 'Plus Jakarta Sans',
                        fontSize: 32,
                        fontWeight: FontWeight.w700,
                        color: Colors.white,
                        letterSpacing: -0.5,
                      ),
                    ),
                    const SizedBox(height: 16),
                    Text(
                      "Securing your connection & generating cryptographic keys for end-to-end privacy.",
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontFamily: 'Plus Jakarta Sans',
                        fontSize: 16,
                        color: Colors.white.withOpacity(0.7),
                        height: 1.5,
                      ),
                    ),
                    const SizedBox(height: 48),
                    
                    // Glass Card
                    Container(
                      padding: const EdgeInsets.all(24),
                      decoration: BoxDecoration(
                        color: Colors.white.withOpacity(0.02),
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(color: Colors.white.withOpacity(0.05)),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withOpacity(0.2),
                            blurRadius: 30,
                            offset: const Offset(0, 4),
                          ),
                        ],
                      ),
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(16),
                        child: BackdropFilter(
                          filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              // Progress bar
                              Container(
                                height: 2,
                                width: double.infinity,
                                decoration: BoxDecoration(
                                  color: Colors.white.withOpacity(0.05),
                                  borderRadius: BorderRadius.circular(2),
                                ),
                                child: LayoutBuilder(
                                  builder: (context, constraints) {
                                    return AnimatedContainer(
                                      duration: const Duration(milliseconds: 500),
                                      curve: Curves.easeOut,
                                      alignment: Alignment.centerLeft,
                                      child: Container(
                                        width: constraints.maxWidth * _progressWidth,
                                        height: 2,
                                        color: primary,
                                      ),
                                    );
                                  },
                                ),
                              ),
                              const SizedBox(height: 24),
                              
                              // Step 1
                              _buildStepItem(
                                title: "Creating secure vault...",
                                status: "Done",
                                statusColor: success,
                                icon: Container(
                                  width: 20,
                                  height: 20,
                                  decoration: BoxDecoration(
                                    color: success.withOpacity(0.2),
                                    shape: BoxShape.circle,
                                  ),
                                  child: const Icon(Icons.check, color: success, size: 14),
                                ),
                                isActive: _currentStep >= 0,
                                isPending: false,
                              ),
                              const SizedBox(height: 16),
                              
                              // Step 2
                              _buildStepItem(
                                title: "Registering public keys...",
                                status: _currentStep >= 1 ? (_currentStep >= 2 ? "Done" : "In Progress") : "Waiting",
                                statusColor: _currentStep >= 2 ? success : (_currentStep >= 1 ? primary : textGrey),
                                icon: _currentStep >= 2
                                    ? Container(
                                        width: 20,
                                        height: 20,
                                        decoration: BoxDecoration(
                                          color: success.withOpacity(0.2),
                                          shape: BoxShape.circle,
                                        ),
                                        child: const Icon(Icons.check, color: success, size: 14),
                                      )
                                    : _currentStep == 1
                                        ? FadeTransition(
                                            opacity: _pulseController,
                                            child: Container(
                                              width: 20,
                                              height: 20,
                                              decoration: BoxDecoration(
                                                border: Border.all(color: primary.withOpacity(0.4)),
                                                shape: BoxShape.circle,
                                              ),
                                              alignment: Alignment.center,
                                              child: Container(
                                                width: 6,
                                                height: 6,
                                                decoration: const BoxDecoration(color: primary, shape: BoxShape.circle),
                                              ),
                                            ),
                                          )
                                        : Container(
                                            width: 20,
                                            height: 20,
                                            decoration: BoxDecoration(
                                              border: Border.all(color: Colors.white.withOpacity(0.2)),
                                              shape: BoxShape.circle,
                                            ),
                                          ),
                                isActive: _currentStep >= 1,
                                isPending: _currentStep < 1,
                                animatePulse: _currentStep == 1,
                              ),
                              const SizedBox(height: 16),
                              
                              // Step 3
                              _buildStepItem(
                                title: "Syncing handshake protocols...",
                                status: _currentStep >= 2 ? "Done" : "Waiting",
                                statusColor: _currentStep >= 2 ? success : textGrey,
                                icon: _currentStep >= 2
                                    ? Container(
                                        width: 20,
                                        height: 20,
                                        decoration: BoxDecoration(
                                          color: success.withOpacity(0.2),
                                          shape: BoxShape.circle,
                                        ),
                                        child: const Icon(Icons.check, color: success, size: 14),
                                      )
                                    : Container(
                                        width: 20,
                                        height: 20,
                                        decoration: BoxDecoration(
                                          border: Border.all(color: Colors.white.withOpacity(0.2)),
                                          shape: BoxShape.circle,
                                        ),
                                      ),
                                isActive: _currentStep >= 2,
                                isPending: _currentStep < 2,
                                animatePulse: _currentStep == 2,
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStepItem({
    required String title,
    required String status,
    required Color statusColor,
    required Widget icon,
    required bool isActive,
    required bool isPending,
    bool animatePulse = false,
  }) {
    final titleColor = isPending ? Colors.white.withOpacity(0.4) : (isActive ? Colors.white : Colors.white.withOpacity(0.7));
    
    Widget content = Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Expanded(
          child: Row(
            children: [
              icon,
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  title,
                  style: TextStyle(
                    fontFamily: 'Plus Jakarta Sans',
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: titleColor,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(width: 8),
        Text(
          status,
          style: TextStyle(
            fontFamily: 'Plus Jakarta Sans',
            fontSize: 12,
            fontWeight: FontWeight.w500,
            color: statusColor,
          ),
        ),
      ],
    );

    if (animatePulse) {
      return FadeTransition(
        opacity: _pulseController,
        child: content,
      );
    }

    return Opacity(
      opacity: isPending ? 0.4 : 1.0,
      child: content,
    );
  }
}

class _WelcomeSuccessScreen extends StatefulWidget {
  const _WelcomeSuccessScreen();

  @override
  State<_WelcomeSuccessScreen> createState() => _WelcomeSuccessScreenState();
}

class _WelcomeSuccessScreenState extends State<_WelcomeSuccessScreen> {
  double _opacity = 0.0;

  @override
  void initState() {
    super.initState();
    
    // 1. Wait a tiny bit, then fade IN the text
    Future.delayed(const Duration(milliseconds: 300), () {
      if (mounted) setState(() => _opacity = 1.0);
    });

    // 2. Wait for user to read it, then fade OUT the text
    Future.delayed(const Duration(milliseconds: 2000), () {
      if (mounted) setState(() => _opacity = 0.0);
    });

    // 3. Once text is completely gone, fade into the ChatScreen
    Future.delayed(const Duration(milliseconds: 2800), () {
      if (mounted) {
        Navigator.of(context).pushReplacement(
          PageRouteBuilder(
            transitionDuration: const Duration(milliseconds: 800),
            pageBuilder: (context, animation, secondaryAnimation) => const HomeScreen(),
            transitionsBuilder: (context, animation, secondaryAnimation, child) {
              return FadeTransition(opacity: animation, child: child);
            },
          ),
        );
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF111318),
      body: Center(
        child: AnimatedOpacity(
          opacity: _opacity,
          duration: const Duration(milliseconds: 800),
          curve: Curves.easeInOut,
          child: const Padding(
            padding: EdgeInsets.symmetric(horizontal: 24.0),
            child: Text(
              "Welcome to Aura",
              textAlign: TextAlign.center,
              style: TextStyle(
                fontFamily: 'Plus Jakarta Sans',
                color: Colors.white,
                fontSize: 32,
                fontWeight: FontWeight.w700,
                letterSpacing: -0.5,
              ),
            ),
          ),
        ),
      ),
    );
  }
}
