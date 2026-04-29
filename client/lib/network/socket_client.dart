import 'dart:async';
import 'package:phoenix_socket/phoenix_socket.dart';

class SocketClient {
  final String socketUrl;
  final String authToken;
  final String myUserId;
  
  PhoenixSocket? _socket;
  PhoenixChannel? _chatChannel;
  
  // Streams
  final _messageController = StreamController<Map<String, dynamic>>.broadcast();
  final _receiptController = StreamController<Map<String, dynamic>>.broadcast();
  final _typingController = StreamController<Map<String, dynamic>>.broadcast();
  final _presenceController = StreamController<Map<String, dynamic>>.broadcast();
  final _connectionStateController = StreamController<PhoenixSocketOpenEvent>.broadcast();
  final _syncCompleteController = StreamController<Map<String, dynamic>>.broadcast();

  Stream<Map<String, dynamic>> get incomingMessages => _messageController.stream;
  Stream<Map<String, dynamic>> get receiptEvents => _receiptController.stream;
  Stream<Map<String, dynamic>> get typingEvents => _typingController.stream;
  Stream<Map<String, dynamic>> get presenceEvents => _presenceController.stream;
  Stream<PhoenixSocketOpenEvent> get connectionState => _connectionStateController.stream;
  Stream<Map<String, dynamic>> get syncCompleteEvents => _syncCompleteController.stream;

  bool get isConnected => _socket?.isConnected ?? false;

  final Map<String, PhoenixChannel> _groupChannels = {};

  SocketClient({
    required this.socketUrl,
    required this.authToken,
    required this.myUserId,
  });

  Future<void> connect() async {
    if (_socket != null && _socket!.isConnected) return;

    _socket = PhoenixSocket('$socketUrl/socket/websocket', socketOptions: PhoenixSocketOptions(
      params: {'token': authToken}
    ));
    
    // Listen to socket state
    _socket!.openStream.listen((event) {
      _connectionStateController.add(event);
    });

    _socket!.closeStream.listen((event) {
      // Could emit custom disconnected event here if needed
    });

    await _socket!.connect();

    _chatChannel = _socket!.addChannel(topic: 'encrypted_chat:$myUserId');
    
    _chatChannel!.messages.listen((message) {
      final event = message.event.value;
      if (message.payload == null) return;
      final payload = Map<String, dynamic>.from(message.payload!);

      switch (event) {
        case 'receive_message':
          _messageController.add(payload);
          break;
        case 'receipt':
          _receiptController.add(payload);
          break;
        case 'read_cursor':
          _receiptController.add({'isCursor': true, ...payload});
          break;
        case 'typing_start':
        case 'typing_stop':
          _typingController.add({'event': event, ...payload});
          break;
        case 'presence_diff':
          _presenceController.add({'event': event, ...payload});
          break;
        case 'sync_complete':
          _syncCompleteController.add(payload);
          break;
      }
    });

    _chatChannel!.join();
  }

  Future<void> joinGroupChannel(String groupId) async {
    if (_socket == null) throw Exception("Socket not connected");
    if (_groupChannels.containsKey(groupId)) return;

    final channel = _socket!.addChannel(topic: 'group_chat:$groupId');
    
    channel.messages.listen((message) {
      if (message.event.value == 'receive_group_message') {
        if (message.payload != null) {
          final payload = Map<String, dynamic>.from(message.payload!);
          payload['type'] = 'group_message'; 
          _messageController.add(payload);
        }
      }
    });

    channel.join();
    _groupChannels[groupId] = channel;
  }

  Future<Map<String, dynamic>> sendMessage(String recipientId, String msgId, Map<String, dynamic> encryptedPayload) async {
    if (_chatChannel == null) throw Exception("Socket not connected");

    final payload = {
      'recipient_user_id': recipientId,
      'msg_id': msgId,
      ...encryptedPayload,
    };

    final push = _chatChannel!.push('send_message', payload);
    final response = await push.future;
    
    if (response.isError) {
      throw Exception('Message sending failed: ${response.response}');
    }

    return Map<String, dynamic>.from(response.response); // Should contain server_ts and seq
  }

  Future<Map<String, dynamic>> sendGroupMessage(String groupId, String msgId, Map<String, dynamic> encryptedPayload) async {
    final channel = _groupChannels[groupId];
    if (channel == null) throw Exception("Not joined to group channel");

    final payload = {
      'msg_id': msgId,
      ...encryptedPayload,
    };

    final push = channel.push('send_group_message', payload);
    final response = await push.future;
    
    if (response.isError) {
      throw Exception('Group message sending failed: ${response.response}');
    }

    return Map<String, dynamic>.from(response.response);
  }

  Future<void> sendDeliveryReceipt(String senderId, String msgId) async {
    if (_chatChannel == null) return;
    _chatChannel!.push('delivery_receipt', {
      'msg_id': msgId,
      'sender_user_id': senderId,
    });
  }

  Future<void> sendReadReceipt(String senderId, String msgId) async {
    if (_chatChannel == null) return;
    _chatChannel!.push('read_receipt', {
      'msg_id': msgId,
      'sender_user_id': senderId,
    });
  }

  Future<void> sendReadCursor(String senderId, int lastReadSeq) async {
    if (_chatChannel == null) return;
    _chatChannel!.push('read_cursor', {
      'sender_id': senderId,
      'last_read_seq': lastReadSeq,
    });
  }

  Future<void> sendTyping(String recipientId) async {
    if (_chatChannel == null) return;
    _chatChannel!.push('typing', {'recipient_id': recipientId});
  }

  Future<void> sendStopTyping(String recipientId) async {
    if (_chatChannel == null) return;
    _chatChannel!.push('stop_typing', {'recipient_id': recipientId});
  }

  Future<void> sendSyncAck() async {
    if (_chatChannel == null) return;
    _chatChannel!.push('sync_ack', {});
  }

  void disconnect() {
    _chatChannel?.leave();
    for (var channel in _groupChannels.values) {
      channel.leave();
    }
    _groupChannels.clear();
    _socket?.dispose();
    _socket = null;
    
    // We don't close the streams here so the singleton services can stay subscribed across reconnects
  }
}
