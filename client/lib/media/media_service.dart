import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';
import 'dart:convert';
import '../network/api_client.dart';
import 'media_crypto_engine.dart';
import '../services/chat_repository.dart';
import 'package:flutter/foundation.dart';

class MediaService {
  final ApiClient apiClient;
  final ChatRepository chatRepository;

  MediaService(this.apiClient, this.chatRepository);

  Future<void> sendImage(String recipientId, File imageFile, {String? msgId}) async {
    try {
      // 1. Get pre-signed upload URL from backend
      final mimeType = 'image/jpeg'; // Simplification for now
      final uploadInfo = await apiClient.getUploadUrl(imageFile.lengthSync(), mimeType, recipientId);
      
      final uploadUrl = uploadInfo['upload_url'];
      final s3Key = uploadInfo['s3_key'];

      // 2. Encrypt the file
      final tempDir = await getTemporaryDirectory();
      final encryptionResult = await MediaCryptoEngine.encryptFile(imageFile, tempDir.path);

      // 3. Upload encrypted blob to S3 via StreamedRequest
      final uploadRequest = http.StreamedRequest('PUT', Uri.parse(uploadUrl));
      uploadRequest.headers['Content-Type'] = mimeType;
      uploadRequest.headers['Content-Length'] = encryptionResult.encryptedFile.lengthSync().toString();
      
      final fileStream = encryptionResult.encryptedFile.openRead();
      fileStream.listen(
        (chunk) => uploadRequest.sink.add(chunk),
        onDone: () => uploadRequest.sink.close(),
        onError: (e) => uploadRequest.sink.addError(e),
      );

      final uploadResponse = await uploadRequest.send();
      if (uploadResponse.statusCode != 200) {
        throw Exception('Failed to upload to S3: ${uploadResponse.statusCode}');
      }

      // 4. Construct metadata and send via Signal
      final metadata = {
        'type': 'media',
        's3_key': s3Key,
        'aes_key': encryptionResult.aesKeyBase64,
        'iv': encryptionResult.ivBase64,
        'mime': mimeType,
        'size': imageFile.lengthSync(),
      };

      await chatRepository.enqueueMessage(recipientId, json.encode(metadata), msgId: msgId);
      
      // Cleanup encrypted temp file
      await encryptionResult.encryptedFile.delete();
    } catch (e) {
      debugPrint('Error sending image: $e');
      rethrow;
    }
  }

  Future<String> downloadImage(Map<String, dynamic> metadata) async {
    try {
      final s3Key = metadata['s3_key'];
      final aesKey = metadata['aes_key'];
      final iv = metadata['iv'];

      // 1. Get pre-signed download URL from backend
      final downloadInfo = await apiClient.getDownloadUrl(s3Key);
      final downloadUrl = downloadInfo['download_url'];

      // 2. Download the encrypted blob via stream
      final tempDir = await getTemporaryDirectory();
      final safeKey = s3Key.toString().replaceAll('/', '_');
      final encryptedFilePath = '${tempDir.path}/$safeKey.enc';
      final encryptedFile = File(encryptedFilePath);

      final downloadRequest = http.Request('GET', Uri.parse(downloadUrl));
      final response = await http.Client().send(downloadRequest);
      
      if (response.statusCode != 200) {
        throw Exception('Failed to download from S3: ${response.statusCode}');
      }
      
      final sink = encryptedFile.openWrite();
      await response.stream.pipe(sink);

      // 3. Decrypt the file
      final decryptedFilePath = '${tempDir.path}/$safeKey.jpg';
      await MediaCryptoEngine.decryptFile(encryptedFile, aesKey, iv, decryptedFilePath);

      // Cleanup encrypted blob
      await encryptedFile.delete();

      return decryptedFilePath;
    } catch (e) {
      debugPrint('Error downloading image: $e');
      rethrow;
    }
  }
}
