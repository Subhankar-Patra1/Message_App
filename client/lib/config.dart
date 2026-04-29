class AppConfig {
  static String serverIp = '10.73.211.109'; 
  static String myUserId = 'Bob'; // Change to 'Bob' for the other device
  
//static String serverIp = '10.0.2.2';  
//  static String myUserId = 'Alice';

  static String get authToken => myUserId; // For dev: Use username as token
  
  static String get apiBaseUrl => 'http://$serverIp:4000';
  static String get socketUrl => 'ws://$serverIp:4000';
} 
  