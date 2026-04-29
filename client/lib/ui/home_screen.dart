import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:hive_flutter/hive_flutter.dart';
import '../database_service.dart';
import '../services/service_locator.dart';
import 'chat_screen.dart';
import 'new_chat_screen.dart';
import 'profile_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _currentIndex = 0;

  static const _bgColor = Color(0xFF111318);
  static const _primary = Color(0xFFC3C0FF);

  static const List<Color> _avatarColors = [
    Color(0xFFC3C0FF),
    Color(0xFFFFC0C0),
    Color(0xFFC0FFC3),
    Color(0xFFFFF6C0),
    Color(0xFFC0F4FF),
  ];

  @override
  void initState() {
    super.initState();
    _resolveAllProfiles();
  }

  /// Kick off background profile resolution for all conversations
  /// so that UUIDs get replaced with real names.
  Future<void> _resolveAllProfiles() async {
    final repo = await ServiceLocator.getRepoAsync();
    if (repo == null) return;

    final conversations = DatabaseService.getAllConversations();
    for (final chat in conversations) {
      final userId = chat['userId'] as String;
      final name = chat['name'] as String? ?? '';

      // If the name looks like a UUID, resolve it
      if (_looksLikeUuid(name) || name == 'Unknown Contact' || name.isEmpty) {
        final resolved = await repo.profileResolver.resolve(userId);
        if (resolved != null && mounted) {
          await DatabaseService.updateConversation(
            userId: userId,
            name: resolved['name'] ?? name,
            avatarUrl: resolved['avatarUrl'],
            lastMessage: chat['lastMessage'] ?? '',
            lastMessageTime: DateTime.tryParse(chat['lastMessageTime'] ?? '') ?? DateTime.now(),
          );
        }
      }
    }
  }

  bool _looksLikeUuid(String input) {
    return RegExp(r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
        caseSensitive: false).hasMatch(input);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: _bgColor,
      body: ValueListenableBuilder(
        valueListenable: Hive.box('conversations').listenable(),
        builder: (context, Box box, _) {
          final conversations = DatabaseService.getAllConversations();

          return CustomScrollView(
            physics: const BouncingScrollPhysics(),
            slivers: [
              // App Bar
              SliverAppBar(
                expandedHeight: 95.0,
                floating: true,
                pinned: true,
                stretch: true,
                backgroundColor: _bgColor.withOpacity(0.9),
                flexibleSpace: FlexibleSpaceBar(
                  centerTitle: false,
                  titlePadding: const EdgeInsets.only(left: 16, bottom: 12),
                  title: const Text(
                    "Messages",
                    style: TextStyle(
                      fontFamily: 'Plus Jakarta Sans',
                      color: Colors.white,
                      fontSize: 21,
                      fontWeight: FontWeight.w600,
                      letterSpacing: -0.5,
                    ),
                  ),
                ),
                actions: [
                  IconButton(
                    icon: const Icon(Icons.search, color: Colors.white70, size: 22),
                    onPressed: () {},
                  ),
                  IconButton(
                    icon: const Icon(Icons.more_vert, color: Colors.white70, size: 22),
                    onPressed: () {},
                  ),
                  const SizedBox(width: 4),
                ],
              ),

              // Chat List or Empty State
              if (conversations.isEmpty)
                SliverFillRemaining(
                  hasScrollBody: false,
                  child: Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.chat_bubble_outline,
                            color: Colors.white.withOpacity(0.15), size: 64),
                        const SizedBox(height: 16),
                        Text(
                          "No messages yet",
                          style: TextStyle(
                              color: Colors.white.withOpacity(0.4), fontSize: 16),
                        ),
                      ],
                    ),
                  ),
                )
              else
                SliverList(
                  delegate: SliverChildBuilderDelegate(
                    (context, index) {
                      final chat = conversations[index];
                      return Column(
                        children: [
                          _buildChatTile(
                            context,
                            id: chat['userId'],
                            name: chat['name'] ?? chat['userId'],
                            message: chat['lastMessage'] ?? '',
                            time: DatabaseService.formatTime(chat['lastMessageTime']),
                            unread: (chat['unreadCount'] ?? 0) > 0,
                            avatarUrl: chat['avatarUrl'],
                            avatarColor: _avatarColors[index % _avatarColors.length],
                          ),
                          if (index < conversations.length - 1)
                            Padding(
                              padding: const EdgeInsets.only(left: 76, right: 16),
                              child: Divider(
                                color: Colors.white.withOpacity(0.05),
                                height: 1,
                              ),
                            ),
                        ],
                      );
                    },
                    childCount: conversations.length,
                  ),
                ),
            ],
          );
        },
      ),

      // FAB
      floatingActionButton: Padding(
        padding: const EdgeInsets.only(bottom: 60),
        child: Container(
          height: 52,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(18),
            boxShadow: [
              BoxShadow(
                color: _primary.withOpacity(0.3),
                blurRadius: 15,
                offset: const Offset(0, 6),
              ),
            ],
          ),
          child: FloatingActionButton.extended(
            onPressed: () {
              Navigator.of(context).push(
                MaterialPageRoute(builder: (context) => const NewChatScreen()),
              );
            },
            backgroundColor: _primary,
            foregroundColor: _bgColor,
            elevation: 4,
            icon: const Icon(Icons.edit_rounded, size: 20),
            label: const Text(
              "New Chat",
              style: TextStyle(fontWeight: FontWeight.w600, fontSize: 13.5, letterSpacing: 0.1),
            ),
          ),
        ),
      ),

      bottomNavigationBar: _buildBottomNavBar(context),
    );
  }

  // --- Chat Tile ---

  Widget _buildChatTile(
    BuildContext context, {
    required String id,
    required String name,
    required String message,
    required String time,
    required bool unread,
    required Color avatarColor,
    String? avatarUrl,
  }) {
    // Show a clean display name, never a raw UUID
    final displayName = _looksLikeUuid(name) ? 'Unknown' : name;

    return Material(
      color: Colors.transparent,
      child: ListTile(
        onTap: () async {
          await DatabaseService.markAsRead(id);
          if (!context.mounted) return;
          Navigator.of(context).push(
            MaterialPageRoute(
              builder: (context) => ChatScreen(
                recipientId: id,
                recipientName: displayName,
                recipientAvatar: avatarUrl,
              ),
            ),
          );
        },
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 1),
        leading: _buildAvatar(displayName, avatarColor, avatarUrl, unread),
        title: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Expanded(
              child: Text(
                displayName,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w600,
                  fontSize: 16,
                  fontFamily: 'Plus Jakarta Sans',
                  letterSpacing: -0.2,
                ),
              ),
            ),
            const SizedBox(width: 8),
            Text(
              time,
              style: TextStyle(
                color: unread ? _primary : Colors.white.withOpacity(0.35),
                fontSize: 11.5,
                fontWeight: unread ? FontWeight.w600 : FontWeight.w400,
              ),
            ),
          ],
        ),
        subtitle: Padding(
          padding: const EdgeInsets.only(top: 2),
          child: Text(
            message,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              color: unread ? Colors.white.withOpacity(0.9) : Colors.white.withOpacity(0.4),
              fontSize: 13.5,
              fontWeight: unread ? FontWeight.w500 : FontWeight.w400,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildAvatar(String name, Color color, String? avatarUrl, bool unread) {
    Widget avatarContent;

    if (avatarUrl != null && avatarUrl.isNotEmpty) {
      avatarContent = CircleAvatar(
        radius: 25,
        backgroundImage: NetworkImage(avatarUrl),
        backgroundColor: color.withOpacity(0.12),
        onBackgroundImageError: (_, __) {},
      );
    } else {
      avatarContent = Container(
        width: 50,
        height: 50,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: color.withOpacity(0.12),
          shape: BoxShape.circle,
        ),
        child: Text(
          name.isNotEmpty ? name[0].toUpperCase() : '?',
          style: TextStyle(
            color: color,
            fontWeight: FontWeight.w600,
            fontSize: 19,
            fontFamily: 'Plus Jakarta Sans',
            height: 1.0,
          ),
        ),
      );
    }

    return Stack(
      children: [
        avatarContent,
        if (unread)
          Positioned(
            right: 0,
            top: 0,
            child: Container(
              width: 12,
              height: 12,
              decoration: BoxDecoration(
                color: _primary,
                shape: BoxShape.circle,
                border: Border.all(color: _bgColor, width: 2),
              ),
            ),
          ),
      ],
    );
  }

  // --- Bottom Navigation ---

  Widget _buildBottomNavBar(BuildContext context) {
    return Container(
      height: 90,
      decoration: const BoxDecoration(color: Colors.transparent),
      child: Stack(
        children: [
          Container(color: _bgColor),
          Container(
            height: 1,
            color: Colors.white.withOpacity(0.05),
          ),
          SafeArea(
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                Expanded(child: _buildNavItem(0, CupertinoIcons.chat_bubble, CupertinoIcons.chat_bubble_fill, "Chats")),
                Expanded(child: _buildNavItem(1, CupertinoIcons.phone, CupertinoIcons.phone_fill, "Calls")),
                Expanded(child: _buildNavItem(2, CupertinoIcons.person_crop_circle, CupertinoIcons.person_crop_circle_fill, "Profile")),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildNavItem(int index, IconData icon, IconData selectedIcon, String label) {
    final isSelected = _currentIndex == index;

    return GestureDetector(
      onTap: () {
        if (index == 2) {
          Navigator.of(context).push(
            MaterialPageRoute(builder: (context) => const ProfileScreen()),
          );
        } else {
          setState(() => _currentIndex = index);
        }
      },
      behavior: HitTestBehavior.opaque,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const SizedBox(height: 8),
          AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 4),
            decoration: BoxDecoration(
              color: isSelected ? _primary.withOpacity(0.15) : Colors.transparent,
              borderRadius: BorderRadius.circular(20),
            ),
            child: Icon(
              isSelected ? selectedIcon : icon,
              color: isSelected ? _primary : Colors.white.withOpacity(0.4),
              size: 26,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            label,
            style: TextStyle(
              color: isSelected ? _primary : Colors.white.withOpacity(0.4),
              fontSize: 11,
              fontWeight: isSelected ? FontWeight.w600 : FontWeight.w400,
              fontFamily: 'Plus Jakarta Sans',
            ),
          ),
          const SizedBox(height: 8),
        ],
      ),
    );
  }
}
