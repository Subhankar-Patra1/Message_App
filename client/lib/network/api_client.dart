import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:uuid/uuid.dart';
import '../auth/session_manager.dart';
import '../auth/auth_service.dart';

class ApiClient {
  final String baseUrl;
  
  ApiClient({required this.baseUrl});

  /// Internal HTTP interceptor.
  /// Attaches the JWT, retries once with a fresh token on 401.
  Future<http.Response> _sendRequest(Future<http.Response> Function(String token) request) async {
    final token = await SessionManager.getToken() ?? '';
    var response = await request(token);

    if (response.statusCode == 401) {
      // Access token expired — try to silently refresh it
      final refreshed = await AuthService.refreshToken();
      if (refreshed) {
        final newToken = await SessionManager.getToken() ?? '';
        response = await request(newToken);
      }
    }

    return response;
  }

  Future<void> registerKeys(Map<String, dynamic> payload) async {
    final uri = Uri.parse('$baseUrl/api/v1/keys/register');
    final idempotencyKey = const Uuid().v4();

    final response = await _sendRequest((token) => http.post(
      uri,
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer $token',
        'Idempotency-Key': idempotencyKey,
      },
      body: jsonEncode({
        'version': '1.0',
        'device_id': const Uuid().v4(), // Mock unique device per run for testing
        ...payload
      }),
    ));

    if (response.statusCode != 201) {
      throw Exception('Failed to register keys: ${response.body}');
    }
  }

  Future<Map<String, dynamic>> fetchPreKeys(String recipientId) async {
    final uri = Uri.parse('$baseUrl/api/v1/pre_keys?recipient_id=$recipientId');

    final response = await _sendRequest((token) => http.get(
      uri,
      headers: {'Authorization': 'Bearer $token'},
    ));

    if (response.statusCode == 200) {
      return jsonDecode(response.body);
    } else {
      throw Exception('Failed to fetch pre keys: ${response.body}');
    }
  }

  Future<Map<String, dynamic>> fetchPreKeysByUsername(String username, {String? pin}) async {
    final query = pin != null ? '?pin=$pin' : '';
    final uri = Uri.parse('$baseUrl/api/v1/keys/fetch_by_username/$username$query');

    final response = await _sendRequest((token) => http.get(
      uri,
      headers: {'Authorization': 'Bearer $token'},
    ));

    if (response.statusCode == 200) {
      return jsonDecode(response.body);
    } else {
      throw Exception('Failed to fetch keys for username $username: ${response.statusCode} - ${response.body}');
    }
  }

  Future<Map<String, dynamic>> fetchPreKeysByIdentifier(String identifier) async {
    final uri = Uri.parse('$baseUrl/api/v1/keys/fetch_by_identifier?identifier=${Uri.encodeComponent(identifier)}');

    final response = await _sendRequest((token) => http.get(
      uri,
      headers: {'Authorization': 'Bearer $token'},
    ));

    if (response.statusCode == 200) {
      return jsonDecode(response.body);
    } else {
      throw Exception('${response.statusCode} - ${response.body}');
    }
  }

  Future<Map<String, dynamic>> getUploadUrl(int fileSize, String mimeType, String recipientId) async {
    final uri = Uri.parse('$baseUrl/api/v1/media/upload_url');
    final response = await _sendRequest((token) => http.post(
      uri,
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer $token',
      },
      body: jsonEncode({
        'file_size': fileSize,
        'mime_type': mimeType,
        'recipient_id': recipientId,
      }),
    ));

    if (response.statusCode == 200) {
      return jsonDecode(response.body);
    } else {
      throw Exception('Failed to get upload URL: ${response.body}');
    }
  }

  Future<Map<String, dynamic>> getDownloadUrl(String s3Key) async {
    final uri = Uri.parse('$baseUrl/api/v1/media/download_url');
    final response = await _sendRequest((token) => http.post(
      uri,
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer $token',
      },
      body: jsonEncode({'s3_key': s3Key}),
    ));

    if (response.statusCode == 200) {
      return jsonDecode(response.body);
    } else {
      throw Exception('Failed to get download URL: ${response.body}');
    }
  }

  Future<Map<String, dynamic>> fetchUserStatus(String userId) async {
    final uri = Uri.parse('$baseUrl/api/v1/account/status/$userId');

    final response = await _sendRequest((token) => http.get(
      uri,
      headers: {'Authorization': 'Bearer $token'},
    ));

    if (response.statusCode == 200) {
      return jsonDecode(response.body);
    } else {
      throw Exception('Failed to fetch status: ${response.body}');
    }
  }

  Future<Map<String, dynamic>> fetchPublicProfile(String userId) async {
    final uri = Uri.parse('$baseUrl/api/v1/account/public_profile/$userId');

    final response = await _sendRequest((token) => http.get(
      uri,
      headers: {'Authorization': 'Bearer $token'},
    ));

    if (response.statusCode == 200) {
      return jsonDecode(response.body);
    } else {
      throw Exception('Failed to fetch profile: ${response.body}');
    }
  }
}
