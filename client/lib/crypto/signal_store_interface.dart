import 'package:libsignal_protocol_dart/libsignal_protocol_dart.dart';

abstract class SignalStoreInterface
    implements
        IdentityKeyStore,
        PreKeyStore,
        SignedPreKeyStore,
        SessionStore,
        SenderKeyStore {
  
  Future<void> init();
  Future<void> clear();
  
  Future<void> storeIdentityKeyPair(IdentityKeyPair identityKeyPair);
  Future<void> storeLocalRegistrationId(int registrationId);
}
