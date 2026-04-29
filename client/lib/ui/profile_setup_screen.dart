import 'dart:io';
import 'dart:ui';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:image_cropper/image_cropper.dart';
import '../auth/auth_service.dart';
import 'onboarding/onboarding_flow.dart';
import 'onboarding/username_setup_screen.dart';
class ProfileSetupScreen extends StatefulWidget {
  const ProfileSetupScreen({super.key});

  @override
  State<ProfileSetupScreen> createState() => _ProfileSetupScreenState();
}

class _ProfileSetupScreenState extends State<ProfileSetupScreen> {
  final _firstNameController = TextEditingController();
  final _lastNameController = TextEditingController();
  
  final _firstNameFocusNode = FocusNode();
  final _lastNameFocusNode = FocusNode();
  
  bool _isFirstNameValid = false;
  bool _isLoading = false;
  String? _profileImagePath;
  final ImagePicker _picker = ImagePicker();

  @override
  void initState() {
    super.initState();
    _firstNameController.addListener(() {
      final valid = _firstNameController.text.trim().isNotEmpty;
      if (valid != _isFirstNameValid) setState(() => _isFirstNameValid = valid);
    });
  }

  @override
  void dispose() {
    _firstNameController.dispose();
    _lastNameController.dispose();
    _firstNameFocusNode.dispose();
    _lastNameFocusNode.dispose();
    super.dispose();
  }

  Future<void> _pickAndCropImage() async {
    try {
      final XFile? pickedFile = await _picker.pickImage(source: ImageSource.gallery);
      if (pickedFile != null) {
        final CroppedFile? croppedFile = await ImageCropper().cropImage(
          sourcePath: pickedFile.path,
          aspectRatio: const CropAspectRatio(ratioX: 1, ratioY: 1),
          uiSettings: [
            AndroidUiSettings(
              toolbarTitle: 'Crop Avatar',
              toolbarColor: const Color(0xFF111318),
              toolbarWidgetColor: const Color(0xFFc3c0ff),
              initAspectRatio: CropAspectRatioPreset.square,
              lockAspectRatio: true,
              hideBottomControls: true,
            ),
            IOSUiSettings(
              title: 'Crop Avatar',
              aspectRatioLockEnabled: true,
              resetAspectRatioEnabled: false,
              aspectRatioPickerButtonHidden: true,
            ),
            WebUiSettings(
              context: context,
              presentStyle: WebPresentStyle.dialog,
            ),
          ],
        );

        if (croppedFile != null) {
          setState(() {
            _profileImagePath = croppedFile.path;
          });
        }
      }
    } catch (e) {
      debugPrint("Error picking image: $e");
    }
  }

  Future<void> _handleComplete() async {
    HapticFeedback.mediumImpact();
    
    setState(() => _isLoading = true);
    try {
      await AuthService.updateProfile(
        firstName: _firstNameController.text.trim(),
        lastName: _lastNameController.text.trim(),
        imagePath: _profileImagePath,
      );
      
      if (!mounted) return;
      Navigator.of(context).pushReplacement(
        MaterialPageRoute(builder: (context) => const UsernameSetupScreen()),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(e.toString()),
          backgroundColor: Colors.redAccent,
        ),
      );
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    const bgColor = Color(0xFF111318);
    const surfaceContainer = Color(0xFF1e2025);
    const surfaceHighest = Color(0xFF33353a);
    const primary = Color(0xFFc3c0ff);
    const onPrimary = Color(0xFF0f0069);
    const outline = Color(0xFF8e9099);
    const onSurface = Color(0xFFe2e2e6);

    return Scaffold(
      backgroundColor: bgColor,
      appBar: PreferredSize(
        preferredSize: const Size.fromHeight(64),
        child: ClipRRect(
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
            child: Container(
              decoration: BoxDecoration(
                color: bgColor.withOpacity(0.8),
              ),
              child: SafeArea(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Row(
                    children: [
                      IconButton(
                        icon: const Icon(Icons.arrow_back, color: primary),
                        onPressed: () => Navigator.of(context).pop(),
                        splashRadius: 24,
                      ),
                      const Expanded(
                        child: Text(
                          "Profile info",
                          style: TextStyle(
                            fontFamily: 'Plus Jakarta Sans',
                            color: onSurface,
                            fontSize: 22,
                            fontWeight: FontWeight.w700,
                            letterSpacing: -0.5,
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ),
                      const SizedBox(width: 48), // Balance for back button
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
      body: SafeArea(
        child: CustomScrollView(
            physics: MediaQuery.of(context).viewInsets.bottom > 0 
                ? const BouncingScrollPhysics() 
                : const NeverScrollableScrollPhysics(),
            keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
            slivers: [
              SliverPadding(
                padding: const EdgeInsets.symmetric(horizontal: 24),
                sliver: SliverFillRemaining(
                  hasScrollBody: false,
                  child: Column(
                    children: [
                      const SizedBox(height: 32),
                      
                      // Avatar Section
                      GestureDetector(
                        onTap: () {
                          FocusScope.of(context).unfocus();
                          _pickAndCropImage();
                        },
                        child: Stack(
                          clipBehavior: Clip.none,
                          children: [
                            Container(
                              width: 140,
                              height: 140,
                              decoration: BoxDecoration(
                                color: surfaceContainer,
                                shape: BoxShape.circle,
                                border: Border.all(color: surfaceHighest, width: 4),
                                image: _profileImagePath != null
                                    ? DecorationImage(
                                        image: kIsWeb 
                                            ? NetworkImage(_profileImagePath!) as ImageProvider
                                            : FileImage(File(_profileImagePath!)), 
                                        fit: BoxFit.cover
                                      )
                                    : null,
                              ),
                              child: _profileImagePath == null
                                  ? const Icon(Icons.person, color: outline, size: 64)
                                  : null,
                            ),
                            Positioned(
                              bottom: 4,
                              right: -8,
                              child: Container(
                                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                                decoration: BoxDecoration(
                                  color: primary,
                                  borderRadius: BorderRadius.circular(20),
                                  boxShadow: [
                                    BoxShadow(
                                      color: Colors.black.withOpacity(0.3),
                                      blurRadius: 8,
                                      offset: const Offset(0, 4),
                                    ),
                                  ],
                                ),
                                child: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: const [
                                    Icon(Icons.photo_camera, color: onPrimary, size: 16),
                                    SizedBox(width: 6),
                                    Text(
                                      "EDIT",
                                      style: TextStyle(
                                        fontFamily: 'Plus Jakarta Sans',
                                        color: onPrimary,
                                        fontSize: 12,
                                        fontWeight: FontWeight.bold,
                                        letterSpacing: 0.5,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      
                      AnimatedBuilder(
                        animation: Listenable.merge([_firstNameController, _lastNameController]),
                        builder: (context, _) {
                          final first = _firstNameController.text.trim();
                          final last = _lastNameController.text.trim();
                          final fullName = [first, last].where((s) => s.isNotEmpty).join(" ");
                          final display = fullName.isEmpty ? "\u200b" : fullName;
                          
                          return Padding(
                            padding: const EdgeInsets.only(top: 16, bottom: 8),
                            child: Text(
                              display,
                              style: const TextStyle(
                                fontFamily: 'Plus Jakarta Sans',
                                color: Colors.white,
                                fontSize: 24,
                                fontWeight: FontWeight.bold,
                                letterSpacing: -0.5,
                              ),
                              textAlign: TextAlign.center,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          );
                        },
                      ),
                      
                      // Context Text
                      const Padding(
                        padding: EdgeInsets.symmetric(horizontal: 16),
                        child: Text(
                          "Help your friends recognize you by adding a name and photo.",
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            fontFamily: 'Plus Jakarta Sans',
                            color: outline,
                            fontSize: 14,
                            height: 1.4,
                          ),
                        ),
                      ),
                      
                      const SizedBox(height: 32),
                      
                      // Form Fields
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          TextField(
                            controller: _firstNameController,
                            focusNode: _firstNameFocusNode,
                            style: const TextStyle(color: onSurface, fontSize: 16),
                            textInputAction: TextInputAction.next,
                            onTapOutside: (_) => FocusScope.of(context).unfocus(),
                            onSubmitted: (_) => FocusScope.of(context).requestFocus(_lastNameFocusNode),
                            decoration: InputDecoration(
                              labelText: "First name (required)",
                              labelStyle: const TextStyle(color: outline, fontFamily: 'Plus Jakarta Sans'),
                              floatingLabelStyle: const TextStyle(color: primary, fontWeight: FontWeight.w600),
                              hintText: "e.g. Alex",
                              hintStyle: TextStyle(color: outline.withOpacity(0.3)),
                              filled: true,
                              fillColor: surfaceContainer,
                              contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 18),
                              border: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(16),
                                borderSide: BorderSide.none,
                              ),
                              focusedBorder: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(16),
                                borderSide: const BorderSide(color: primary, width: 1.5),
                              ),
                            ),
                          ),
                          
                          const SizedBox(height: 16),
                          
                          TextField(
                            controller: _lastNameController,
                            focusNode: _lastNameFocusNode,
                            style: const TextStyle(color: onSurface, fontSize: 16),
                            textInputAction: TextInputAction.done,
                            onTapOutside: (_) => FocusScope.of(context).unfocus(),
                            onSubmitted: (_) => FocusScope.of(context).unfocus(),
                            decoration: InputDecoration(
                              labelText: "Last name (optional)",
                              labelStyle: const TextStyle(color: outline, fontFamily: 'Plus Jakarta Sans'),
                              floatingLabelStyle: const TextStyle(color: outline, fontWeight: FontWeight.w600),
                              hintText: "e.g. Rivera",
                              hintStyle: TextStyle(color: outline.withOpacity(0.3)),
                              filled: true,
                              fillColor: surfaceContainer,
                              contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 18),
                              border: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(16),
                                borderSide: BorderSide.none,
                              ),
                              focusedBorder: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(16),
                                borderSide: const BorderSide(color: primary, width: 1.5),
                              ),
                            ),
                          ),
                        ],
                      ),
                      
                      // Spacer pushes everything below it to the bottom
                      const Spacer(),
                      const SizedBox(height: 32),
                      
                      // Subtle Privacy Footnote
                      Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.lock_outline, color: outline.withOpacity(0.7), size: 14),
                          const SizedBox(width: 6),
                          Text(
                            "Your data is end-to-end encrypted",
                            style: TextStyle(
                              fontFamily: 'Plus Jakarta Sans',
                              color: outline.withOpacity(0.7),
                              fontSize: 12,
                              letterSpacing: 0.2,
                            ),
                          ),
                        ],
                      ),
                      
                      const SizedBox(height: 16),
                      
                      // Next Button
                      SizedBox(
                        width: double.infinity,
                        height: 56,
                        child: ElevatedButton(
                          onPressed: (_isFirstNameValid && !_isLoading) ? _handleComplete : null,
                          style: ElevatedButton.styleFrom(
                            backgroundColor: primary,
                            foregroundColor: onPrimary,
                            disabledBackgroundColor: primary.withOpacity(0.3),
                            disabledForegroundColor: onPrimary.withOpacity(0.5),
                            elevation: _isFirstNameValid ? 4 : 0,
                            shadowColor: primary.withOpacity(0.5),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(28),
                            ),
                          ),
                          child: _isLoading
                              ? const SizedBox(
                                  height: 24,
                                  width: 24,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2.5,
                                    color: onPrimary,
                                  ),
                                )
                              : Row(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  children: const [
                                    Text(
                                      "Next",
                                      style: TextStyle(
                                        fontFamily: 'Plus Jakarta Sans',
                                        fontSize: 16,
                                        fontWeight: FontWeight.bold,
                                      ),
                                    ),
                                    SizedBox(width: 8),
                                    Icon(Icons.chevron_right, size: 24),
                                  ],
                                ),
                        ),
                      ),
                      
                      const SizedBox(height: 32),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
    );
  }
}
