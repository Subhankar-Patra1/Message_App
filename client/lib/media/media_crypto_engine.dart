import 'dart:io';
import 'package:cryptography/cryptography.dart';
import 'dart:convert';
import 'package:flutter/foundation.dart';

class MediaEncryptionResult {
  final File encryptedFile;
  final String aesKeyBase64;
  final String ivBase64;

  MediaEncryptionResult({
    required this.encryptedFile,
    required this.aesKeyBase64,
    required this.ivBase64,
  });
}

class _EncryptRequest {
  final String sourcePath;
  final String destPath;
  final List<int> keyBytes;
  final List<int> nonceBytes;

  _EncryptRequest(this.sourcePath, this.destPath, this.keyBytes, this.nonceBytes);
}

class _DecryptRequest {
  final String sourcePath;
  final String destPath;
  final List<int> keyBytes;
  final List<int> nonceBytes;

  _DecryptRequest(this.sourcePath, this.destPath, this.keyBytes, this.nonceBytes);
}

class MediaCryptoEngine {
  static const int _chunkSize = 1024 * 1024; // 1MB chunks

  static Uint8List _getChunkNonce(List<int> baseNonce, int chunkIndex) {
    final nonce = Uint8List.fromList(baseNonce);
    if (nonce.length < 12) {
      // Pad to 12 bytes if somehow shorter
      final padded = Uint8List(12);
      padded.setAll(0, nonce);
      nonce.setAll(0, padded);
    }
    final byteData = ByteData.view(nonce.buffer);
    final current = byteData.getUint32(8, Endian.big);
    byteData.setUint32(8, current + chunkIndex, Endian.big);
    return nonce;
  }

  static Future<void> _encryptIsolate(_EncryptRequest req) async {
    final algorithm = AesGcm.with256bits();
    final secretKey = SecretKey(req.keyBytes);
    
    final sourceFile = File(req.sourcePath);
    final destFile = File(req.destPath);
    
    final reader = sourceFile.openSync(mode: FileMode.read);
    final writer = destFile.openSync(mode: FileMode.write);

    try {
      int chunkIndex = 0;
      while (true) {
        final buffer = reader.readSync(_chunkSize);
        if (buffer.isEmpty) break;
        
        final chunkNonce = _getChunkNonce(req.nonceBytes, chunkIndex);
        final secretBox = await algorithm.encrypt(
          buffer,
          secretKey: secretKey,
          nonce: chunkNonce,
        );
        
        final encryptedChunk = secretBox.concatenation();
        final sizeBytes = ByteData(4)..setUint32(0, encryptedChunk.length, Endian.big);
        writer.writeFromSync(sizeBytes.buffer.asUint8List());
        writer.writeFromSync(encryptedChunk);
        
        chunkIndex++;
        if (buffer.length < _chunkSize) break;
      }
    } finally {
      reader.closeSync();
      writer.closeSync();
      
      // Memory Wipe
      req.keyBytes.fillRange(0, req.keyBytes.length, 0);
      req.nonceBytes.fillRange(0, req.nonceBytes.length, 0);
    }
  }

  static Future<void> _decryptIsolate(_DecryptRequest req) async {
    final algorithm = AesGcm.with256bits();
    final secretKey = SecretKey(req.keyBytes);
    
    final sourceFile = File(req.sourcePath);
    final destFile = File(req.destPath);
    
    final reader = sourceFile.openSync(mode: FileMode.read);
    final writer = destFile.openSync(mode: FileMode.write);

    try {
      int chunkIndex = 0;
      while (true) {
        final sizeBuffer = reader.readSync(4);
        if (sizeBuffer.isEmpty) break;
        if (sizeBuffer.length < 4) throw Exception("Corrupted encrypted file (invalid size block)");

        final encryptedChunkSize = ByteData.view(Uint8List.fromList(sizeBuffer).buffer).getUint32(0, Endian.big);
        final encryptedChunk = reader.readSync(encryptedChunkSize);
        if (encryptedChunk.length < encryptedChunkSize) throw Exception("Corrupted encrypted file (unexpected EOF)");

        final chunkNonce = _getChunkNonce(req.nonceBytes, chunkIndex);
        final secretBox = SecretBox.fromConcatenation(
          encryptedChunk,
          nonceLength: chunkNonce.length,
          macLength: 16, // Default for AES-GCM
        );

        final decryptedBytes = await algorithm.decrypt(
          secretBox,
          secretKey: secretKey,
        );

        writer.writeFromSync(decryptedBytes);
        chunkIndex++;
      }
    } finally {
      reader.closeSync();
      writer.closeSync();
      
      // Memory Wipe
      req.keyBytes.fillRange(0, req.keyBytes.length, 0);
      req.nonceBytes.fillRange(0, req.nonceBytes.length, 0);
    }
  }

  /// Encrypts a file using AES-256-GCM in an isolate.
  static Future<MediaEncryptionResult> encryptFile(File sourceFile, String tempDirPath) async {
    final algorithm = AesGcm.with256bits();
    final secretKey = await algorithm.newSecretKey();
    final nonce = algorithm.newNonce();
    
    final keyBytes = await secretKey.extractBytes();
    
    final destPath = '$tempDirPath/${DateTime.now().millisecondsSinceEpoch}.enc';
    
    final req = _EncryptRequest(
      sourceFile.path,
      destPath,
      List<int>.from(keyBytes),
      List<int>.from(nonce),
    );
    
    await compute(_encryptIsolate, req);

    final aesKeyBase64 = base64Url.encode(keyBytes).replaceAll('=', '');
    final ivBase64 = base64Url.encode(nonce).replaceAll('=', '');
    
    // Explicit wipe in main isolate
    keyBytes.fillRange(0, keyBytes.length, 0);
    nonce.fillRange(0, nonce.length, 0);

    return MediaEncryptionResult(
      encryptedFile: File(destPath),
      aesKeyBase64: aesKeyBase64,
      ivBase64: ivBase64,
    );
  }

  /// Decrypts a file using the provided AES key and IV in an isolate.
  static Future<File> decryptFile(File encryptedFile, String aesKeyBase64, String ivBase64, String outputFilePath) async {
    String normalizeBase64(String str) {
      var padding = str.length % 4;
      if (padding != 0) {
        str += '=' * (4 - padding);
      }
      return str;
    }
    
    final keyBytes = base64Url.decode(normalizeBase64(aesKeyBase64));
    final nonceBytes = base64Url.decode(normalizeBase64(ivBase64));
    
    final req = _DecryptRequest(
      encryptedFile.path,
      outputFilePath,
      List<int>.from(keyBytes),
      List<int>.from(nonceBytes),
    );
    
    await compute(_decryptIsolate, req);
    
    // Explicit wipe in main isolate
    keyBytes.fillRange(0, keyBytes.length, 0);
    nonceBytes.fillRange(0, nonceBytes.length, 0);
    
    return File(outputFilePath);
  }
}
