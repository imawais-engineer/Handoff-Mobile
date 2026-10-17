import JSZip from 'jszip';

// Config files
import settingsGradle from '../../android/settings.gradle.kts?raw';
import buildGradle from '../../android/build.gradle.kts?raw';
import gradleProperties from '../../android/gradle.properties?raw';
import gradleWrapperProps from '../../android/gradle/wrapper/gradle-wrapper.properties?raw';
import libsVersions from '../../android/gradle/libs.versions.toml?raw';
import gradlewScript from '../../android/gradlew?raw';
import appBuildGradle from '../../android/app/build.gradle.kts?raw';
import proguardRules from '../../android/app/proguard-rules.pro?raw';
import androidManifest from '../../android/app/src/main/AndroidManifest.xml?raw';

// Binary jar — imported as URL, fetched at zip-generation time
import gradleWrapperJarUrl from '../../android/gradle/wrapper/gradle-wrapper.jar?url';

// Protocol layer
import handoffFramer from '../../android/app/src/main/java/com/handoff/files/protocol/HandoffFramer.kt?raw';
import handoffPacket from '../../android/app/src/main/java/com/handoff/files/protocol/HandoffPacket.kt?raw';
import protocolConstants from '../../android/app/src/main/java/com/handoff/files/protocol/ProtocolConstants.kt?raw';

// Security layer
import cryptoEngine from '../../android/app/src/main/java/com/handoff/files/security/CryptoEngine.kt?raw';
import rateLimiter from '../../android/app/src/main/java/com/handoff/files/security/RateLimiter.kt?raw';

// Transfer layer
import fileStreamer from '../../android/app/src/main/java/com/handoff/files/transfer/FileStreamer.kt?raw';
import stagingFileManager from '../../android/app/src/main/java/com/handoff/files/transfer/StagingFileManager.kt?raw';

// Transport layer
import iTransportChannel from '../../android/app/src/main/java/com/handoff/files/transport/ITransportChannel.kt?raw';
import networkResolver from '../../android/app/src/main/java/com/handoff/files/transport/NetworkResolver.kt?raw';
import tcpTransportChannel from '../../android/app/src/main/java/com/handoff/files/transport/TcpTransportChannel.kt?raw';
import webTransferServer from '../../android/app/src/main/java/com/handoff/files/transport/WebTransferServer.kt?raw';

// Domain model
import transferModels from '../../android/app/src/main/java/com/handoff/files/domain/model/TransferModels.kt?raw';

// UI layer
import mainActivity from '../../android/app/src/main/java/com/handoff/files/ui/MainActivity.kt?raw';
import transferViewModel from '../../android/app/src/main/java/com/handoff/files/ui/TransferViewModel.kt?raw';
import homeScreen from '../../android/app/src/main/java/com/handoff/files/ui/screens/HomeScreen.kt?raw';
import sendScreen from '../../android/app/src/main/java/com/handoff/files/ui/screens/SendScreen.kt?raw';
import receiveScreen from '../../android/app/src/main/java/com/handoff/files/ui/screens/ReceiveScreen.kt?raw';
import historyScreen from '../../android/app/src/main/java/com/handoff/files/ui/screens/HistoryScreen.kt?raw';
import settingsScreen from '../../android/app/src/main/java/com/handoff/files/ui/screens/SettingsScreen.kt?raw';
import themeColor from '../../android/app/src/main/java/com/handoff/files/ui/theme/Color.kt?raw';
import themeKt from '../../android/app/src/main/java/com/handoff/files/ui/theme/Theme.kt?raw';

// Unit tests
import cryptoEngineTest from '../../android/app/src/test/java/com/handoff/files/CryptoEngineTest.kt?raw';
import fileSizeValidatorTest from '../../android/app/src/test/java/com/handoff/files/FileSizeValidatorTest.kt?raw';
import handoffFramerTest from '../../android/app/src/test/java/com/handoff/files/HandoffFramerTest.kt?raw';
import stagingFileManagerTest from '../../android/app/src/test/java/com/handoff/files/StagingFileManagerTest.kt?raw';

const SOURCE_FILES: Record<string, string> = {
  'settings.gradle.kts': settingsGradle,
  'build.gradle.kts': buildGradle,
  'gradle.properties': gradleProperties,
  'local.properties': 'sdk.dir=/path/to/your/android-sdk\n',
  'gradlew': gradlewScript,
  'gradle/wrapper/gradle-wrapper.properties': gradleWrapperProps,
  'gradle/libs.versions.toml': libsVersions,
  'app/build.gradle.kts': appBuildGradle,
  'app/proguard-rules.pro': proguardRules,
  'app/src/main/AndroidManifest.xml': androidManifest,
  'app/src/main/java/com/handoff/files/protocol/HandoffFramer.kt': handoffFramer,
  'app/src/main/java/com/handoff/files/protocol/HandoffPacket.kt': handoffPacket,
  'app/src/main/java/com/handoff/files/protocol/ProtocolConstants.kt': protocolConstants,
  'app/src/main/java/com/handoff/files/security/CryptoEngine.kt': cryptoEngine,
  'app/src/main/java/com/handoff/files/security/RateLimiter.kt': rateLimiter,
  'app/src/main/java/com/handoff/files/transfer/FileStreamer.kt': fileStreamer,
  'app/src/main/java/com/handoff/files/transfer/StagingFileManager.kt': stagingFileManager,
  'app/src/main/java/com/handoff/files/transport/ITransportChannel.kt': iTransportChannel,
  'app/src/main/java/com/handoff/files/transport/NetworkResolver.kt': networkResolver,
  'app/src/main/java/com/handoff/files/transport/TcpTransportChannel.kt': tcpTransportChannel,
  'app/src/main/java/com/handoff/files/transport/WebTransferServer.kt': webTransferServer,
  'app/src/main/java/com/handoff/files/domain/model/TransferModels.kt': transferModels,
  'app/src/main/java/com/handoff/files/ui/MainActivity.kt': mainActivity,
  'app/src/main/java/com/handoff/files/ui/TransferViewModel.kt': transferViewModel,
  'app/src/main/java/com/handoff/files/ui/screens/HomeScreen.kt': homeScreen,
  'app/src/main/java/com/handoff/files/ui/screens/SendScreen.kt': sendScreen,
  'app/src/main/java/com/handoff/files/ui/screens/ReceiveScreen.kt': receiveScreen,
  'app/src/main/java/com/handoff/files/ui/screens/HistoryScreen.kt': historyScreen,
  'app/src/main/java/com/handoff/files/ui/screens/SettingsScreen.kt': settingsScreen,
  'app/src/main/java/com/handoff/files/ui/theme/Color.kt': themeColor,
  'app/src/main/java/com/handoff/files/ui/theme/Theme.kt': themeKt,
  'app/src/test/java/com/handoff/files/CryptoEngineTest.kt': cryptoEngineTest,
  'app/src/test/java/com/handoff/files/FileSizeValidatorTest.kt': fileSizeValidatorTest,
  'app/src/test/java/com/handoff/files/HandoffFramerTest.kt': handoffFramerTest,
  'app/src/test/java/com/handoff/files/StagingFileManagerTest.kt': stagingFileManagerTest,
};

const README = `# Handoff Files — Android Project

Offline-first, peer-to-peer file transfer for Android (Kotlin, Jetpack Compose, Material 3).

## Build Requirements

- JDK 17
- Android SDK (compileSdk 34, minSdk 26)
- Android Studio Hedgehog (2023.1) or newer
- Gradle 8.7 (auto-downloaded via wrapper)

## Build Instructions

\`\`\`bash
# 1. Update local.properties with your Android SDK path
echo "sdk.dir=/path/to/your/android-sdk" > local.properties

# 2. Make gradlew executable (Unix/macOS)
chmod +x gradlew

# 3. Run unit tests (framing, crypto, file size, staging)
./gradlew testDebugUnitTest

# 4. Assemble debug APK
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# 5. Install onto connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
\`\`\`

## Architecture

- **Protocol** (\`protocol/\`): 10-byte HNDF wire framing, packet types, Big-Endian encoding
- **Security** (\`security/\`): ECDH P-256 key agreement, HKDF-SHA256 derivation, AES-256-GCM AEAD, rate limiting
- **Transfer** (\`transfer/\`): Chunked encrypted streaming, atomic staging with SHA-256 verification
- **Transport** (\`transport/\`): TCP socket channel, embedded HTTP server for zero-install Windows transfers
- **UI** (\`ui/\`): Compose Material 3 screens — Home, Send, Receive, History, Settings

## Protocol Version
v1.0.0 — see PROTOCOL_SPEC.md for the full wire protocol specification.
`;

export async function generateAndroidProjectZip(): Promise<Blob> {
  const zip = new JSZip();

  // Add all text source files
  for (const [path, content] of Object.entries(SOURCE_FILES)) {
    zip.file(path, content);
  }

  // Add gradle-wrapper.jar (binary — fetched from bundled asset URL)
  try {
    const jarResponse = await fetch(gradleWrapperJarUrl);
    if (jarResponse.ok) {
      const jarBlob = await jarResponse.blob();
      zip.file('gradle/wrapper/gradle-wrapper.jar', jarBlob);
    }
  } catch {
    // If fetch fails, the wrapper jar is omitted; Android Studio can regenerate it
  }

  // Add README
  zip.file('README.md', README);

  return await zip.generateAsync({ type: 'blob' });
}
