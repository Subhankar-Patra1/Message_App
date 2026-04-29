import 'dart:async';
import '../network/socket_client.dart';
import '../database_service.dart';
import 'pending_message_queue.dart';
import '../models/message_state.dart';

class ReceiptService {
  final SocketClient socketClient;
  final PendingMessageQueue pendingQueue;
  
  StreamSubscription? _receiptSub;

  ReceiptService({
    required this.socketClient,
    required this.pendingQueue,
  }) {
    _receiptSub = socketClient.receiptEvents.listen(_handleReceipt);
  }

  final _receiptUpdatesController = StreamController<Map<String, dynamic>>.broadcast();
  Stream<Map<String, dynamic>> get receiptUpdates => _receiptUpdatesController.stream;

  Future<void> _handleReceipt(Map<String, dynamic> payload) async {
    final isCursor = payload['isCursor'] == true;

    if (isCursor) {
      // Bulk update up to seq
      final readerId = payload['reader_id'] as String;
      final seq = payload['last_read_seq'] as int;
      await DatabaseService.bulkUpdateMessagesRead(readerId, seq);
      
      _receiptUpdatesController.add({
        'isCursor': true,
        'reader_id': readerId,
        'seq': seq,
        'status': MessageState.read,
      });
    } else {
      // Single message receipt
      final msgId = payload['msg_id'] as String;
      final status = payload['status'] as String; // 'delivered' or 'read'
      final senderId = payload['by'] as String;
      
      await DatabaseService.updateMessageStatus(senderId, msgId, status);
      
      _receiptUpdatesController.add({
        'msg_id': msgId,
        'status': status,
      });
    }
  }

  Future<void> sendReadCursor(String senderId, int lastReadSeq) async {
    await socketClient.sendReadCursor(senderId, lastReadSeq);
  }

  Future<void> sendReadReceipt(String senderId, String msgId) async {
    await socketClient.sendReadReceipt(senderId, msgId);
  }

  void dispose() {
    _receiptSub?.cancel();
    _receiptUpdatesController.close();
  }
}
