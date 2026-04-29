import 'dart:async';
import '../network/socket_client.dart';
import '../database_service.dart';
import '../crypto/crypto_engine.dart';
import '../models/message_state.dart';
import 'profile_resolver.dart';

class MessageSyncService {
  final SocketClient socketClient;
  final CryptoEngine cryptoEngine;
  final ProfileResolver? profileResolver;
  
  StreamSubscription? _messageSub;
  StreamSubscription? _syncSub;

  final _syncProgressController = StreamController<Map<String, dynamic>>.broadcast();
  Stream<Map<String, dynamic>> get syncProgress => _syncProgressController.stream;

  // Emits the userId of the chat that just received a message
  final _newMessageController = StreamController<String>.broadcast();
  Stream<String> get onNewMessage => _newMessageController.stream;

  final _decryptedMessageController = StreamController<Map<String, dynamic>>.broadcast();
  Stream<Map<String, dynamic>> get decryptedMessages => _decryptedMessageController.stream;

  final List<Map<String, dynamic>> _incomingBuffer = [];
  bool _isProcessingBatch = false;

  MessageSyncService({
    required this.socketClient,
    required this.cryptoEngine,
    this.profileResolver,
  }) {
    _messageSub = socketClient.incomingMessages.listen((payload) {
      // It's a regular message
      _incomingBuffer.add(payload);
      _processBuffer();
    });

    _syncSub = socketClient.syncCompleteEvents.listen((payload) {
      socketClient.sendSyncAck();
      _syncProgressController.add({'status': 'complete', 'total': payload['total']});
    });
  }

  Future<void> _processBuffer() async {
    if (_isProcessingBatch || _incomingBuffer.isEmpty) return;
    _isProcessingBatch = true;

    try {
      // Process in batches of 20 for backpressure
      final batchSize = 20;
      final batch = _incomingBuffer.take(batchSize).toList();
      
      for (var payload in batch) {
        await _handleIncomingMessage(payload);
      }
      
      _incomingBuffer.removeRange(0, batch.length);

      // Let UI breathe
      if (_incomingBuffer.isNotEmpty) {
        await Future.delayed(const Duration(milliseconds: 50));
      }
    } finally {
      _isProcessingBatch = false;
      if (_incomingBuffer.isNotEmpty) {
        _processBuffer();
      }
    }
  }

  Future<void> _handleIncomingMessage(Map<String, dynamic> payload) async {
    final senderId = payload['sender_id'] as String;
    final msgId = payload['msg_id'] as String;
    final serverTs = payload['server_ts'] as int;
    final seq = payload['seq'] as int?;

    // 1. Dedup
    if (await DatabaseService.messageExists(senderId, msgId)) {
      // Already processed, just ack
      socketClient.sendDeliveryReceipt(senderId, msgId);
      return;
    }

    // 2. Reorder buffer logic could go here if we want to hold messages.
    // For now, we will save them. DatabaseService.getMessages sorts by seq.
    
    // 3. Decrypt
    try {
      final decryptedText = await cryptoEngine.decryptMessage(
        senderId, 
        Map<String, dynamic>.from(payload)
      );

      // 4. Save to DB
      await DatabaseService.saveMessage(
        recipientId: socketClient.myUserId,
        senderId: senderId,
        text: decryptedText,
        timestamp: DateTime.fromMillisecondsSinceEpoch(serverTs),
        msgId: msgId,
        status: MessageState.delivered, // We are receiving it, so it's delivered to us
        isMe: false,
        seq: seq,
      );

      // 5. Update Conversation — resolve name via ProfileResolver
      String contactName = 'Unknown Contact';
      String? contactAvatar;
      
      final cached = DatabaseService.getContactProfile(senderId);
      if (cached != null && cached['name'] != null) {
        contactName = cached['name'];
        contactAvatar = cached['avatarUrl'];
      } else if (profileResolver != null) {
        // Kick off async resolution — the UI will update when cache is populated
        final resolved = await profileResolver!.resolve(senderId);
        if (resolved != null) {
          contactName = resolved['name'] ?? senderId;
          contactAvatar = resolved['avatarUrl'];
        }
      }

      await DatabaseService.updateConversation(
        userId: senderId,
        name: contactName,
        avatarUrl: contactAvatar,
        lastMessage: decryptedText,
        lastMessageTime: DateTime.fromMillisecondsSinceEpoch(serverTs),
        incrementUnread: true,
      );

      // 6. Send delivery receipt back
      socketClient.sendDeliveryReceipt(senderId, msgId);
      
      // 7. Notify UI
      _newMessageController.add(senderId);
      
      _decryptedMessageController.add({
        'senderId': senderId,
        'plaintext': decryptedText,
        'msg_id': msgId,
        'server_ts': serverTs,
      });

    } catch (e) {
      // Don't send delivery receipt if we can't decrypt
    }
  }

  void dispose() {
    _messageSub?.cancel();
    _syncSub?.cancel();
    _syncProgressController.close();
    _newMessageController.close();
    _decryptedMessageController.close();
  }
}
