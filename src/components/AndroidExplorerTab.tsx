import React, { useState } from 'react';
import { Folder, FileCode, Copy, Check, Terminal } from 'lucide-react';

interface AndroidFileItem {
  path: string;
  category: 'config' | 'protocol' | 'security' | 'transfer' | 'ui' | 'test';
  contentSnippet: string;
}

const ANDROID_FILES: AndroidFileItem[] = [
  {
    path: 'android/app/src/main/java/com/handoff/files/protocol/HandoffFramer.kt',
    category: 'protocol',
    contentSnippet: `package com.handoff.files.protocol

object HandoffFramer {
    fun readPacket(input: InputStream): HandoffPacket {
        val header = ByteArray(ProtocolConstants.HEADER_LENGTH)
        readFully(input, header)
        // 1. Validate 4-byte Magic 'HNDF'
        // 2. Validate Version (0x01)
        // 3. Extract Big-Endian uint32 length
        ...
    }
}`,
  },
  {
    path: 'android/app/src/main/java/com/handoff/files/security/CryptoEngine.kt',
    category: 'security',
    contentSnippet: `package com.handoff.files.security

object CryptoEngine {
    fun computeSharedSecret(myPrivateKey: PrivateKey, peerPublicKeyBytes: ByteArray): ByteArray
    fun deriveSessionKeys(sharedSecret: ByteArray, clientNonce: ByteArray, serverNonce: ByteArray, salt: ByteArray): SessionKeys
    fun encryptChunk(plaintext: ByteArray, aesKey: ByteArray, iv: ByteArray): EncryptedChunkResult
    fun decryptChunk(iv: ByteArray, ciphertext: ByteArray, tag: ByteArray, aesKey: ByteArray): ByteArray
    fun computeSha256(inputStream: InputStream): String
}`,
  },
  {
    path: 'android/app/src/main/java/com/handoff/files/transport/WebTransferServer.kt',
    category: 'transfer',
    contentSnippet: `package com.handoff.files.transport

class WebTransferServer(
    private val stagingManager: StagingFileManager,
    private val onApprovalRequested: suspend (filename: String, sizeBytes: Long, mimeType: String) -> Boolean,
    private val onFileReceived: (File) -> Unit
) {
    fun start(port: Int = 8080, pin: String, token: String, progressFlow: MutableStateFlow<TransferProgress>)
    fun stop()
    // Serves responsive HTML/JS to Windows desktop browsers
    // Handles /api/pair, /api/upload (max 10 MiB, SHA-256 verified), and /api/download
}`,
  },
  {
    path: 'android/app/src/main/java/com/handoff/files/transfer/FileStreamer.kt',
    category: 'transfer',
    contentSnippet: `package com.handoff.files.transfer

class FileStreamer(private val stagingManager: StagingFileManager) {
    suspend fun sendFile(...): Boolean
    suspend fun receiveFile(...): File?
}`,
  },
  {
    path: 'android/app/src/main/java/com/handoff/files/transfer/StagingFileManager.kt',
    category: 'transfer',
    contentSnippet: `package com.handoff.files.transfer

class StagingFileManager(stagingDirectory: File, destinationDirectory: File) {
    fun sanitizeFilename(rawName: String): String // strips ../ and ..\\
    fun verifyAndPublish(transferId: String, rawFilename: String, expectedSha256: String): File
    fun cleanupStagingFile(transferId: String)
}`,
  },
  {
    path: 'android/app/src/main/java/com/handoff/files/ui/TransferViewModel.kt',
    category: 'ui',
    contentSnippet: `package com.handoff.files.ui

class TransferViewModel(application: Application) : AndroidViewModel(application) {
    val uiState: StateFlow<TransferUiState>
    fun startReceiveMode()
    fun startSend(host: String, port: Int, qrToken: String?, manualPin: String?)
    fun approveIncomingTransfer(approved: Boolean)
    fun cancelTransfer()
}`,
  },
  {
    path: 'android/app/src/test/java/com/handoff/files/HandoffFramerTest.kt',
    category: 'test',
    contentSnippet: `package com.handoff.files

class HandoffFramerTest {
    @Test fun testTransferProposalRoundtrip()
    @Test fun testChunkDataRoundtrip()
    @Test fun testInvalidMagicThrowsProtocolException()
    @Test fun testUnsupportedVersionThrowsProtocolException()
}`,
  },
  {
    path: 'android/app/src/test/java/com/handoff/files/CryptoEngineTest.kt',
    category: 'test',
    contentSnippet: `package com.handoff.files

class CryptoEngineTest {
    @Test fun testEcdhKeyAgreementAndSessionDerivation()
    @Test fun testAesGcmEncryptionAndDecryptionRoundtrip()
    @Test fun testTamperedCiphertextFailsAuthenticationTag()
    @Test fun testSha256KnownTestVector()
}`,
  },
  {
    path: 'android/app/src/main/AndroidManifest.xml',
    category: 'config',
    contentSnippet: `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" android:usesPermissionFlags="neverForLocation" />
    <uses-permission android:name="android.permission.CAMERA" />
</manifest>`,
  },
];

export const AndroidExplorerTab: React.FC = () => {
  const [selectedFile, setSelectedFile] = useState<AndroidFileItem>(ANDROID_FILES[0]);
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(selectedFile.contentSnippet);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold text-white">Android Project Architecture & Codebase</h2>
        <p className="text-xs text-slate-400">
          Explore the production-ready Android Studio Hedgehog/Jellyfish Kotlin project structure and source files.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left: File Tree */}
        <div className="p-4 bg-slate-900 border border-slate-800 rounded-2xl space-y-3">
          <div className="flex items-center gap-2 text-xs font-bold text-slate-400 uppercase tracking-wider px-2">
            <Folder className="w-4 h-4 text-indigo-400" />
            <span>Project Files (/android)</span>
          </div>

          <div className="space-y-1">
            {ANDROID_FILES.map((file) => {
              const isSelected = selectedFile.path === file.path;
              const fileName = file.path.split('/').pop();
              return (
                <button
                  key={file.path}
                  onClick={() => setSelectedFile(file)}
                  className={`w-full text-left px-3 py-2 rounded-lg text-xs font-mono transition-colors flex items-center justify-between ${
                    isSelected
                      ? 'bg-indigo-600 text-white'
                      : 'text-slate-300 hover:bg-slate-800/60'
                  }`}
                >
                  <div className="flex items-center gap-2 truncate">
                    <FileCode className="w-3.5 h-3.5 shrink-0 opacity-70" />
                    <span className="truncate">{fileName}</span>
                  </div>
                  <span className="text-[10px] opacity-70 uppercase tracking-wider font-sans">
                    {file.category}
                  </span>
                </button>
              );
            })}
          </div>
        </div>

        {/* Right: Code Viewer */}
        <div className="lg:col-span-2 p-5 bg-slate-900 border border-slate-800 rounded-2xl space-y-4">
          <div className="flex items-center justify-between border-b border-slate-800 pb-3">
            <div className="text-xs font-mono text-slate-300 truncate">
              {selectedFile.path}
            </div>
            <button
              onClick={handleCopy}
              className="flex items-center gap-1.5 px-3 py-1 text-xs text-slate-300 hover:text-white bg-slate-800 hover:bg-slate-700 rounded-lg transition-colors shrink-0"
            >
              {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
              <span>{copied ? 'Copied' : 'Copy'}</span>
            </button>
          </div>

          <pre className="p-4 bg-slate-950 rounded-xl text-xs font-mono text-slate-300 overflow-x-auto leading-relaxed border border-slate-800/80 max-h-[500px]">
            {selectedFile.contentSnippet}
          </pre>
        </div>
      </div>
    </div>
  );
};
