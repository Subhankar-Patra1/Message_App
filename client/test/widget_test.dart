// This is a basic Flutter widget test.
//
// To perform an interaction with a widget in your test, use the WidgetTester
// utility in the flutter_test package. For example, you can send tap and scroll
// gestures. You can also use WidgetTester to find child widgets in the widget
// tree, read text, and verify that the values of widget properties are correct.

import 'package:flutter_test/flutter_test.dart';

import 'package:client/main.dart';
import 'package:client/ui/onboarding/welcome_screen.dart';

void main() {
  testWidgets('App loads LoginScreen correctly', (WidgetTester tester) async {
    // Build our app and trigger a frame.
    await tester.pumpWidget(const E2EEChatApp(initialScreen: WelcomeScreen()));

    // Verify that the login screen title and fields are present.
    expect(find.text('E2EE Chat Setup'), findsOneWidget);
    expect(find.text('Server IP Address'), findsOneWidget);
    expect(find.text('Your User ID'), findsOneWidget);
    expect(find.text('Start Secure Chat'), findsOneWidget);
  });
}
