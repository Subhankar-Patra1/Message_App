import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class SessionManager {
  static const _storage = FlutterSecureStorage();
  
  static const String _keyToken = 'jwt_token';
  static const String _keyRefreshToken = 'refresh_token';
  static const String _keyUserId = 'user_id';
  static const String _keyDeviceId = 'device_id';
  static const String _keyIsOnboarded = 'is_onboarded';

  static Future<void> saveSession({
    required String token,
    required String userId,
    required String deviceId,
    String? refreshToken,
  }) async {
    await _storage.write(key: _keyToken, value: token);
    await _storage.write(key: _keyUserId, value: userId);
    await _storage.write(key: _keyDeviceId, value: deviceId);
    if (refreshToken != null) {
      await _storage.write(key: _keyRefreshToken, value: refreshToken);
    }
  }

  static Future<String?> getToken() async {
    return await _storage.read(key: _keyToken);
  }

  static Future<String?> getRefreshToken() async {
    return await _storage.read(key: _keyRefreshToken);
  }

  static Future<String?> getUserId() async {
    return await _storage.read(key: _keyUserId);
  }

  static Future<String?> getDeviceId() async {
    return await _storage.read(key: _keyDeviceId);
  }

  static Future<void> clearSession() async {
    await _storage.delete(key: _keyToken);
    await _storage.delete(key: _keyRefreshToken);
    await _storage.delete(key: _keyUserId);
    await _storage.delete(key: _keyDeviceId);
  }

  static Future<bool> isOnboarded() async {
    final val = await _storage.read(key: _keyIsOnboarded);
    return val == 'true';
  }

  static Future<void> setOnboarded() async {
    await _storage.write(key: _keyIsOnboarded, value: 'true');
  }

  static Future<bool> isLoggedIn() async {
    final token = await getToken();
    return token != null && token.isNotEmpty;
  }
}
