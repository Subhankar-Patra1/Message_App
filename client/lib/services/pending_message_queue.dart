import 'dart:async';
import 'package:flutter/foundation.dart';
import '../database_service.dart';
import '../models/message_state.dart';
import '../network/socket_client.dart';

class PendingMessageQueue {
  final SocketClient socketClient;
  Timer? _retryTimer;
  bool _isProcessing = false;

  PendingMessageQueue(this.socketClient);

  Future<void> enqueue(Map<String, dynamic> msg) async {
    msg['state'] = MessageState.pending;
    msg['attempt_count'] = 0;
    msg['created_at'] = DateTime.now().toIso8601String();
    
    await DatabaseService.savePendingMessage(msg);
    _scheduleProcess();
  }

  Future<void> onAck(String msgId, int serverTs, int seq) async {
    final pending = DatabaseService.getPendingMessages();
    final msg = pending.firstWhere((m) => m['msg_id'] == msgId, orElse: () => {});
    
    if (msg.isNotEmpty) {
      await DatabaseService.removePendingMessage(msgId);
      
      // Move to main messages box as sent/acked
      await DatabaseService.saveMessage(
        recipientId: msg['recipient_id'],
        senderId: msg['sender_id'],
        text: msg['plaintext'], // Note: this requires plaintext to be in the pending msg
        timestamp: DateTime.fromMillisecondsSinceEpoch(serverTs),
        msgId: msgId,
        status: MessageState.sent,
        isMe: true,
        seq: seq,
      );
    }
  }

  Future<void> onTimeout(String msgId) async {
    final pending = DatabaseService.getPendingMessages();
    final msg = pending.firstWhere((m) => m['msg_id'] == msgId, orElse: () => {});
    
    if (msg.isNotEmpty) {
      int attempts = (msg['attempt_count'] as int?) ?? 0;
      attempts++;
      
      if (attempts >= 5) {
        msg['state'] = MessageState.failed;
        msg['attempt_count'] = attempts;
        await DatabaseService.savePendingMessage(msg);
        
        // Save to main messages as failed so UI shows it
        await DatabaseService.saveMessage(
          recipientId: msg['recipient_id'],
          senderId: msg['sender_id'],
          text: msg['plaintext'],
          timestamp: DateTime.now(),
          msgId: msgId,
          status: MessageState.failed,
          isMe: true,
        );
        await DatabaseService.removePendingMessage(msgId);
      } else {
        msg['state'] = MessageState.retrying;
        msg['attempt_count'] = attempts;
        msg['next_retry_at'] = DateTime.now().add(_getRetryDelay(attempts)).toIso8601String();
        await DatabaseService.savePendingMessage(msg);
        _scheduleProcess();
      }
    }
  }

  void retryAll() {
    final pending = DatabaseService.getPendingMessages();
    for (var msg in pending) {
      if (msg['state'] == MessageState.retrying || msg['state'] == MessageState.pending) {
        // Reset retry time to now to force immediate processing
        msg['next_retry_at'] = DateTime.now().toIso8601String();
        DatabaseService.savePendingMessage(msg);
      }
    }
    _scheduleProcess();
  }

  Duration _getRetryDelay(int attempt) {
    switch (attempt) {
      case 1: return const Duration(seconds: 5);
      case 2: return const Duration(seconds: 10);
      case 3: return const Duration(seconds: 20);
      case 4: return const Duration(seconds: 40);
      default: return const Duration(seconds: 60);
    }
  }

  void _scheduleProcess() {
    if (_isProcessing) return;
    _retryTimer?.cancel();
    _retryTimer = Timer(const Duration(milliseconds: 500), _processQueue);
  }

  Future<void> _processQueue() async {
    if (!socketClient.isConnected) return;
    _isProcessing = true;

    try {
      final pending = DatabaseService.getPendingMessages();
      final now = DateTime.now();

      for (var msg in pending) {
        if (msg['state'] == MessageState.failed) continue;
        
        final nextRetryAtStr = msg['next_retry_at'] as String?;
        final nextRetryAt = nextRetryAtStr != null ? DateTime.parse(nextRetryAtStr) : now;

        if (now.isAfter(nextRetryAt) || now.isAtSameMomentAs(nextRetryAt)) {
          // Process this message
          msg['state'] = MessageState.encrypting;
          await DatabaseService.savePendingMessage(msg);

          try {
            // Note: encryption happens via ChatRepository, so the queue just delegates back or the repository handles encryption before queueing.
            // For architecture purity: The queue should hold encrypted payloads ready to send, OR a callback to encrypt.
            // Since CryptoEngine isolated compute needs plaintext, it's easier if PendingQueue holds plaintext and delegates encryption to ChatRepository.
            // We will expose a stream of "ready to send" messages.
            _readyToSendController.add(msg);
          } catch (e) {
            debugPrint("Encryption error: $e");
            await onTimeout(msg['msg_id']);
          }
        }
      }
    } finally {
      _isProcessing = false;
      // Re-schedule if there are still pending items
      final stillPending = DatabaseService.getPendingMessages().where((m) => m['state'] != MessageState.failed);
      if (stillPending.isNotEmpty) {
        _retryTimer = Timer(const Duration(seconds: 5), _processQueue);
      }
    }
  }

  // Stream for ChatRepository to actually encrypt and send
  final _readyToSendController = StreamController<Map<String, dynamic>>.broadcast();
  Stream<Map<String, dynamic>> get readyToSend => _readyToSendController.stream;

  void dispose() {
    _retryTimer?.cancel();
    _readyToSendController.close();
  }
}
