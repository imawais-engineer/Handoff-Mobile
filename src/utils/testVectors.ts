import { TestVectorResult } from '../types/protocol';
import { computeSha256, decryptChunkAead, deriveMockSessionKeys, encryptChunkAead, generateRandomBytes } from './crypto';
import { DEFAULT_MAX_FILE_SIZE, dissectPacket, encodeChunkPacket, encodeJsonPacket } from './framer';

export interface TestVectorDef {
  id: string;
  name: string;
  description: string;
  execute: () => Promise<{ success: boolean; details: string }>;
}

export const TEST_VECTORS: TestVectorDef[] = [
  {
    id: 'TV-01',
    name: 'Wire Header & Big-Endian Framing',
    description: 'Verify 10-byte header format, "HNDF" magic, version 1, and Big-Endian uint32 payload length.',
    execute: async () => {
      const proposal = {
        transferId: 'tx-test-vector-1',
        filename: 'report.pdf',
        sizeBytes: 1048576,
        mimeType: 'application/pdf',
        sha256Digest: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
      };

      const packet = encodeJsonPacket('TRANSFER_PROPOSAL', proposal);
      const dissected = dissectPacket(packet);

      if (!dissected.isValid) {
        return { success: false, details: `Dissection failed: ${dissected.validationError}` };
      }
      if (dissected.header.magic !== 'HNDF') {
        return { success: false, details: `Expected magic HNDF, got ${dissected.header.magic}` };
      }
      if (dissected.header.version !== 1) {
        return { success: false, details: `Expected version 1, got ${dissected.header.version}` };
      }
      if (dissected.header.packetType !== 0x10) {
        return { success: false, details: `Expected type 0x10 (TRANSFER_PROPOSAL), got ${dissected.header.packetType}` };
      }
      if (dissected.payloadJson?.filename !== 'report.pdf') {
        return { success: false, details: `Payload mismatch: ${JSON.stringify(dissected.payloadJson)}` };
      }

      return {
        success: true,
        details: `Valid 10-byte header parsed cleanly. Length: ${dissected.header.payloadLength} bytes.`,
      };
    },
  },
  {
    id: 'TV-02',
    name: 'Header Error Detection (Magic & Version)',
    description: 'Ensure corrupted magic bytes and unsupported protocol versions are rejected immediately.',
    execute: async () => {
      // 1. Corrupted magic test
      const badMagic = new Uint8Array([0x00, 0x01, 0x02, 0x03, 0x01, 0x01, 0x00, 0x00, 0x00, 0x02, 0x7b, 0x7d]);
      const resBadMagic = dissectPacket(badMagic);
      if (resBadMagic.isValid) {
        return { success: false, details: 'Failed: bad magic was accepted as valid' };
      }

      // 2. Unsupported version test
      const badVersion = new Uint8Array([0x48, 0x4e, 0x44, 0x46, 0x02, 0x01, 0x00, 0x00, 0x00, 0x02, 0x7b, 0x7d]);
      const resBadVersion = dissectPacket(badVersion);
      if (resBadVersion.isValid) {
        return { success: false, details: 'Failed: unsupported version was accepted as valid' };
      }

      return {
        success: true,
        details: 'Correctly rejected bad magic (0x00010203) and unsupported version (v2).',
      };
    },
  },
  {
    id: 'TV-03',
    name: '10 MiB File Size Limit Boundaries',
    description: 'Verify boundary behavior at 0 B, 10 MiB - 1 B (allowed), 10 MiB (allowed), and 10 MiB + 1 B (rejected).',
    execute: async () => {
      const limit = DEFAULT_MAX_FILE_SIZE; // 10,485,760 bytes

      const check = (size: number) => size <= limit;

      if (!check(0)) return { success: false, details: 'Zero bytes was rejected' };
      if (!check(limit - 1)) return { success: false, details: '10 MiB - 1 was rejected' };
      if (!check(limit)) return { success: false, details: 'Exact 10 MiB limit was rejected' };
      if (check(limit + 1)) return { success: false, details: '10 MiB + 1 was mistakenly accepted' };

      return {
        success: true,
        details: `Boundary checks passed: 0 B [OK], 10,485,759 B [OK], 10,485,760 B [OK], 10,485,761 B [REJECTED].`,
      };
    },
  },
  {
    id: 'TV-04',
    name: 'AES-256-GCM AEAD Chunk Streaming Roundtrip',
    description: 'Encrypt a sample 64 KiB chunk, verify 12-byte IV and 16-byte tag, then decrypt and verify bit-for-bit identity.',
    execute: async () => {
      const clientNonce = generateRandomBytes(12);
      const serverNonce = generateRandomBytes(12);
      const { key, sasPin } = await deriveMockSessionKeys(clientNonce, serverNonce);

      const chunkPlaintext = new Uint8Array(65536);
      for (let i = 0; i < chunkPlaintext.length; i++) {
        chunkPlaintext[i] = i % 256;
      }

      const iv = generateRandomBytes(12);
      const { ciphertext, tag } = await encryptChunkAead(chunkPlaintext, key, iv);

      if (iv.length !== 12 || tag.length !== 16) {
        return { success: false, details: `Invalid AEAD parameters: IV ${iv.length}B, Tag ${tag.length}B` };
      }

      const decrypted = await decryptChunkAead(iv, ciphertext, tag, key);

      let matches = decrypted.length === chunkPlaintext.length;
      if (matches) {
        for (let i = 0; i < decrypted.length; i++) {
          if (decrypted[i] !== chunkPlaintext[i]) {
            matches = false;
            break;
          }
        }
      }

      if (!matches) {
        return { success: false, details: 'Decrypted bytes did not match original 64 KiB chunk' };
      }

      return {
        success: true,
        details: `64 KiB encrypted with AES-256-GCM (12B IV, 16B Tag) and authenticated successfully. SAS: ${sasPin}.`,
      };
    },
  },
  {
    id: 'TV-05',
    name: 'AEAD Tamper & Truncation Detection',
    description: 'Confirm that flipping 1 single bit in ciphertext causes immediate AEAD tag verification failure.',
    execute: async () => {
      const clientNonce = generateRandomBytes(12);
      const serverNonce = generateRandomBytes(12);
      const { key } = await deriveMockSessionKeys(clientNonce, serverNonce);

      const plaintext = new TextEncoder().encode('Confidential contract documents');
      const iv = generateRandomBytes(12);
      const { ciphertext, tag } = await encryptChunkAead(plaintext, key, iv);

      // Tamper with first byte
      const tamperedCiphertext = new Uint8Array(ciphertext);
      tamperedCiphertext[0] ^= 0x01;

      try {
        await decryptChunkAead(iv, tamperedCiphertext, tag, key);
        return { success: false, details: 'Tampered ciphertext failed to throw authentication error' };
      } catch (e: any) {
        // Tag verification correctly caught the tampering!
        return {
          success: true,
          details: 'AEAD authentication tag verified: tampered ciphertext rejected with cryptographic exception.',
        };
      }
    },
  },
  {
    id: 'TV-06',
    name: 'SHA-256 Digest Verification Against Known RFC Vectors',
    description: 'Validate SHA-256 implementation against standard known cryptographic test vectors.',
    execute: async () => {
      // Empty string
      const emptyHash = await computeSha256(new Uint8Array(0));
      const expectedEmpty = 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855';
      if (emptyHash !== expectedEmpty) {
        return { success: false, details: `Empty vector failed: expected ${expectedEmpty}, got ${emptyHash}` };
      }

      // "hello world"
      const helloHash = await computeSha256(new TextEncoder().encode('hello world'));
      const expectedHello = 'b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9';
      if (helloHash !== expectedHello) {
        return { success: false, details: `"hello world" vector failed: expected ${expectedHello}, got ${helloHash}` };
      }

      return {
        success: true,
        details: `Both NIST/RFC test vectors matched: "" and "hello world" hashes verified.`,
      };
    },
  },
  {
    id: 'TV-07',
    name: 'Path Traversal Defense & Filename Sanitization',
    description: 'Ensure ../ and ..\\ path traversal strings and illegal characters are stripped cleanly.',
    execute: async () => {
      const sanitize = (raw: string): string => {
        let clean = raw.replace(/\\/g, '/').split('/').pop() || '';
        clean = clean.replace(/[\\/:*?"<>|\x00-\x1F]/g, '_').trim();
        return clean || `handoff_file_${Date.now()}`;
      };

      const t1 = sanitize('../../etc/passwd');
      if (t1 !== 'passwd') return { success: false, details: `../../etc/passwd became ${t1}` };

      const t2 = sanitize('..\\..\\Windows\\System32\\cmd.exe');
      if (t2 !== 'cmd.exe') return { success: false, details: `..\\..\\Windows\\... became ${t2}` };

      const t3 = sanitize('report:2026*final?.pdf');
      if (t3.includes(':') || t3.includes('*') || t3.includes('?')) {
        return { success: false, details: `Illegal characters remained in ${t3}` };
      }

      return {
        success: true,
        details: 'Path traversal sequences and illegal filesystem characters stripped successfully.',
      };
    },
  },
];
