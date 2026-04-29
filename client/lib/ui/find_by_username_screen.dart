import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../network/api_client.dart';
import '../config.dart';
import '../database_service.dart';
import 'chat_screen.dart';

class FindByUsernameScreen extends StatefulWidget {
  const FindByUsernameScreen({super.key});

  @override
  State<FindByUsernameScreen> createState() => _FindByUsernameScreenState();
}

class _FindByUsernameScreenState extends State<FindByUsernameScreen> {
  final ApiClient _apiClient = ApiClient(baseUrl: AppConfig.apiBaseUrl);
  final TextEditingController _usernameController = TextEditingController();
  bool _isLoading = false;
  bool _isButtonActive = false;
  Map<String, dynamic>? _foundUser;

  @override
  void initState() {
    super.initState();
    _usernameController.addListener(() {
      final text = _usernameController.text.trim();
      setState(() {
        _isButtonActive = text.length >= 3;
      });
    });
  }

  @override
  void dispose() {
    _usernameController.dispose();
    super.dispose();
  }

  void _searchUsername() async {
    final username = _usernameController.text.trim();
    if (username.isEmpty) return;

    setState(() {
      _isLoading = true;
      _foundUser = null;
    });

    try {
      final bundle = await _apiClient.fetchPreKeysByUsername(username);
      if (mounted) {
        setState(() {
          _isLoading = false;
          _foundUser = bundle;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
        _showNotFoundDialog(username);
      }
    }
  }

  void _showNotFoundDialog(String username) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF2D2F33),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const Text('Username not found', style: TextStyle(color: Colors.white, fontSize: 20)),
        content: Text(
          '$username is not an Aura user. Please check the username and try again.',
          style: TextStyle(color: Colors.white.withOpacity(0.8), fontSize: 16),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('OK', style: TextStyle(color: Color(0xFFC3C0FF))),
          ),
        ],
      ),
    );
  }

  void _showPinDialog(String username, Map<String, dynamic> userProfile) {
    final TextEditingController pinController = TextEditingController();
    bool isPinLoading = false;
    String? errorText;
    int retriesLeft = 5;

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            final isPinValid = pinController.text.length == 4;

            return AlertDialog(
              backgroundColor: const Color(0xFF2D2F33),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
              title: const Text(
                'PIN Required',
                style: TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.w600),
                textAlign: TextAlign.center,
              ),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    'Enter PIN for @$username\nAttempts left: $retriesLeft',
                    style: TextStyle(color: Colors.white.withOpacity(0.7), fontSize: 14, height: 1.4),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 24),
                  TextField(
                    controller: pinController,
                    obscureText: true,
                    keyboardType: TextInputType.number,
                    textAlign: TextAlign.center,
                    style: const TextStyle(color: Colors.white, fontSize: 24, letterSpacing: 8),
                    inputFormatters: [
                      FilteringTextInputFormatter.digitsOnly,
                      LengthLimitingTextInputFormatter(4),
                    ],
                    onChanged: (_) {
                      setDialogState(() {});
                    },
                    decoration: InputDecoration(
                      hintText: '••••',
                      hintStyle: TextStyle(color: Colors.white.withOpacity(0.3), letterSpacing: 8),
                      errorText: errorText,
                      filled: true,
                      fillColor: const Color(0xFF1e2025),
                      contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(16),
                        borderSide: BorderSide.none,
                      ),
                      focusedBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(16),
                        borderSide: const BorderSide(color: Color(0xFFC3C0FF), width: 1.5),
                      ),
                    ),
                  ),
                  if (isPinLoading)
                    const Padding(
                      padding: EdgeInsets.only(top: 24.0),
                      child: CircularProgressIndicator(color: Color(0xFFC3C0FF)),
                    ),
                ],
              ),
              actions: [
                TextButton(
                  onPressed: isPinLoading ? null : () => Navigator.pop(ctx),
                  child: const Text('Cancel', style: TextStyle(color: Colors.white54, fontSize: 16)),
                ),
                TextButton(
                  onPressed: (isPinLoading || retriesLeft <= 0 || !isPinValid)
                      ? null
                      : () async {
                          final pin = pinController.text.trim();
                          if (pin.isEmpty) return;

                          setDialogState(() {
                            isPinLoading = true;
                            errorText = null;
                          });

                          try {
                            final bundle = await _apiClient.fetchPreKeysByUsername(username, pin: pin);
                            if (ctx.mounted) {
                              Navigator.pop(ctx);
                              _navigateToChat(
                                bundle['user_id'],
                                bundle['display_name'],
                                bundle['avatar_url'],
                              );
                            }
                          } catch (e) {
                            setDialogState(() {
                              retriesLeft--;
                              isPinLoading = false;
                              if (retriesLeft <= 0) {
                                errorText = 'Too many failed attempts.';
                              } else {
                                errorText = 'Incorrect PIN.';
                                pinController.clear();
                              }
                            });
                          }
                        },
                  child: Text(
                    'Verify',
                    style: TextStyle(
                      color: isPinValid ? const Color(0xFFC3C0FF) : Colors.white.withOpacity(0.3),
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],
            );
          },
        );
      },
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
    return Scaffold(
      backgroundColor: const Color(0xFF111318),
      appBar: AppBar(
        backgroundColor: const Color(0xFF111318),
        elevation: 0,
        title: const Text('Find by username', style: TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.w400)),
        iconTheme: const IconThemeData(color: Colors.white),
      ),
      body: Stack(
        children: [
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  decoration: BoxDecoration(
                    color: const Color(0xFF22242A),
                    borderRadius: BorderRadius.circular(24),
                  ),
                  child: TextField(
                    controller: _usernameController,
                    style: const TextStyle(color: Colors.white, fontSize: 16),
                    decoration: InputDecoration(
                      hintText: 'Username',
                      hintStyle: TextStyle(color: Colors.white.withOpacity(0.5)),
                      border: InputBorder.none,
                      contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                Text(
                  'Enter a username to chat with that person.',
                  style: TextStyle(color: Colors.white.withOpacity(0.6), fontSize: 14),
                ),
                const SizedBox(height: 32),
                Center(
                  child: Material(
                    color: const Color(0xFF22242A),
                    borderRadius: BorderRadius.circular(24),
                    child: InkWell(
                      borderRadius: BorderRadius.circular(24),
                      onTap: () {}, // Future: Scan QR
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(Icons.qr_code_scanner, color: Colors.white.withOpacity(0.8), size: 20),
                            const SizedBox(width: 12),
                            Text(
                              'Scan QR code',
                              style: TextStyle(color: Colors.white.withOpacity(0.9), fontSize: 15, fontWeight: FontWeight.w500),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
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
                          if (_foundUser!['pin_required'] == true && _foundUser!['identity_key'] == null) {
                            _showPinDialog(_foundUser!['username'], _foundUser!);
                          } else {
                            _navigateToChat(
                              _foundUser!['user_id'],
                              _getDisplayName(_foundUser!),
                              _foundUser!['avatar_url'],
                            );
                          }
                        },
                        style: TextButton.styleFrom(
                          backgroundColor: const Color(0xFFC3C0FF),
                          foregroundColor: const Color(0xFF111318),
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
              child: CircularProgressIndicator(color: Color(0xFFC3C0FF)),
            ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _isButtonActive && !_isLoading ? _searchUsername : null,
        backgroundColor: _isButtonActive ? const Color(0xFFC3C0FF) : const Color(0xFF22242A),
        elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(30)),
        child: Icon(
          Icons.arrow_forward,
          color: _isButtonActive ? const Color(0xFF111318) : Colors.white.withOpacity(0.3),
        ),
      ),
    );
  }
}
