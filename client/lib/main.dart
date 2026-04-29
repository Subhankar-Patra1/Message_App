import 'package:flutter/material.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:firebase_core/firebase_core.dart';
import 'ui/onboarding/welcome_screen.dart';
import 'ui/login_screen.dart';
import 'ui/home_screen.dart';
import 'auth/session_manager.dart';
import 'database_service.dart';
import 'services/service_locator.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Initialize Firebase (make sure google-services.json is present)
  try {
    await Firebase.initializeApp();
  } catch (e) {
    debugPrint("Firebase not initialized. Ensure google-services.json is present. $e");
  }
  
  await Hive.initFlutter();
  await DatabaseService.initialize();
  
  final bool onboarded = await SessionManager.isOnboarded();
  final bool loggedIn = await SessionManager.isLoggedIn();
  
  if (loggedIn) {
    // Don't await this, let it initialize in the background 
    // so we don't get stuck on the splash screen!
    ServiceLocator.init().catchError((e) {
      debugPrint("ServiceLocator init error: $e");
    });
  }
  
  Widget initialScreen = const WelcomeScreen();
  if (onboarded) {
    initialScreen = loggedIn ? const HomeScreen() : const LoginScreen();
  }

  runApp(E2EEChatApp(initialScreen: initialScreen));
}

class E2EEChatApp extends StatelessWidget {
  final Widget initialScreen;
  
  const E2EEChatApp({super.key, required this.initialScreen});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Aura',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: Colors.blue,
          brightness: Brightness.light,
        ),
      ),
      darkTheme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: Colors.blue,
          brightness: Brightness.dark,
        ),
      ),
      home: initialScreen,
    );
  }
}
