package com.handoff.files

import com.handoff.files.domain.model.TransferFile
import com.handoff.files.protocol.ProtocolConstants
import org.junit.Assert.*
import org.junit.Test

class FileSizeValidatorTest {

    private val limit = ProtocolConstants.DEFAULT_MAX_FILE_SIZE_BYTES // 10,485,760 bytes

    @Test
    fun testZeroBytesIsAllowed() {
        val file = TransferFile("uri", "empty.txt", 0L, "text/plain")
        assertFalse(file.isOversized)
    }

    @Test
    fun testLimitMinusOneIsAllowed() {
        val file = TransferFile("uri", "under_limit.dat", limit - 1, "application/octet-stream")
        assertFalse(file.isOversized)
    }

    @Test
    fun testExactLimitIsAllowed() {
        val file = TransferFile("uri", "exact_10mib.dat", limit, "application/octet-stream")
        assertFalse(file.isOversized)
        assertEquals("10.00 MiB", file.formattedSize)
    }

    @Test
    fun testLimitPlusOneIsRejected() {
        val file = TransferFile("uri", "over_limit.dat", limit + 1, "application/octet-stream")
        assertTrue(file.isOversized)
    }

    @Test
    fun testFormattingDistinguishesKibAndMib() {
        assertEquals("512 B", TransferFile.formatBytes(512L))
        assertEquals("100.0 KiB", TransferFile.formatBytes(100L * 1024L))
        assertEquals("5.00 MiB", TransferFile.formatBytes(5L * 1024L * 1024L))
    }
}
