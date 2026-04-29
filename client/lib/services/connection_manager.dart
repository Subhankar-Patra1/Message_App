import 'dart:async';
import 'dart:math';
import 'package:flutter/widgets.dart';
import 'package:phoenix_socket/phoenix_socket.dart';
import '../network/socket_client.dart';
import '../config.dart';

class ConnectionManager extends WidgetsBindingObserver {
  final SocketClient socketClient;
  
  Timer? _reconnectTimer;
  int _reconnectAttempts = 0;
  final List<DateTime> _recentReconnects = [];
  bool _isDisposed = false;

  ConnectionManager(this.socketClient) {
    WidgetsBinding.instance.addObserver(this);
    
    // Listen to socket state to handle disconnects
    socketClient.connectionState.listen((event) {
      if (event == PhoenixSocketOpenEvent) {
        _reconnectAttempts = 0;
        _recentReconnects.clear();
      }
    });
  }

  Future<void> connect() async {
    if (_isDisposed) return;
    
    try {
      await socketClient.connect();
    } catch (e) {
      debugPrint("Socket connect error: $e");
      _scheduleReconnect();
    }
  }

  void reconnect() {
    if (socketClient.isConnected) return;
    
    // Storm detection: if >5 reconnects in 60s, force 60s backoff
    _recentReconnects.removeWhere((t) => DateTime.now().difference(t).inSeconds > 60);
    _recentReconnects.add(DateTime.now());
    
    if (_recentReconnects.length > 5) {
      debugPrint("Reconnect storm detected. Backing off for 60s.");
      _reconnectTimer?.cancel();
      _reconnectTimer = Timer(const Duration(seconds: 60), connect);
      return;
    }

    _scheduleReconnect();
  }

  void _scheduleReconnect() {
    _reconnectTimer?.cancel();
    
    // Exponential backoff: 1s, 2s, 4s, 8s, max 30s
    final delaySeconds = min(30, pow(2, _reconnectAttempts)).toInt();
    // Jitter: 0-500ms
    final jitter = Random().nextInt(500);
    
    _reconnectAttempts++;
    
    _reconnectTimer = Timer(Duration(seconds: delaySeconds, milliseconds: jitter), () {
      connect();
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      reconnect();
    }
  }

  void dispose() {
    _isDisposed = true;
    _reconnectTimer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    socketClient.disconnect();
  }
}
