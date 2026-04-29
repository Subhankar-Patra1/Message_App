# 🔐 Crypto Contract Specification v1.0
## End-to-End Encrypted Messaging Protocol — Wire Format & Integration Contract

> 📋 **Purpose**: Define the exact binary formats, encoding strategies, transport framing, and error handling for all cryptographic operations between Flutter client and Elixir backend.  
> 🎯 **Audience**: Flutter developers, Elixir backend engineers, security auditors  
> 🚫 **Out of Scope**: Internal cryptographic algorithm details (handled by `libsignal_protocol` libraries)  
> 🔄 **Versioning**: This spec is versioned independently of app releases. Breaking changes require `v2.0` and client backward-compatibility windows.

---

## 🧭 Core Principles

1. **Server Never Sees Secrets**: Private keys, session state, and plaintext content never cross the client boundary.
2. **Explicit Over Implicit**: Every field has a defined type, encoding, and validation rule — no "magic" inference.
3. **Fail Securely**: Invalid cryptographic payloads are rejected with generic errors — never leak implementation details.
4. **Forward-Compatible**: Reserve fields and version headers to allow protocol evolution without breaking clients.
5. **Minimal Metadata**: Transmit only what is strictly necessary for routing and delivery.

---

## 🔑 Section 1: Key Types & Binary Formats

All cryptographic keys use **Curve25519** for ECDH and **Ed25519** for signatures, per Signal Protocol specification.

### 1.1 Identity Key Pair
| Component | Format | Size | Encoding | Notes |
|-----------|--------|------|----------|-------|
| `identity_public_key` | Curve25519 public key | 32 bytes | Base64 (URL-safe, no padding) | Stored/transmitted only |
| `identity_private_key` | Curve25519 private key | 32 bytes | **Never transmitted** | Stored in hardware-backed secure storage only |
| `identity_signature` | Ed25519 signature of public key | 64 bytes | Base64 (URL-safe, no padding) | Signed by private key; verifies key authenticity |

### 1.2 Pre-Key Bundle Components
| Component | Format | Size | Encoding | Notes |
|-----------|--------|------|----------|-------|
| `pre_key_public` | Curve25519 public key | 32 bytes | Base64 (URL-safe, no padding) | One-time use; marked `is_used=true` after session initiation |
| `signed_pre_key_public` | Curve25519 public key | 32 bytes | Base64 (URL-safe, no padding) | Rotated periodically; signed by identity key |
| `signed_pre_key_signature` | Ed25519 signature | 64 bytes | Base64 (URL-safe, no padding) | Signature of `signed_pre_key_public` by identity private key |
| `pre_key_id` / `signed_pre_key_id` | Unsigned 32-bit integer | 4 bytes | JSON integer | Unique per device; used for key lookup |

### 1.3 Sender Key (Group Chats — V2)
| Component | Format | Size | Encoding | Notes |
|-----------|--------|------|----------|-------|
| `sender_key_public` | Curve25519 public key | 32 bytes | Base64 (URL-safe, no padding) | Distributed via 1:1 channels to group members |
| `sender_key_private` | Curve25519 private key | 32 bytes | **Never transmitted** | Used to encrypt group messages; device-local only |
| `sender_key_signature` | Ed25519 signature | 64 bytes | Base64 (URL-safe, no padding) | Verifies sender key authenticity |

### 1.4 Identity Hashes
| Component | Format | Size | Encoding | Notes |
|-----------|--------|------|----------|-------|
| `phone_hash` | SHA-256 hash of E.164 phone number | 32 bytes | Base64 (URL-safe, no padding) | Used for privacy-preserving contact discovery |

> 📌 **Encoding Standard**: All base64 encoding uses **URL-safe alphabet** (`-` and `_` instead of `+` and `/`) with **no padding** (`=` removed). This ensures safe inclusion in URLs and JSON without additional escaping.

---

## 📦 Section 2: Transport Encoding Strategy

### 2.1 HTTP/JSON Payloads (Key Registration, Pre-Key Fetch)
```json
{
  "version": "1.0",
  "device_id": "uuid-v4-string",
  "identity_key": {
    "public": "base64url-no-pad-string",
    "signature": "base64url-no-pad-string"
  },
  "pre_keys": [
    {
      "key_id": 12345,
      "public": "base64url-no-pad-string"
    }
  ],
  "signed_pre_key": {
    "key_id": 1,
    "public": "base64url-no-pad-string",
    "signature": "base64url-no-pad-string"
  }
}
```

**Validation Rules**:
- All base64 strings must decode to exact byte lengths (32 or 64 bytes)
- `key_id` values must be unique within their namespace per device
- `version` field is mandatory; unknown versions are rejected with `400 Bad Request`

### 2.2 WebSocket/Channel Payloads (Message Relay)
```json
{
  "v": "1.0",
  "msg_id": "uuid-v4-string",
  "sender_device_id": "uuid-v4-string",
  "recipient_user_id": "uuid-v4-string",
  "recipient_device_id": "uuid-v4-string", // optional for fan-out
  "payload": {
    "type": "message|receipt|key_rotation",
    "ciphertext": "base64url-no-pad-string",
    "iv": "base64url-no-pad-string", // if applicable
    "timestamp": 1712345678901
  },
  "metadata": {
    "client_version": "flutter-1.2.3",
    "platform": "ios|android"
  }
}
```

**Critical Notes**:
- `ciphertext` contains the **full Signal Protocol message envelope** (includes ratchet state, MAC, etc.)
- Server validates JSON structure and auth, but **never inspects or modifies** `payload.ciphertext`
- `recipient_device_id` is optional: if omitted, server fans out to all active devices for the user

### 2.3 Binary Framing (Optional Optimization — V2)
For bandwidth-constrained environments, a binary protobuf-like framing may be introduced:
```
[2 bytes: version][1 byte: message type][4 bytes: payload length][N bytes: payload]
```
> 🔄 **Backward Compatibility**: JSON framing remains supported indefinitely. Binary framing is opt-in via `Accept: application/msgpack` header.

---

## 🚨 Section 3: Error Handling & Validation

### 3.1 Server-Side Validation (Elixir/Phoenix)
| Error Condition | HTTP Status | Response Body (Generic) | Logging (Internal) |
|----------------|-------------|-------------------------|-------------------|
| Invalid base64 encoding | `400 Bad Request` | `{"error": "invalid_format"}` | Log field name + raw value hash |
| Wrong key length (e.g., 31-byte public key) | `400 Bad Request` | `{"error": "invalid_format"}` | Log expected vs actual length |
| Duplicate `key_id` for device | `409 Conflict` | `{"error": "key_conflict"}` | Log device_id + conflicting key_id |
| Missing required field | `400 Bad Request` | `{"error": "missing_field"}` | Log missing field name |
| Auth token invalid/expired | `401 Unauthorized` | `{"error": "unauthorized"}` | Log user_id hash + token hash |
| Rate limit exceeded | `429 Too Many Requests` | `{"error": "rate_limited"}` | Log user_id hash + endpoint |

> 🔐 **Security Rule**: Never include field values, stack traces, or cryptographic details in client-facing errors. All sensitive logging is hashed or redacted.

### 3.2 Client-Side Validation (Flutter)
| Error Condition | User-Facing Message | Recovery Action |
|----------------|---------------------|----------------|
| Failed to generate keys (hardware error) | "Security setup failed. Please restart the app." | Retry key generation; if persistent, prompt re-install |
| Server rejects key upload | "Could not register device. Check connection and try again." | Retry with exponential backoff; alert user after 3 failures |
| Decryption fails (corrupted ciphertext) | "Message could not be decrypted. It may have been tampered with." | Show error inline; offer to request re-send |
| Key not found for recipient | "This contact's security information has changed. Verify their safety number." | Trigger key re-fetch + user verification flow |

---

## 🔄 Section 4: Protocol Versioning & Evolution

### 4.1 Version Negotiation
- All requests include `"version": "1.0"` in top-level JSON
- Server responds with `"supported_versions": ["1.0"]` in handshake endpoints
- Client must abort if its version is not in the supported list

### 4.2 Deprecation Policy
- Minor changes (new optional fields): Backward-compatible; no version bump
- Breaking changes (field removal, format change): Require `v2.0` + 6-month client support window
- Security-critical changes (algorithm upgrade): Immediate deprecation of old version + forced client update

### 4.3 Reserved Fields
```json
{
  "version": "1.0",
  "_reserved": {
    "future_key_type": null,
    "post_quantum_hint": null
  }
}
```
> 📦 Clients must ignore unknown fields; servers must preserve reserved fields in echoes.
