import 'dart:convert';
import 'dart:typed_data';
import 'package:flutter/foundation.dart';
import 'package:pointycastle/export.dart';

class BackupCrypto {
  /// Derives an encryption key from the given passphrase using Argon2id.
  /// Runs in an isolate because Argon2id is intentionally CPU-heavy.
  static Future<Uint8List> deriveKey(String passphrase) async {
    return await compute(_deriveKeyIsolate, passphrase);
  }

  static Uint8List _deriveKeyIsolate(String passphrase) {
    final salt = utf8.encode('backup_salt_static_v1'); // In a real app, salt should be unique per user and stored.
    
    final argon2 = Argon2BytesGenerator()
      ..init(Argon2Parameters(
        Argon2Parameters.ARGON2_id,
        salt,
        desiredKeyLength: 32, // 256 bit
        iterations: 3,
        memory: 65536, // 64MB
        lanes: 4,
        version: Argon2Parameters.ARGON2_VERSION_13,
      ));

    final passwordBytes = utf8.encode(passphrase);
    return argon2.process(passwordBytes);
  }
}
