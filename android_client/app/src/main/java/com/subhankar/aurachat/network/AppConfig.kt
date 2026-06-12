package com.subhankar.aurachat.network

/**
 * App-wide configuration — replaces Dart config.dart.
 *
 * In production, these would come from BuildConfig fields
 * or a secure configuration file. For development, they are hardcoded.
 */
object AppConfig {
    // Change this IP to your machine's local IP for development
    var serverIp: String = "10.75.72.109"

    val apiBaseUrl: String get() = "http://$serverIp:4000"
    val socketUrl: String get() = "ws://$serverIp:4000"
    val socketEndpoint: String get() = "$socketUrl/socket/websocket"
}
