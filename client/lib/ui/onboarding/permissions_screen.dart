import 'package:flutter/material.dart';

class PermissionsScreen extends StatelessWidget {
  final VoidCallback onNext;
  final VoidCallback onNotNow;

  const PermissionsScreen({
    super.key,
    required this.onNext,
    required this.onNotNow,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return SingleChildScrollView(
      padding: const EdgeInsets.all(24.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const SizedBox(height: 16),
          // Title
          Text(
            "Allow permissions",
            style: TextStyle(
              fontSize: 28,
              fontWeight: FontWeight.bold,
              color: isDark ? Colors.white : Colors.black87,
              letterSpacing: -0.5,
            ),
          ),
          const SizedBox(height: 12),
          // Subtitle
          Text(
            "To help you message people you know, Antigravity needs access to your contacts and notifications.",
            style: TextStyle(
              fontSize: 16,
              color: isDark ? Colors.white70 : Colors.black54,
              height: 1.4,
            ),
          ),
          const SizedBox(height: 48),

          _PermissionRow(
            icon: Icons.notifications_none_rounded,
            title: "Notifications",
            subtitle: "Get notified when you receive a message or call.",
            isDark: isDark,
          ),
          _PermissionRow(
            icon: Icons.people_outline_rounded,
            title: "Contacts",
            subtitle: "Find people you know and stay connected.",
            isDark: isDark,
          ),
          _PermissionRow(
            icon: Icons.folder_open_rounded,
            title: "Files and media",
            subtitle: "Send photos, videos, and files to your friends.",
            isDark: isDark,
          ),
          _PermissionRow(
            icon: Icons.phone_outlined,
            title: "Phone calls",
            subtitle: "Make registering easier by automatically reading your phone number.",
            isDark: isDark,
          ),
        ],
      ),
    );
  }
}

class _PermissionRow extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final bool isDark;

  const _PermissionRow({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.isDark,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 32.0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            icon,
            color: isDark ? Colors.white70 : Colors.black87,
            size: 40,
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w600,
                    color: isDark ? Colors.white : Colors.black87,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  subtitle,
                  style: TextStyle(
                    fontSize: 15,
                    color: isDark ? Colors.white60 : Colors.black54,
                    height: 1.3,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
