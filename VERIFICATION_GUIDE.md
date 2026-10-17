# Handoff Files — Verification & Validation Guide

This document defines the physical hardware test procedures, benchmark matrix, traceability matrix, and Android Studio / Gradle build instructions for **Handoff Files**.

---

## 1. Traceability Matrix

| Requirement | Description | Implementation File | Verification Suite / Test |
| :--- | :--- | :--- | :--- |
| **FR-01** | Home Screen & Offline State | `ui/screens/HomeScreen.kt` | UI state observation, Wi-Fi toggle |
| **FR-02** | SAF File Selection & 10 MiB Limit | `ui/screens/SendScreen.kt` | `FileSizeValidatorTest.kt` (0, 10M-1, 10M, 10M+1) |
| **FR-03** | Offline Transport (Local Wi-Fi/Hotspot) | `transport/TcpTransportChannel.kt`, `NetworkResolver.kt` | Local socket tests, peer IP validation |
| **FR-04** | QR & 6-digit PIN Pairing | `ui/screens/ReceiveScreen.kt`, `security/CryptoEngine.kt` | `CryptoEngineTest.kt` (SAS generation) |
| **FR-05** | Cryptographic Handshake & AEAD | `security/CryptoEngine.kt`, `security/RateLimiter.kt` | `CryptoEngineTest.kt` (ECDH, HKDF, AES-GCM) |
| **FR-06** | Wire Protocol & Chunk Streaming | `protocol/HandoffFramer.kt`, `transfer/FileStreamer.kt` | `HandoffFramerTest.kt` (framing roundtrip) |
| **FR-07** | Transfer UX, Speed & Cancellation | `ui/TransferViewModel.kt`, `transfer/FileStreamer.kt` | Cancellation tests, speed calculation |
| **FR-08** | Local Transfer History | `ui/screens/HistoryScreen.kt`, `TransferViewModel.kt` | History persistence, Clear History |
| **FR-09** | Permissions & Lifecycle | `AndroidManifest.xml` | Justified permissions audit |
| **FR-10** | Settings & Sanitized Diagnostics | `ui/screens/SettingsScreen.kt` | Diagnostics export (zero secret leaks) |

---

## 2. Benchmark & Performance Test Procedure

The primary target is: **Transfer a 10 MiB file in under 10 seconds after connection is established** (target throughput $\ge 1.0\text{ MiB/s}$).

### Test File Fixtures
Prepare test files with random entropy:
1. `benchmark_1kib.dat` (1,024 bytes)
2. `benchmark_100kib.dat` (102,400 bytes)
3. `benchmark_1mib.dat` (1,048,576 bytes)
4. `benchmark_5mib.dat` (5,242,880 bytes)
5. `benchmark_10mib.dat` (10,485,760 bytes)

### Test Environment Recording
For each test run, document:
- **Android Device**: Model (e.g. Pixel 8, Galaxy S23), Android OS version (e.g. Android 14)
- **Windows Computer**: Model, Windows version (Windows 11 23H2), Wi-Fi card (e.g. Intel Wi-Fi 6E AX211)
- **Network Setup**: Shared 5 GHz Wi-Fi router vs Phone Hotspot (2.4 GHz vs 5 GHz)

### Benchmark Measurement Table

| File Size | Setup Time (s) | Transfer Duration (s) | Effective Speed (MiB/s) | SHA-256 Match | Result |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1 KiB** | Record | Record | Record | `[PASS/FAIL]` | Nominal |
| **100 KiB** | Record | Record | Record | `[PASS/FAIL]` | Nominal |
| **1 MiB** | Record | Record | Record | `[PASS/FAIL]` | Nominal |
| **5 MiB** | Record | Record | Record | `[PASS/FAIL]` | Nominal |
| **10 MiB (Run 1)**| Record | Record | Record | `[PASS/FAIL]` | Target < 10s |
| **10 MiB (Run 2)**| Record | Record | Record | `[PASS/FAIL]` | Target < 10s |
| **10 MiB (Run 3)**| Record | Record | Record | `[PASS/FAIL]` | Target < 10s |

---

## 3. How to Build the Android Project

### Prerequisites
- JDK 17 (Java Development Kit 17 or higher)
- Android Studio Hedgehog (2023.1.1) / Iguana / Jellyfish or Android SDK Command-line Tools (API 35, Build-tools 35.0.0)

### Building via Android Studio
1. Open Android Studio.
2. Select **Open**, and navigate to the `/android` folder in this repository.
3. Allow Gradle to sync the project.
4. Select target device / emulator and click **Run** (`Shift + F10`).

### Building via Command Line (Gradle)
From the `/android` directory:

```bash
# Run unit test suite
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug

# Output APK path:
# android/app/build/outputs/apk/debug/app-debug.apk

# Install to connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
