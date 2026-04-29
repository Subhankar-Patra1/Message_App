import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../auth/auth_service.dart';
import '../database_service.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  bool _isLoading = false;
  Map<String, dynamic>? _profileData;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadInitialData();
    _fetchProfile();
  }

  void _loadInitialData() {
    final cached = DatabaseService.getMyProfile();
    if (cached != null) {
      setState(() {
        _profileData = cached;
      });
    } else {
      setState(() {
        _isLoading = true;
      });
    }
  }

  Future<void> _fetchProfile() async {
    try {
      final data = await AuthService.getProfile();
      if (mounted) {
        setState(() {
          _profileData = data;
          _isLoading = false;
        });
        // Cache for next time
        await DatabaseService.saveMyProfile(data);
      }
    } catch (e) {
      if (mounted && _profileData == null) {
        setState(() {
          _error = e.toString();
          _isLoading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    const bgColor = Color(0xFF111318);
    const surfaceColor = Color(0xFF1e2025);
    const accentColor = Color(0xFFc3c0ff);
    const textSecondary = Colors.white54;

    return Scaffold(
      backgroundColor: bgColor,
      appBar: AppBar(
        backgroundColor: bgColor,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.white),
          onPressed: () => Navigator.pop(context),
        ),
        title: const Text(
          'Profile',
          style: TextStyle(
            color: Colors.white,
            fontSize: 20,
            fontWeight: FontWeight.w500,
          ),
        ),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator(color: accentColor))
          : _error != null
              ? Center(child: Text(_error!, style: const TextStyle(color: Colors.redAccent)))
              : SingleChildScrollView(
                  child: Column(
                    children: [
                      const SizedBox(height: 20),
                      // Avatar Section
                      Center(
                        child: Column(
                          children: [
                            CircleAvatar(
                              radius: 60,
                              backgroundColor: surfaceColor,
                              backgroundImage: _profileData?['avatar_url'] != null
                                  ? NetworkImage(_profileData!['avatar_url'])
                                  : null,
                              child: _profileData?['avatar_url'] == null
                                  ? const Icon(Icons.person, size: 60, color: Colors.white24)
                                  : null,
                            ),
                            const SizedBox(height: 16),
                            ElevatedButton(
                              onPressed: () {
                                // Logic to edit photo
                              },
                              style: ElevatedButton.styleFrom(
                                backgroundColor: surfaceColor,
                                foregroundColor: Colors.white,
                                elevation: 0,
                                shape: const StadiumBorder(),
                                padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
                              ),
                              child: const Text('Edit photo', style: TextStyle(fontSize: 14)),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 40),

                      // Profile Info List
                      _buildProfileItem(
                        icon: Icons.person_outline,
                        title: "${_profileData?['first_name'] ?? ''} ${_profileData?['last_name'] ?? ''}".trim(),
                        onTap: () {},
                      ),
                      _buildProfileItem(
                        icon: Icons.edit_outlined,
                        title: 'About',
                        onTap: () {},
                      ),
                      
                      Padding(
                        padding: const EdgeInsets.fromLTRB(72, 16, 24, 16),
                        child: Text(
                          'Your profile and changes to it will be visible to people you message, contacts, and groups.',
                          style: TextStyle(color: textSecondary, fontSize: 13, height: 1.4),
                        ),
                      ),

                      const Divider(color: Colors.white10, height: 32, thickness: 1),

                      // Username & QR
                      _buildProfileItem(
                        icon: Icons.alternate_email,
                        title: _profileData?['username'] ?? 'No username set',
                        onTap: () {},
                      ),
                      _buildProfileItem(
                        icon: Icons.qr_code_outlined,
                        title: 'QR code or link',
                        onTap: () {},
                      ),

                      Padding(
                        padding: const EdgeInsets.fromLTRB(72, 16, 24, 32),
                        child: Text(
                          "Your username, QR code and link aren't visible on your profile. Only share your username with people you trust.",
                          style: TextStyle(color: textSecondary, fontSize: 13, height: 1.4),
                        ),
                      ),
                    ],
                  ),
                ),
    );
  }

  Widget _buildProfileItem({
    required IconData icon,
    required String title,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
        child: Row(
          children: [
            Icon(icon, color: Colors.white70, size: 26),
            const SizedBox(width: 24),
            Expanded(
              child: Text(
                title,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 16,
                  fontWeight: FontWeight.w400,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
