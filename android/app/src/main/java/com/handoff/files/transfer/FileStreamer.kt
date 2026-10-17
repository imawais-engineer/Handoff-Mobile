package com.handoff.files.transfer

import com.handoff.files.domain.model.TransferProgress
import com.handoff.files.domain.model.TransferStatus
import com.handoff.files.protocol.HandoffPacket
import com.handoff.files.protocol.ProtocolConstants
import com.handoff.files.protocol.ProtocolException
import com.handoff.files.security.CryptoEngine
import com.handoff.files.transport.ITransportChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class FileStreamer(private val stagingManager: StagingFileManager) {

    /**
     * Sends a file over the authenticated transport channel with AES-256-GCM encryption.
     */
    suspend fun sendFile(
        channel: ITransportChannel,
        transferId: String,
        filename: String,
        mimeType: String,
        fileSize: Long,
        inputStreamProvider: () -> InputStream,
        aesKey: ByteArray,
        progressFlow: MutableStateFlow<TransferProgress>,
        isCancelled: () -> Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        // 1. Enforce 10 MiB limit on sender
        if (fileSize > ProtocolConstants.DEFAULT_MAX_FILE_SIZE_BYTES) {
            channel.sendPacket(HandoffPacket.Error(ProtocolConstants.ERR_SIZE_EXCEEDED, "File exceeds 10 MiB limit"))
            progressFlow.value = progressFlow.value.copy(
                status = TransferStatus.FAILED,
                errorMessage = "File size (${fileSize / (1024 * 1024)} MiB) exceeds 10 MiB limit"
            )
            return@withContext false
        }

        // 2. Pre-calculate SHA-256 digest
        progressFlow.value = progressFlow.value.copy(
            transferId = transferId,
            filename = filename,
            totalBytes = fileSize,
            status = TransferStatus.VERIFYING
        )
        val expectedSha256 = inputStreamProvider().use { CryptoEngine.computeSha256(it) }

        // 3. Send Transfer Proposal
        progressFlow.value = progressFlow.value.copy(
            status = TransferStatus.AWAITING_APPROVAL,
            sha256Digest = expectedSha256
        )
        channel.sendPacket(
            HandoffPacket.TransferProposal(
                transferId = transferId,
                filename = filename,
                sizeBytes = fileSize,
                mimeType = mimeType,
                sha256Digest = expectedSha256
            )
        )

        // 4. Await receiver response
        val response = channel.receivePacket()
        if (response !is HandoffPacket.TransferResponse || !response.accepted) {
            val reason = (response as? HandoffPacket.TransferResponse)?.reason ?: "Transfer rejected by receiver"
            progressFlow.value = progressFlow.value.copy(
                status = TransferStatus.FAILED,
                errorMessage = reason
            )
            return@withContext false
        }

        // 5. Stream encrypted chunks
        progressFlow.value = progressFlow.value.copy(status = TransferStatus.TRANSFERRING)
        val startTime = System.currentTimeMillis()
        var bytesSent = 0L
        var sequenceNumber = 0

        inputStreamProvider().use { input ->
            val chunkBuffer = ByteArray(ProtocolConstants.CHUNK_SIZE_BYTES)
            var readCount: Int

            while (input.read(chunkBuffer).also { readCount = it } != -1) {
                if (isCancelled()) {
                    channel.sendPacket(HandoffPacket.TransferCancel(transferId, "Cancelled by sender"))
                    progressFlow.value = progressFlow.value.copy(status = TransferStatus.CANCELLED)
                    return@withContext false
                }

                val plaintext = if (readCount == chunkBuffer.size) chunkBuffer else chunkBuffer.copyOf(readCount)
                val iv = CryptoEngine.generateNonce()
                val encResult = CryptoEngine.encryptChunk(plaintext, aesKey, iv)

                val chunkPacket = HandoffPacket.ChunkData(
                    transferId = transferId,
                    sequenceNumber = sequenceNumber++,
                    chunkLength = encResult.ciphertext.size,
                    iv = encResult.iv,
                    ciphertext = encResult.ciphertext,
                    tag = encResult.tag
                )
                channel.sendPacket(chunkPacket)
                bytesSent += readCount

                val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                val speed = (bytesSent / 1024.0 / 1024.0) / (elapsed / 1000.0)
                val remainingBytes = (fileSize - bytesSent).coerceAtLeast(0)
                val etaSeconds = if (speed > 0) (remainingBytes / (speed * 1024 * 1024)).toLong() else 0L

                progressFlow.value = progressFlow.value.copy(
                    bytesTransferred = bytesSent,
                    speedMibPerSec = speed,
                    elapsedSeconds = elapsed / 1000,
                    estimatedRemainingSeconds = etaSeconds
                )
            }
        }

        // 6. Complete and verify
        channel.sendPacket(HandoffPacket.TransferComplete(transferId, expectedSha256))
        val verifyAck = channel.receivePacket()
        val success = (verifyAck as? HandoffPacket.TransferVerified)?.verified == true

        progressFlow.value = progressFlow.value.copy(
            status = if (success) TransferStatus.COMPLETED else TransferStatus.FAILED,
            verified = success,
            errorMessage = if (!success) "Peer integrity verification failed" else null
        )
        return@withContext success
    }

    /**
     * Receives a file from the transport channel, writing to an isolated staging file
     * and atomically promoting it only after full SHA-256 integrity verification.
     */
    suspend fun receiveFile(
        channel: ITransportChannel,
        aesKey: ByteArray,
        onProposalReceived: suspend (HandoffPacket.TransferProposal) -> Boolean,
        progressFlow: MutableStateFlow<TransferProgress>,
        isCancelled: () -> Boolean
    ): File? = withContext(Dispatchers.IO) {
        val proposalPacket = channel.receivePacket()
        if (proposalPacket !is HandoffPacket.TransferProposal) {
            channel.sendPacket(HandoffPacket.Error(ProtocolConstants.ERR_UNKNOWN_PACKET_TYPE, "Expected TransferProposal"))
            return@withContext null
        }

        // 1. Enforce 10 MiB limit on receiver
        if (proposalPacket.sizeBytes > ProtocolConstants.DEFAULT_MAX_FILE_SIZE_BYTES) {
            channel.sendPacket(HandoffPacket.TransferResponse(proposalPacket.transferId, false, "File exceeds 10 MiB limit"))
            progressFlow.value = progressFlow.value.copy(
                status = TransferStatus.FAILED,
                errorMessage = "File size (${proposalPacket.sizeBytes / (1024 * 1024)} MiB) exceeds 10 MiB limit"
            )
            return@withContext null
        }

        progressFlow.value = progressFlow.value.copy(
            transferId = proposalPacket.transferId,
            filename = proposalPacket.filename,
            totalBytes = proposalPacket.sizeBytes,
            status = TransferStatus.AWAITING_APPROVAL,
            sha256Digest = proposalPacket.sha256Digest
        )

        // 2. Prompt user for explicit approval
        val accepted = onProposalReceived(proposalPacket)
        channel.sendPacket(HandoffPacket.TransferResponse(proposalPacket.transferId, accepted, if (accepted) null else "User declined transfer"))

        if (!accepted) {
            progressFlow.value = progressFlow.value.copy(
                status = TransferStatus.FAILED,
                errorMessage = "Transfer declined by user"
            )
            return@withContext null
        }

        val stagingFile = stagingManager.getStagingFile(proposalPacket.transferId)
        val startTime = System.currentTimeMillis()
        var bytesReceived = 0L

        try {
            FileOutputStream(stagingFile).use { fileOut ->
                progressFlow.value = progressFlow.value.copy(status = TransferStatus.TRANSFERRING)

                while (true) {
                    if (isCancelled()) {
                        channel.sendPacket(HandoffPacket.TransferCancel(proposalPacket.transferId, "Cancelled by receiver"))
                        stagingManager.cleanupStagingFile(proposalPacket.transferId)
                        progressFlow.value = progressFlow.value.copy(status = TransferStatus.CANCELLED)
                        return@withContext null
                    }

                    val packet = channel.receivePacket()
                    when (packet) {
                        is HandoffPacket.ChunkData -> {
                            val decrypted = CryptoEngine.decryptChunk(
                                iv = packet.iv,
                                ciphertext = packet.ciphertext,
                                tag = packet.tag,
                                aesKey = aesKey
                            )
                            fileOut.write(decrypted)
                            bytesReceived += decrypted.size

                            if (bytesReceived > ProtocolConstants.DEFAULT_MAX_FILE_SIZE_BYTES) {
                                throw ProtocolException(ProtocolConstants.ERR_SIZE_EXCEEDED, "Stream exceeded 10 MiB limit")
                            }

                            val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                            val speed = (bytesReceived / 1024.0 / 1024.0) / (elapsed / 1000.0)
                            val remainingBytes = (proposalPacket.sizeBytes - bytesReceived).coerceAtLeast(0)
                            val etaSeconds = if (speed > 0) (remainingBytes / (speed * 1024 * 1024)).toLong() else 0L

                            progressFlow.value = progressFlow.value.copy(
                                bytesTransferred = bytesReceived,
                                speedMibPerSec = speed,
                                elapsedSeconds = elapsed / 1000,
                                estimatedRemainingSeconds = etaSeconds
                            )
                        }
                        is HandoffPacket.TransferComplete -> {
                            break
                        }
                        is HandoffPacket.TransferCancel -> {
                            stagingManager.cleanupStagingFile(proposalPacket.transferId)
                            progressFlow.value = progressFlow.value.copy(
                                status = TransferStatus.CANCELLED,
                                errorMessage = "Sender cancelled transfer"
                            )
                            return@withContext null
                        }
                        else -> {
                            throw ProtocolException(ProtocolConstants.ERR_UNKNOWN_PACKET_TYPE, "Unexpected packet during transfer")
                        }
                    }
                }
            }

            // 3. Atomically verify SHA-256 and promote staging file
            progressFlow.value = progressFlow.value.copy(status = TransferStatus.VERIFYING)
            val publishedFile = stagingManager.verifyAndPublish(
                transferId = proposalPacket.transferId,
                rawFilename = proposalPacket.filename,
                expectedSha256 = proposalPacket.sha256Digest
            )

            channel.sendPacket(HandoffPacket.TransferVerified(proposalPacket.transferId, true))
            progressFlow.value = progressFlow.value.copy(
                status = TransferStatus.COMPLETED,
                verified = true
            )
            return@withContext publishedFile

        } catch (e: Exception) {
            stagingManager.cleanupStagingFile(proposalPacket.transferId)
            channel.sendPacket(HandoffPacket.TransferVerified(proposalPacket.transferId, false))
            progressFlow.value = progressFlow.value.copy(
                status = TransferStatus.FAILED,
                errorMessage = e.message ?: "Transfer error"
            )
            return@withContext null
        }
    }
}
