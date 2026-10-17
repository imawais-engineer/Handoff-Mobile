/**
 * Web Cryptography implementation matching Android's CryptoEngine
 * (AES-256-GCM, HKDF-SHA256, SHA-256 stream digest, SAS derivation)
 */

export function bytesToHex(bytes: Uint8Array): string {
  return Array.from(bytes)
    .map(b => b.toString(16).padStart(2, '0'))
    .join('');
}

export function hexToBytes(hex: string): Uint8Array {
  const clean = hex.trim().replace(/^0x/, '');
  const bytes = new Uint8Array(clean.length / 2);
  for (let i = 0; i < clean.length; i += 2) {
    bytes[i / 2] = parseInt(clean.substring(i, i + 2), 16);
  }
  return bytes;
}

export function generateRandomBytes(length: number): Uint8Array {
  const bytes = new Uint8Array(length);
  window.crypto.getRandomValues(bytes);
  return bytes;
}

export async function computeSha256(data: ArrayBuffer | Uint8Array): Promise<string> {
  const bufferSource = data instanceof Uint8Array ? data : new Uint8Array(data);
  const hashBuffer = await window.crypto.subtle.digest('SHA-256', bufferSource as unknown as BufferSource);
  return bytesToHex(new Uint8Array(hashBuffer));
}

/**
 * Derives AES-256-GCM CryptoKey and 6-digit Short Authentication String (SAS)
 * using HKDF-SHA256 from client and server nonces.
 */
export async function deriveMockSessionKeys(clientNonce: Uint8Array, serverNonce: Uint8Array): Promise<{
  key: CryptoKey;
  rawKeyHex: string;
  sasPin: string;
}> {
  // Combine salt = clientNonce + serverNonce
  const salt = new Uint8Array(clientNonce.length + serverNonce.length);
  salt.set(clientNonce, 0);
  salt.set(serverNonce, clientNonce.length);

  // Pseudo-random master secret for simulation
  const dummySecret = new Uint8Array(32);
  dummySecret.fill(0x42);

  const keyMaterial = await window.crypto.subtle.importKey(
    'raw',
    dummySecret,
    { name: 'HKDF' },
    false,
    ['deriveKey', 'deriveBits']
  );

  // Derive AES-256-GCM key
  const aesKey = await window.crypto.subtle.deriveKey(
    {
      name: 'HKDF',
      hash: 'SHA-256',
      salt: salt,
      info: new TextEncoder().encode('HandoffFiles-v1-AES256GCM')
    },
    keyMaterial,
    { name: 'AES-GCM', length: 256 },
    true,
    ['encrypt', 'decrypt']
  );

  const exportedRaw = await window.crypto.subtle.exportKey('raw', aesKey);
  const rawKeyHex = bytesToHex(new Uint8Array(exportedRaw));

  // Derive 4 bytes for SAS PIN
  const sasBits = await window.crypto.subtle.deriveBits(
    {
      name: 'HKDF',
      hash: 'SHA-256',
      salt: salt,
      info: new TextEncoder().encode('HandoffFiles-v1-SAS')
    },
    keyMaterial,
    32 // 4 bytes
  );

  const view = new DataView(sasBits);
  const sasInt = (view.getUint32(0, false) & 0x7fffffff) % 1_000_000;
  const sasPin = sasInt.toString().padStart(6, '0');

  return { key: aesKey, rawKeyHex, sasPin };
}

/**
 * Encrypts data using AES-GCM (12-byte IV, 16-byte tag).
 * WebCrypto appends the 16-byte tag to the ciphertext.
 */
export async function encryptChunkAead(
  plaintext: Uint8Array,
  key: CryptoKey,
  iv: Uint8Array
): Promise<{ ciphertext: Uint8Array; tag: Uint8Array }> {
  const encryptedBuffer = await window.crypto.subtle.encrypt(
    { name: 'AES-GCM', iv: iv as unknown as BufferSource, tagLength: 128 },
    key,
    plaintext as unknown as BufferSource
  );

  const combined = new Uint8Array(encryptedBuffer);
  const tagLength = 16;
  const cipherLength = combined.length - tagLength;

  const ciphertext = combined.slice(0, cipherLength);
  const tag = combined.slice(cipherLength);

  return { ciphertext, tag };
}

/**
 * Decrypts data using AES-GCM, verifying the 16-byte tag.
 */
export async function decryptChunkAead(
  iv: Uint8Array,
  ciphertext: Uint8Array,
  tag: Uint8Array,
  key: CryptoKey
): Promise<Uint8Array> {
  const combined = new Uint8Array(ciphertext.length + tag.length);
  combined.set(ciphertext, 0);
  combined.set(tag, ciphertext.length);

  const decryptedBuffer = await window.crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: iv as unknown as BufferSource, tagLength: 128 },
    key,
    combined as unknown as BufferSource
  );

  return new Uint8Array(decryptedBuffer);
}
