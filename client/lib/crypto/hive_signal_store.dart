import 'dart:typed_data';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:libsignal_protocol_dart/libsignal_protocol_dart.dart';
import 'signal_store_interface.dart';
import 'dart:convert';

class HiveSignalStore implements SignalStoreInterface, SenderKeyStore {
  final FlutterSecureStorage _secureStorage = const FlutterSecureStorage();
  
  late Box<String> _identityBox;
  late Box<String> _sessionBox;
  late Box<String> _preKeyBox;
  late Box<String> _signedPreKeyBox;
  late Box<String> _senderKeyBox;

  @override
  Future<void> init() async {
    await Hive.initFlutter();
    
    // Setup encryption key
    final encryptionKeyString = await _secureStorage.read(key: 'hive_encryption_key');
    List<int> encryptionKey;
    if (encryptionKeyString == null) {
      encryptionKey = Hive.generateSecureKey();
      await _secureStorage.write(
        key: 'hive_encryption_key', 
        value: base64UrlEncode(encryptionKey).replaceAll('=', '')
      );
    } else {
      // Pad to parse if needed
      String padded = encryptionKeyString;
      while (padded.length % 4 != 0) {
        padded += '=';
      }
      encryptionKey = base64Url.decode(padded);
    }

    final cipher = HiveAesCipher(encryptionKey);
    
    _identityBox = await Hive.openBox<String>('identity', encryptionCipher: cipher);
    _sessionBox = await Hive.openBox<String>('sessions', encryptionCipher: cipher);
    _preKeyBox = await Hive.openBox<String>('prekeys', encryptionCipher: cipher);
    _signedPreKeyBox = await Hive.openBox<String>('signed_prekeys', encryptionCipher: cipher);
    _senderKeyBox = await Hive.openBox<String>('sender_keys', encryptionCipher: cipher);
  }

  @override
  Future<void> clear() async {
    await _identityBox.clear();
    await _sessionBox.clear();
    await _preKeyBox.clear();
    await _signedPreKeyBox.clear();
    await _senderKeyBox.clear();
  }

  // --- IdentityKeyStore ---

  @override
  Future<IdentityKeyPair> getIdentityKeyPair() async {
    final pubKeyStr = _identityBox.get('identity_public');
    final privKeyStr = await _secureStorage.read(key: 'identity_private');
    
    if (pubKeyStr == null || privKeyStr == null) {
      throw Exception('Identity keys not found');
    }
    
    return IdentityKeyPair(
      IdentityKey(Curve.decodePoint(Uint8List.fromList(_decodeB64(pubKeyStr)), 0)),
      Curve.decodePrivatePoint(Uint8List.fromList(_decodeB64(privKeyStr)))
    );
  }

  @override
  Future<int> getLocalRegistrationId() async {
    final id = _identityBox.get('registration_id');
    return id != null ? int.parse(id) : 0;
  }

  @override
  Future<bool> saveIdentity(SignalProtocolAddress address, IdentityKey? identityKey) async {
    if (identityKey == null) {
      await _identityBox.delete('contact_${address.getName()}');
      return true;
    }
    
    final keyStr = _encodeB64(identityKey.serialize());
    final existing = _identityBox.get('contact_${address.getName()}');
    if (existing != keyStr) {
      await _identityBox.put('contact_${address.getName()}', keyStr);
      return true;
    }
    return false;
  }

  @override
  Future<bool> isTrustedIdentity(SignalProtocolAddress address, IdentityKey? identityKey, Direction direction) async {
    if (identityKey == null) return false;
    final trusted = _identityBox.get('contact_${address.getName()}');
    if (trusted == null) return true; // Trust on first use
    return trusted == _encodeB64(identityKey.serialize());
  }

  @override
  Future<IdentityKey?> getIdentity(SignalProtocolAddress address) async {
    final trusted = _identityBox.get('contact_${address.getName()}');
    if (trusted == null) return null;
    return IdentityKey(Curve.decodePoint(Uint8List.fromList(_decodeB64(trusted)), 0));
  }

  @override
  Future<void> storeIdentityKeyPair(IdentityKeyPair identityKeyPair) async {
    await _identityBox.put('identity_public', _encodeB64(identityKeyPair.getPublicKey().serialize()));
    // SECURITY: Private keys only in secure_storage
    await _secureStorage.write(
      key: 'identity_private', 
      value: _encodeB64(identityKeyPair.getPrivateKey().serialize())
    );
  }

  @override
  Future<void> storeLocalRegistrationId(int registrationId) async {
    await _identityBox.put('registration_id', registrationId.toString());
  }

  // --- SessionStore ---

  @override
  Future<SessionRecord> loadSession(SignalProtocolAddress address) async {
    final recordStr = _sessionBox.get(address.toString());
    if (recordStr != null) {
      return SessionRecord.fromSerialized(Uint8List.fromList(_decodeB64(recordStr)));
    }
    return SessionRecord();
  }

  @override
  Future<void> storeSession(SignalProtocolAddress address, SessionRecord record) async {
    await _sessionBox.put(address.toString(), _encodeB64(record.serialize()));
  }

  @override
  Future<bool> containsSession(SignalProtocolAddress address) async {
    return _sessionBox.containsKey(address.toString());
  }

  @override
  Future<void> deleteSession(SignalProtocolAddress address) async {
    await _sessionBox.delete(address.toString());
  }

  @override
  Future<void> deleteAllSessions(String name) async {
    final keys = _sessionBox.keys.where((k) => k.toString().startsWith(name)).toList();
    for (var k in keys) {
      await _sessionBox.delete(k);
    }
  }

  @override
  Future<List<int>> getSubDeviceSessions(String name) async {
    return [];
  }

  // --- PreKeyStore ---

  @override
  Future<PreKeyRecord> loadPreKey(int preKeyId) async {
    final recordStr = _preKeyBox.get(preKeyId.toString());
    if (recordStr == null) throw InvalidKeyIdException('No such prekey');
    return PreKeyRecord.fromBuffer(Uint8List.fromList(_decodeB64(recordStr)));
  }

  @override
  Future<void> storePreKey(int preKeyId, PreKeyRecord record) async {
    await _preKeyBox.put(preKeyId.toString(), _encodeB64(record.serialize()));
  }

  @override
  Future<bool> containsPreKey(int preKeyId) async {
    return _preKeyBox.containsKey(preKeyId.toString());
  }

  @override
  Future<void> removePreKey(int preKeyId) async {
    await _preKeyBox.delete(preKeyId.toString());
  }

  // --- SignedPreKeyStore ---

  @override
  Future<SignedPreKeyRecord> loadSignedPreKey(int signedPreKeyId) async {
    final recordStr = _signedPreKeyBox.get(signedPreKeyId.toString());
    if (recordStr == null) throw InvalidKeyIdException('No such signed prekey');
    return SignedPreKeyRecord.fromSerialized(Uint8List.fromList(_decodeB64(recordStr)));
  }

  @override
  Future<void> storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) async {
    await _signedPreKeyBox.put(signedPreKeyId.toString(), _encodeB64(record.serialize()));
  }

  @override
  Future<List<SignedPreKeyRecord>> loadSignedPreKeys() async {
    return _signedPreKeyBox.values.map((v) => SignedPreKeyRecord.fromSerialized(Uint8List.fromList(_decodeB64(v)))).toList();
  }

  @override
  Future<bool> containsSignedPreKey(int signedPreKeyId) async {
    return _signedPreKeyBox.containsKey(signedPreKeyId.toString());
  }

  @override
  Future<void> removeSignedPreKey(int signedPreKeyId) async {
    await _signedPreKeyBox.delete(signedPreKeyId.toString());
  }
  
  // --- SenderKeyStore ---

  @override
  Future<SenderKeyRecord> loadSenderKey(SenderKeyName senderKeyName) async {
    final key = '${senderKeyName.groupId}::${senderKeyName.sender.toString()}';
    final recordStr = _senderKeyBox.get(key);
    if (recordStr != null) {
      return SenderKeyRecord.fromSerialized(Uint8List.fromList(_decodeB64(recordStr)));
    }
    return SenderKeyRecord();
  }

  @override
  Future<void> storeSenderKey(SenderKeyName senderKeyName, SenderKeyRecord record) async {
    final key = '${senderKeyName.groupId}::${senderKeyName.sender.toString()}';
    await _senderKeyBox.put(key, _encodeB64(record.serialize()));
  }

  // Helpers
  String _encodeB64(List<int> bytes) => base64UrlEncode(bytes).replaceAll('=', '');
  List<int> _decodeB64(String b64) {
    String padded = b64;
    while (padded.length % 4 != 0) {
      padded += '=';
    }
    return base64Url.decode(padded);
  }
}
