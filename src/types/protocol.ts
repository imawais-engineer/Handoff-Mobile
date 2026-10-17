export interface ProtocolHeader {
  magic: string; // 'HNDF'
  version: number; // 1
  packetType: number;
  payloadLength: number;
}

export type PacketTypeName =
  | 'PAIR_KEY_EXCHANGE'
  | 'PAIR_KEY_RESPONSE'
  | 'PAIR_VERIFY'
  | 'PAIR_ACK'
  | 'TRANSFER_PROPOSAL'
  | 'TRANSFER_RESPONSE'
  | 'CHUNK_DATA'
  | 'PROGRESS_ACK'
  | 'TRANSFER_COMPLETE'
  | 'TRANSFER_VERIFIED'
  | 'TRANSFER_CANCEL'
  | 'ERROR';

export interface TransferProposalPayload {
  transferId: string;
  filename: string;
  sizeBytes: number;
  mimeType: string;
  sha256Digest: string;
}

export interface ChunkDataPayload {
  transferId: string;
  sequenceNumber: number;
  chunkLength: number;
  ivHex: string;
  ciphertextHex: string;
  tagHex: string;
}

export interface DissectedPacket {
  rawHex: string;
  header: ProtocolHeader;
  typeName: PacketTypeName;
  payloadJson?: any;
  chunkDetails?: {
    sequenceNumber: number;
    chunkLength: number;
    iv: string;
    tag: string;
  };
  isValid: boolean;
  validationError?: string;
}

export interface TestVectorResult {
  id: string;
  name: string;
  description: string;
  status: 'pending' | 'running' | 'passed' | 'failed';
  durationMs?: number;
  details?: string;
}
