package com.handoff.files.protocol

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ProtocolException(val errorCode: Int, message: String) : Exception(message)

/**
 * Encodes and decodes Handoff Files wire packets over streaming I/O.
 * All multi-byte numerical fields use Big-Endian (Network Byte Order).
 */
object HandoffFramer {

    /**
     * Reads a complete, validated packet from an InputStream.
     * Blocks until the 10-byte header and full payload are read, or EOF.
     */
    fun readPacket(input: InputStream): HandoffPacket {
        val header = ByteArray(ProtocolConstants.HEADER_LENGTH)
        readFully(input, header)

        // 1. Validate 4-byte Magic
        for (i in 0 until 4) {
            if (header[i] != ProtocolConstants.MAGIC[i]) {
                throw ProtocolException(
                    ProtocolConstants.ERR_MALFORMED_HEADER,
                    "Invalid packet magic header: expected 'HNDF', got 0x${header.sliceArray(0..3).joinToString("") { "%02X".format(it) }}"
                )
            }
        }

        // 2. Validate Protocol Version
        val version = header[4]
        if (version != ProtocolConstants.PROTOCOL_VERSION) {
            throw ProtocolException(
                ProtocolConstants.ERR_UNSUPPORTED_VERSION,
                "Unsupported protocol version: $version (expected ${ProtocolConstants.PROTOCOL_VERSION})"
            )
        }

        // 3. Extract Type and Payload Length
        val packetType = header[5]
        val payloadLength = ByteBuffer.wrap(header, 6, 4).order(ByteOrder.BIG_ENDIAN).int

        if (payloadLength < 0 || payloadLength > ProtocolConstants.MAX_CHUNK_PACKET_PAYLOAD_BYTES) {
            throw ProtocolException(
                ProtocolConstants.ERR_PAYLOAD_TOO_LARGE,
                "Payload length exceeds maximum allowed bound: $payloadLength bytes"
            )
        }

        // 4. Read Payload fully
        val payload = ByteArray(payloadLength)
        readFully(input, payload)

        return deserializePayload(packetType, payload)
    }

    /**
     * Writes an encoded packet to an OutputStream with a 10-byte header.
     */
    fun writePacket(output: OutputStream, packet: HandoffPacket) {
        val payload = serializePayload(packet)
        val header = ByteBuffer.allocate(ProtocolConstants.HEADER_LENGTH).order(ByteOrder.BIG_ENDIAN)

        header.put(ProtocolConstants.MAGIC)
        header.put(ProtocolConstants.PROTOCOL_VERSION)
        header.put(packet.type)
        header.putInt(payload.size)

        output.write(header.array())
        output.write(payload)
        output.flush()
    }

    /**
     * Encodes a packet to a complete standalone byte array (for testing & framing analysis).
     */
    fun encodePacketBytes(packet: HandoffPacket): ByteArray {
        val payload = serializePayload(packet)
        val buffer = ByteBuffer.allocate(ProtocolConstants.HEADER_LENGTH + payload.size).order(ByteOrder.BIG_ENDIAN)
        buffer.put(ProtocolConstants.MAGIC)
        buffer.put(ProtocolConstants.PROTOCOL_VERSION)
        buffer.put(packet.type)
        buffer.putInt(payload.size)
        buffer.put(payload)
        return buffer.array()
    }

    /**
     * Deserializes raw payload bytes based on packet type.
     */
    fun deserializePayload(packetType: Byte, payload: ByteArray): HandoffPacket {
        return when (packetType) {
            ProtocolConstants.TYPE_PAIR_KEY_EXCHANGE -> {
                val json = JSONObject(payload.toString(Charsets.UTF_8))
                HandoffPacket.PairKeyExchange(
                    ephemeralPublicKeyHex = json.getString("ephemeralPublicKeyHex"),
                    clientNonceHex = json.getString("clientNonceHex"),
                    deviceName = json.optString("deviceName", "Unknown Peer")
                )
            }
            ProtocolConstants.TYPE_PAIR_KEY_RESPONSE -> {
                val json = JSONObject(payload.toString(Charsets.UTF_8))
                HandoffPacket.PairKeyResponse(
                    ephemeralPublicKeyHex = json.getString("ephemeralPublicKeyHex"),
                    serverNonceHex = json.getString("serverNonceHex"),
                    sasCommitmentHex = json.getString("sasCommitmentHex")
                )
            }
            ProtocolConstants.TYPE_PAIR_VERIFY -> {
                val json = JSONObject(payload.toString(Charsets.UTF_8))
                HandoffPacket.PairVerify(
                    sasProofHex = json.getString("sasProofHex"),
                    qrTokenProofHex = if (json.has("qrTokenProofHex")) json.getString("qrTokenProofHex") else null
                )
            }
            ProtocolConstants.TYPE_PAIR_ACK -> {
                val json = JSONObject(payload.toString(Charsets.UTF_8))
                HandoffPacket.PairAck(
                    status = json.getString("status"),
                    message = json.optString("message", null)
                )
            }
            ProtocolConstants.TYPE_TRANSFER_PROPOSAL -> {
                val json = JSONObject(payload.toString(Charsets.UTF_8))
                HandoffPacket.TransferProposal(
                    transferId = json.getString("transferId"),
                    filename = json.getString("filename"),
                    sizeBytes = json.getLong("sizeBytes"),
                    mimeType = json.optString("mimeType", "application/octet-stream"),
                    sha256Digest = json.getString("sha256Digest")
                )
            }
            ProtocolConstants.TYPE_TRANSFER_RESPONSE -> {
                val json = JSONObject(payload.toString(Charsets.UTF_8))
                HandoffPacket.TransferResponse(
                    transferId = json.getString("transferId"),
                    accepted = json.getBoolean("accepted"),
                    reason = json.optString("reason", null)
                )
            }
            ProtocolConstants.TYPE_CHUNK_DATA -> {
                val dis = DataInputStream(ByteArrayInputStream(payload))
                val idLen = dis.readByte().toInt() and 0xFF
                val idBytes = ByteArray(idLen)
                dis.readFully(idBytes)
                val transferId = String(idBytes, Charsets.UTF_8)
                val seq = dis.readInt()
                val chunkLen = dis.readInt()

                val iv = ByteArray(ProtocolConstants.AEAD_NONCE_SIZE_BYTES)
                dis.readFully(iv)

                val ciphertext = ByteArray(chunkLen)
                dis.readFully(ciphertext)

                val tag = ByteArray(ProtocolConstants.AEAD_TAG_SIZE_BYTES)
                dis.readFully(tag)

                HandoffPacket.ChunkData(
                    transferId = transferId,
                    sequenceNumber = seq,
                    chunkLength = chunkLen,
                    iv = iv,
                    ciphertext = ciphertext,
                    tag = tag
                )
            }
            ProtocolConstants.TYPE_TRANSFER_PROGRESS_ACK -> {
                val json = JSONObject(payload.toString(Charsets.UTF_8))
                HandoffPacket.ProgressAck(
                    transferId = json.getString("transferId"),
                    bytesReceived = json.getLong("bytesReceived")
                )
            }
            ProtocolConstants.TYPE_TRANSFER_COMPLETE -> {
                val json = JSONObject(payload.toString(Charsets.UTF_8))
                HandoffPacket.TransferComplete(
                    transferId = json.getString("transferId"),
                    clientSha256 = json.getString("clientSha256")
                )
            }
            ProtocolConstants.TYPE_TRANSFER_VERIFIED -> {
                val json = JSONObject(payload.toString(Charsets.UTF_8))
                HandoffPacket.TransferVerified(
                    transferId = json.getString("transferId"),
                    verified = json.getBoolean("verified")
                )
            }
            ProtocolConstants.TYPE_TRANSFER_CANCEL -> {
                val json = JSONObject(payload.toString(Charsets.UTF_8))
                HandoffPacket.TransferCancel(
                    transferId = json.getString("transferId"),
                    reason = json.getString("reason")
                )
            }
            ProtocolConstants.TYPE_ERROR -> {
                val json = JSONObject(payload.toString(Charsets.UTF_8))
                HandoffPacket.Error(
                    errorCode = json.getInt("errorCode"),
                    message = json.getString("message")
                )
            }
            else -> throw ProtocolException(
                ProtocolConstants.ERR_UNKNOWN_PACKET_TYPE,
                "Encountered unknown packet type: 0x${"%02X".format(packetType)}"
            )
        }
    }

    /**
     * Serializes packet content into wire payload bytes.
     */
    private fun serializePayload(packet: HandoffPacket): ByteArray {
        return when (packet) {
            is HandoffPacket.PairKeyExchange -> {
                val json = JSONObject().apply {
                    put("ephemeralPublicKeyHex", packet.ephemeralPublicKeyHex)
                    put("clientNonceHex", packet.clientNonceHex)
                    put("deviceName", packet.deviceName)
                }
                json.toString().toByteArray(Charsets.UTF_8)
            }
            is HandoffPacket.PairKeyResponse -> {
                val json = JSONObject().apply {
                    put("ephemeralPublicKeyHex", packet.ephemeralPublicKeyHex)
                    put("serverNonceHex", packet.serverNonceHex)
                    put("sasCommitmentHex", packet.sasCommitmentHex)
                }
                json.toString().toByteArray(Charsets.UTF_8)
            }
            is HandoffPacket.PairVerify -> {
                val json = JSONObject().apply {
                    put("sasProofHex", packet.sasProofHex)
                    packet.qrTokenProofHex?.let { put("qrTokenProofHex", it) }
                }
                json.toString().toByteArray(Charsets.UTF_8)
            }
            is HandoffPacket.PairAck -> {
                val json = JSONObject().apply {
                    put("status", packet.status)
                    packet.message?.let { put("message", it) }
                }
                json.toString().toByteArray(Charsets.UTF_8)
            }
            is HandoffPacket.TransferProposal -> {
                val json = JSONObject().apply {
                    put("transferId", packet.transferId)
                    put("filename", packet.filename)
                    put("sizeBytes", packet.sizeBytes)
                    put("mimeType", packet.mimeType)
                    put("sha256Digest", packet.sha256Digest)
                }
                json.toString().toByteArray(Charsets.UTF_8)
            }
            is HandoffPacket.TransferResponse -> {
                val json = JSONObject().apply {
                    put("transferId", packet.transferId)
                    put("accepted", packet.accepted)
                    packet.reason?.let { put("reason", it) }
                }
                json.toString().toByteArray(Charsets.UTF_8)
            }
            is HandoffPacket.ChunkData -> {
                val baos = ByteArrayOutputStream()
                val dos = DataOutputStream(baos)
                val idBytes = packet.transferId.toByteArray(Charsets.UTF_8)
                dos.writeByte(idBytes.size)
                dos.write(idBytes)
                dos.writeInt(packet.sequenceNumber)
                dos.writeInt(packet.chunkLength)
                dos.write(packet.iv)
                dos.write(packet.ciphertext)
                dos.write(packet.tag)
                dos.flush()
                baos.toByteArray()
            }
            is HandoffPacket.ProgressAck -> {
                val json = JSONObject().apply {
                    put("transferId", packet.transferId)
                    put("bytesReceived", packet.bytesReceived)
                }
                json.toString().toByteArray(Charsets.UTF_8)
            }
            is HandoffPacket.TransferComplete -> {
                val json = JSONObject().apply {
                    put("transferId", packet.transferId)
                    put("clientSha256", packet.clientSha256)
                }
                json.toString().toByteArray(Charsets.UTF_8)
            }
            is HandoffPacket.TransferVerified -> {
                val json = JSONObject().apply {
                    put("transferId", packet.transferId)
                    put("verified", packet.verified)
                }
                json.toString().toByteArray(Charsets.UTF_8)
            }
            is HandoffPacket.TransferCancel -> {
                val json = JSONObject().apply {
                    put("transferId", packet.transferId)
                    put("reason", packet.reason)
                }
                json.toString().toByteArray(Charsets.UTF_8)
            }
            is HandoffPacket.Error -> {
                val json = JSONObject().apply {
                    put("errorCode", packet.errorCode)
                    put("message", packet.message)
                }
                json.toString().toByteArray(Charsets.UTF_8)
            }
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var bytesRead = 0
        while (bytesRead < buffer.size) {
            val count = input.read(buffer, bytesRead, buffer.size - bytesRead)
            if (count < 0) {
                throw ProtocolException(
                    ProtocolConstants.ERR_IO_FAILURE,
                    "Unexpected EOF reading packet stream (read $bytesRead/${buffer.size} bytes)"
                )
            }
            bytesRead += count
        }
    }
}
