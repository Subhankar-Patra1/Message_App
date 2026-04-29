import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:google_sign_in/google_sign_in.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import '../config.dart';
import 'session_manager.dart';



/// Result from verify_password or verify_otp
class AuthResult {
  final bool requiresKeySetup;
  final bool isNewUser;
  final String? nextStep;
  final String? message;

  AuthResult({
    required this.requiresKeySetup,
    required this.isNewUser,
    this.nextStep,
    this.message,
  });
}

/// HTTP wrapper that auto-refreshes JWT on 401 responses.
class AuthenticatedClient {
  static Future<http.Response> get(Uri uri) async {
    var token = await SessionManager.getToken();
    var response = await http.get(uri, headers: _headers(token));

    if (response.statusCode == 401) {
      final refreshed = await AuthService.refreshToken();
      if (refreshed) {
        token = await SessionManager.getToken();
        response = await http.get(uri, headers: _headers(token));
      }
    }
    return response;
  }

  static Future<http.Response> post(Uri uri, {Object? body}) async {
    var token = await SessionManager.getToken();
    var response = await http.post(uri, headers: _headers(token), body: body);

    if (response.statusCode == 401) {
      final refreshed = await AuthService.refreshToken();
      if (refreshed) {
        token = await SessionManager.getToken();
        response = await http.post(uri, headers: _headers(token), body: body);
      }
    }
    return response;
  }

  static Future<http.Response> put(Uri uri, {Object? body}) async {
    var token = await SessionManager.getToken();
    var response = await http.put(uri, headers: _headers(token), body: body);

    if (response.statusCode == 401) {
      final refreshed = await AuthService.refreshToken();
      if (refreshed) {
        token = await SessionManager.getToken();
        response = await http.put(uri, headers: _headers(token), body: body);
      }
    }
    return response;
  }

  static Future<http.Response> delete(Uri uri) async {
    var token = await SessionManager.getToken();
    var response = await http.delete(uri, headers: _headers(token));

    if (response.statusCode == 401) {
      final refreshed = await AuthService.refreshToken();
      if (refreshed) {
        token = await SessionManager.getToken();
        response = await http.delete(uri, headers: _headers(token));
      }
    }
    return response;
  }

  static Map<String, String> _headers(String? token) => {
    'Content-Type': 'application/json',
    if (token != null) 'Authorization': 'Bearer $token',
  };
}

class AuthService {
  static final String _baseUrl = AppConfig.apiBaseUrl;

  static final GoogleSignIn _googleSignIn = GoogleSignIn(
    serverClientId: '132565546749-7fjpe9g1ekrtnvjri1dlk2034vmgf2r4.apps.googleusercontent.com',
  );

  /// Sign in with Google
  static Future<AuthResult?> signInWithGoogle() async {
    try {
      final GoogleSignInAccount? googleUser = await _googleSignIn.signIn();
      if (googleUser == null) return null; // User cancelled

      final GoogleSignInAuthentication googleAuth = await googleUser.authentication;
      final String? idToken = googleAuth.idToken;

      if (idToken == null) throw Exception("Could not get ID Token from Google");

      final uri = Uri.parse('$_baseUrl/api/v1/auth/google');
      final response = await http.post(
        uri,
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'id_token': idToken}),
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        await SessionManager.saveSession(
          token: data['token'],
          userId: data['user_id'],
          deviceId: data['device_id'],
          refreshToken: data['refresh_token'],
        );
        return AuthResult(
          requiresKeySetup: data['requires_key_setup'] == true,
          isNewUser: data['is_new_user'] == true,
        );
      } else {
        throw Exception(jsonDecode(response.body)['error'] ?? 'Google login failed');
      }
    } catch (e) {
      rethrow;
    }
  }

  /// Step 1: Identify if email/phone exists
  /// Returns { message, next_step, temp_token }
  static Future<Map<String, dynamic>> identify(String identifier) async {
    final uri = Uri.parse('$_baseUrl/api/v1/auth/identify');
    final response = await http.post(
      uri,
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'identifier': identifier}),
    );

    if (response.statusCode == 200) {
      return jsonDecode(response.body);
    } else if (response.statusCode == 429) {
      throw Exception('Too many attempts. Please try again later.');
    } else {
      throw Exception(jsonDecode(response.body)['error'] ?? 'Identification failed');
    }
  }

  /// Step 2a: Verify Password (login or signup)
  static Future<AuthResult> verifyPassword(String tempToken, String password) async {
    final uri = Uri.parse('$_baseUrl/api/v1/auth/verify_password');
    final response = await http.post(
      uri,
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'temp_token': tempToken,
        'password': password,
      }),
    );

    if (response.statusCode == 200) {
      final data = jsonDecode(response.body);
      
      if (data['next_step'] == 'otp_verify' || data['next_step'] == 'email_collect') {
        return AuthResult(
          requiresKeySetup: false,
          isNewUser: true,
          nextStep: data['next_step'],
          message: data['message'],
        );
      }
      
      await SessionManager.saveSession(
        token: data['token'],
        userId: data['user_id'],
        deviceId: data['device_id'],
        refreshToken: data['refresh_token'],
      );
      return AuthResult(
        requiresKeySetup: data['requires_key_setup'] == true,
        isNewUser: data['is_new_user'] == true,
      );
    } else if (response.statusCode == 422) {
      final data = jsonDecode(response.body);
      final details = (data['details'] as List?)?.join(', ') ?? 'Password too weak';
      throw Exception(details);
    } else {
      throw Exception(jsonDecode(response.body)['error'] ?? 'Invalid credentials');
    }
  }

  /// Step 2b: Verify OTP
  static Future<AuthResult> verifyOtp(String tempToken, String otp) async {
    final uri = Uri.parse('$_baseUrl/api/v1/auth/verify_otp');
    final response = await http.post(
      uri,
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'temp_token': tempToken,
        'otp': otp,
      }),
    );

    if (response.statusCode == 200) {
      final data = jsonDecode(response.body);
      await SessionManager.saveSession(
        token: data['token'],
        userId: data['user_id'],
        deviceId: data['device_id'],
        refreshToken: data['refresh_token'],
      );
      return AuthResult(
        requiresKeySetup: data['requires_key_setup'] == true,
        isNewUser: data['is_new_user'] == true,
      );
    } else {
      final error = jsonDecode(response.body)['error'] ?? 'Invalid OTP';
      throw Exception(error);
    }
  }

  /// Resend OTP to the specified email
  static Future<void> resendOtp(String tempToken, String email) async {
    final uri = Uri.parse('$_baseUrl/api/v1/auth/resend_otp');
    final response = await http.post(
      uri,
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'temp_token': tempToken,
        'email': email,
      }),
    );

    if (response.statusCode == 429) {
      throw Exception('Please wait before requesting a new code.');
    } else if (response.statusCode != 200) {
      throw Exception(jsonDecode(response.body)['error'] ?? 'Failed to resend');
    }
  }

  /// Send OTP for phone number signup (sends to user's provided email)
  static Future<AuthResult> sendPhoneOtp(String tempToken, String email) async {
    final uri = Uri.parse('$_baseUrl/api/v1/auth/send_phone_otp');
    final response = await http.post(
      uri,
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'temp_token': tempToken,
        'email': email,
      }),
    );

    if (response.statusCode == 200) {
      final data = jsonDecode(response.body);
      return AuthResult(
        requiresKeySetup: false,
        isNewUser: true,
        nextStep: data['next_step'],
        message: data['message'],
      );
    } else if (response.statusCode == 422) {
      throw Exception(jsonDecode(response.body)['error'] ?? 'Invalid email');
    } else {
      throw Exception(jsonDecode(response.body)['error'] ?? 'Failed to send verification');
    }
  }

  /// Request a password reset code
  static Future<void> forgotPassword(String email) async {
    final uri = Uri.parse('$_baseUrl/api/v1/auth/forgot_password');
    await http.post(
      uri,
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'email': email}),
    );
    // Always succeeds (anti-enumeration)
  }

  /// Reset password with code
  static Future<void> resetPassword(String email, String code, String newPassword) async {
    final uri = Uri.parse('$_baseUrl/api/v1/auth/reset_password');
    final response = await http.post(
      uri,
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'email': email,
        'code': code,
        'new_password': newPassword,
      }),
    );

    if (response.statusCode == 422) {
      final data = jsonDecode(response.body);
      final details = (data['details'] as List?)?.join(', ') ?? 'Password too weak';
      throw Exception(details);
    } else if (response.statusCode != 200) {
      throw Exception(jsonDecode(response.body)['error'] ?? 'Reset failed');
    }
  }

  /// Silently refresh the access token using the stored refresh token.
  static Future<bool> refreshToken() async {
    final storedRefreshToken = await SessionManager.getRefreshToken();
    if (storedRefreshToken == null) return false;

    final uri = Uri.parse('$_baseUrl/api/v1/auth/refresh');
    try {
      final response = await http.post(
        uri,
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'refresh_token': storedRefreshToken}),
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final userId = await SessionManager.getUserId();
        final deviceId = await SessionManager.getDeviceId();
        await SessionManager.saveSession(
          token: data['token'],
          userId: userId ?? '',
          deviceId: deviceId ?? '',
          refreshToken: data['refresh_token'],
        );
        return true;
      }
    } catch (_) {}
    return false;
  }

  /// Update the user's profile (name and avatar)
  static Future<void> updateProfile({
    required String firstName,
    String? lastName,
    String? imagePath,
  }) async {
    var token = await SessionManager.getToken();
    final uri = Uri.parse('$_baseUrl/api/v1/account/profile');

    Future<http.Response> sendRequest(String? currentToken) async {
      var request = http.MultipartRequest('PUT', uri);
      request.headers['Authorization'] = 'Bearer $currentToken';
      
      request.fields['first_name'] = firstName;
      if (lastName != null && lastName.isNotEmpty) {
        request.fields['last_name'] = lastName;
      }

      if (imagePath != null && imagePath.isNotEmpty) {
        if (kIsWeb) {
          final fileResponse = await http.get(Uri.parse(imagePath));
          request.files.add(http.MultipartFile.fromBytes(
            'avatar',
            fileResponse.bodyBytes,
            filename: 'avatar.jpg',
          ));
        } else {
          request.files.add(await http.MultipartFile.fromPath('avatar', imagePath));
        }
      }

      final streamedResponse = await request.send();
      return await http.Response.fromStream(streamedResponse);
    }

    var response = await sendRequest(token);

    if (response.statusCode == 401) {
      final refreshed = await refreshToken();
      if (refreshed) {
        token = await SessionManager.getToken();
        response = await sendRequest(token);
      }
    }

    if (response.statusCode != 200) {
      throw Exception(jsonDecode(response.body)['error'] ?? 'Failed to update profile');
    }
  }

  /// Fetch the current user's profile
  static Future<Map<String, dynamic>> getProfile() async {
    final uri = Uri.parse('$_baseUrl/api/v1/account/profile');
    final response = await AuthenticatedClient.get(uri);
    if (response.statusCode == 200) {
      return jsonDecode(response.body);
    } else {
      throw Exception(jsonDecode(response.body)['error'] ?? 'Failed to fetch profile');
    }
  }

  /// Update the user's username and optional PIN
  static Future<void> setUsername({
    required String username,
    String? pin,
  }) async {
    final uri = Uri.parse('$_baseUrl/api/v1/account/username');
    final Map<String, dynamic> body = {'username': username};
    if (pin != null && pin.isNotEmpty) {
      body['pin'] = pin;
    }

    final response = await AuthenticatedClient.put(uri, body: jsonEncode(body));

    if (response.statusCode != 200) {
      final errorBody = jsonDecode(response.body);
      
      if (errorBody['errors'] != null && errorBody['errors'] is Map) {
        final errors = errorBody['errors'] as Map;
        if (errors.containsKey('username')) {
          final msgs = errors['username'] as List;
          throw Exception("Username ${msgs.first}");
        }
      }
      
      throw Exception(errorBody['error'] ?? 'Failed to set username');
    }
  }

  /// Check if a username is available
  static Future<bool> checkUsername(String username) async {
    final uri = Uri.parse('$_baseUrl/api/v1/account/check_username?username=${Uri.encodeComponent(username)}');
    try {
      final response = await http.get(uri);
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        return data['available'] == true;
      }
      return false;
    } catch (_) {
      // In case of network errors, default to false or handle differently.
      // For real-time check, returning false is safer, but returning true might avoid false positives if network flickers.
      // We will let the final submit catch actual errors.
      return true; // Assume available so we don't block typing on network blip
    }
  }
}
