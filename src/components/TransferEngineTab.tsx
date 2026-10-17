import React, { useState, useRef } from 'react';
import { UploadCloud, CheckCircle2, AlertCircle, Play, Pause, XCircle, FileText, Lock } from 'lucide-react';
import { computeSha256, decryptChunkAead, deriveMockSessionKeys, encryptChunkAead, generateRandomBytes } from '../utils/crypto';
import { DEFAULT_MAX_FILE_SIZE } from '../utils/framer';

export const TransferEngineTab: React.FC = () => {
  const [selectedFileName, setSelectedFileName] = useState('handoff_benchmark_sample.dat');
  const [selectedFileSize, setSelectedFileSize] = useState(2 * 1024 * 1024); // 2 MiB default
  const [selectedFileBuffer, setSelectedFileBuffer] = useState<Uint8Array | null>(null);

  const [isTransferring, setIsTransferring] = useState(false);
  const [progressFraction, setProgressFraction] = useState(0);
  const [bytesTransferred, setBytesTransferred] = useState(0);
  const [speedMib, setSpeedMib] = useState(0);
  const [elapsedSec, setElapsedSec] = useState(0);
  const [expectedSha256, setExpectedSha256] = useState<string | null>(null);
  const [receivedSha256, setReceivedSha256] = useState<string | null>(null);
  const [isVerified, setIsVerified] = useState<boolean | null>(null);
  const [statusText, setStatusText] = useState('Idle');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Receiver Approval Dialog State
  const [showApprovalDialog, setShowApprovalDialog] = useState(false);
  const approvalResolverRef = useRef<((accepted: boolean) => void) | null>(null);
  const isCancelledRef = useRef(false);

  const handleSelectSample = (sizeMib: number) => {
    const bytes = sizeMib * 1024 * 1024;
    setSelectedFileName(`sample_${sizeMib}mib.dat`);
    setSelectedFileSize(bytes);

    // Generate in-memory buffer
    const buf = new Uint8Array(bytes);
    for (let i = 0; i < bytes; i += 1024) {
      buf[i] = (i / 1024) % 256;
    }
    setSelectedFileBuffer(buf);
    resetState();
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setSelectedFileName(file.name);
    setSelectedFileSize(file.size);

    const reader = new FileReader();
    reader.onload = () => {
      setSelectedFileBuffer(new Uint8Array(reader.result as ArrayBuffer));
    };
    reader.readAsArrayBuffer(file);
    resetState();
  };

  const resetState = () => {
    setIsTransferring(false);
    setProgressFraction(0);
    setBytesTransferred(0);
    setSpeedMib(0);
    setElapsedSec(0);
    setExpectedSha256(null);
    setReceivedSha256(null);
    setIsVerified(null);
    setStatusText('Idle');
    setErrorMessage(null);
    setShowApprovalDialog(false);
    isCancelledRef.current = false;
  };

  const handleStartTransfer = async () => {
    resetState();
    setErrorMessage(null);

    // 1. Enforce 10 MiB limit
    if (selectedFileSize > DEFAULT_MAX_FILE_SIZE) {
      setErrorMessage(`File exceeds the 10 MiB limit (${(selectedFileSize / (1024 * 1024)).toFixed(2)} MiB). Transfer rejected.`);
      return;
    }

    const fileBytes = selectedFileBuffer || new Uint8Array(selectedFileSize);
    setIsTransferring(true);
    setStatusText('Calculating SHA-256 manifest digest...');

    // 2. Pre-calculate sender SHA-256
    const expectedHash = await computeSha256(fileBytes);
    setExpectedSha256(expectedHash);

    // 3. Trigger receiver explicit approval
    setStatusText('Waiting for receiver approval...');
    setShowApprovalDialog(true);

    const approved = await new Promise<boolean>((resolve) => {
      approvalResolverRef.current = resolve;
    });

    setShowApprovalDialog(false);

    if (!approved) {
      setIsTransferring(false);
      setStatusText('Transfer rejected by receiver');
      setErrorMessage('Receiver declined the transfer proposal.');
      return;
    }

    // 4. Session key agreement & HKDF derivation
    setStatusText('Deriving AES-256-GCM session keys via HKDF-SHA256...');
    const clientNonce = generateRandomBytes(12);
    const serverNonce = generateRandomBytes(12);
    const { key } = await deriveMockSessionKeys(clientNonce, serverNonce);

    // 5. Streaming loop in bounded 64 KiB chunks
    setStatusText('Streaming encrypted AES-256-GCM chunks...');
    const chunkSize = 64 * 1024;
    const totalChunks = Math.ceil(fileBytes.length / chunkSize);
    const decryptedSink: Uint8Array[] = [];

    const startTime = performance.now();
    let sent = 0;

    for (let i = 0; i < totalChunks; i++) {
      if (isCancelledRef.current) {
        setIsTransferring(false);
        setStatusText('Transfer cancelled');
        return;
      }

      const start = i * chunkSize;
      const end = Math.min(start + chunkSize, fileBytes.length);
      const plaintextChunk = fileBytes.slice(start, end);

      // Encrypt with AES-GCM
      const iv = generateRandomBytes(12);
      const { ciphertext, tag } = await encryptChunkAead(plaintextChunk, key, iv);

      // Decrypt on simulated receiver
      const decrypted = await decryptChunkAead(iv, ciphertext, tag, key);
      decryptedSink.push(decrypted);

      sent += plaintextChunk.length;
      setBytesTransferred(sent);
      setProgressFraction(sent / fileBytes.length);

      const elapsedMs = Math.max(1, performance.now() - startTime);
      const mibPerSec = (sent / (1024 * 1024)) / (elapsedMs / 1000);
      setSpeedMib(mibPerSec);
      setElapsedSec(Math.round(elapsedMs / 1000));

      // Realistic non-blocking latency tick
      await new Promise((r) => setTimeout(r, 15));
    }

    // 6. Assemble receiver buffer and compute SHA-256
    setStatusText('Verifying received file integrity...');
    const totalReceivedLength = decryptedSink.reduce((acc, c) => acc + c.length, 0);
    const assembledReceived = new Uint8Array(totalReceivedLength);
    let offset = 0;
    for (const chunk of decryptedSink) {
      assembledReceived.set(chunk, offset);
      offset += chunk.length;
    }

    const calculatedHash = await computeSha256(assembledReceived);
    setReceivedSha256(calculatedHash);

    const matches = calculatedHash === expectedHash;
    setIsVerified(matches);
    setIsTransferring(false);
    setStatusText(matches ? 'Transfer Completed & Verified' : 'Integrity Mismatch Failure');
  };

  const handleCancel = () => {
    isCancelledRef.current = true;
    setIsTransferring(false);
    setStatusText('Transfer cancelled');
  };

  const isOversized = selectedFileSize > DEFAULT_MAX_FILE_SIZE;

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold text-white">Interactive Transfer Engine Sandbox</h2>
        <p className="text-xs text-slate-400">
          Simulate end-to-end peer file streaming with real WebCrypto AES-256-GCM encryption, 64 KiB chunking, and SHA-256 integrity verification.
        </p>
      </div>

      {/* Receiver Approval Modal */}
      {showApprovalDialog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
          <div className="bg-slate-900 border border-slate-700 rounded-2xl p-6 max-w-md w-full space-y-4 shadow-2xl">
            <h3 className="text-base font-bold text-white">Receiver Transfer Request</h3>
            <p className="text-xs text-slate-300">
              A peer device is requesting permission to transfer a file:
            </p>

            <div className="p-3.5 bg-slate-950 rounded-xl border border-slate-800 space-y-1 text-xs">
              <div className="font-semibold text-white">{selectedFileName}</div>
              <div className="text-slate-400">
                {(selectedFileSize / (1024 * 1024)).toFixed(2)} MiB · application/octet-stream
              </div>
            </div>

            <div className="flex justify-end gap-3 pt-2">
              <button
                onClick={() => approvalResolverRef.current?.(false)}
                className="px-4 py-2 text-xs font-semibold text-rose-300 hover:text-white bg-rose-950/40 hover:bg-rose-900/60 rounded-lg transition-colors"
              >
                Decline
              </button>
              <button
                onClick={() => approvalResolverRef.current?.(true)}
                className="px-4 py-2 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg transition-colors"
              >
                Accept & Stream
              </button>
            </div>
          </div>
        </div>
      )}

      {/* File Selection Card */}
      <div className="p-6 bg-slate-900 border border-slate-800 rounded-2xl space-y-4">
        <h3 className="text-sm font-bold text-white">1. Select or Generate Test File</h3>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Quick Preset Buttons */}
          <div className="space-y-2">
            <label className="block text-xs font-medium text-slate-400">Quick Test Fixtures</label>
            <div className="flex flex-wrap gap-2 text-xs">
              <button
                onClick={() => handleSelectSample(0.1)}
                className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg"
              >
                100 KiB
              </button>
              <button
                onClick={() => handleSelectSample(1)}
                className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg"
              >
                1 MiB
              </button>
              <button
                onClick={() => handleSelectSample(5)}
                className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg"
              >
                5 MiB
              </button>
              <button
                onClick={() => handleSelectSample(10)}
                className="px-3 py-1.5 bg-indigo-600/30 border border-indigo-500/40 hover:bg-indigo-600/50 text-indigo-200 rounded-lg font-semibold"
              >
                10 MiB (Benchmark Target)
              </button>
              <button
                onClick={() => handleSelectSample(11)}
                className="px-3 py-1.5 bg-rose-950/40 border border-rose-800/40 hover:bg-rose-900/50 text-rose-300 rounded-lg"
              >
                11 MiB (Test Limit Rejection)
              </button>
            </div>
          </div>

          {/* Local File Picker */}
          <div className="space-y-2">
            <label className="block text-xs font-medium text-slate-400">Or Pick File From Computer</label>
            <input
              type="file"
              onChange={handleFileUpload}
              className="w-full text-xs text-slate-300 file:mr-3 file:py-1.5 file:px-3 file:rounded-lg file:border-0 file:text-xs file:font-semibold file:bg-slate-800 file:text-slate-200 hover:file:bg-slate-700"
            />
          </div>
        </div>

        {/* Selected File Badge */}
        <div className="flex items-center justify-between p-3.5 bg-slate-950 rounded-xl border border-slate-800/80 text-xs">
          <div className="flex items-center gap-2.5">
            <FileText className="w-4 h-4 text-indigo-400 shrink-0" />
            <div>
              <span className="font-semibold text-white">{selectedFileName}</span>
              <span className="text-slate-400 ml-2">
                ({(selectedFileSize / (1024 * 1024)).toFixed(2)} MiB / {selectedFileSize.toLocaleString()} bytes)
              </span>
            </div>
          </div>

          {isOversized ? (
            <span className="px-2 py-0.5 text-[11px] font-bold text-rose-400 bg-rose-950/60 rounded">
              EXCEEDS 10 MiB LIMIT
            </span>
          ) : (
            <span className="px-2 py-0.5 text-[11px] font-semibold text-emerald-400 bg-emerald-950/40 rounded">
              READY
            </span>
          )}
        </div>

        {errorMessage && (
          <div className="p-3 bg-rose-950/30 border border-rose-900/50 rounded-lg text-xs text-rose-300 flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{errorMessage}</span>
          </div>
        )}
      </div>

      {/* Live Transfer Card */}
      <div className="p-6 bg-slate-900 border border-slate-800 rounded-2xl space-y-5">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Lock className="w-4 h-4 text-emerald-400" />
            <h3 className="text-sm font-bold text-white">2. AES-256-GCM Streaming Pipeline</h3>
          </div>
          <span className="text-xs font-mono text-slate-400">{statusText}</span>
        </div>

        {/* Progress Bar */}
        <div className="space-y-2">
          <div className="w-full bg-slate-950 rounded-full h-3 overflow-hidden border border-slate-800">
            <div
              className={`h-full transition-all duration-150 ${
                isVerified === true
                  ? 'bg-emerald-500'
                  : isVerified === false
                  ? 'bg-rose-500'
                  : 'bg-indigo-500'
              }`}
              style={{ width: `${Math.round(progressFraction * 100)}%` }}
            />
          </div>

          <div className="flex items-center justify-between text-xs font-mono tabular-nums text-slate-400">
            <span>
              {(bytesTransferred / (1024 * 1024)).toFixed(2)} / {(selectedFileSize / (1024 * 1024)).toFixed(2)} MiB
            </span>
            <span className="font-bold text-white">{Math.round(progressFraction * 100)}%</span>
            <span>{speedMib.toFixed(2)} MiB/s</span>
            <span>Elapsed: {elapsedSec}s</span>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex gap-3 pt-2">
          {!isTransferring ? (
            <button
              onClick={handleStartTransfer}
              disabled={isOversized}
              className="px-5 py-2.5 text-xs font-bold text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg transition-colors flex items-center gap-2 disabled:opacity-40"
            >
              <Play className="w-3.5 h-3.5" />
              <span>Start Streaming Transfer</span>
            </button>
          ) : (
            <button
              onClick={handleCancel}
              className="px-5 py-2.5 text-xs font-bold text-rose-300 hover:text-white bg-rose-950/40 hover:bg-rose-900 rounded-lg transition-colors flex items-center gap-2"
            >
              <XCircle className="w-3.5 h-3.5" />
              <span>Cancel Transfer</span>
            </button>
          )}
        </div>

        {/* Checksum Verification Box */}
        {expectedSha256 && (
          <div className="p-4 bg-slate-950 rounded-xl border border-slate-800 space-y-2 text-xs font-mono">
            <div className="flex items-center justify-between">
              <span className="text-slate-400 font-sans">Sender Declared SHA-256:</span>
              <span className="text-indigo-400 truncate max-w-sm">{expectedSha256}</span>
            </div>
            {receivedSha256 && (
              <div className="flex items-center justify-between">
                <span className="text-slate-400 font-sans">Receiver Verified SHA-256:</span>
                <span className="text-emerald-400 truncate max-w-sm">{receivedSha256}</span>
              </div>
            )}
            {isVerified !== null && (
              <div
                className={`pt-2 flex items-center gap-2 font-sans font-bold ${
                  isVerified ? 'text-emerald-400' : 'text-rose-400'
                }`}
              >
                {isVerified ? (
                  <>
                    <CheckCircle2 className="w-4 h-4" />
                    <span>Integrity Verified: Digests match 100%. File promoted safely.</span>
                  </>
                ) : (
                  <>
                    <AlertCircle className="w-4 h-4" />
                    <span>Integrity Failed: Digest mismatch. Staging file deleted.</span>
                  </>
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
