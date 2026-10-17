import { DissectedPacket, PacketTypeName, ProtocolHeader } from '../types/protocol';
import { bytesToHex, hexToBytes } from './crypto';

export const MAGIC_BYTES = new Uint8Array([0x48, 0x4e, 0x44, 0x46]); // "HNDF"
export const PROTOCOL_VERSION = 0x01;
export const HEADER_LENGTH = 10;
export const DEFAULT_MAX_FILE_SIZE = 10 * 1024 * 1024; // 10,485,760 bytes

export const PACKET_TYPES: Record<number, PacketTypeName> = {
  0x01: 'PAIR_KEY_EXCHANGE',
  0x02: 'PAIR_KEY_RESPONSE',
  0x03: 'PAIR_VERIFY',
  0x04: 'PAIR_ACK',
  0x10: 'TRANSFER_PROPOSAL',
  0x11: 'TRANSFER_RESPONSE',
  0x20: 'CHUNK_DATA',
  0x21: 'PROGRESS_ACK',
  0x22: 'TRANSFER_COMPLETE',
  0x23: 'TRANSFER_VERIFIED',
  0x30: 'TRANSFER_CANCEL',
  0xff: 'ERROR',
};

export const PACKET_TYPE_CODES: Record<PacketTypeName, number> = {
  PAIR_KEY_EXCHANGE: 0x01,
  PAIR_KEY_RESPONSE: 0x02,
  PAIR_VERIFY: 0x03,
  PAIR_ACK: 0x04,
  TRANSFER_PROPOSAL: 0x10,
  TRANSFER_RESPONSE: 0x11,
  CHUNK_DATA: 0x20,
  PROGRESS_ACK: 0x21,
  TRANSFER_COMPLETE: 0x22,
  TRANSFER_VERIFIED: 0x23,
  TRANSFER_CANCEL: 0x30,
  ERROR: 0xff,
};

/**
 * Encodes a JSON payload into wire packet bytes with 10-byte Big-Endian header
 */
export function encodeJsonPacket(typeName: PacketTypeName, payload: object): Uint8Array {
  const typeCode = PACKET_TYPE_CODES[typeName];
  const payloadBytes = new TextEncoder().encode(JSON.stringify(payload));
  const packet = new Uint8Array(HEADER_LENGTH + payloadBytes.length);

  // 1. Magic (4 bytes)
  packet.set(MAGIC_BYTES, 0);

  // 2. Version (1 byte)
  packet[4] = PROTOCOL_VERSION;

  // 3. Type (1 byte)
  packet[5] = typeCode;

  // 4. Payload Length (4 bytes Big-Endian)
  const view = new DataView(packet.buffer);
  view.setUint32(6, payloadBytes.length, false);

  // 5. Payload
  packet.set(payloadBytes, HEADER_LENGTH);

  return packet;
}

/**
 * Encodes a CHUNK_DATA binary packet
 */
export function encodeChunkPacket(
  transferId: string,
  sequenceNumber: number,
  iv: Uint8Array,
  ciphertext: Uint8Array,
  tag: Uint8Array
): Uint8Array {
  const idBytes = new TextEncoder().encode(transferId);
  const binaryPayloadLength = 1 + idBytes.length + 4 + 4 + iv.length + ciphertext.length + tag.length;
  const payload = new Uint8Array(binaryPayloadLength);
  const pView = new DataView(payload.buffer);

  let offset = 0;
  payload[offset++] = idBytes.length;
  payload.set(idBytes, offset);
  offset += idBytes.length;

  pView.setUint32(offset, sequenceNumber, false);
  offset += 4;

  pView.setUint32(offset, ciphertext.length, false);
  offset += 4;

  payload.set(iv, offset);
  offset += iv.length;

  payload.set(ciphertext, offset);
  offset += ciphertext.length;

  payload.set(tag, offset);

  // Wrap in header
  const packet = new Uint8Array(HEADER_LENGTH + payload.length);
  packet.set(MAGIC_BYTES, 0);
  packet[4] = PROTOCOL_VERSION;
  packet[5] = PACKET_TYPE_CODES.CHUNK_DATA;
  new DataView(packet.buffer).setUint32(6, payload.length, false);
  packet.set(payload, HEADER_LENGTH);

  return packet;
}

/**
 * Dissects raw packet bytes into a structured inspection model
 */
export function dissectPacket(bytes: Uint8Array): DissectedPacket {
  const rawHex = bytesToHex(bytes);

  if (bytes.length < HEADER_LENGTH) {
    return {
      rawHex,
      header: { magic: '', version: 0, packetType: 0, payloadLength: 0 },
      typeName: 'ERROR',
      isValid: false,
      validationError: `Truncated header: expected ${HEADER_LENGTH} bytes, got ${bytes.length} bytes`,
    };
  }

  const magic = String.fromCharCode(...bytes.slice(0, 4));
  const version = bytes[4];
  const packetType = bytes[5];
  const payloadLength = new DataView(bytes.buffer, bytes.byteOffset).getUint32(6, false);

  const header: ProtocolHeader = { magic, version, packetType, payloadLength };
  const typeName = PACKET_TYPES[packetType] || 'ERROR';

  // Validations
  if (magic !== 'HNDF') {
    return {
      rawHex,
      header,
      typeName,
      isValid: false,
      validationError: `Invalid Magic: expected 'HNDF', got '${magic}' (0x${rawHex.slice(0, 8)})`,
    };
  }

  if (version !== PROTOCOL_VERSION) {
    return {
      rawHex,
      header,
      typeName,
      isValid: false,
      validationError: `Unsupported Protocol Version: expected 1, got ${version}`,
    };
  }

  if (bytes.length < HEADER_LENGTH + payloadLength) {
    return {
      rawHex,
      header,
      typeName,
      isValid: false,
      validationError: `Truncated payload: header declares ${payloadLength} bytes, but only ${bytes.length - HEADER_LENGTH} available`,
    };
  }

  const payloadBytes = bytes.slice(HEADER_LENGTH, HEADER_LENGTH + payloadLength);

  if (typeName === 'CHUNK_DATA') {
    try {
      const pView = new DataView(payloadBytes.buffer, payloadBytes.byteOffset);
      const idLen = payloadBytes[0];
      const transferId = new TextDecoder().decode(payloadBytes.slice(1, 1 + idLen));
      const seq = pView.getUint32(1 + idLen, false);
      const chunkLen = pView.getUint32(1 + idLen + 4, false);
      const iv = bytesToHex(payloadBytes.slice(1 + idLen + 8, 1 + idLen + 8 + 12));
      const tag = bytesToHex(payloadBytes.slice(payloadBytes.length - 16));

      return {
        rawHex,
        header,
        typeName,
        isValid: true,
        chunkDetails: {
          sequenceNumber: seq,
          chunkLength: chunkLen,
          iv,
          tag,
        },
      };
    } catch (e: any) {
      return {
        rawHex,
        header,
        typeName,
        isValid: false,
        validationError: `Malformed ChunkData binary layout: ${e.message}`,
      };
    }
  }

  // Parse JSON payloads
  try {
    const text = new TextDecoder().decode(payloadBytes);
    const json = JSON.parse(text);
    return {
      rawHex,
      header,
      typeName,
      payloadJson: json,
      isValid: true,
    };
  } catch (e: any) {
    return {
      rawHex,
      header,
      typeName,
      isValid: false,
      validationError: `Malformed JSON payload: ${e.message}`,
    };
  }
}
