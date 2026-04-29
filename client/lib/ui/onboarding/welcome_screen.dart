import 'package:flutter/material.dart';
import 'package:flutter/gestures.dart';
import 'dart:ui';
import '../../auth/session_manager.dart';
import 'onboarding_flow.dart';

class WelcomeScreen extends StatelessWidget {
  const WelcomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final primaryColor = Theme.of(context).colorScheme.primary;

    return Scaffold(
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 24.0),
          child: Column(
            children: [
              const Spacer(flex: 3),
              
              // Abstract Chat Bubbles Graphic
              const _ChatBubbleGraphic(),
              
              const SizedBox(height: 48),
              
              // Headline
              Text(
                "Take privacy with you.",
                style: TextStyle(
                  fontSize: 28,
                  fontWeight: FontWeight.bold,
                  color: isDark ? Colors.white : Colors.black87,
                  letterSpacing: -0.5,
                ),
                textAlign: TextAlign.center,
              ),
              
              const SizedBox(height: 16),
              
              // Subtitle
              Text(
                "Be yourself in every message.",
                style: TextStyle(
                  fontSize: 16,
                  color: isDark ? Colors.white70 : Colors.black54,
                ),
                textAlign: TextAlign.center,
              ),
              
              const Spacer(flex: 4),
              
              // Privacy Policy
              Text.rich(
                TextSpan(
                  text: 'Terms & Privacy Policy',
                  style: TextStyle(
                    color: primaryColor,
                    fontSize: 14,
                    fontWeight: FontWeight.w500,
                  ),
                  recognizer: TapGestureRecognizer()..onTap = () {
                    // Open Privacy Policy
                  },
                ),
              ),
              
              const SizedBox(height: 32),
              
              // Get Started Button
              _ShimmerButton( // <-- Updated here
                onPressed: () {
                  // Navigate to OnboardingFlow (permissions)
                  Navigator.pushReplacement(
                    context,
                    MaterialPageRoute(builder: (_) => const OnboardingFlow()),
                  );
                },
                text: "Get Started",
                color: primaryColor,
                isDark: isDark,
              ),
              
              const SizedBox(height: 16),
            ],
          ),
        ),
      ),
    );
  }
}

class _ChatBubbleGraphic extends StatelessWidget {
  const _ChatBubbleGraphic();

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 200,
      height: 160,
      child: Stack(
        alignment: Alignment.center,
        children: [
          // Back Left Bubble (Teal)
          Positioned(
            left: 10,
            top: 10,
            child: CustomPaint(
              size: const Size(110, 80),
              painter: _BubblePainter(color: const Color(0xFF6C9E95), isLeft: true),
            ),
          ),
          
          // Front Right Bubble (Light/Purple tint)
          Positioned(
            right: 10,
            bottom: 20,
            child: CustomPaint(
              size: const Size(120, 90),
              painter: _BubblePainter(color: const Color(0xFFE3E8FF), isLeft: false),
            ),
          ),
          
          // Center Frosted Glass Lock
          Positioned(
            child: ClipRRect(
              borderRadius: BorderRadius.circular(20),
              child: BackdropFilter(
                filter: ImageFilter.blur(sigmaX: 8, sigmaY: 8),
                child: Container(
                  width: 70,
                  height: 70,
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.2),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: Colors.white.withValues(alpha: 0.5), width: 1.5),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withValues(alpha: 0.1),
                        blurRadius: 10,
                      )
                    ]
                  ),
                  child: const Center(
                    child: Icon(
                      Icons.lock_rounded,
                      color: Colors.white,
                      size: 32,
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _BubblePainter extends CustomPainter {
  final Color color;
  final bool isLeft;

  _BubblePainter({required this.color, required this.isLeft});

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.fill;

    final path = Path();
    final double r = 24.0;
    final double tailW = 12.0;
    final double tailH = 14.0;
    final double bodyH = size.height - tailH;
    
    if (isLeft) {
      // Start at Top-Left, just past the curve
      path.moveTo(tailW + r, 0);
      
      // Top edge
      path.lineTo(size.width - r, 0);
      // Top-Right corner
      path.quadraticBezierTo(size.width, 0, size.width, r);
      
      // Right edge
      path.lineTo(size.width, bodyH - r);
      // Bottom-Right corner
      path.quadraticBezierTo(size.width, bodyH, size.width - r, bodyH);
      
      // Bottom edge (drawing towards the left)
      path.lineTo(tailW + 18, bodyH);
      
      // Tail: curve down to tip
      path.quadraticBezierTo(tailW, bodyH, 0, size.height);
      
      // Tail: curve back UP from the tip to the left edge
      path.quadraticBezierTo(tailW, size.height, tailW, bodyH - 10);
      
      // Left edge (drawing upwards)
      path.lineTo(tailW, r);
      // Top-Left corner
      path.quadraticBezierTo(tailW, 0, tailW + r, 0);
      
      path.close();
    } else {
      // Start at Top-Left, just past the curve
      path.moveTo(r, 0);
      
      // Top edge
      path.lineTo(size.width - tailW - r, 0);
      // Top-Right corner
      path.quadraticBezierTo(size.width - tailW, 0, size.width - tailW, r);
      
      // Right edge (drawing downwards)
      path.lineTo(size.width - tailW, bodyH - 10);
      
      // Tail: curve down to tip
      path.quadraticBezierTo(size.width - tailW, size.height, size.width, size.height);
      
      // Tail: curve back UP from the tip to the bottom edge
      path.quadraticBezierTo(size.width - tailW, bodyH, size.width - tailW - 18, bodyH);
      
      // Bottom edge (drawing towards the left)
      path.lineTo(r, bodyH);
      // Bottom-Left corner
      path.quadraticBezierTo(0, bodyH, 0, bodyH - r);
      
      // Left edge (drawing upwards)
      path.lineTo(0, r);
      // Top-Left corner
      path.quadraticBezierTo(0, 0, r, 0);
      
      path.close();
    }

    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

class _ShimmerButton extends StatefulWidget {
  final VoidCallback onPressed;
  final String text;
  final Color color;
  final bool isDark;

  const _ShimmerButton({
    required this.onPressed,
    required this.text,
    required this.color,
    required this.isDark,
  });

  @override
  State<_ShimmerButton> createState() => _ShimmerButtonState();
}

class _ShimmerButtonState extends State<_ShimmerButton> with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _animation;

  @override
  void initState() {
    super.initState();
    // A 2.5 second duration gives it that calm, premium Telegram-style pacing
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 2500),
    );
    
    // Animate from off-screen left (-1.5) to off-screen right (2.5)
    _animation = Tween<double>(begin: -1.5, end: 2.5).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeInOutSine),
    );
    
    _controller.repeat();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 52,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(26),
        child: Stack(
          alignment: Alignment.center,
          children: [
            // 1. Base solid color
            Container(
              width: double.infinity,
              height: 52,
              color: widget.color,
            ),
            
            // 2. The Animated Shimmer Layer
            AnimatedBuilder(
              animation: _animation,
              builder: (context, child) {
                return Container(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      // Angled slightly for that slick diagonal sweep
                      begin: Alignment(_animation.value - 1, -1.0),
                      end: Alignment(_animation.value + 1, 1.0),
                      colors: [
                        Colors.white.withValues(alpha: 0.0), // Transparent
                        Colors.white.withValues(alpha: 0.3), // The bright shimmer peak
                        Colors.white.withValues(alpha: 0.0), // Transparent
                      ],
                      // Keeps the bright spot relatively tight
                      stops: const [0.3, 0.5, 0.7], 
                    ),
                  ),
                );
              },
            ),
            
            // 3. Interactive button layer (kept transparent so the shimmer shows underneath)
            SizedBox(
              width: double.infinity,
              height: 52,
              child: ElevatedButton(
                onPressed: widget.onPressed,
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.transparent,
                  foregroundColor: widget.isDark ? Colors.black : Colors.white,
                  shadowColor: Colors.transparent,
                  elevation: 0,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(26),
                  ),
                ).copyWith(
                  splashFactory: InkRipple.splashFactory,
                  overlayColor: WidgetStateProperty.resolveWith<Color?>(
                    (Set<WidgetState> states) {
                      if (states.contains(WidgetState.pressed)) {
                        return (widget.isDark ? Colors.black : Colors.white).withValues(alpha: 0.2);
                      }
                      return null;
                    },
                  ),
                ),
                child: Text(
                  widget.text,
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

