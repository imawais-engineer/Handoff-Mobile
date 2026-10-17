# Handoff Files — Security & Threat Model

This document outlines the security architecture, cryptographic design choices, threat boundaries, and risk mitigations for **Handoff Files**.

---

## 1. Security Objectives & Protected Assets

1. **Confidentiality in Transit**: Unauthenticated local network observers must not be able to read transferred file bytes or sensitive file names.
2. **Integrity & Authenticity**: In-flight tampering or truncation of packets must be detected and rejected before file bytes are accepted or presented to the user.
3. **Explicit Consent & Non-Spamming**: An unapproved sender cannot force files onto a receiving device without explicit interactive approval displaying the sender, filename, and size.
4. **Local Storage Isolation**: Malicious peers cannot overwrite arbitrary operating system files, escape target download folders (path traversal), or leave persistent corrupt files.
5. **Zero-Telemetry Offline Privacy**: The application never transmits logs, identifiers, file names, or telemetry to external cloud servers.

---

## 2. Threat Analysis & Mitigations

### Threat 1: Passive Eavesdropping on Open Wi-Fi
- **Attack**: An attacker on the same Wi-Fi LAN captures TCP packets using Wireshark / tcpdump.
- **Mitigation**: All file chunks and control payloads post-handshake are encrypted with **AES-256-GCM**. Ephemeral ECDH generates fresh keys per session with Forward Secrecy. Capturing historical network packets does not allow retroactive decryption.

### Threat 2: PIN Guessing & Online Brute Force
- **Attack**: A 6-digit numeric PIN has $\approx 10^6$ combinations ($\sim 20\text{ bits}$ entropy). An attacker script spams PIN guesses to hijack a pending receiver.
- **Mitigation**:
  1. The pairing endpoint enforces **RateLimiter**: maximum 3 failed attempts per connection window.
  2. On the 3rd failure, the client address is locked out for 30 seconds and the active TCP socket is immediately terminated.
  3. Pairing tickets and PINs expire after 120 seconds.
  4. In QR mode, a 128-bit high-entropy ephemeral token is exchanged directly via camera, bypassing PIN entropy limits entirely.

### Threat 3: Replay Attacks
- **Attack**: An attacker captures a valid handshake message and attempts to replay it later to initiate an unauthorized transfer.
- **Mitigation**: Both peers exchange fresh, cryptographically secure 12-byte random nonces ($N_C$ and $N_S$) generated via `SecureRandom`. The derived keys and authentication proofs are strictly bound to $(N_C \mathbin{\Vert} N_S)$. Replayed messages fail verification because the receiver uses a new server nonce for every connection.

### Threat 4: Path Traversal & Destination Hijacking
- **Attack**: A sender transmits a malicious filename containing path separators (e.g. `../../../../etc/passwd` or `..\AppData\Roaming\Startup\malware.exe`).
- **Mitigation**: `StagingFileManager.sanitizeFilename()`:
  - Strips all leading directory elements via `substringAfterLast("/")` and `substringAfterLast("\\")`.
  - Replaces illegal filesystem characters (`: * ? " < > | \0`) with underscores.
  - Clamps filename to 255 characters.
  - Generates safe fallback names if empty.
  - Always writes to a sandboxed app-private staging directory (`cacheDir/staging/`) first.

### Threat 5: Resource Exhaustion & Corrupted Transfer Flooding
- **Attack**: A malicious client declares a 100 KiB file but streams gigabytes of data, or sends a corrupted payload hoping the recipient uses it.
- **Mitigation**:
  - Receiver checks declared size against the 10 MiB limit ($10{,}485{,}760\text{ bytes}$) in `TRANSFER_PROPOSAL`.
  - Receiver counts decrypted bytes continuously. If accumulated bytes exceed 10 MiB, the socket is severed and the staging file is deleted immediately.
  - The staging file is promoted to user storage **only** after the full computed SHA-256 hash matches the expected digest.

---

## 3. Assumptions & Out-of-Scope Risks

- **In Scope**: Protection against malicious clients, eavesdroppers, and active attackers on the shared local wireless network.
- **Out of Scope**:
  - Malicious local administrator or compromised host operating system (e.g., memory inspection on a rooted Android device or infected Windows host).
  - Physical shoulder-surfing while the user displays the QR code or 6-digit PIN.
  - Hardware side-channel attacks on AES coprocessors.
