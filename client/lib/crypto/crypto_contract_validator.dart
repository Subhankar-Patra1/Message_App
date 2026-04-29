import 'dart:convert';

class CryptoContractValidator {
  /// Validates that the base64url string decodes to the exact expected number of bytes.
  static void validateBase64UrlNoPad(String input, int expectedBytes) {
    try {
      String base64String = input;
      // Re-add padding if missing, so dart core decoder doesn't fail parsing.
      // But we enforce no-pad over the wire.
      while (base64String.length % 4 != 0) {
        base64String += '=';
      }
      
      final decoded = base64Url.decode(base64String);
      assert(decoded.length == expectedBytes, 
        'Expected $expectedBytes bytes, got ${decoded.length}');
    } catch (e) {
      throw FormatException('Invalid base64url encoding: $e');
    }
  }
  
  static void validatePublicKeyFormat(String b64) {
    validateBase64UrlNoPad(b64, 32); // Raw Curve25519 public key (without type byte)
  }
  
  static void validateSignatureFormat(String b64) {
    validateBase64UrlNoPad(b64, 64); // Ed25519 signature
  }
}
