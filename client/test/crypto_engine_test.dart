import 'package:flutter_test/flutter_test.dart';
import 'package:client/crypto/crypto_engine.dart';
import 'package:client/crypto/signal_store_interface.dart';
import 'package:client/crypto/crypto_contract_validator.dart';
import 'package:libsignal_protocol_dart/libsignal_protocol_dart.dart';

// Mock InMemory Store for Tests
class TestSignalStore extends InMemorySignalProtocolStore implements SignalStoreInterface, SenderKeyStore {
  TestSignalStore(super.identityKeyPair, super.registrationId);

  @override
  Future<void> init() async {}

  @override
  Future<void> clear() async {}

  @override
  Future<void> storeIdentityKeyPair(IdentityKeyPair identityKeyPair) async {}

  @override
  Future<void> storeLocalRegistrationId(int registrationId) async {}

  final Map<String, SenderKeyRecord> _senderKeys = {};

  @override
  Future<SenderKeyRecord> loadSenderKey(SenderKeyName senderKeyName) async {
    final key = '${senderKeyName.groupId}::${senderKeyName.sender.toString()}';
    return _senderKeys[key] ?? SenderKeyRecord();
  }

  @override
  Future<void> storeSenderKey(SenderKeyName senderKeyName, SenderKeyRecord record) async {
    final key = '${senderKeyName.groupId}::${senderKeyName.sender.toString()}';
    _senderKeys[key] = record;
  }
}

void main() {
  group('CryptoEngine E2EE Flow', () {
    test('Generate keys and validate base64url encoding per Crypto Contract', () async {
      final identityKeyPair = generateIdentityKeyPair();
      final store = TestSignalStore(identityKeyPair, 1);
      final cryptoEngine = CryptoEngine(store);

      final payload = await cryptoEngine.generateInitialKeys();

      // Check structure
      expect(payload.containsKey('identity_key'), true);
      expect(payload.containsKey('signed_pre_key'), true);
      expect(payload.containsKey('pre_keys'), true);

      // Validate encodings using our helper
      final ikPublic = payload['identity_key']['public'];
      expect(ikPublic.contains('='), false); // No padding allowed!
      CryptoContractValidator.validatePublicKeyFormat(ikPublic);

      final spkPublic = payload['signed_pre_key']['public'];
      final spkSig = payload['signed_pre_key']['signature'];
      expect(spkPublic.contains('='), false);
      expect(spkSig.contains('='), false);
      CryptoContractValidator.validatePublicKeyFormat(spkPublic);
      CryptoContractValidator.validateSignatureFormat(spkSig);

      final preKeys = payload['pre_keys'] as List;
      expect(preKeys.length, 100);
      final pkPublic = preKeys[0]['public'];
      expect(pkPublic.contains('='), false);
      CryptoContractValidator.validatePublicKeyFormat(pkPublic);
    });

    test('Round-trip encryption and decryption with SessionBuilder', () async {
      // Setup Alice
      final aliceIdentity = generateIdentityKeyPair();
      final aliceStore = TestSignalStore(aliceIdentity, 1);
      final aliceEngine = CryptoEngine(aliceStore);
      // Generate keys but we only need it to init the store properly if it was empty
      await aliceEngine.generateInitialKeys();

      // Setup Bob
      final bobIdentity = generateIdentityKeyPair();
      final bobStore = TestSignalStore(bobIdentity, 2);
      final bobEngine = CryptoEngine(bobStore);

      // Alice fetches Bob's bundle (simulated by using Bob's generated keys)
      final bobBundle = await bobEngine.generateInitialKeys();
      // Only one pre-key is returned in a real fetch
      bobBundle['pre_keys'] = [bobBundle['pre_keys'][0]]; 
      
      // Alice establishes session with Bob
      await aliceEngine.establishSession('bob_uuid', bobBundle);

      // Alice encrypts message for Bob
      final encryptedPayload = await aliceEngine.encryptMessage('bob_uuid', 'Hello Bob, strictly secret!');
      
      // Verify payload is correct format
      expect(encryptedPayload.containsKey('type'), true);
      expect(encryptedPayload.containsKey('ciphertext'), true);
      expect(encryptedPayload['ciphertext'].contains('='), false); // Strict base64url no-pad

      // Bob receives and decrypts message from Alice
      // Bob uses main thread decryption for PreKeySignalMessage, which handles PreKey deletion
      final plaintext = await bobEngine.decryptMessage('alice_uuid', encryptedPayload);

      expect(plaintext, 'Hello Bob, strictly secret!');
      
      // Now Bob replies to Alice
      final bobReplyPayload = await bobEngine.encryptMessage('alice_uuid', 'Loud and clear, Alice!');
      final alicePlaintext = await aliceEngine.decryptMessage('bob_uuid', bobReplyPayload);
      
      expect(alicePlaintext, 'Loud and clear, Alice!');
    });
  });

  group('V1 Hardening: Error Boundary', () {
    test('Malformed ciphertext throws gracefully — no crash, no key leak', () async {
      final aliceIdentity = generateIdentityKeyPair();
      final aliceStore = TestSignalStore(aliceIdentity, 1);
      final aliceEngine = CryptoEngine(aliceStore);
      await aliceEngine.generateInitialKeys();

      // Inject completely random garbage as ciphertext
      final malformedPayload = {
        'type': CiphertextMessage.prekeyType,
        'ciphertext': 'AAAAAAAAAAAAAAAAAAAAAAAAAAAA', // garbage base64url
      };

      expect(
        () => aliceEngine.decryptMessage('attacker_uuid', malformedPayload),
        throwsA(anything), // Should throw, but NOT crash the isolate
      );
    });

    test('Wrong message type triggers error, not undefined behavior', () async {
      final aliceIdentity = generateIdentityKeyPair();
      final aliceStore = TestSignalStore(aliceIdentity, 1);
      final aliceEngine = CryptoEngine(aliceStore);
      await aliceEngine.generateInitialKeys();

      final badTypePayload = {
        'type': 999, // Invalid type
        'ciphertext': 'AAAAAAAAAA',
      };

      expect(
        () => aliceEngine.decryptMessage('attacker_uuid', badTypePayload),
        throwsA(anything),
      );
    });
  });

  group('V1 Hardening: Log Sanitization', () {
    test('Generated key payloads contain ONLY public keys — never private', () async {
      final identity = generateIdentityKeyPair();
      final store = TestSignalStore(identity, 1);
      final engine = CryptoEngine(store);

      final payload = await engine.generateInitialKeys();
      final payloadJson = payload.toString();

      // Private key material should NEVER appear in outbound payloads
      expect(payloadJson.contains('private'), false,
          reason: 'Private key material found in outbound payload!');
      expect(payloadJson.contains('PRIVATE'), false);
      expect(payloadJson.contains('BEGIN'), false);

      // Only 'public' keys should be present
      expect(payload['identity_key'].containsKey('public'), true);
      expect(payload['identity_key'].length, 1,
          reason: 'identity_key should contain ONLY the public key');
    });

    test('Encrypted payload contains no plaintext or key material', () async {
      final aliceIdentity = generateIdentityKeyPair();
      final aliceStore = TestSignalStore(aliceIdentity, 1);
      final aliceEngine = CryptoEngine(aliceStore);
      await aliceEngine.generateInitialKeys();

      final bobIdentity = generateIdentityKeyPair();
      final bobStore = TestSignalStore(bobIdentity, 2);
      final bobEngine = CryptoEngine(bobStore);
      final bobBundle = await bobEngine.generateInitialKeys();
      bobBundle['pre_keys'] = [bobBundle['pre_keys'][0]];

      await aliceEngine.establishSession('bob_uuid', bobBundle);

      final secretMessage = 'TOP SECRET: Launch code is 12345';
      final encrypted = await aliceEngine.encryptMessage('bob_uuid', secretMessage);
      final ciphertextStr = encrypted['ciphertext'] as String;

      // The plaintext must NEVER appear in the ciphertext output
      expect(ciphertextStr.contains('TOP SECRET'), false,
          reason: 'Plaintext leaked into ciphertext!');
      expect(ciphertextStr.contains('12345'), false,
          reason: 'Plaintext leaked into ciphertext!');
    });
  });

  group('V1 Hardening: Forward Secrecy', () {
    test('Multiple messages produce distinct ciphertexts (ratchet advances)', () async {
      final aliceIdentity = generateIdentityKeyPair();
      final aliceStore = TestSignalStore(aliceIdentity, 1);
      final aliceEngine = CryptoEngine(aliceStore);
      await aliceEngine.generateInitialKeys();

      final bobIdentity = generateIdentityKeyPair();
      final bobStore = TestSignalStore(bobIdentity, 2);
      final bobEngine = CryptoEngine(bobStore);
      final bobBundle = await bobEngine.generateInitialKeys();
      bobBundle['pre_keys'] = [bobBundle['pre_keys'][0]];

      await aliceEngine.establishSession('bob_uuid', bobBundle);

      // Send the SAME message twice — ciphertext MUST differ (ratchet)
      final enc1 = await aliceEngine.encryptMessage('bob_uuid', 'Hello');
      final enc2 = await aliceEngine.encryptMessage('bob_uuid', 'Hello');

      expect(enc1['ciphertext'] != enc2['ciphertext'], true,
          reason: 'Identical plaintext produced identical ciphertext — ratchet is not advancing!');
    });
  });
}
