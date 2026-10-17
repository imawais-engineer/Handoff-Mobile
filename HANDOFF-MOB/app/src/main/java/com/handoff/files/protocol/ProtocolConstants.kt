package com.handoff.files.protocol

/**
 * Protocol Constants for Handoff Files (v1).
 *
 * Wire format:
 * [Magic (4B: "HNDF")][Version (1B: 0x01)][Type (1B)][PayloadLength (4B Big-Endian uint32)][Payload]
 */
object ProtocolConstants {
    // 4-byte Magic header identifier: 'H', 'N', 'D', 'F'
    val MAGIC = byteArrayOf(0x48.toByte(), 0x4E.toByte(), 0x44.toByte(), 0x46.toByte())
    const val PROTOCOL_VERSION: Byte = 0x01

    // Header is strictly 10 bytes in Big-Endian network byte order
    const val HEADER_LENGTH = 10

    // Exact 10 MiB limit: 10 * 1024 * 1024 = 10,485,760 bytes
    const val DEFAULT_MAX_FILE_SIZE_BYTES: Long = 10L * 1024L * 1024L

    // Bounded chunk size for streaming I/O: 64 KiB
    const val CHUNK_SIZE_BYTES = 64 * 1024

    // Maximum allowed length for JSON control payloads: 64 KiB
    const val MAX_CONTROL_PAYLOAD_BYTES = 64 * 1024

    // Maximum payload for a chunk packet (64 KiB plaintext + 12B IV + 16B AEAD Tag + overhead)
    const val MAX_CHUNK_PACKET_PAYLOAD_BYTES = CHUNK_SIZE_BYTES + 12 + 16 + 8

    // AEAD (AES-256-GCM) constants
    const val AEAD_NONCE_SIZE_BYTES = 12
    const val AEAD_TAG_SIZE_BYTES = 16

    // Pairing limits
    const val PAIRING_TICKET_TTL_SECONDS = 120L
    const val MAX_FAILED_PAIR_ATTEMPTS = 3
    const val RATE_LIMIT_LOCKOUT_MS = 30_000L

    // Packet Types
    const val TYPE_PAIR_KEY_EXCHANGE: Byte = 0x01
    const val TYPE_PAIR_KEY_RESPONSE: Byte = 0x02
    const val TYPE_PAIR_VERIFY: Byte = 0x03
    const val TYPE_PAIR_ACK: Byte = 0x04

    const val TYPE_TRANSFER_PROPOSAL: Byte = 0x10
    const val TYPE_TRANSFER_RESPONSE: Byte = 0x11

    const val TYPE_CHUNK_DATA: Byte = 0x20
    const val TYPE_TRANSFER_PROGRESS_ACK: Byte = 0x21
    const val TYPE_TRANSFER_COMPLETE: Byte = 0x22
    const val TYPE_TRANSFER_VERIFIED: Byte = 0x23

    const val TYPE_TRANSFER_CANCEL: Byte = 0x30
    const val TYPE_ERROR: Byte = 0xFF.toByte()

    // Error Codes
    const val ERR_MALFORMED_HEADER = 100
    const val ERR_UNSUPPORTED_VERSION = 101
    const val ERR_UNKNOWN_PACKET_TYPE = 102
    const val ERR_PAYLOAD_TOO_LARGE = 103

    const val ERR_AUTH_FAILED = 200
    const val ERR_RATE_LIMITED = 201
    const val ERR_SESSION_EXPIRED = 202

    const val ERR_SIZE_EXCEEDED = 300
    const val ERR_TRANSFER_REJECTED = 301
    const val ERR_INTEGRITY_FAILED = 302
    const val ERR_TRANSFER_CANCELLED = 303

    const val ERR_IO_FAILURE = 400
}
