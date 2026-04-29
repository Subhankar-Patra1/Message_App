import 'dart:async';
import '../network/socket_client.dart';
import '../network/api_client.dart';

class PresenceService {
  final SocketClient socketClient;
  final ApiClient apiClient;
  
  StreamSubscription? _presenceSub;
  
  // Emits the userId and their presence data
  final _presenceStateController = StreamController<Map<String, dynamic>>.broadcast();
  Stream<Map<String, dynamic>> get presenceState => _presenceStateController.stream;

  // In-memory cache of presence
  final Map<String, Map<String, dynamic>> _presenceCache = {};

  PresenceService({
    required this.socketClient,
    required this.apiClient,
  }) {
    _presenceSub = socketClient.presenceEvents.listen(_handleIncomingPresence);
  }

  void _handleIncomingPresence(Map<String, dynamic> payload) {
    // payload structure from Phoenix Presence diff
    final joins = payload['joins'] as Map<String, dynamic>?;
    final leaves = payload['leaves'] as Map<String, dynamic>?;

    if (joins != null) {
      for (var userId in joins.keys) {
        final state = {
          'userId': userId,
          'online': true,
          'lastSeenAt': null, // Online right now
        };
        _presenceCache[userId] = state;
        _presenceStateController.add(state);
      }
    }

    if (leaves != null) {
      for (var userId in leaves.keys) {
        // Technically they might still be joined on another device, but for v1 we'll treat a leave as offline
        // and fetch fresh status from API to be sure.
        // We add a short delay to allow the server to write the disconnect timestamp to the DB
        Future.delayed(const Duration(milliseconds: 1000), () {
          _checkStatusFallback(userId);
        });
      }
    }
  }

  Future<void> _checkStatusFallback(String userId) async {
    try {
      final data = await apiClient.fetchUserStatus(userId);
      
      final state = {
        'userId': userId,
        'online': data['online'] == true,
        'lastSeenAt': data['last_seen_at'], // ISO8601 string or null
      };
      
      _presenceCache[userId] = state;
      _presenceStateController.add(state);
    } catch (e) {
      // Ignore API errors, default to offline
      final state = {
        'userId': userId,
        'online': false,
        'lastSeenAt': null,
      };
      _presenceCache[userId] = state;
      _presenceStateController.add(state);
    }
  }

  Future<Map<String, dynamic>> getPresence(String userId) async {
    if (_presenceCache.containsKey(userId)) {
      // Return cached state but kick off an async refresh if offline
      if (_presenceCache[userId]!['online'] == false) {
        _checkStatusFallback(userId);
      }
      return _presenceCache[userId]!;
    }

    // Await first fetch
    await _checkStatusFallback(userId);
    return _presenceCache[userId]!;
  }

  void dispose() {
    _presenceSub?.cancel();
    _presenceStateController.close();
  }
}
