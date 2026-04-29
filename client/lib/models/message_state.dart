class MessageState {
  static const String pending = 'pending';
  static const String encrypting = 'encrypting';
  static const String sent = 'sent';
  static const String acked = 'acked';
  static const String retrying = 'retrying';
  static const String delivered = 'delivered';
  static const String read = 'read';
  static const String failed = 'failed';

  /// Valid transitions from a given state.
  /// Keys are current state, values are allowed next states.
  static const Map<String, List<String>> allowedTransitions = {
    pending: [encrypting],
    encrypting: [sent, failed],
    sent: [acked, retrying],
    retrying: [sent, failed],
    acked: [delivered, read], // can skip delivered if read arrives first
    delivered: [read],
    read: [], // Terminal state
    failed: [pending], // Manual retry
  };

  /// Checks if a transition is valid.
  static bool isValidTransition(String current, String next) {
    if (current == next) return true;
    final allowed = allowedTransitions[current];
    if (allowed == null) return false;
    return allowed.contains(next);
  }
  
  /// Helper to get a numeric weight for visual sorting/priority
  static int getWeight(String state) {
    switch (state) {
      case pending: return 0;
      case encrypting: return 1;
      case retrying: return 2;
      case sent: return 3;
      case acked: return 4;
      case delivered: return 5;
      case read: return 6;
      case failed: return -1;
      default: return 0;
    }
  }
}
