import 'dart:async';
import 'dart:io';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:uuid/uuid.dart';
import 'package:emoji_picker_flutter/emoji_picker_flutter.dart';
import '../services/service_locator.dart';
import '../services/chat_repository.dart';
import '../database_service.dart';
import '../models/message_state.dart';

class ChatScreen extends StatefulWidget {
  final String recipientId;
  final String recipientName;
  final String? recipientAvatar;

  const ChatScreen({
    super.key,
    required this.recipientId,
    required this.recipientName,
    this.recipientAvatar,
  });

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> with TickerProviderStateMixin {
  // --- Services ---
  ChatRepository? _repo;

  // --- State ---
  final List<Map<String, dynamic>> _messages = [];
  final _inputController = TextEditingController();
  final _focusNode = FocusNode();
  late AnimationController _sendAnimController;

  // Presence
  bool _isTyping = false;
  bool _isOnline = false;
  String? _lastSeenAt;

  // Emoji picker state
  bool _showEmojiPicker = false;
  bool _isTransitioningToEmoji = false;
  bool _isTransitioningToKeyboard = false;
  double _lastKnownKeyboardHeight = 310.0;

  // Stream subscriptions
  StreamSubscription? _receiptSub;
  StreamSubscription? _syncSub;
  StreamSubscription? _typingSub;
  StreamSubscription? _presenceSub;

  // Display name (can be resolved from UUID)
  late String _displayName;

  // Colors
  static const _bgColor = Color(0xFF111318);
  static const _receivedBubbleColor = Color(0xFF212121);
  static const _sentBubbleColor = Color(0xFF2c6bed);

  @override
  void initState() {
    super.initState();
    _displayName = widget.recipientName;
    _sendAnimController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 150),
    );

    _focusNode.addListener(_onFocusChange);
    _inputController.addListener(_onTextChanged);
    _initChat();
  }

  @override
  void dispose() {
    _receiptSub?.cancel();
    _syncSub?.cancel();
    _typingSub?.cancel();
    _presenceSub?.cancel();
    _focusNode.removeListener(_onFocusChange);
    _focusNode.dispose();
    _inputController.dispose();
    _sendAnimController.dispose();
    super.dispose();
  }

  // --- Initialization ---

  Future<void> _initChat() async {
    // 1. Load local history immediately
    final history = await DatabaseService.getMessages(widget.recipientId);
    final sortedHistory = history.reversed.map((m) => {
      ...m,
      'plaintext': m['text'],
      'server_ts': DateTime.parse(m['timestamp']).millisecondsSinceEpoch,
      'isMe': m['isMe'] == true,
    }).toList();

    if (mounted) setState(() => _messages
      ..clear()
      ..addAll(sortedHistory));

    // 2. Wire up services
    _repo = await ServiceLocator.getRepoAsync();
    if (_repo == null) return;

    await DatabaseService.markAsRead(widget.recipientId);

    // Resolve name if it looks like a UUID
    if (_looksLikeUuid(_displayName)) {
      final resolved = await _repo!.profileResolver.resolve(widget.recipientId);
      if (resolved != null && mounted) {
        setState(() => _displayName = resolved['name'] ?? _displayName);
      }
    }

    // 3. Subscribe to live receipt updates
    _receiptSub = _repo!.receiptService.receiptUpdates.listen((receipt) {
      if (!mounted) return;
      setState(() {
        if (receipt['isCursor'] == true) {
          final seq = receipt['seq'] as int;
          for (var m in _messages) {
            if (m['isMe'] == true && (m['seq'] ?? 0) <= seq) {
              if (m['status'] != MessageState.read) m['status'] = MessageState.read;
            }
          }
        } else {
          final idx = _messages.indexWhere((m) => m['msg_id'] == receipt['msg_id']);
          if (idx != -1) _messages[idx]['status'] = receipt['status'];
        }
      });
    });

    // 4. Subscribe to incoming messages
    _syncSub = _repo!.syncService.decryptedMessages.listen((msg) {
      if (!mounted) return;
      if (msg['senderId'] == widget.recipientId) {
        setState(() => _messages.insert(0, msg));
        // Auto-send read receipt since this chat is open
        _repo!.receiptService.sendReadReceipt(msg['senderId'], msg['msg_id']);
        final seq = msg['seq'] as int? ?? 0;
        _repo!.receiptService.sendReadCursor(msg['senderId'], seq);
      }
    });

    // 5. Subscribe to typing events
    _typingSub = _repo!.typingService.typingState.listen((event) {
      if (event['userId'] == widget.recipientId && mounted) {
        setState(() => _isTyping = event['isTyping']);
      }
    });

    // 6. Subscribe to presence (on-demand, only for this chat)
    _presenceSub = _repo!.presenceService.presenceState.listen((event) {
      if (event['userId'] == widget.recipientId && mounted) {
        setState(() {
          _isOnline = event['online'];
          _lastSeenAt = event['lastSeenAt'];
        });
      }
    });

    // Initial presence fetch
    final presence = await _repo!.presenceService.getPresence(widget.recipientId);
    if (mounted) {
      setState(() {
        _isOnline = presence['online'];
        _lastSeenAt = presence['lastSeenAt'];
      });
    }
  }

  // --- Listeners ---

  void _onFocusChange() {
    if (_focusNode.hasFocus && _showEmojiPicker && !_isTransitioningToKeyboard) {
      setState(() {
        _showEmojiPicker = false;
        _isTransitioningToKeyboard = true;
      });
      Future.delayed(const Duration(milliseconds: 400), () {
        if (mounted) setState(() => _isTransitioningToKeyboard = false);
      });
    }
  }

  void _onTextChanged() {
    if (_inputController.text.isNotEmpty) {
      _repo?.typingService.sendTyping(widget.recipientId);
    }
  }

  // --- Actions ---

  void _onEmojiButtonTap() {
    if (_showEmojiPicker) {
      setState(() {
        _showEmojiPicker = false;
        _isTransitioningToKeyboard = true;
      });
      _focusNode.requestFocus();
      Future.delayed(const Duration(milliseconds: 400), () {
        if (mounted) setState(() => _isTransitioningToKeyboard = false);
      });
    } else {
      final keyboardOpen = MediaQuery.of(context).viewInsets.bottom > 50;
      _focusNode.unfocus();
      setState(() {
        _showEmojiPicker = true;
        _isTransitioningToEmoji = keyboardOpen;
      });
      if (keyboardOpen) {
        Future.delayed(const Duration(milliseconds: 300), () {
          if (mounted) setState(() => _isTransitioningToEmoji = false);
        });
      }
    }
  }

  void _sendMessage() async {
    final text = _inputController.text.trim();
    if (text.isEmpty || _repo == null) return;

    _inputController.clear();
    _sendAnimController.reset();
    setState(() {});

    final msgId = const Uuid().v4();
    final serverTs = DateTime.now().millisecondsSinceEpoch;

    setState(() {
      _messages.insert(0, {
        'msg_id': msgId,
        'senderId': 'Me',
        'isMe': true,
        'plaintext': text,
        'server_ts': serverTs,
        'status': MessageState.sent,
      });
    });

    try {
      await _repo!.enqueueMessage(widget.recipientId, text, msgId: msgId);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to send: $e')),
      );
    }
  }

  bool _looksLikeUuid(String input) {
    return RegExp(r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
        caseSensitive: false).hasMatch(input);
  }

  // --- Build ---

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: !_showEmojiPicker,
      onPopInvokedWithResult: (didPop, dynamic result) {
        if (didPop) return;
        if (_showEmojiPicker) setState(() => _showEmojiPicker = false);
      },
      child: Scaffold(
        resizeToAvoidBottomInset: true,
        backgroundColor: _bgColor,
        appBar: _buildAppBar(),
        body: _buildBody(),
      ),
    );
  }

  PreferredSizeWidget _buildAppBar() {
    return PreferredSize(
      preferredSize: const Size.fromHeight(65),
      child: AppBar(
        backgroundColor: _bgColor,
        elevation: 0,
        leadingWidth: 40,
        leading: IconButton(
          icon: const Icon(CupertinoIcons.back, color: Colors.white),
          onPressed: () => Navigator.pop(context),
        ),
        title: Row(
          children: [
            // Avatar
            _buildHeaderAvatar(),
            const SizedBox(width: 12),
            // Name + Status
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    _displayName.isNotEmpty ? _displayName : 'Contact',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                        color: Colors.white,
                        fontSize: 16,
                        fontWeight: FontWeight.w600),
                  ),
                  if (_isTyping || _isOnline || _lastSeenAt != null)
                    Text(
                      _isTyping
                          ? 'typing...'
                          : (_isOnline
                              ? 'Online'
                              : 'last seen ${DatabaseService.formatTime(_lastSeenAt)}'),
                      style: TextStyle(
                          color: _isTyping ? Colors.blueAccent : Colors.white70,
                          fontSize: 13,
                          fontStyle: _isTyping ? FontStyle.italic : FontStyle.normal),
                    ),
                ],
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(CupertinoIcons.video_camera, color: Colors.white),
            onPressed: () {},
          ),
          IconButton(
            icon: const Icon(CupertinoIcons.phone, color: Colors.white),
            onPressed: () {},
          ),
          IconButton(
            icon: const Icon(CupertinoIcons.ellipsis_vertical, color: Colors.white),
            onPressed: () {},
          ),
        ],
      ),
    );
  }

  Widget _buildHeaderAvatar() {
    if (widget.recipientAvatar != null && widget.recipientAvatar!.isNotEmpty) {
      return CircleAvatar(
        radius: 20,
        backgroundImage: NetworkImage(widget.recipientAvatar!),
        backgroundColor: _receivedBubbleColor,
      );
    }
    return CircleAvatar(
      radius: 20,
      backgroundColor: _receivedBubbleColor,
      child: Text(
        _displayName.isNotEmpty ? _displayName[0].toUpperCase() : '?',
        style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
      ),
    );
  }

  // --- Chat Body ---

  Widget _buildBody() {
    final bottomInset = MediaQuery.of(context).viewInsets.bottom;

    if (bottomInset > 100 && !_showEmojiPicker && !_isTransitioningToKeyboard) {
      _lastKnownKeyboardHeight = bottomInset;
    }

    double bottomPanelHeight = 0.0;
    if (_showEmojiPicker) {
      if (_isTransitioningToEmoji) {
        bottomPanelHeight = math.max(0.0, _lastKnownKeyboardHeight - bottomInset);
      } else {
        bottomPanelHeight = math.max(100.0, _lastKnownKeyboardHeight - bottomInset);
      }
    } else if (_isTransitioningToKeyboard && bottomInset < _lastKnownKeyboardHeight) {
      bottomPanelHeight = math.max(0.0, _lastKnownKeyboardHeight - bottomInset);
    }

    return Column(
      children: [
        // Date chip
        Padding(
          padding: const EdgeInsets.symmetric(vertical: 12),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
            decoration: BoxDecoration(
              color: _receivedBubbleColor.withValues(alpha: 0.8),
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Text(
              'Today',
              style: TextStyle(color: Colors.white70, fontSize: 12, fontWeight: FontWeight.w500),
            ),
          ),
        ),

        // Message list
        Expanded(
          child: GestureDetector(
            onTap: () => _focusNode.unfocus(),
            child: ListView.builder(
              reverse: true,
              padding: const EdgeInsets.only(left: 16, right: 16, top: 8, bottom: 4),
              itemCount: _messages.length,
              itemBuilder: (context, index) {
                final msg = _messages[index];
                final isMe = msg['isMe'] == true || msg['senderId'] == 'Me';
                return Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 12.0),
                  child: _buildMessageItem(index, msg, isMe),
                );
              },
            ),
          ),
        ),

        // Input bar
        _buildInputBar(),

        // Emoji picker panel
        AnimatedContainer(
          duration: Duration(milliseconds: bottomInset > 0 ? 0 : 250),
          curve: Curves.easeOutCubic,
          height: bottomPanelHeight,
          clipBehavior: Clip.hardEdge,
          decoration: const BoxDecoration(color: _bgColor),
          child: SizedBox(
            height: _lastKnownKeyboardHeight,
            child: _buildEmojiPicker(),
          ),
        ),
      ],
    );
  }

  // --- Input Bar ---

  Widget _buildInputBar() {
    return Container(
      color: _bgColor,
      padding: const EdgeInsets.only(left: 8, right: 8, bottom: 8, top: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Expanded(
            child: Container(
              decoration: BoxDecoration(
                color: _receivedBubbleColor,
                borderRadius: BorderRadius.circular(24),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  IconButton(
                    icon: Icon(
                      _showEmojiPicker ? CupertinoIcons.keyboard : CupertinoIcons.smiley,
                      color: Colors.white54,
                    ),
                    onPressed: _onEmojiButtonTap,
                  ),
                  Expanded(
                    child: TextField(
                      focusNode: _focusNode,
                      controller: _inputController,
                      style: const TextStyle(
                        color: Colors.white,
                        fontFamilyFallback: ['AppleEmoji'],
                      ),
                      maxLines: 5,
                      minLines: 1,
                      decoration: const InputDecoration(
                        hintText: 'Message',
                        hintStyle: TextStyle(color: Colors.white54, fontSize: 16),
                        border: InputBorder.none,
                        contentPadding: EdgeInsets.symmetric(vertical: 12),
                      ),
                      onChanged: (_) => setState(() {}),
                      onTap: () {
                        if (_showEmojiPicker && !_isTransitioningToKeyboard) {
                          setState(() {
                            _showEmojiPicker = false;
                            _isTransitioningToKeyboard = true;
                          });
                          Future.delayed(const Duration(milliseconds: 400), () {
                            if (mounted) setState(() => _isTransitioningToKeyboard = false);
                          });
                        }
                      },
                    ),
                  ),
                  IconButton(
                    icon: const Icon(CupertinoIcons.paperclip, color: Colors.white54),
                    onPressed: () {},
                  ),
                  IconButton(
                    icon: const Icon(CupertinoIcons.camera, color: Colors.white54),
                    onPressed: () {},
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(width: 8),
          GestureDetector(
            onTap: _inputController.text.isNotEmpty ? _sendMessage : null,
            child: CircleAvatar(
              radius: 24,
              backgroundColor: _sentBubbleColor,
              child: AnimatedSwitcher(
                duration: const Duration(milliseconds: 150),
                transitionBuilder: (child, animation) => ScaleTransition(
                  scale: animation,
                  child: FadeTransition(opacity: animation, child: child),
                ),
                child: _inputController.text.isNotEmpty
                    ? SizedBox(
                        key: const ValueKey('send'),
                        width: 48,
                        height: 48,
                        child: Center(
                          child: Transform.translate(
                            offset: const Offset(-0.5, 0.5),
                            child: const Icon(CupertinoIcons.paperplane_fill,
                                color: Colors.white, size: 26),
                          ),
                        ),
                      )
                    : const SizedBox(
                        key: ValueKey('mic'),
                        width: 48,
                        height: 48,
                        child: Center(child: Icon(Icons.mic, color: Colors.white, size: 24)),
                      ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  // --- Emoji Picker ---

  Widget _buildEmojiPicker() {
    return EmojiPicker(
      textEditingController: _inputController,
      config: Config(
        height: _lastKnownKeyboardHeight,
        checkPlatformCompatibility: false,
        emojiTextStyle: const TextStyle(fontFamily: 'AppleEmoji'),
        viewOrderConfig: const ViewOrderConfig(
          top: EmojiPickerItem.searchBar,
          middle: EmojiPickerItem.emojiView,
          bottom: EmojiPickerItem.categoryBar,
        ),
        emojiViewConfig: EmojiViewConfig(
          columns: 8,
          buttonMode: ButtonMode.CUPERTINO,
          emojiSizeMax: 42 * (kIsWeb ? 1.0 : (Platform.isIOS ? 1.30 : 1.0)),
          backgroundColor: _bgColor,
        ),
        categoryViewConfig: const CategoryViewConfig(
          backgroundColor: _bgColor,
          indicatorColor: _sentBubbleColor,
          iconColorSelected: _sentBubbleColor,
          iconColor: Colors.grey,
        ),
        bottomActionBarConfig: const BottomActionBarConfig(
          backgroundColor: _bgColor,
          buttonColor: _bgColor,
          buttonIconColor: Colors.grey,
        ),
        searchViewConfig: SearchViewConfig(
          backgroundColor: _bgColor,
          buttonIconColor: Colors.grey,
          customSearchView: (config, state, showEmojiView) {
            return _CustomSearchView(config, state, showEmojiView);
          },
        ),
      ),
    );
  }

  // --- Message Bubble ---

  Widget _buildMessageItem(int index, Map<String, dynamic> msg, bool isMe) {
    final isLastInSequence = index == 0 ||
        (index < _messages.length - 1 && _messages[index - 1]['senderId'] != msg['senderId']);
    final isFirstInSequence = index == _messages.length - 1 ||
        _messages[index + 1]['senderId'] != msg['senderId'];

    final borderRadius = BorderRadius.only(
      topLeft: Radius.circular(isMe ? 16 : (isFirstInSequence ? 16 : 4)),
      topRight: Radius.circular(isMe ? (isFirstInSequence ? 16 : 4) : 16),
      bottomLeft: Radius.circular(isMe ? 16 : (isLastInSequence ? 0 : 4)),
      bottomRight: Radius.circular(isMe ? (isLastInSequence ? 0 : 4) : 16),
    );

    return Padding(
      padding: EdgeInsets.only(bottom: isLastInSequence ? 4.0 : 2.0),
      child: Align(
        alignment: isMe ? Alignment.centerRight : Alignment.centerLeft,
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            if (isLastInSequence)
              Positioned(
                bottom: 0,
                right: isMe ? -8 : null,
                left: isMe ? null : -8,
                child: CustomPaint(
                  size: const Size(8, 16),
                  painter: _BubbleTailPainter(
                    color: isMe ? _sentBubbleColor : _receivedBubbleColor,
                    isMe: isMe,
                  ),
                ),
              ),
            Container(
              constraints: BoxConstraints(
                maxWidth: MediaQuery.of(context).size.width * 0.75,
              ),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
              decoration: BoxDecoration(
                color: isMe ? _sentBubbleColor : _receivedBubbleColor,
                borderRadius: borderRadius,
              ),
              child: _buildTextBubble(msg, isMe),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTextBubble(Map<String, dynamic> msg, bool isMe) {
    final status = msg['status'] ?? MessageState.sent;

    return Wrap(
      alignment: WrapAlignment.end,
      crossAxisAlignment: WrapCrossAlignment.end,
      children: [
        Padding(
          padding: const EdgeInsets.only(right: 8.0, bottom: 2.0),
          child: Text(
            msg['plaintext'] ?? msg['text'] ?? '',
            style: const TextStyle(
              color: Colors.white,
              fontSize: 15,
              fontFamilyFallback: ['AppleEmoji'],
            ),
          ),
        ),
        Transform.translate(
          offset: const Offset(4, 2),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                _formatTimestamp(msg['server_ts']),
                style: TextStyle(
                  color: Colors.white.withValues(alpha: 0.6),
                  fontSize: 10,
                ),
              ),
              if (isMe) ...[
                const SizedBox(width: 4),
                _buildReceiptIcon(status),
              ],
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildReceiptIcon(String status) {
    IconData icon;
    Color color;

    switch (status) {
      case MessageState.read:
        icon = Icons.done_all;
        color = const Color(0xFF4FC3F7); // Blue checkmarks
        break;
      case MessageState.delivered:
        icon = Icons.done_all;
        color = Colors.white.withValues(alpha: 0.5); // Gray double checkmarks
        break;
      case MessageState.failed:
        icon = Icons.error_outline;
        color = Colors.redAccent;
        break;
      default:
        // sent, acked, pending, encrypting, retrying
        icon = Icons.done;
        color = Colors.white.withValues(alpha: 0.5); // Gray single checkmark
    }

    return Icon(icon, size: 14, color: color);
  }

  String _formatTimestamp(int? serverTs) {
    if (serverTs == null) return '';
    final time = DateTime.fromMillisecondsSinceEpoch(serverTs);
    int hour = time.hour > 12 ? time.hour - 12 : time.hour;
    if (hour == 0) hour = 12;
    final amPm = time.hour >= 12 ? 'PM' : 'AM';
    return '$hour:${time.minute.toString().padLeft(2, '0')} $amPm';
  }
}

// --- Bubble Tail Painter ---

class _BubbleTailPainter extends CustomPainter {
  final Color color;
  final bool isMe;

  _BubbleTailPainter({required this.color, required this.isMe});

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.fill
      ..isAntiAlias = true;

    final path = Path();

    if (isMe) {
      path.moveTo(0, size.height);
      path.lineTo(size.width, size.height);
      path.cubicTo(
        size.width, size.height * 0.8,
        size.width * 0.1, size.height * 0.9,
        0, 0,
      );
      path.lineTo(-1, 0);
      path.lineTo(-1, size.height);
    } else {
      path.moveTo(size.width, size.height);
      path.lineTo(0, size.height);
      path.cubicTo(
        0, size.height * 0.8,
        size.width * 0.9, size.height * 0.9,
        size.width, 0,
      );
      path.lineTo(size.width + 1, 0);
      path.lineTo(size.width + 1, size.height);
    }

    path.close();
    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

// --- Custom Emoji Search View ---

class _CustomSearchView extends SearchView {
  const _CustomSearchView(super.config, super.state, super.showEmojiView);

  @override
  _CustomSearchViewState createState() => _CustomSearchViewState();
}

class _CustomSearchViewState extends SearchViewState<_CustomSearchView> {
  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(builder: (context, constraints) {
      final emojiSize = widget.config.emojiViewConfig.getEmojiSize(constraints.maxWidth);
      final emojiBoxSize = widget.config.emojiViewConfig.getEmojiBoxSize(constraints.maxWidth);

      return Container(
        color: widget.config.searchViewConfig.backgroundColor,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                IconButton(
                  onPressed: () => widget.showEmojiView(),
                  color: widget.config.searchViewConfig.buttonIconColor,
                  icon: const Icon(Icons.arrow_back),
                ),
                Expanded(
                  child: TextField(
                    onChanged: onTextInputChanged,
                    focusNode: focusNode,
                    style: widget.config.searchViewConfig.inputTextStyle,
                    decoration: InputDecoration(
                      border: InputBorder.none,
                      hintText: widget.config.searchViewConfig.hintText,
                      hintStyle: widget.config.searchViewConfig.hintTextStyle,
                      contentPadding: const EdgeInsets.symmetric(horizontal: 16),
                    ),
                  ),
                ),
              ],
            ),
            Material(
              color: Colors.transparent,
              child: SizedBox(
                height: emojiBoxSize + 8.0,
                child: ListView.builder(
                  padding: const EdgeInsets.symmetric(vertical: 4.0),
                  scrollDirection: Axis.horizontal,
                  itemCount: results.length,
                  itemBuilder: (context, index) {
                    return buildEmoji(results[index], emojiSize, emojiBoxSize);
                  },
                ),
              ),
            ),
          ],
        ),
      );
    });
  }
}