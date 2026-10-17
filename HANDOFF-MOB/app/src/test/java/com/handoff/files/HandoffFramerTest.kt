package com.handoff.files

import com.handoff.files.protocol.HandoffFramer
import com.handoff.files.protocol.HandoffPacket
import com.handoff.files.protocol.ProtocolConstants
import com.handoff.files.protocol.ProtocolException
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class HandoffFramerTest {

    @Test
    fun testTransferProposalRoundtrip() {
        val original = HandoffPacket.TransferProposal(
            transferId = "tx-1001",
            filename = "document.pdf",
            sizeBytes = 5_242_880L,
            mimeType = "application/pdf",
            sha256Digest = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        )

        val bytes = HandoffFramer.encodePacketBytes(original)

        // Verify 10-byte header: Magic 'HNDF', Version 1, Type 0x10
        assertEquals('H'.code.toByte(), bytes[0])
        assertEquals('N'.code.toByte(), bytes[1])
        assertEquals('D'.code.toByte(), bytes[2])
        assertEquals('F'.code.toByte(), bytes[3])
        assertEquals(0x01.toByte(), bytes[4])
        assertEquals(ProtocolConstants.TYPE_TRANSFER_PROPOSAL, bytes[5])

        val deserialized = HandoffFramer.readPacket(ByteArrayInputStream(bytes))
        assertTrue(deserialized is HandoffPacket.TransferProposal)
        val result = deserialized as HandoffPacket.TransferProposal

        assertEquals("tx-1001", result.transferId)
        assertEquals("document.pdf", result.filename)
        assertEquals(5_242_880L, result.sizeBytes)
        assertEquals("application/pdf", result.mimeType)
        assertEquals(original.sha256Digest, result.sha256Digest)
    }

    @Test
    fun testChunkDataRoundtrip() {
        val iv = ByteArray(12) { it.toByte() }
        val ciphertext = ByteArray(1024) { (it % 256).toByte() }
        val tag = ByteArray(16) { 0xFF.toByte() }

        val chunk = HandoffPacket.ChunkData(
            transferId = "tx-99",
            sequenceNumber = 42,
            chunkLength = ciphertext.size,
            iv = iv,
            ciphertext = ciphertext,
            tag = tag
        )

        val bytes = HandoffFramer.encodePacketBytes(chunk)
        val deserialized = HandoffFramer.readPacket(ByteArrayInputStream(bytes))

        assertTrue(deserialized is HandoffPacket.ChunkData)
        val result = deserialized as HandoffPacket.ChunkData

        assertEquals("tx-99", result.transferId)
        assertEquals(42, result.sequenceNumber)
        assertEquals(1024, result.chunkLength)
        assertArrayEquals(iv, result.iv)
        assertArrayEquals(ciphertext, result.ciphertext)
        assertArrayEquals(tag, result.tag)
    }

    @Test
    fun testInvalidMagicThrowsProtocolException() {
        val corrupted = byteArrayOf(
            0x00, 0x01, 0x02, 0x03, // Corrupt magic
            0x01,                   // Version
            0x01,                   // Type
            0x00, 0x00, 0x00, 0x04, // Length
            0x7B, 0x7D              // "{}"
        )

        try {
            HandoffFramer.readPacket(ByteArrayInputStream(corrupted))
            fail("Expected ProtocolException for bad magic")
        } catch (e: ProtocolException) {
            assertEquals(ProtocolConstants.ERR_MALFORMED_HEADER, e.errorCode)
        }
    }

    @Test
    fun testUnsupportedVersionThrowsProtocolException() {
        val badVersion = byteArrayOf(
            0x48, 0x4E, 0x44, 0x46, // 'HNDF'
            0x02,                   // Version 2 (unsupported)
            0x01,                   // Type
            0x00, 0x00, 0x00, 0x02,
            0x7B, 0x7D
        )

        try {
            HandoffFramer.readPacket(ByteArrayInputStream(badVersion))
            fail("Expected ProtocolException for unsupported version")
        } catch (e: ProtocolException) {
            assertEquals(ProtocolConstants.ERR_UNSUPPORTED_VERSION, e.errorCode)
        }
    }
}
