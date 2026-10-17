# Handoff Files — Mobile (HANDOFF-MOB)

Standalone, self-contained Android project for the Handoff Files app.
Kotlin + Jetpack Compose (Material 3), offline peer-to-peer file transfer.

This folder is a complete copy — it does not depend on anything outside it.
The original `android/` folder in the main repo is untouched.

## Two ways to get your APK

### Option A — Let GitHub build it (no tools installed on your machine)

1. Create a new empty repository on github.com.
2. Upload **the contents of this HANDOFF-MOB folder** (including the hidden
   `.github` folder — use "uploading an existing file", or git push).
3. GitHub builds automatically (Actions tab → "Build Handoff Files APK",
   roughly 10–15 minutes).
4. Open the finished run, scroll to **Artifacts**, download
   **HandoffFiles-debug-apk**.

### Option B — Build it yourself (JDK 17 + Android SDK required)

```bash
# 1. Point at your Android SDK
echo "sdk.dir=/path/to/your/android-sdk" > local.properties

# 2. Make the wrapper executable (Unix/macOS)
chmod +x gradlew

# 3. Run the unit tests (optional but recommended)
./gradlew testDebugUnitTest

# 4. Build the APK
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

## Install on your phone

Copy `app-debug.apk` to the phone and tap it (allow "install from unknown
sources"), or connect via USB with developer options + USB debugging
enabled and run:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## What's inside

- `app/src/main/java/com/handoff/files/protocol/` — 10-byte HNDF wire framing
- `app/src/main/java/com/handoff/files/security/` — ECDH P-256, HKDF-SHA256,
  AES-256-GCM, rate limiting
- `app/src/main/java/com/handoff/files/transfer/` — chunked encrypted
  streaming, SHA-256 verified staging
- `app/src/main/java/com/handoff/files/transport/` — TCP channel + embedded
  HTTP server for zero-install browser transfers
- `app/src/main/java/com/handoff/files/ui/` — Compose screens (Home, Send,
  Receive, History, Settings)
- `app/src/test/` — unit tests for framing, crypto, file size limits, staging

## Requirements

- JDK 17
- Android SDK, compileSdk 34, minSdk 26
- Gradle 8.7 (auto-downloaded by the wrapper)

## Quick test after install

Open the app → Receive Files → Start Web Mode. It shows a URL and a 6-digit
PIN. From any computer on the same Wi-Fi, open that URL in a browser, enter
the PIN, and send files up to 10 MiB — no software install needed on the
sender side.
