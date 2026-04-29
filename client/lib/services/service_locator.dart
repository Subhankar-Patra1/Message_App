import 'dart:async';
import '../config.dart';
import '../auth/session_manager.dart';
import '../crypto/hive_signal_store.dart';
import '../crypto/crypto_engine.dart';
import '../network/api_client.dart';
import 'chat_repository.dart';

class ServiceLocator {
  static ChatRepository? chatRepository;
  static Completer<void>? _initCompleter;

  static Future<void> init() async {
    if (chatRepository != null) return;
    if (_initCompleter != null) return _initCompleter!.future;
    
    _initCompleter = Completer<void>();

    try {
      final token = await SessionManager.getToken();
      final userId = await SessionManager.getUserId() ?? AppConfig.myUserId;

      if (token == null) return; // Not logged in

      final store = HiveSignalStore();
      await store.init();
      final cryptoEngine = CryptoEngine(store);
      
      final apiClient = ApiClient(baseUrl: AppConfig.apiBaseUrl);

      chatRepository = ChatRepository(
        cryptoEngine: cryptoEngine,
        apiClient: apiClient,
        myUserId: userId,
        socketUrl: AppConfig.socketUrl,
        authToken: token,
      );

      await chatRepository!.initialize();
      _initCompleter!.complete();
    } catch (e) {
      _initCompleter!.completeError(e);
      _initCompleter = null;
      rethrow;
    }
  }

  static Future<ChatRepository?> getRepoAsync() async {
    await init();
    return chatRepository;
  }

  static void dispose() {
    chatRepository?.dispose();
    chatRepository = null;
  }
}
