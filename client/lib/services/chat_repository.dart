import 'dart:async';
import 'package:uuid/uuid.dart';
import '../crypto/crypto_engine.dart';
import '../network/api_client.dart';
import '../network/socket_client.dart';
import '../database_service.dart';
import '../models/message_state.dart';

import 'connection_manager.dart';
import 'pending_message_queue.dart';
import 'message_sync_service.dart';
import 'receipt_service.dart';
import 'typing_service.dart';
import 'presence_service.dart';
import 'profile_resolver.dart';

class ChatRepository {
  final CryptoEngine cryptoEngine;
  final ApiClient apiClient;
  final String myUserId;

  late final SocketClient socketClient;
  late final ConnectionManager connectionManager;
  late final PendingMessageQueue pendingQueue;
  late final MessageSyncService syncService;
  late final ReceiptService receiptService;
  late final TypingService typingService;
  late final PresenceService presenceService;
  late final ProfileResolver profileResolver;

  StreamSubscription? _readyToSendSub;

  ChatRepository({
    required this.cryptoEngine,
    required this.apiClient,
    required this.myUserId,
    required String socketUrl,
    required String authToken,
  }) {
    socketClient = SocketClient(
      socketUrl: socketUrl,
      authToken: authToken,
      myUserId: myUserId,
    );
    
    connectionManager = ConnectionManager(socketClient);
    pendingQueue = PendingMessageQueue(socketClient);
    profileResolver = ProfileResolver(apiClient: apiClient);
    syncService = MessageSyncService(socketClient: socketClient, cryptoEngine: cryptoEngine, profileResolver: profileResolver);
    receiptService = ReceiptService(socketClient: socketClient, pendingQueue: pendingQueue);
    typingService = TypingService(socketClient: socketClient);
    presenceService = PresenceService(socketClient: socketClient, apiClient: apiClient);

    _readyToSendSub = pendingQueue.readyToSend.listen(_processOutgoingMessage);
  }

  Future<void> initialize() async {
    // Generate/register keys
    final payload = await cryptoEngine.generateInitialKeys();
    await apiClient.registerKeys(payload);

    // Connect socket
    await connectionManager.connect();
  }

  // Enqueue a message to be sent reliably
  Future<String> enqueueMessage(String recipientIdentifier, String plaintext, {String? msgId}) async {
    final finalMsgId = msgId ?? const Uuid().v4();
    String actualRecipientId = recipientIdentifier;

    // Resolve username if needed (for MVP/v1 we assume API resolve)
    if (!_isUuid(recipientIdentifier)) {
      try {
        final bundle = await apiClient.fetchPreKeysByUsername(recipientIdentifier);
        actualRecipientId = bundle['user_id'];
      } catch (e) {
        throw Exception('Could not resolve username: $e');
      }
    }

    // Save as pending immediately
    final Map<String, dynamic> msg = {
      'msg_id': finalMsgId,
      'recipient_id': actualRecipientId,
      'sender_id': myUserId,
      'plaintext': plaintext,
    };
    
    await DatabaseService.saveMessage(
      recipientId: actualRecipientId,
      senderId: myUserId,
      text: plaintext,
      timestamp: DateTime.now(),
      msgId: finalMsgId,
      status: MessageState.pending,
      isMe: true,
    );

    await pendingQueue.enqueue(msg);
    return finalMsgId;
  }

  // Internal processor for the pending queue
  Future<void> _processOutgoingMessage(Map<String, dynamic> msg) async {
    final actualRecipientId = msg['recipient_id'];
    final plaintext = msg['plaintext'];
    final msgId = msg['msg_id'];

    try {
      Map<String, dynamic> encryptedPayload;
      try {
        encryptedPayload = await cryptoEngine.encryptMessage(actualRecipientId, plaintext);
      } catch (e) {
        // Fetch bundle and retry
        final bundle = await apiClient.fetchPreKeys(actualRecipientId);
        await cryptoEngine.establishSession(actualRecipientId, bundle);
        encryptedPayload = await cryptoEngine.encryptMessage(actualRecipientId, plaintext);
      }

      final response = await socketClient.sendMessage(actualRecipientId, msgId, {
        'sender_id': myUserId,
        ...encryptedPayload
      });

      // Server returns {status: 'delivered', server_ts: 123, seq: 456}
      await pendingQueue.onAck(msgId, response['server_ts'] as int, response['seq'] as int);

      // Update Conversation Summary
      final existing = DatabaseService.getContactProfile(actualRecipientId);
      await DatabaseService.updateConversation(
        userId: actualRecipientId,
        name: existing?['name'] ?? actualRecipientId,
        avatarUrl: existing?['avatarUrl'],
        lastMessage: plaintext,
        lastMessageTime: DateTime.fromMillisecondsSinceEpoch(response['server_ts'] as int),
      );

    } catch (e) {
      await pendingQueue.onTimeout(msgId);
    }
  }

  bool _isUuid(String input) {
    final RegExp regex = RegExp(r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', caseSensitive: false);
    return regex.hasMatch(input);
  }

  void dispose() {
    _readyToSendSub?.cancel();
    pendingQueue.dispose();
    syncService.dispose();
    receiptService.dispose();
    typingService.dispose();
    presenceService.dispose();
    connectionManager.dispose();
  }
}
