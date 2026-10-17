# Handoff Files — Cross-Platform Wire Protocol Specification (v1)

This specification defines the wire-level transfer protocol used by **Handoff Files** across Android and Windows implementations. It is language-neutral, transport-agnostic, and designed for deterministic interoperability between Kotlin (Android) and C# / .NET (Windows).

---

## 1. Network & Framing Contract

All numerical integer fields in packet headers are transmitted in **Network Byte Order (Big-Endian)**.

### Fixed 10-Byte Header Layout

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       Magic ('HNDF')                          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|    Version    |  Packet Type  |        Payload Length         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|       (Payload Length cont.)  |           Payload ...         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+                               +
```

| Field | Offset | Length | Type | Description |
| :--- | :--- | :--- | :--- | :--- |
| `Magic` | 0 | 4 Bytes | ASCII | Fixed bytes `0x48, 0x4E, 0x44, 0x46` (`"HNDF"`). Any packet with mismatching magic must be dropped immediately and the socket terminated. |
| `Version` | 4 | 1 Byte | uint8 | Protocol version. Current version is `0x01`. |
| `Type` | 5 | 1 Byte | uint8 | Packet type identifier (see Section 2). |
| `PayloadLength`| 6 | 4 Bytes | uint32 (Big-Endian) | Length of the following payload in bytes. Maximum bound: `65,536` bytes for control packets, `65,564` bytes for encrypted chunk packets. |

---

## 2. Packet Types

| Type Code | Hex | Name | Purpose |
| :--- | :--- | :--- | :--- |
| `1` | `0x01` | `PAIR_KEY_EXCHANGE` | Client sends ephemeral ECDH public key and nonce |
| `2` | `0x02` | `PAIR_KEY_RESPONSE` | Server replies with ephemeral ECDH public key, nonce, and SAS commitment |
| `3` | `0x03` | `PAIR_VERIFY` | Client submits SAS proof or QR token proof |
| `4` | `0x04` | `PAIR_ACK` | Server confirms authenticated session status (`OK` or error) |
| `16` | `0x10` | `TRANSFER_PROPOSAL` | Sender manifests file name, size, MIME type, and SHA-256 digest |
| `17` | `0x11` | `TRANSFER_RESPONSE` | Receiver explicitly approves or rejects the transfer |
| `32` | `0x20` | `CHUNK_DATA` | Streamed file chunk encrypted with AES-256-GCM |
| `33` | `0x21` | `PROGRESS_ACK` | Periodic acknowledgment of received byte offset |
| `34` | `0x22` | `TRANSFER_COMPLETE` | Sender signals end of chunks and expected file digest |
| `35` | `0x23` | `TRANSFER_VERIFIED` | Receiver confirms complete SHA-256 integrity match |
| `48` | `0x30` | `TRANSFER_CANCEL` | Either peer cancels the active transfer |
| `255`| `0xFF` | `ERROR` | Fatal error code and message; precedes socket closure |

---

## 3. Cryptography & Key Establishment

### 3.1 Ephemeral Diffie-Hellman Key Agreement
- Curve: NIST P-256 (`secp256r1`) or X25519.
- Both endpoints generate a fresh, single-use keypair for each connection.
- Neither public key is reused across connections.

### 3.2 HKDF-SHA256 Session Key Derivation
From the computed shared secret $S$, client nonce $N_C$ (12 bytes), and server nonce $N_S$ (12 bytes):

$$\text{PRK} = \text{HMAC-SHA256}(\text{Salt} = N_C \mathbin{\Vert} N_S, S)$$

Derived keys:
1. **AES-256-GCM Key** (32 bytes):
   $$\text{Key}_{\text{AES}} = \text{HKDF-Expand}(\text{PRK}, \text{"HandoffFiles-v1-AES256GCM"} \mathbin{\Vert} N_C \mathbin{\Vert} N_S, 32)$$
2. **HMAC Auth Key** (32 bytes):
   $$\text{Key}_{\text{Auth}} = \text{HKDF-Expand}(\text{PRK}, \text{"HandoffFiles-v1-AuthHMAC"} \mathbin{\Vert} N_C \mathbin{\Vert} N_S, 32)$$
3. **6-Digit Short Authentication String (SAS)**:
   $$\text{SAS}_{\text{Raw}} = \text{HKDF-Expand}(\text{PRK}, \text{"HandoffFiles-v1-SAS"} \mathbin{\Vert} N_C \mathbin{\Vert} N_S, 4)$$
   $$\text{PIN} = (\text{uint32}(\text{SAS}_{\text{Raw}}) \ \& \ \text{0x7FFFFFFF}) \pmod{1{,}000{,}000}$$
   Formatted as 6 zero-padded decimal digits (e.g. `049281`).

---

## 4. Chunk Data Layout (`0x20`)

Chunk payloads carry authenticated encryption:

```
+--------------------+--------------------------+--------------------+
| IV (12 Bytes)      | Ciphertext (N Bytes)     | Tag (16 Bytes)     |
+--------------------+--------------------------+--------------------+
```

Inside the decrypted ciphertext:
- `transferIdLength` (1 byte uint8)
- `transferId` (UTF-8 bytes)
- `sequenceNumber` (4 bytes uint32 Big-Endian)
- `chunkLength` (4 bytes uint32 Big-Endian)
- `chunkBytes` (Raw file bytes, max 65,536 bytes)

---

## 5. File Size Limits & Integrity Rules

- **Limit**: Exactly $10\text{ MiB} = 10 \times 1024 \times 1024 = 10{,}485{,}760\text{ bytes}$.
- **Sender enforcement**: Before opening or transmitting file contents, inspect `sizeBytes`. If $> 10{,}485{,}760$, abort immediately.
- **Receiver enforcement**:
  1. In `TRANSFER_PROPOSAL`: If declared `sizeBytes > 10,485,760`, reply with `TRANSFER_RESPONSE(accepted = false, reason = "ERR_SIZE_EXCEEDED")`.
  2. While receiving chunks: Count decrypted bytes. If total exceeds $10{,}485{,}760$, terminate socket and delete staging file.
- **Integrity**: Receiver hashes incoming stream with SHA-256 into a staging file `.handoff_<id>.tmp`. Only after the completed digest strictly equals the sender's digest is the file moved to public storage.


---

## 6. Embedded Web Transfer Endpoint (Zero-Install Windows Mode)

To allow immediate file transfer from Windows without installing a custom desktop executable, the Android app hosts a lightweight, local-only HTTP server (default port `8080`).

### 6.1 Authentication & Endpoint Flow

1. **`GET /`**: Serves the self-contained HTML/CSS/JS client.
2. **`POST /api/pair`**:
   - Request: `{ "pin": "492817" }`
   - Response: `{ "status": "OK", "token": "<ephemeral-session-token>" }`
   - Enforces strict rate-limiting: max 3 failed attempts, then 30-second lockout.
3. **`POST /api/upload`**:
   - Headers:
     - `X-Session-Token`: `<token>`
     - `X-File-Name`: URL-encoded filename (sanitized on Android)
     - `X-File-Size`: Content size in bytes (must be $\le 10{,}485{,}760$)
     - `X-File-Type`: MIME type
     - `X-File-Sha256`: Hex-encoded SHA-256 calculated by the browser via `crypto.subtle.digest`
   - Body: Raw binary octet stream
   - Receiver Action: Prompts user with interactive approval dialog before reading stream. Decrypted/received bytes stream to `.handoff_<id>.tmp` staging file and are verified against SHA-256 before publishing.
4. **`GET /api/download`**:
   - Downloads a queued file from Android with streaming byte counts.
