import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter_contacts/flutter_contacts.dart';
import '../network/api_client.dart';
import '../config.dart';
import 'chat_screen.dart';
import 'find_by_username_screen.dart';
import 'find_by_identifier_screen.dart';

class NewChatScreen extends StatefulWidget {
  const NewChatScreen({super.key});

  @override
  State<NewChatScreen> createState() => _NewChatScreenState();
}

class _NewChatScreenState extends State<NewChatScreen> {
  final ApiClient _apiClient = ApiClient(baseUrl: AppConfig.apiBaseUrl);
  
  bool _isLoadingContacts = false;
  List<Contact> _contacts = [];
  bool _permissionDenied = false;

  @override
  void initState() {
    super.initState();
    _fetchContacts();
  }

  Future<void> _fetchContacts() async {
    setState(() {
      _isLoadingContacts = true;
    });

    if (await FlutterContacts.permissions.request(PermissionType.read) == PermissionStatus.granted) {
      List<Contact> contacts = await FlutterContacts.getAll(properties: {ContactProperty.phone, ContactProperty.name});
      if (mounted) {
        setState(() {
          _contacts = contacts;
          _isLoadingContacts = false;
          _permissionDenied = false;
        });
      }
    } else {
      if (mounted) {
        setState(() {
          _isLoadingContacts = false;
          _permissionDenied = true;
        });
      }
    }
  }



  Widget _buildActionItem(IconData icon, String title, VoidCallback onTap) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: const Color(0xFF1e2025),
                shape: BoxShape.circle,
              ),
              child: Icon(icon, color: Colors.white70, size: 20),
            ),
            const SizedBox(width: 16),
            Text(
              title,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 16,
                fontWeight: FontWeight.w500,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildContactItem(Contact contact) {
    String name = contact.displayName ?? '';
    String phone = contact.phones.isNotEmpty ? contact.phones.first.number : '';
    
    return ListTile(
      leading: Container(
        width: 40,
        height: 40,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: const Color(0xFFC3C0FF).withOpacity(0.12),
          shape: BoxShape.circle,
        ),
        child: Text(
          name.isNotEmpty ? name[0].toUpperCase() : '?',
          style: const TextStyle(
            color: Color(0xFFC3C0FF),
            fontWeight: FontWeight.w600,
            fontSize: 16,
          ),
        ),
      ),
      title: Text(
        name,
        style: const TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.w500),
      ),
      subtitle: phone.isNotEmpty
          ? Text(
              phone,
              style: TextStyle(color: Colors.white.withOpacity(0.5), fontSize: 13),
            )
          : null,
      onTap: () {
        // Handle contact tap
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    const bgColor = Color(0xFF111318);

    return Scaffold(
      backgroundColor: bgColor,
      appBar: AppBar(
        backgroundColor: bgColor,
        elevation: 0,
        title: const Text(
          'New Message',
          style: TextStyle(
            color: Colors.white,
            fontSize: 20,
            fontWeight: FontWeight.w600,
          ),
        ),
        iconTheme: const IconThemeData(color: Colors.white),
      ),
      body: CustomScrollView(
        slivers: [
          SliverToBoxAdapter(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildActionItem(CupertinoIcons.at, "Find by Username", () {
                  Navigator.push(context, MaterialPageRoute(builder: (_) => const FindByUsernameScreen()));
                }),
                _buildActionItem(CupertinoIcons.group, "New Group", () {}),
                _buildActionItem(CupertinoIcons.person_add, "New Contact", () {}),
                _buildActionItem(CupertinoIcons.mail, "Find by Number or Email", () {
                  Navigator.push(context, MaterialPageRoute(builder: (_) => const FindByIdentifierScreen()));
                }),
                
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 24, 16, 8),
                  child: Text(
                    'Contacts on Aura',
                    style: TextStyle(
                      color: Colors.white.withOpacity(0.5),
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],
            ),
          ),
          if (_isLoadingContacts)
            const SliverToBoxAdapter(
              child: Center(
                child: Padding(
                  padding: EdgeInsets.all(20.0),
                  child: CircularProgressIndicator(color: Color(0xFFC3C0FF)),
                ),
              ),
            )
          else if (_permissionDenied)
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.all(20.0),
                child: Column(
                  children: [
                    Text(
                      'Contact permission is required to sync your contacts.',
                      style: TextStyle(color: Colors.white.withOpacity(0.7)),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 12),
                    ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFFC3C0FF),
                        foregroundColor: bgColor,
                      ),
                      onPressed: () async {
                        await FlutterContacts.permissions.openSettings();
                        _fetchContacts();
                      },
                      child: const Text('Open Settings'),
                    ),
                  ],
                ),
              ),
            )
          else if (_contacts.isEmpty)
            SliverToBoxAdapter(
              child: Center(
                child: Padding(
                  padding: const EdgeInsets.all(20.0),
                  child: Text(
                    'No contacts found.',
                    style: TextStyle(color: Colors.white.withOpacity(0.5)),
                  ),
                ),
              ),
            )
          else
            SliverList(
              delegate: SliverChildBuilderDelegate(
                (context, index) {
                  return _buildContactItem(_contacts[index]);
                },
                childCount: _contacts.length,
              ),
            ),
        ],
      ),
    );
  }
}
