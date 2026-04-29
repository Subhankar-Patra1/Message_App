import 'package:flutter/material.dart';
import '../login_screen.dart';
import '../../auth/session_manager.dart';
import 'welcome_screen.dart';
import 'permissions_screen.dart';
import 'privacy_screen.dart';
import 'backup_screen.dart';

class OnboardingFlow extends StatefulWidget {
  const OnboardingFlow({Key? key}) : super(key: key);

  @override
  _OnboardingFlowState createState() => _OnboardingFlowState();
}

class _OnboardingFlowState extends State<OnboardingFlow> {
  final PageController _pageController = PageController();
  int _currentPage = 0;

  void _nextPage() {
    if (_currentPage < 2) {
      _pageController.nextPage(
        duration: const Duration(milliseconds: 400),
        curve: Curves.easeInOutCubic,
      );
    } else {
      _finishOnboarding();
    }
  }

  Future<void> _finishOnboarding() async {
    await SessionManager.setOnboarded();
    if (mounted) {
      Navigator.of(context).pushReplacement(
        PageRouteBuilder(
          pageBuilder: (context, animation, secondaryAnimation) => const LoginScreen(),
          transitionsBuilder: (context, animation, secondaryAnimation, child) {
            return FadeTransition(opacity: animation, child: child);
          },
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    
    return Scaffold(
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: PageView(
                controller: _pageController,
                onPageChanged: (index) {
                  setState(() => _currentPage = index);
                },
                physics: const NeverScrollableScrollPhysics(),
                children: [
                  PermissionsScreen(
                    onNext: _nextPage,
                    onNotNow: _nextPage,
                  ),
                  const PrivacyScreen(),
                  const BackupScreen(),
                ],
              ),
            ),
            
            // Shared Onboarding Footer
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  if (_currentPage == 0)
                    TextButton(
                      onPressed: () {
                        // Go back to Welcome Screen
                        Navigator.of(context).pushReplacement(
                          MaterialPageRoute(builder: (_) => const WelcomeScreen()),
                        );
                      },
                      style: TextButton.styleFrom(
                        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                      ),
                      child: const Text(
                        "Back",
                        style: TextStyle(
                          fontSize: 16,
                          color: Colors.blue, // Simplified for brevity in this call, but matches context
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    )
                  else
                    IconButton(
                      onPressed: () {
                        _pageController.previousPage(
                          duration: const Duration(milliseconds: 400),
                          curve: Curves.easeInOutCubic,
                        );
                      },
                      icon: Icon(
                        Icons.arrow_back_rounded,
                        color: isDark ? Colors.white70 : Colors.black54,
                      ),
                    ),
                  
                  SizedBox(
                    height: 52,
                    child: FilledButton.tonal(
                      onPressed: _nextPage,
                      style: FilledButton.styleFrom(
                        padding: const EdgeInsets.symmetric(horizontal: 32),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(26),
                        ),
                      ),
                      child: Text(
                        _currentPage == 2 ? 'Get Started' : 'Next',
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
