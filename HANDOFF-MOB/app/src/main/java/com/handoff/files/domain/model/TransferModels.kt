package com.handoff.files.domain.model

import com.handoff.files.protocol.ProtocolConstants
import java.util.Locale

data class TransferFile(
    val uriString: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String
) {
    val isOversized: Boolean
        get() = sizeBytes > ProtocolConstants.DEFAULT_MAX_FILE_SIZE_BYTES

    val formattedSize: String
        get() = formatBytes(sizeBytes)

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kib = bytes / 1024.0
            if (kib < 1024) return String.format(Locale.US, "%.1f KiB", kib)
            val mib = kib / 1024.0
            return String.format(Locale.US, "%.2f MiB", mib)
        }
    }
}

enum class TransferDirection {
    SEND,
    RECEIVE
}

enum class TransferStatus {
    IDLE,
    CONNECTING,
    DISCOVERING,
    PAIRING,
    AWAITING_APPROVAL,
    TRANSFERRING,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class TransferProgress(
    val transferId: String = "",
    val filename: String = "",
    val totalBytes: Long = 0L,
    val bytesTransferred: Long = 0L,
    val speedMibPerSec: Double = 0.0,
    val elapsedSeconds: Long = 0L,
    val estimatedRemainingSeconds: Long = 0L,
    val sha256Digest: String = "",
    val verified: Boolean = false,
    val status: TransferStatus = TransferStatus.IDLE,
    val errorMessage: String? = null
) {
    val progressFraction: Float
        get() = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val progressPercent: Int
        get() = (progressFraction * 100).toInt()
}

data class TransferHistoryItem(
    val id: String,
    val filename: String,
    val sizeBytes: Long,
    val direction: TransferDirection,
    val timestamp: Long,
    val outcome: String, // "COMPLETED", "REJECTED", "INTEGRITY_MISMATCH", "CANCELLED", "TIMEOUT"
    val durationMs: Long,
    val peerDevice: String
) {
    val formattedSize: String
        get() = TransferFile.formatBytes(sizeBytes)
}

data class PairingInfo(
    val hostIp: String,
    val port: Int,
    val ephemeralToken: String,
    val sasPinCode: String,
    val expiresAtTimestamp: Long
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiresAtTimestamp

    /**
     * URI encoding for QR code: handoff://v1?ip=192.168.1.50&port=8943&token=a3f8...&pin=492817
     */
    fun toQrUri(): String =
        "handoff://v1?ip=$hostIp&port=$port&token=$ephemeralToken&pin=$sasPinCode"
}
