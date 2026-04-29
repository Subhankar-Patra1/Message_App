import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../network/api_client.dart';
import '../config.dart';
import '../database_service.dart';
import 'chat_screen.dart';

class FindByIdentifierScreen extends StatefulWidget {
  const FindByIdentifierScreen({super.key});

  @override
  State<FindByIdentifierScreen> createState() => _FindByIdentifierScreenState();
}

class _FindByIdentifierScreenState extends State<FindByIdentifierScreen>
    with SingleTickerProviderStateMixin {
  final ApiClient _apiClient = ApiClient(baseUrl: AppConfig.apiBaseUrl);
  final TextEditingController _identifierController = TextEditingController();
  late TabController _tabController;
  bool _isLoading = false;
  bool _isButtonActive = false;
  Map<String, dynamic>? _foundUser;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _tabController.addListener(() {
      // Clear input and CLOSE keyboard when switching tabs
      _identifierController.clear();
      FocusScope.of(context).unfocus();
      setState(() => _isButtonActive = false);
    });
    _identifierController.addListener(_validateInput);
  }

  @override
  void dispose() {
    _identifierController.dispose();
    _tabController.dispose();
    super.dispose();
  }

  void _validateInput() {
    final text = _identifierController.text.trim();
    bool valid;

    if (_tabController.index == 0) {
      // Phone: exactly 10 digits
      final digitsOnly = text.replaceAll(RegExp(r'[^0-9]'), '');
      valid = digitsOnly.length == 10;
    } else {
      // Email: basic @ check
      valid = text.contains('@') && text.contains('.') && text.length >= 5;
    }

    setState(() => _isButtonActive = valid);
  }

  void _search() async {
    var identifier = _identifierController.text.trim();
    if (identifier.isEmpty) return;

    // Prepend +91 for phone tab
    if (_tabController.index == 0) {
      identifier = '+91$identifier';
    }

    setState(() => _isLoading = true);

    try {
      final bundle = await _apiClient.fetchPreKeysByIdentifier(identifier);
      if (mounted) {
        setState(() {
          _isLoading = false;
          _foundUser = bundle;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isLoading = false);
        final errorStr = e.toString();

        String title;
        String message;

        if (errorStr.contains('cannot_message_self')) {
          title = "That's you!";
          message = "You can't start a chat with yourself.";
        } else {
          final isPhone = _tabController.index == 0;
          title = isPhone ? 'Phone number not found' : 'Email not found';
          message = isPhone
              ? 'No Aura user is registered with this phone number. Please check and try again.'
              : 'No Aura user is registered with this email. Please check and try again.';
        }

        _showNotFoundDialog(title, message);
      }
    }
  }

  void _showNotFoundDialog(String title, String message) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF2D2F33),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: Text(title,
            style: const TextStyle(color: Colors.white, fontSize: 20)),
        content: Text(
          message,
          style: TextStyle(color: Colors.white.withOpacity(0.8), fontSize: 16),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('OK',
                style: TextStyle(color: Color(0xFFC3C0FF))),
          ),
        ],
      ),
    );
  }

  String _getDisplayName(Map<String, dynamic> user) {
    if (user['first_name'] != null && user['first_name'].isNotEmpty) {
      return "${user['first_name']} ${user['last_name'] ?? ""}".trim();
    }
    if (user['display_name'] != null && user['display_name'].isNotEmpty) {
      return user['display_name'];
    }
    if (user['username'] != null) return "@${user['username']}";
    return 'Unknown';
  }

  Widget _buildSearchResultAvatar(Map<String, dynamic> user) {
    final avatarUrl = user['avatar_url'] as String?;
    final name = _getDisplayName(user);

    if (avatarUrl != null && avatarUrl.isNotEmpty) {
      return CircleAvatar(
        radius: 25,
        backgroundImage: NetworkImage(avatarUrl),
        backgroundColor: const Color(0xFF22242A),
        onBackgroundImageError: (_, __) {},
      );
    }

    return CircleAvatar(
      radius: 25,
      backgroundColor: const Color(0xFF22242A),
      child: Text(
        name.isNotEmpty ? name[0].toUpperCase() : '?',
        style: const TextStyle(
            color: Colors.white, fontSize: 19, fontWeight: FontWeight.bold),
      ),
    );
  }

  void _cacheContactProfile(Map<String, dynamic> user) {
    final userId = user['user_id'] as String?;
    if (userId == null) return;
    DatabaseService.saveContactProfile(
      userId,
      _getDisplayName(user),
      user['avatar_url'],
    );
  }

  void _navigateToChat(String userId, String name, String? avatar) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (context) => ChatScreen(
          recipientId: userId,
          recipientName: name,
          recipientAvatar: avatar,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    const bgColor = Color(0xFF111318);
    const surfaceColor = Color(0xFF22242A);
    const accent = Color(0xFFC3C0FF);

    final isPhone = _tabController.index == 0;

    return Scaffold(
      backgroundColor: bgColor,
      appBar: AppBar(
        backgroundColor: bgColor,
        elevation: 0,
        title: const Text('Find by number or email',
            style: TextStyle(
                color: Colors.white,
                fontSize: 20,
                fontWeight: FontWeight.w400)),
        iconTheme: const IconThemeData(color: Colors.white),
        bottom: TabBar(
          controller: _tabController,
          indicatorColor: accent,
          indicatorWeight: 2.5,
          labelColor: accent,
          unselectedLabelColor: Colors.white54,
          labelStyle:
              const TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
          tabs: const [
            Tab(text: 'Phone'),
            Tab(text: 'Email'),
          ],
        ),
      ),
      body: Stack(
        children: [
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SizedBox(height: 8),
                Container(
                  decoration: BoxDecoration(
                    color: surfaceColor,
                    borderRadius: BorderRadius.circular(24),
                  ),
                  child: TextField(
                    controller: _identifierController,
                    keyboardType: isPhone
                        ? TextInputType.phone
                        : TextInputType.emailAddress,
                    inputFormatters: isPhone
                        ? [
                            FilteringTextInputFormatter.digitsOnly,
                            LengthLimitingTextInputFormatter(10),
                          ]
                        : [],
                    style:
                        const TextStyle(color: Colors.white, fontSize: 16),
                    decoration: InputDecoration(
                      hintText: isPhone
                          ? '98765 43210'
                          : 'Email address',
                      hintStyle:
                          TextStyle(color: Colors.white.withOpacity(0.5)),
                      border: InputBorder.none,
                      contentPadding: const EdgeInsets.symmetric(
                          horizontal: 20, vertical: 16),
                      prefixIcon: isPhone
                          ? Padding(
                              padding: const EdgeInsets.only(left: 16, right: 4),
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  const Icon(Icons.phone_outlined, color: Colors.white38, size: 20),
                                  const SizedBox(width: 8),
                                  Text(
                                    '+91',
                                    style: TextStyle(color: Colors.white.withOpacity(0.8), fontSize: 16, fontWeight: FontWeight.w500),
                                  ),
                                  const SizedBox(width: 4),
                                  Container(width: 1, height: 20, color: Colors.white24),
                                ],
                              ),
                            )
                          : const Icon(Icons.email_outlined, color: Colors.white38),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                Text(
                  isPhone
                      ? 'Enter a phone number to find an Aura user registered with it.'
                      : 'Enter an email address to find an Aura user registered with it.',
                  style: TextStyle(
                      color: Colors.white.withOpacity(0.6), fontSize: 14),
                ),
                if (_foundUser != null) ...[
                  const SizedBox(height: 32),
                  const Text(
                    'Search Result',
                    style: TextStyle(
                        color: Colors.white38,
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                        letterSpacing: 0.5),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      _buildSearchResultAvatar(_foundUser!),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              _getDisplayName(_foundUser!),
                              style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 16,
                                  fontWeight: FontWeight.w600),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                            if (_foundUser!['first_name'] != null &&
                                _foundUser!['first_name'].isNotEmpty &&
                                _foundUser!['username'] != null)
                              Text(
                                '@${_foundUser!['username']}',
                                style: TextStyle(
                                    color: Colors.white.withOpacity(0.5),
                                    fontSize: 13),
                              ),
                          ],
                        ),
                      ),
                      TextButton(
                        onPressed: () {
                          _cacheContactProfile(_foundUser!);
                          _navigateToChat(
                            _foundUser!['user_id'],
                            _getDisplayName(_foundUser!),
                            _foundUser!['avatar_url'],
                          );
                        },
                        style: TextButton.styleFrom(
                          backgroundColor: accent,
                          foregroundColor: bgColor,
                          shape: const StadiumBorder(),
                          padding: const EdgeInsets.symmetric(
                              horizontal: 20, vertical: 10),
                        ),
                        child: const Text(
                          'Message',
                          style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold),
                        ),
                      ),
                    ],
                  ),
                ],
              ],
            ),
          ),
          if (_isLoading)
            const Center(
              child: CircularProgressIndicator(color: accent),
            ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _isButtonActive && !_isLoading ? _search : null,
        backgroundColor:
            _isButtonActive ? accent : surfaceColor,
        elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(30)),
        child: Icon(
          Icons.arrow_forward,
          color: _isButtonActive
              ? bgColor
              : Colors.white.withOpacity(0.3),
        ),
      ),
    );
  }
}
