import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:libsignal_protocol_dart/libsignal_protocol_dart.dart';
import 'signal_store_interface.dart';
import 'crypto_contract_validator.dart';

class CryptoEngine {
  final SignalStoreInterface store;
  
  CryptoEngine(this.store);

  Future<Map<String, dynamic>> generateInitialKeys() async {
    IdentityKeyPair identityKeyPair;
    int registrationId;

    try {
      identityKeyPair = await store.getIdentityKeyPair();
      registrationId = await store.getLocalRegistrationId();
    } catch (e) {
      identityKeyPair = generateIdentityKeyPair();
      registrationId = generateRegistrationId(false);
      await store.storeIdentityKeyPair(identityKeyPair);
      await store.storeLocalRegistrationId(registrationId);
    }

    // Generate Signed PreKey
    final signedPreKey = generateSignedPreKey(identityKeyPair, 1);
    await store.storeSignedPreKey(signedPreKey.id, signedPreKey);
    
    //final preKeys = generatePreKeys(0, 100); 

    // Generate One-Time PreKeys (Reduced from 100 to 10 for faster Web initialization)
    final preKeys = generatePreKeys(0, 10);
    for (var pk in preKeys) {
      await store.storePreKey(pk.id, pk);
    }

    String stripTypeByte(List<int> bytes) => 
        base64UrlEncode(bytes.sublist(1)).replaceAll('=', '');

    final identityPublic = identityKeyPair.getPublicKey().serialize();
    final identitySignature = Curve.calculateSignature(
      identityKeyPair.getPrivateKey(), 
      identityPublic.sublist(1)
    );

    return {
      'identity_key': {
        'public': stripTypeByte(identityPublic),
        'signature': base64UrlEncode(identitySignature).replaceAll('=', '')
      },
      'signed_pre_key': {
        'key_id': signedPreKey.id,
        'public': stripTypeByte(signedPreKey.getKeyPair().publicKey.serialize()),
        'signature': base64UrlEncode(signedPreKey.signature).replaceAll('=', '')
      },
      'pre_keys': preKeys.map((pk) => {
        'key_id': pk.id,
        'public': stripTypeByte(pk.getKeyPair().publicKey.serialize())
      }).toList(),
    };
  }

  Future<void> establishSession(String recipientId, Map<String, dynamic> bundle) async {
    final address = SignalProtocolAddress(recipientId, 1);

    CryptoContractValidator.validatePublicKeyFormat(bundle['identity_key']['public']);
    final identityKey = IdentityKey(Curve.decodePoint(_decodeWithPrefix(bundle['identity_key']['public']), 0));

    ECPublicKey? signedPreKey;
    int? signedPreKeyId;
    List<int>? signature;
    if (bundle['signed_pre_key'] != null) {
      CryptoContractValidator.validatePublicKeyFormat(bundle['signed_pre_key']['public']);
      CryptoContractValidator.validateSignatureFormat(bundle['signed_pre_key']['signature']);
      
      signedPreKey = Curve.decodePoint(_decodeWithPrefix(bundle['signed_pre_key']['public']), 0);
      signedPreKeyId = bundle['signed_pre_key']['key_id'];
      signature = base64Url.decode(_padBase64(bundle['signed_pre_key']['signature']));
    }

    ECPublicKey? preKey;
    int? preKeyId;
    if (bundle['pre_keys'] != null && (bundle['pre_keys'] as List).isNotEmpty) {
      final pk = bundle['pre_keys'].first;
      CryptoContractValidator.validatePublicKeyFormat(pk['public']);
      preKeyId = pk['key_id'];
      preKey = Curve.decodePoint(_decodeWithPrefix(pk['public']), 0);
    }

    final preKeyBundle = PreKeyBundle(
      0, // registrationId
      1, // deviceId
      preKeyId,
      preKey,
      signedPreKeyId!,
      signedPreKey!,
      Uint8List.fromList(signature!),
      identityKey,
    );

    final sessionBuilder = SessionBuilder(store, store, store, store, address);
    await sessionBuilder.processPreKeyBundle(preKeyBundle);
  }

  Future<Map<String, dynamic>> encryptMessage(String recipientId, String plaintext) async {
    final address = SignalProtocolAddress(recipientId, 1);
    final sessionRecord = await store.loadSession(address);
    final identityKeyPair = await store.getIdentityKeyPair();
    final regId = await store.getLocalRegistrationId();

    final result = await compute(_encryptMessageIsolated, {
      'sessionRecordBytes': sessionRecord.serialize(),
      'identityPubKey': identityKeyPair.getPublicKey().serialize(),
      'identityPrivKey': identityKeyPair.getPrivateKey().serialize(),
      'registrationId': regId,
      'recipientId': recipientId,
      'plaintext': plaintext,
    });
    
    await store.storeSession(address, SessionRecord.fromSerialized(result['newSessionRecordBytes']));
    
    return {
      'type': result['type'],
      'ciphertext': result['ciphertext'],
    };
  }

  static Future<Map<String, dynamic>> _encryptMessageIsolated(Map<String, dynamic> args) async {
    final plaintext = args['plaintext'] as String;
    final recipientId = args['recipientId'] as String;
    final sessionRecordBytes = args['sessionRecordBytes'] as List<int>;
    final pubKey = args['identityPubKey'] as List<int>;
    final privKey = args['identityPrivKey'] as List<int>;
    final registrationId = args['registrationId'] as int;

    final identityKeyPair = IdentityKeyPair(IdentityKey(Curve.decodePoint(Uint8List.fromList(pubKey), 0)), Curve.decodePrivatePoint(Uint8List.fromList(privKey)));
    final inMemoryStore = InMemorySignalProtocolStore(identityKeyPair, registrationId);
    
    final address = SignalProtocolAddress(recipientId, 1);
    await inMemoryStore.storeSession(address, SessionRecord.fromSerialized(Uint8List.fromList(sessionRecordBytes)));

    final sessionCipher = SessionCipher(inMemoryStore, inMemoryStore, inMemoryStore, inMemoryStore, address);
    final ciphertextMessage = await sessionCipher.encrypt(Uint8List.fromList(utf8.encode(plaintext)));
    
    final newSession = await inMemoryStore.loadSession(address);

    return {
      'type': ciphertextMessage.getType(),
      'ciphertext': base64UrlEncode(ciphertextMessage.serialize()).replaceAll('=', ''),
      'newSessionRecordBytes': newSession.serialize(),
    };
  }

  Future<String> decryptMessage(String senderId, Map<String, dynamic> encryptedPayload) async {
    final address = SignalProtocolAddress(senderId, 1);
    final type = encryptedPayload['type'] as int;
    final ciphertextBytes = base64Url.decode(_padBase64(encryptedPayload['ciphertext']));
    
    // For PreKeySignalMessage, we run on main thread to allow PreKeyStore deletions.
    // For normal SignalMessage, we could use compute() but running on main thread 
    // for decryption is often fast enough. We'll use compute for normal messages.
    try {
      if (type == CiphertextMessage.prekeyType) {
        final sessionCipher = SessionCipher(store, store, store, store, address);
        final plaintext = await sessionCipher.decrypt(PreKeySignalMessage(ciphertextBytes));
        return utf8.decode(plaintext);
      } else {
        final sessionRecord = await store.loadSession(address);
        final identityKeyPair = await store.getIdentityKeyPair();
        final regId = await store.getLocalRegistrationId();

        final result = await compute(_decryptMessageIsolated, {
          'sessionRecordBytes': sessionRecord.serialize(),
          'identityPubKey': identityKeyPair.getPublicKey().serialize(),
          'identityPrivKey': identityKeyPair.getPrivateKey().serialize(),
          'registrationId': regId,
          'senderId': senderId,
          'ciphertextBytes': ciphertextBytes,
        });

        await store.storeSession(address, SessionRecord.fromSerialized(result['newSessionRecordBytes']));
        return result['plaintext'];
      }
    } catch (e) {
      // SECURITY: Never log plaintext or keys
      rethrow;
    }
  }

  static Future<Map<String, dynamic>> _decryptMessageIsolated(Map<String, dynamic> args) async {
    final senderId = args['senderId'] as String;
    final sessionRecordBytes = args['sessionRecordBytes'] as List<int>;
    final pubKey = args['identityPubKey'] as List<int>;
    final privKey = args['identityPrivKey'] as List<int>;
    final registrationId = args['registrationId'] as int;
    final ciphertextBytes = args['ciphertextBytes'] as List<int>;

    final identityKeyPair = IdentityKeyPair(IdentityKey(Curve.decodePoint(Uint8List.fromList(pubKey), 0)), Curve.decodePrivatePoint(Uint8List.fromList(privKey)));
    final inMemoryStore = InMemorySignalProtocolStore(identityKeyPair, registrationId);
    
    final address = SignalProtocolAddress(senderId, 1);
    await inMemoryStore.storeSession(address, SessionRecord.fromSerialized(Uint8List.fromList(sessionRecordBytes)));

    final sessionCipher = SessionCipher(inMemoryStore, inMemoryStore, inMemoryStore, inMemoryStore, address);
    final plaintext = await sessionCipher.decryptFromSignal(SignalMessage.fromSerialized(Uint8List.fromList(ciphertextBytes)));
    
    final newSession = await inMemoryStore.loadSession(address);

    return {
      'plaintext': utf8.decode(plaintext),
      'newSessionRecordBytes': newSession.serialize(),
    };
  }

  // --- Group Messaging (Sender Key Protocol) ---

  Future<String> generateSenderKeyDistributionMessage(String groupId, String senderId) async {
    final senderKeyName = SenderKeyName(groupId, SignalProtocolAddress(senderId, 1));
    final builder = GroupSessionBuilder(store as SenderKeyStore);
    final distributionMessage = await builder.create(senderKeyName);
    return base64UrlEncode(distributionMessage.serialize()).replaceAll('=', '');
  }

  Future<void> processSenderKeyDistribution(String groupId, String senderId, String distributionMessageB64) async {
    final senderKeyName = SenderKeyName(groupId, SignalProtocolAddress(senderId, 1));
    final builder = GroupSessionBuilder(store as SenderKeyStore);
    final bytes = base64Url.decode(_padBase64(distributionMessageB64));
    final distributionMessage = SenderKeyDistributionMessageWrapper.fromSerialized(Uint8List.fromList(bytes));
    await builder.process(senderKeyName, distributionMessage);
  }

  Future<String> rotateSenderKey(String groupId, String senderId) async {
    final senderKeyName = SenderKeyName(groupId, SignalProtocolAddress(senderId, 1));
    // Overwrite with empty record to force new key generation
    await (store as SenderKeyStore).storeSenderKey(senderKeyName, SenderKeyRecord());
    return await generateSenderKeyDistributionMessage(groupId, senderId);
  }

  Future<String> encryptGroupMessage(String groupId, String senderId, String plaintext) async {
    final senderKeyName = SenderKeyName(groupId, SignalProtocolAddress(senderId, 1));
    final cipher = GroupCipher(store as SenderKeyStore, senderKeyName);
    final ciphertextMessage = await cipher.encrypt(Uint8List.fromList(utf8.encode(plaintext)));
    return base64UrlEncode(ciphertextMessage).replaceAll('=', '');
  }

  Future<String> decryptGroupMessage(String groupId, String senderId, String ciphertextB64) async {
    final senderKeyName = SenderKeyName(groupId, SignalProtocolAddress(senderId, 1));
    final cipher = GroupCipher(store as SenderKeyStore, senderKeyName);
    final bytes = base64Url.decode(_padBase64(ciphertextB64));
    final plaintextBytes = await cipher.decrypt(Uint8List.fromList(bytes));
    return utf8.decode(plaintextBytes);
  }

  Uint8List _decodeWithPrefix(String b64) {
    final decoded = base64Url.decode(_padBase64(b64));
    final prefixed = Uint8List(33);
    prefixed[0] = 0x05;
    prefixed.setRange(1, 33, decoded);
    return prefixed;
  }

  String _padBase64(String b64) {
    String padded = b64;
    while (padded.length % 4 != 0) {
      padded += '=';
    }
    return padded;
  }
}
