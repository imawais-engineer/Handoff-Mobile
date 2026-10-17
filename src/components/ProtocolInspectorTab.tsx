import React, { useState, useMemo } from 'react';
import { PACKET_TYPES, PACKET_TYPE_CODES, dissectPacket, encodeChunkPacket, encodeJsonPacket } from '../utils/framer';
import { PacketTypeName } from '../types/protocol';
import { bytesToHex, hexToBytes } from '../utils/crypto';
import { AlertCircle, CheckCircle2, RefreshCw } from 'lucide-react';

export const ProtocolInspectorTab: React.FC = () => {
  const [selectedType, setSelectedType] = useState<PacketTypeName>('TRANSFER_PROPOSAL');
  const [filename, setFilename] = useState('project_spec.pdf');
  const [fileSizeBytes, setFileSizeBytes] = useState(5242880); // 5 MiB
  const [mimeType, setMimeType] = useState('application/pdf');
  const [sha256Digest, setSha256Digest] = useState('e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855');
  const [tamperMagic, setTamperMagic] = useState(false);
  const [tamperVersion, setTamperVersion] = useState(false);

  // Generate packet
  const packetBytes = useMemo(() => {
    let bytes: Uint8Array;
    if (selectedType === 'TRANSFER_PROPOSAL') {
      bytes = encodeJsonPacket('TRANSFER_PROPOSAL', {
        transferId: 'tx-8041-92b',
        filename,
        sizeBytes: Number(fileSizeBytes),
        mimeType,
        sha256Digest,
      });
    } else if (selectedType === 'CHUNK_DATA') {
      const iv = new Uint8Array(12).fill(0x0a);
      const ciphertext = new Uint8Array(256).fill(0x7f);
      const tag = new Uint8Array(16).fill(0xee);
      bytes = encodeChunkPacket('tx-8041-92b', 0, iv, ciphertext, tag);
    } else if (selectedType === 'PAIR_KEY_EXCHANGE') {
      bytes = encodeJsonPacket('PAIR_KEY_EXCHANGE', {
        ephemeralPublicKeyHex: '043f7a8b9c...e01d',
        clientNonceHex: 'd190f84a7e2b10499e0f1a23',
        deviceName: 'Pixel 8 Pro',
      });
    } else if (selectedType === 'TRANSFER_COMPLETE') {
      bytes = encodeJsonPacket('TRANSFER_COMPLETE', {
        transferId: 'tx-8041-92b',
        clientSha256: sha256Digest,
      });
    } else {
      bytes = encodeJsonPacket('ERROR', {
        errorCode: 300,
        message: 'File size limit exceeded',
      });
    }

    if (tamperMagic) {
      const modified = new Uint8Array(bytes);
      modified[0] = 0x58; // 'X' instead of 'H'
      return modified;
    }
    if (tamperVersion) {
      const modified = new Uint8Array(bytes);
      modified[4] = 0x99; // Version 0x99
      return modified;
    }
    return bytes;
  }, [selectedType, filename, fileSizeBytes, mimeType, sha256Digest, tamperMagic, tamperVersion]);

  // Dissect packet
  const dissection = useMemo(() => {
    return dissectPacket(packetBytes);
  }, [packetBytes]);

  const rawHexFormatted = useMemo(() => {
    const hex = bytesToHex(packetBytes);
    const chunks = [];
    for (let i = 0; i < hex.length; i += 2) {
      chunks.push(hex.substring(i, i + 2));
    }
    return chunks;
  }, [packetBytes]);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-white">Wire Protocol Inspector & Hex Dissector</h2>
          <p className="text-xs text-slate-400">
            Construct, serialize, and dissect Big-Endian 10-byte wire frames with live payload inspection.
          </p>
        </div>

        {/* Packet Type Selector */}
        <div className="flex items-center gap-2">
          <label className="text-xs text-slate-400 font-medium">Type:</label>
          <select
            value={selectedType}
            onChange={(e) => setSelectedType(e.target.value as PacketTypeName)}
            className="bg-slate-900 border border-slate-700 text-xs text-white rounded-lg px-3 py-1.5 focus:outline-none focus:border-indigo-500"
          >
            <option value="TRANSFER_PROPOSAL">TRANSFER_PROPOSAL (0x10)</option>
            <option value="CHUNK_DATA">CHUNK_DATA (0x20)</option>
            <option value="PAIR_KEY_EXCHANGE">PAIR_KEY_EXCHANGE (0x01)</option>
            <option value="TRANSFER_COMPLETE">TRANSFER_COMPLETE (0x22)</option>
            <option value="ERROR">ERROR (0xFF)</option>
          </select>
        </div>
      </div>

      {/* Configuration Controls */}
      {selectedType === 'TRANSFER_PROPOSAL' && (
        <div className="p-4 bg-slate-900 border border-slate-800 rounded-xl grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
          <div>
            <label className="block text-slate-400 mb-1">Filename</label>
            <input
              type="text"
              value={filename}
              onChange={(e) => setFilename(e.target.value)}
              className="w-full bg-slate-950 border border-slate-800 rounded px-2.5 py-1.5 text-white"
            />
          </div>
          <div>
            <label className="block text-slate-400 mb-1">Size in Bytes (Max 10,485,760)</label>
            <input
              type="number"
              value={fileSizeBytes}
              onChange={(e) => setFileSizeBytes(Number(e.target.value))}
              className="w-full bg-slate-950 border border-slate-800 rounded px-2.5 py-1.5 text-white font-mono"
            />
          </div>
          <div>
            <label className="block text-slate-400 mb-1">MIME Type</label>
            <input
              type="text"
              value={mimeType}
              onChange={(e) => setMimeType(e.target.value)}
              className="w-full bg-slate-950 border border-slate-800 rounded px-2.5 py-1.5 text-white"
            />
          </div>
        </div>
      )}

      {/* Tamper / Chaos Injection Toggles */}
      <div className="flex flex-wrap items-center gap-4 text-xs">
        <span className="text-slate-400 font-medium">Inject Wire Faults:</span>
        <label className="flex items-center gap-2 cursor-pointer text-slate-300">
          <input
            type="checkbox"
            checked={tamperMagic}
            onChange={(e) => {
              setTamperMagic(e.target.checked);
              if (e.target.checked) setTamperVersion(false);
            }}
            className="rounded border-slate-700 text-indigo-600 focus:ring-0"
          />
          <span>Corrupt Magic ('XNDF' instead of 'HNDF')</span>
        </label>
        <label className="flex items-center gap-2 cursor-pointer text-slate-300">
          <input
            type="checkbox"
            checked={tamperVersion}
            onChange={(e) => {
              setTamperVersion(e.target.checked);
              if (e.target.checked) setTamperMagic(false);
            }}
            className="rounded border-slate-700 text-indigo-600 focus:ring-0"
          />
          <span>Corrupt Version (v0x99)</span>
        </label>
      </div>

      {/* Dissection Status Badge */}
      <div
        className={`p-4 rounded-xl border flex items-center justify-between ${
          dissection.isValid
            ? 'bg-emerald-950/20 border-emerald-900/50 text-emerald-300'
            : 'bg-rose-950/20 border-rose-900/50 text-rose-300'
        }`}
      >
        <div className="flex items-center gap-3">
          {dissection.isValid ? (
            <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0" />
          ) : (
            <AlertCircle className="w-5 h-5 text-rose-400 shrink-0" />
          )}
          <div>
            <div className="font-semibold text-xs">
              {dissection.isValid ? 'Wire Packet Valid & Decodable' : 'Packet Header / Framing Failure'}
            </div>
            <div className="text-xs opacity-80">
              {dissection.isValid
                ? `Header parsed successfully. Declared payload length: ${dissection.header.payloadLength} bytes.`
                : dissection.validationError}
            </div>
          </div>
        </div>
        <div className="text-xs font-mono tabular-nums">
          Total Length: {packetBytes.length} B
        </div>
      </div>

      {/* Raw Hex Dissection Grid */}
      <div className="p-5 bg-slate-900 border border-slate-800 rounded-xl space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-xs font-bold text-slate-300 uppercase tracking-wider">
            Raw Byte Dump (Network Order / Big-Endian)
          </h3>
          <div className="flex items-center gap-3 text-xs">
            <span className="flex items-center gap-1.5 text-blue-400">
              <span className="w-2.5 h-2.5 rounded-sm bg-blue-500" />
              <span>Magic (0..3)</span>
            </span>
            <span className="flex items-center gap-1.5 text-purple-400">
              <span className="w-2.5 h-2.5 rounded-sm bg-purple-500" />
              <span>Ver (4)</span>
            </span>
            <span className="flex items-center gap-1.5 text-emerald-400">
              <span className="w-2.5 h-2.5 rounded-sm bg-emerald-500" />
              <span>Type (5)</span>
            </span>
            <span className="flex items-center gap-1.5 text-amber-400">
              <span className="w-2.5 h-2.5 rounded-sm bg-amber-500" />
              <span>Len (6..9)</span>
            </span>
          </div>
        </div>

        <div className="p-4 bg-slate-950 rounded-lg font-mono text-xs leading-relaxed break-all border border-slate-800/80">
          {rawHexFormatted.map((byte, idx) => {
            let colorClass = 'text-slate-400';
            if (idx >= 0 && idx < 4) colorClass = 'text-blue-400 font-bold bg-blue-500/10 rounded px-0.5';
            else if (idx === 4) colorClass = 'text-purple-400 font-bold bg-purple-500/10 rounded px-0.5';
            else if (idx === 5) colorClass = 'text-emerald-400 font-bold bg-emerald-500/10 rounded px-0.5';
            else if (idx >= 6 && idx < 10) colorClass = 'text-amber-400 font-bold bg-amber-500/10 rounded px-0.5';

            return (
              <span key={idx} className={`inline-block mr-1.5 mb-1 ${colorClass}`}>
                {byte}
              </span>
            );
          })}
        </div>
      </div>

      {/* Parsed Structure Details */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="p-5 bg-slate-900 border border-slate-800 rounded-xl space-y-3">
          <h3 className="text-xs font-bold text-slate-300 uppercase tracking-wider">
            10-Byte Header Fields
          </h3>
          <div className="space-y-2 text-xs font-mono">
            <div className="flex justify-between py-1 border-b border-slate-800">
              <span className="text-slate-400">Magic (Bytes 0..3):</span>
              <span className="text-blue-400">{dissection.header.magic || 'N/A'}</span>
            </div>
            <div className="flex justify-between py-1 border-b border-slate-800">
              <span className="text-slate-400">Version (Byte 4):</span>
              <span className="text-purple-400">0x{dissection.header.version.toString(16).padStart(2, '0')} ({dissection.header.version})</span>
            </div>
            <div className="flex justify-between py-1 border-b border-slate-800">
              <span className="text-slate-400">Packet Type (Byte 5):</span>
              <span className="text-emerald-400">0x{dissection.header.packetType.toString(16).padStart(2, '0')} ({dissection.typeName})</span>
            </div>
            <div className="flex justify-between py-1 border-b border-slate-800">
              <span className="text-slate-400">Payload Length (Bytes 6..9):</span>
              <span className="text-amber-400 tabular-nums">{dissection.header.payloadLength} Bytes</span>
            </div>
          </div>
        </div>

        <div className="p-5 bg-slate-900 border border-slate-800 rounded-xl space-y-3">
          <h3 className="text-xs font-bold text-slate-300 uppercase tracking-wider">
            Decoded Payload Model
          </h3>
          <div className="p-3 bg-slate-950 rounded-lg text-xs font-mono text-slate-300 overflow-x-auto max-h-48 border border-slate-800">
            {dissection.chunkDetails ? (
              <pre>{JSON.stringify(dissection.chunkDetails, null, 2)}</pre>
            ) : dissection.payloadJson ? (
              <pre>{JSON.stringify(dissection.payloadJson, null, 2)}</pre>
            ) : (
              <span className="text-slate-500">Payload not decoded</span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
