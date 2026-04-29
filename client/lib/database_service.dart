import 'package:hive_flutter/hive_flutter.dart';
import 'package:intl/intl.dart';
import 'models/message_state.dart';

class DatabaseService {
  static const String _conversationsBoxName = 'conversations';
  static const String _messagesBoxPrefix = 'messages_';
  static const String _settingsBoxName = 'settings';
  static const String _contactsBoxName = 'contacts';
  static const String _pendingMessagesBoxName = 'pending_messages';

  static Future<void> initialize() async {
    await Hive.openBox(_conversationsBoxName);
    await Hive.openBox(_settingsBoxName);
    await Hive.openBox(_contactsBoxName);
    await Hive.openBox(_pendingMessagesBoxName);
  }

  // --- Profile Caching ---
  
  static Future<void> saveMyProfile(Map<String, dynamic> data) async {
    final box = Hive.box(_settingsBoxName);
    await box.put('my_profile', data);
  }

  static Map<String, dynamic>? getMyProfile() {
    final box = Hive.box(_settingsBoxName);
    final data = box.get('my_profile');
    if (data == null) return null;
    return Map<String, dynamic>.from(data as Map);
  }

  // --- Contact Caching ---
  
  static Future<void> saveContactProfile(String userId, String name, String? avatarUrl) async {
    final box = Hive.box(_contactsBoxName);
    await box.put(userId, {
      'name': name,
      'avatarUrl': avatarUrl,
      'lastUpdated': DateTime.now().toIso8601String(),
    });
  }

  static Map<String, dynamic>? getContactProfile(String userId) {
    final box = Hive.box(_contactsBoxName);
    final data = box.get(userId);
    if (data == null) return null;
    return Map<String, dynamic>.from(data as Map);
  }

  // --- Conversations ---

  static Box get _conversationsBox => Hive.box(_conversationsBoxName);

  static List<Map<String, dynamic>> getAllConversations() {
    final raw = _conversationsBox.values.toList();
    // Safely cast each item
    final formatted = raw.map((item) => Map<String, dynamic>.from(item as Map)).toList();
    
    // Sort by timestamp descending
    formatted.sort((a, b) {
      final aTime = DateTime.tryParse(a['lastMessageTime'] ?? '') ?? DateTime(0);
      final bTime = DateTime.tryParse(b['lastMessageTime'] ?? '') ?? DateTime(0);
      return bTime.compareTo(aTime);
    });
    return formatted;
  }

  static Future<void> updateConversation({
    required String userId,
    required String name,
    String? avatarUrl,
    required String lastMessage,
    required DateTime lastMessageTime,
    bool incrementUnread = false,
  }) async {
    final existing = _conversationsBox.get(userId);
    int unreadCount = (existing?['unreadCount'] ?? 0);
    if (incrementUnread) unreadCount++;

    final conversation = {
      'userId': userId,
      'name': name,
      'avatarUrl': avatarUrl,
      'lastMessage': lastMessage,
      'lastMessageTime': lastMessageTime.toIso8601String(),
      'unreadCount': unreadCount,
    };

    await _conversationsBox.put(userId, conversation);
  }

  static Future<void> markAsRead(String userId) async {
    final existing = _conversationsBox.get(userId);
    if (existing != null) {
      existing['unreadCount'] = 0;
      await _conversationsBox.put(userId, existing);
    }
  }

  // --- Pending Messages ---
  static Box get _pendingBox => Hive.box(_pendingMessagesBoxName);

  static Future<void> savePendingMessage(Map<String, dynamic> msg) async {
    await _pendingBox.put(msg['msg_id'], msg);
  }

  static Future<void> removePendingMessage(String msgId) async {
    await _pendingBox.delete(msgId);
  }

  static List<Map<String, dynamic>> getPendingMessages() {
    final raw = _pendingBox.values.toList();
    return raw.map((item) => Map<String, dynamic>.from(item as Map)).toList();
  }

  // --- Messages ---

  static Future<Box> _getMessagesBox(String userId) async {
    final boxName = '$_messagesBoxPrefix$userId';
    if (!Hive.isBoxOpen(boxName)) {
      return await Hive.openBox(boxName);
    }
    return Hive.box(boxName);
  }

  static Future<bool> messageExists(String userId, String msgId) async {
    final box = await _getMessagesBox(userId);
    return box.values.any((item) => (item as Map)['msg_id'] == msgId);
  }

  static Future<void> saveMessage({
    required String recipientId,
    required String senderId,
    required String text,
    required DateTime timestamp,
    String? msgId,
    String? status,
    bool isMe = false,
    int? seq,
  }) async {
    final box = await _getMessagesBox(isMe ? recipientId : senderId);
    
    dynamic existingKey;
    Map<String, dynamic>? existingMsg;

    if (msgId != null) {
      final messages = box.toMap();
      for (var entry in messages.entries) {
        if (entry.value['msg_id'] == msgId) {
          existingKey = entry.key;
          existingMsg = Map<String, dynamic>.from(entry.value);
          break;
        }
      }
    }

    if (existingKey != null && existingMsg != null) {
      // Update existing
      existingMsg['seq'] = seq ?? existingMsg['seq'];
      existingMsg['timestamp'] = (seq != null) ? timestamp.toIso8601String() : existingMsg['timestamp'];
      
      final currentStatus = existingMsg['status'] as String?;
      final newStatus = status ?? MessageState.sent;
      
      if (currentStatus == null || 
          MessageState.isValidTransition(currentStatus, newStatus) ||
          MessageState.getWeight(newStatus) > MessageState.getWeight(currentStatus)) {
        existingMsg['status'] = newStatus;
      }
      
      await box.put(existingKey, existingMsg);
    } else {
      // Insert new
      final message = {
        'msg_id': msgId,
        'status': status ?? MessageState.sent,
        'senderId': senderId,
        'text': text,
        'timestamp': timestamp.toIso8601String(),
        'isMe': isMe,
        'seq': seq,
      };
      await box.add(message);
    }
  }

  static Future<List<Map<String, dynamic>>> getMessages(String userId) async {
    final box = await _getMessagesBox(userId);
    final msgs = box.values.map((item) => Map<String, dynamic>.from(item as Map)).toList();
    
    // Sort by seq, then timestamp
    msgs.sort((a, b) {
      int seqA = a['seq'] ?? 0;
      int seqB = b['seq'] ?? 0;
      if (seqA != seqB) {
        return seqA.compareTo(seqB);
      }
      final timeA = DateTime.tryParse(a['timestamp'] ?? '') ?? DateTime(0);
      final timeB = DateTime.tryParse(b['timestamp'] ?? '') ?? DateTime(0);
      return timeA.compareTo(timeB);
    });

    return msgs;
  }

  // --- Helpers ---

  static Future<void> updateMessageStatus(String userId, String msgId, String status) async {
    final box = await _getMessagesBox(userId);
    final messages = box.toMap();
    
    dynamic targetKey;
    Map<String, dynamic>? targetValue;
    
    messages.forEach((key, value) {
      if (value['msg_id'] == msgId) {
        targetKey = key;
        targetValue = Map<String, dynamic>.from(value);
      }
    });

    if (targetKey != null && targetValue != null) {
      final currentStatus = targetValue!['status'] as String?;
      
      // State machine validation
      if (currentStatus == null || MessageState.isValidTransition(currentStatus, status) || 
         (currentStatus == MessageState.sent && status == MessageState.delivered) || 
         (currentStatus == MessageState.sent && status == MessageState.read) ||
         (currentStatus == MessageState.acked && status == MessageState.read) ||
         MessageState.getWeight(status) > MessageState.getWeight(currentStatus)) {
        
        targetValue!['status'] = status;
        await box.put(targetKey, targetValue);
      }
    }
  }

  static Future<void> bulkUpdateMessagesRead(String userId, int upToSeq) async {
    final box = await _getMessagesBox(userId);
    final messages = box.toMap();
    
    for (var entry in messages.entries) {
      final msg = Map<String, dynamic>.from(entry.value);
      final seq = msg['seq'] as int?;
      final isMe = msg['isMe'] as bool? ?? false;
      
      if (isMe && seq != null && seq <= upToSeq) {
        final currentStatus = msg['status'] as String?;
        if (currentStatus != MessageState.read) {
          msg['status'] = MessageState.read;
          await box.put(entry.key, msg);
        }
      }
    }
  }

  static String formatTime(String? isoString) {
    if (isoString == null) return '';
    final date = DateTime.tryParse(isoString);
    if (date == null) return '';

    final now = DateTime.now();
    final diff = now.difference(date);

    if (diff.inDays == 0) {
      return DateFormat.jm().format(date); // 10:45 AM
    } else if (diff.inDays == 1) {
      return 'Yesterday';
    } else if (diff.inDays < 7) {
      return DateFormat('E').format(date); // Mon, Tue
    } else {
      return DateFormat('dd/MM').format(date); // 22/04
    }
  }
}
