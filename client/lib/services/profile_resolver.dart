import 'dart:async';
import '../network/api_client.dart';
import '../database_service.dart';

/// Resolves user IDs to human-readable display names and avatars.
/// 
/// Uses a local cache (Hive contacts box via DatabaseService) with a 24-hour TTL.
/// Falls back to a server fetch when the cache is empty or stale.
class ProfileResolver {
  final ApiClient apiClient;

  // In-memory pending requests to avoid duplicate concurrent fetches
  final Map<String, Future<Map<String, dynamic>?>> _pendingFetches = {};

  static const Duration _cacheTtl = Duration(hours: 24);

  ProfileResolver({required this.apiClient});

  /// Resolves a userId to a profile map: {name, avatarUrl}
  /// Returns cached data immediately if available and fresh.
  /// Otherwise fetches from server, caches, and returns.
  Future<Map<String, dynamic>?> resolve(String userId) async {
    // 1. Check local cache
    final cached = DatabaseService.getContactProfile(userId);
    if (cached != null) {
      final lastUpdated = DateTime.tryParse(cached['lastUpdated'] ?? '');
      if (lastUpdated != null && DateTime.now().difference(lastUpdated) < _cacheTtl) {
        return cached;
      }
    }

    // 2. Deduplicate concurrent fetches for the same user
    if (_pendingFetches.containsKey(userId)) {
      return _pendingFetches[userId];
    }

    // 3. Fetch from server
    final future = _fetchAndCache(userId);
    _pendingFetches[userId] = future;

    try {
      return await future;
    } finally {
      _pendingFetches.remove(userId);
    }
  }

  Future<Map<String, dynamic>?> _fetchAndCache(String userId) async {
    try {
      final data = await apiClient.fetchPublicProfile(userId);
      final name = data['display_name'] ?? data['username'] ?? userId;
      final avatarUrl = data['avatar_url'] as String?;

      await DatabaseService.saveContactProfile(userId, name, avatarUrl);

      return {
        'name': name,
        'avatarUrl': avatarUrl,
        'lastUpdated': DateTime.now().toIso8601String(),
      };
    } catch (_) {
      // Server unreachable or user not found — return whatever we have cached
      return DatabaseService.getContactProfile(userId);
    }
  }

  /// Synchronously get the cached name for a userId (no network call).
  /// Returns the userId itself if no cached name exists.
  String getNameSync(String userId) {
    final cached = DatabaseService.getContactProfile(userId);
    return cached?['name'] ?? userId;
  }

  /// Synchronously get the cached avatar URL for a userId.
  String? getAvatarSync(String userId) {
    final cached = DatabaseService.getContactProfile(userId);
    return cached?['avatarUrl'];
  }
}
