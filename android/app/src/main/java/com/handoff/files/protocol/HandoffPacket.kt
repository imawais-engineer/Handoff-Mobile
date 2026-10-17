package com.handoff.files.protocol

sealed class HandoffPacket(val type: Byte) {

    data class PairKeyExchange(
        val ephemeralPublicKeyHex: String,
        val clientNonceHex: String,
        val deviceName: String
    ) : HandoffPacket(ProtocolConstants.TYPE_PAIR_KEY_EXCHANGE)

    data class PairKeyResponse(
        val ephemeralPublicKeyHex: String,
        val serverNonceHex: String,
        val sasCommitmentHex: String
    ) : HandoffPacket(ProtocolConstants.TYPE_PAIR_KEY_RESPONSE)

    data class PairVerify(
        val sasProofHex: String,
        val qrTokenProofHex: String? = null
    ) : HandoffPacket(ProtocolConstants.TYPE_PAIR_VERIFY)

    data class PairAck(
        val status: String, // "OK", "AUTH_FAILED", "RATE_LIMITED"
        val message: String? = null
    ) : HandoffPacket(ProtocolConstants.TYPE_PAIR_ACK) {
        val isSuccess: Boolean get() = status == "OK"
    }

    data class TransferProposal(
        val transferId: String,
        val filename: String,
        val sizeBytes: Long,
        val mimeType: String,
        val sha256Digest: String
    ) : HandoffPacket(ProtocolConstants.TYPE_TRANSFER_PROPOSAL)

    data class TransferResponse(
        val transferId: String,
        val accepted: Boolean,
        val reason: String? = null
    ) : HandoffPacket(ProtocolConstants.TYPE_TRANSFER_RESPONSE)

    data class ChunkData(
        val transferId: String,
        val sequenceNumber: Int,
        val chunkLength: Int,
        val iv: ByteArray,
        val ciphertext: ByteArray,
        val tag: ByteArray
    ) : HandoffPacket(ProtocolConstants.TYPE_CHUNK_DATA) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ChunkData
            return transferId == other.transferId &&
                    sequenceNumber == other.sequenceNumber &&
                    chunkLength == other.chunkLength &&
                    iv.contentEquals(other.iv) &&
                    ciphertext.contentEquals(other.ciphertext) &&
                    tag.contentEquals(other.tag)
        }

        override fun hashCode(): Int {
            var result = transferId.hashCode()
            result = 31 * result + sequenceNumber
            result = 31 * result + chunkLength
            result = 31 * result + iv.contentHashCode()
            result = 31 * result + ciphertext.contentHashCode()
            result = 31 * result + tag.contentHashCode()
            return result
        }
    }

    data class ProgressAck(
        val transferId: String,
        val bytesReceived: Long
    ) : HandoffPacket(ProtocolConstants.TYPE_TRANSFER_PROGRESS_ACK)

    data class TransferComplete(
        val transferId: String,
        val clientSha256: String
    ) : HandoffPacket(ProtocolConstants.TYPE_TRANSFER_COMPLETE)

    data class TransferVerified(
        val transferId: String,
        val verified: Boolean
    ) : HandoffPacket(ProtocolConstants.TYPE_TRANSFER_VERIFIED)

    data class TransferCancel(
        val transferId: String,
        val reason: String
    ) : HandoffPacket(ProtocolConstants.TYPE_TRANSFER_CANCEL)

    data class Error(
        val errorCode: Int,
        val message: String
    ) : HandoffPacket(ProtocolConstants.TYPE_ERROR)
}
