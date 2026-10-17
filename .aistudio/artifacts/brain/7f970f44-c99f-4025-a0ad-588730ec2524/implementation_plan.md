# Handoff Files — Native Android & Protocol Test Bench Implementation Plan

Handoff Files is an offline-first, secure local file transfer system designed for direct Android-to-Windows communication without internet access, cloud relays, or user accounts. This plan details the creation of the complete, production-grade native Android application (Kotlin, Jetpack Compose, Material 3, Clean Architecture) paired with an interactive Web Protocol Test Bench to validate wire framing, pairing states, and integrity verification.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The following security and interoperability requirements are strictly enforced across the architecture:

- **Authenticated Key Exchange & Pairing (PAKE / SAS)**:
  - Six-digit pairing PINs possess low entropy ($\approx 20\text{ bits}$) and are never used directly as raw HMAC keys.
  - Ephemeral Diffie-Hellman (ECDH / X25519) establishes a shared session secret.
  - In QR mode: The QR payload contains high-entropy ephemeral cryptographic parameters (a 256-bit ephemeral token + endpoint details) to authenticate the key exchange.
  - In 6-digit PIN mode: A Short Authentication String (SAS) or PAKE protocol is used, backed by strict connection-window rate limiting (max 3 failed attempts, immediate session teardown, 30s lockout) to prevent offline or online brute-force attacks.
- **End-to-End Authenticated Encryption in Transit (AEAD)**:
  - All file contents and sensitive control payloads are encrypted using **AES-256-GCM** (or ChaCha20-Poly1305), utilizing symmetric keys derived via **HKDF-SHA256**.
  - Every chunk frame carries an explicit 12-byte initialization vector (IV/nonce), ciphertext, and a 16-byte authentication tag (`Tag`), ensuring both confidentiality and tamper resistance.
- **Unambiguous Wire Framing & Byte Order**:
  - Strict Network Byte Order (Big-Endian).
  - Fixed 10-byte header: `[Magic (4B: 0x48 0x4E 0x44 0x46 "HNDF")][Version (1B: 0x01)][Type (1B)][PayloadLength (4B Big-Endian uint32)]`.
  - Max payload length enforced per packet type (Max 64 KiB for control JSON, Max 65,564 bytes for encrypted 64 KiB data chunks). Truncated, malformed, or oversized packets trigger immediate socket termination.
- **Precise 10 MiB Limit**:
  - $10\text{ MiB} = 10 \times 1024 \times 1024 = 10,485,760\text{ bytes}$ per file / transfer.
  - Enforced by the sender prior to SAF reading, and enforced by the receiver upon `TRANSFER_PROPOSAL` evaluation and during byte-by-byte streaming.
- **Safe File Publishing & Path Traversal Prevention**:
  - Receiver strips path traversal sequences (`../`, `..\\`), null bytes, and non-printable characters, clamping filename length to 255 chars.
  - In-flight data is written to an isolated staging file (`.handoff_<transferId>.tmp`).
  - Staging files are promoted to public storage **only** after SHA-256 digest verification succeeds. Incomplete or failed staging files are wiped immediately.
- **Realistic Transport & Hotspot Handling**:
  - Transport operates over local Wi-Fi or user-enabled hotspot. The app guides the user through manual hotspot or Wi-Fi setup without assuming programmatic hotspot configuration permissions.
  - Peer address and port validation prevents SSRF or redirection to loopback/multicast destinations.
- **Web Protocol Test Bench Role**:
  - Functions as a protocol test bench, test-vector verification suite, packet serializer sandbox, and mock peer simulator (respecting browser TCP sandbox limits).

---

### 1. Overview & Core Concept

- **What It Does**: Enables peer-to-peer wireless file transfer between an Android device and a Windows computer (or peer device) on the same local network or Wi-Fi hotspot. Transferred files are capped at 10 MiB ($10,485,760\text{ bytes}$), targeting transfer times under 10 seconds post-connection with cryptographic integrity checks and zero telemetry.
- **Target Audience & Context**: Users needing private, direct, high-speed file transfers between Android and Windows in environments without internet, corporate firewalls, or when cloud syncing is prohibited or too slow.
- **Key Value**: True zero-cloud privacy, zero account requirements, instant connection setup via QR/PIN, bounded memory usage (streaming I/O), and deterministic protocol compatibility with the future C#/.NET Windows companion.

---

### 2. User Experience & Visual Design

#### Key User Flows

1. **Send Flow (Android)**:
   - User taps **Send Files** on the Material 3 Home Screen.
   - Android Storage Access Framework (`ActivityResultContracts.OpenDocument`) is invoked. File metadata (name, MIME type, exact byte size) is extracted from Content URIs.
   - Files $> 10\text{ MiB}$ are rejected with an explicit size badge and explanatory notice.
   - The user opens the QR Scanner (or taps "Enter 6-digit PIN") to scan the receiver's pairing card.
   - Authenticated key exchange runs; the UI displays "Waiting for receiver approval...".
   - Upon approval, AES-256-GCM encrypted streaming initiates. A live progress card displays percentage, bytes transferred, real-time throughput ($\text{MiB/s}$), and elapsed/remaining time.
   - On completion, SHA-256 verification status is reported with a persistent transfer record saved to local history.

2. **Receive Flow (Android & Test Bench)**:
   - User taps **Receive Mode**. The device binds an ephemeral TCP server socket on local Wi-Fi/Hotspot IP.
   - Screen renders a high-contrast dynamic QR code displaying `handoff://v1?ip=...&port=...&token=...` and a legible 6-digit PIN with a 120-second validity countdown.
   - When a peer connects, an **Incoming Transfer Dialog** prompts the receiver: *"[Sender Device] wants to send [Filename] (X.XX MiB). Accept or Reject?"*
   - If accepted, the receiver streams incoming encrypted chunks directly to an app-private cache file (`.handoff_<transferId>.tmp`) while updating progress.
   - The receiver validates the SHA-256 digest. If valid, the file is sanitized, renamed to avoid collisions, and published to user-accessible storage.

3. **Web Protocol Test Bench Flow**:
   - Clean developer workbench with interactive tabs: **Protocol Inspector & Hex Dissector**, **AEAD Encryption & Framing Sandbox**, **Pairing & SAS Simulator**, **Deterministic Test Vector Suite**, and **Android Project Exporter**.
   - Allows injecting valid, malformed, truncated, or oversized packets to verify protocol resilience.

#### Visual Identity & Theme
- **Android App**: Material Design 3 with dynamic color support (tonal palettes), crisp typography, clean cards without nested card clutter, and unambiguous status states (Green for authenticated/ready, Blue for active streaming, Amber for awaiting confirmation, Crimson for error/mismatch).
- **Web Protocol Test Bench**:
  - *Aesthetic*: Professional developer workbench (60% dark slate `#0F172A`, 30% structural surfaces `#1E293B`, 10% high-intent action indigo `#6366F1` & emerald `#10B981`).
  - *Typography*: `Plus Jakarta Sans` for titles and controls, `JetBrains Mono` / tabular numerals (`tabular-nums`) for packet headers, hex previews, byte offsets, and checksum displays.

---

### 3. Technical Architecture & Data Strategy

#### System & Component Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       HANDOFF FILES ARCHITECTURE                            │
└─────────────────────────────────────────────────────────────────────────────┘

 ┌───────────────────────────────────────────────────────────────────────────┐
 │                            ANDROID APPLICATION                            │
 │                                                                           │
 │  ┌─────────────────────────────────────────────────────────────────────┐  │
 │  │                     Jetpack Compose UI Layer                        │  │
 │  │  • HomeScreen         • SendScreen (SAF)    • ReceiveScreen (QR/PIN)│  │
 │  │  • TransferDialog     • ProgressCard        • HistoryScreen         │  │
 │  └──────────────────────────────────┬──────────────────────────────────┘  │
 │                                     ▼                                     │
 │  ┌─────────────────────────────────────────────────────────────────────┐  │
 │  │                      TransferViewModel & State                      │  │
 │  │  • StateFlow<TransferUiState>    • One-shot UI Events               │  │
 │  └──────────────────────────────────┬──────────────────────────────────┘  │
 │                                     ▼                                     │
 │  ┌─────────────────────────────────────────────────────────────────────┐  │
 │  │                            Domain Layer                             │  │
 │  │  • SendFileUseCase   • ReceiveFileUseCase   • VerifyIntegrityUseCase│  │
 │  │  • TransferSession   • FileManifest         • PairingSession        │  │
 │  └──────────────────────────────────┬──────────────────────────────────┘  │
 │                                     ▼                                     │
 │  ┌───────────────────────┬───────────────────────┬─────────────────────┐  │
 │  │    Security Engine    │   Protocol Framer     │   Storage / SAF     │  │
 │  │ • ECDH + HKDF-SHA256  │ • HandoffFramer (v1)  │ • SafStreamReader   │  │
 │  │ • AES-256-GCM AEAD    │ • Packet Serializer   │ • Path Sanitizer    │  │
 │  │ • SAS Verification    │ • Chunk Framer (64K)  │ • StagingWriter     │  │
 │  │ • Brute-force Limiter │ • Max Length Checks   │ • Atomic Publisher  │  │
 │  └───────────┬───────────┴───────────┬───────────┴──────────┬──────────┘  │
 │              └───────────────────┐   │   ┌──────────────────┘             │
 │                                  ▼   ▼   ▼                                │
 │  ┌─────────────────────────────────────────────────────────────────────┐  │
 │  │                           Transport Layer                           │  │
 │  │  • ITransportChannel (TCP Client / Server Socket)                  │  │
 │  │  • NetworkStateMonitor (Wi-Fi / Hotspot IP resolver)                │  │
 │  └──────────────────────────────────┬──────────────────────────────────┘  │
 └─────────────────────────────────────┼─────────────────────────────────────┘
                                       │
                      Encrypted Wire Protocol (TCP)
          [4B Magic][1B Ver][1B Type][4B Len][12B IV][Ciphertext][16B Tag]
                                       │
 ┌─────────────────────────────────────┼─────────────────────────────────────┐
 │                                     ▼                                     │
 │                    FUTURE WINDOWS CLIENT / TEST BENCH                     │
 │  • C# .NET Native App (or Web Protocol Test Bench in browser)             │
 │  • Compatible Wire Framer, HKDF, AES-256-GCM, and Checksum Engine         │
 └───────────────────────────────────────────────────────────────────────────┘
```

#### Protocol Wire Specification (v1)

- **Magic**: `0x48 0x4E 0x44 0x46` (`HNDF`, 4 bytes Big-Endian)
- **Version**: `0x01` (1 byte)
- **Header Length**: Fixed 10 bytes: `[Magic (4B)][Version (1B)][Type (1B)][Length (4B Big-Endian)]`
- **Packet Types**:
  - `0x01` `PAIR_KEY_EXCHANGE`: `{ ephemeralPublicKey: string (hex), nonce: string (hex), deviceName: string }`
  - `0x02` `PAIR_KEY_RESPONSE`: `{ ephemeralPublicKey: string (hex), nonce: string (hex), sasCommitment: string (hex) }`
  - `0x03` `PAIR_VERIFY`: `{ sasProof: string (hex), qrTokenProof?: string (hex) }`
  - `0x04` `PAIR_ACK`: `{ status: "OK" | "AUTH_FAILED" | "RATE_LIMITED", message?: string }`
  - `0x10` `TRANSFER_PROPOSAL` (AEAD Encrypted): `{ transferId: string, filename: string, sizeBytes: number, mimeType: string, sha256Digest: string }`
  - `0x11` `TRANSFER_RESPONSE` (AEAD Encrypted): `{ transferId: string, accepted: boolean, reason?: string }`
  - `0x20` `CHUNK_DATA` (AEAD Encrypted): `[12B IV][Encrypted chunk: seq(4B) + chunkLength(4B) + chunkBytes][16B Tag]`
  - `0x21` `TRANSFER_PROGRESS_ACK` (AEAD Encrypted): `{ transferId: string, bytesReceived: number }`
  - `0x22` `TRANSFER_COMPLETE` (AEAD Encrypted): `{ transferId: string, clientSha256: string }`
  - `0x23` `TRANSFER_VERIFIED` (AEAD Encrypted): `{ transferId: string, verified: boolean }`
  - `0x30` `TRANSFER_CANCEL` (AEAD Encrypted): `{ transferId: string, reason: string }`
  - `0xFF` `ERROR`: `{ errorCode: number, message: string }`
