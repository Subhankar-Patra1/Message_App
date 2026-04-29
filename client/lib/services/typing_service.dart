import 'dart:async';
import '../network/socket_client.dart';

class TypingService {
  final SocketClient socketClient;
  
  StreamSubscription? _typingSub;
  
  // Emits the userId and their typing state (true = typing, false = stopped)
  final _typingStateController = StreamController<Map<String, dynamic>>.broadcast();
  Stream<Map<String, dynamic>> get typingState => _typingStateController.stream;

  // Track active typers and auto-clear timers
  final Map<String, Timer> _activeTypers = {};
  
  // Throttle outbound typing events
  final Map<String, DateTime> _lastSentTyping = {};

  TypingService({required this.socketClient}) {
    _typingSub = socketClient.typingEvents.listen(_handleIncomingTyping);
  }

  void _handleIncomingTyping(Map<String, dynamic> payload) {
    final event = payload['event'] as String;
    final userId = payload['user_id'] as String;

    if (event == 'typing_start') {
      _typingStateController.add({'userId': userId, 'isTyping': true});
      
      // Auto-clear after 7 seconds
      _activeTypers[userId]?.cancel();
      _activeTypers[userId] = Timer(const Duration(seconds: 7), () {
        _typingStateController.add({'userId': userId, 'isTyping': false});
        _activeTypers.remove(userId);
      });
      
    } else if (event == 'typing_stop') {
      _activeTypers[userId]?.cancel();
      _activeTypers.remove(userId);
      _typingStateController.add({'userId': userId, 'isTyping': false});
    }
  }

  void sendTyping(String recipientId) {
    final now = DateTime.now();
    final lastSent = _lastSentTyping[recipientId];
    
    // Throttle outbound to once every 3 seconds max
    if (lastSent == null || now.difference(lastSent).inSeconds >= 3) {
      socketClient.sendTyping(recipientId);
      _lastSentTyping[recipientId] = now;
    }
  }

  void sendStopTyping(String recipientId) {
    socketClient.sendStopTyping(recipientId);
    _lastSentTyping.remove(recipientId);
  }

  void dispose() {
    _typingSub?.cancel();
    _typingStateController.close();
    for (var timer in _activeTypers.values) {
      timer.cancel();
    }
    _activeTypers.clear();
  }
}
