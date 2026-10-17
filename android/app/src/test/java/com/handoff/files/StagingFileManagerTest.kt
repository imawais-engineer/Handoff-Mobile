package com.handoff.files

import com.handoff.files.transfer.StagingFileManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

class StagingFileManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var stagingDir: File
    private lateinit var destinationDir: File
    private lateinit var stagingManager: StagingFileManager

    @Before
    fun setup() {
        stagingDir = tempFolder.newFolder("staging")
        destinationDir = tempFolder.newFolder("destination")
        stagingManager = StagingFileManager(stagingDir, destinationDir)
    }

    @Test
    fun testFilenameSanitizationStripsPathTraversal() {
        val malicious1 = "../../etc/passwd"
        val clean1 = stagingManager.sanitizeFilename(malicious1)
        assertEquals("passwd", clean1)

        val malicious2 = "..\\..\\Windows\\System32\\cmd.exe"
        val clean2 = stagingManager.sanitizeFilename(malicious2)
        assertEquals("cmd.exe", clean2)

        val malicious3 = "nested/folder/document.pdf"
        val clean3 = stagingManager.sanitizeFilename(malicious3)
        assertEquals("document.pdf", clean3)
    }

    @Test
    fun testFilenameSanitizationReplacesIllegalCharacters() {
        val illegal = "bad:file*name?with\"quotes<and>pipes|null\u0000.txt"
        val clean = stagingManager.sanitizeFilename(illegal)
        assertFalse(clean.contains(":"))
        assertFalse(clean.contains("*"))
        assertFalse(clean.contains("?"))
        assertFalse(clean.contains("\""))
        assertFalse(clean.contains("<"))
        assertFalse(clean.contains(">"))
        assertFalse(clean.contains("|"))
        assertFalse(clean.contains("\u0000"))
    }

    @Test
    fun testVerifyAndPublishSucceedsOnMatchingDigest() {
        val transferId = "tx-test-42"
        val stagingFile = stagingManager.getStagingFile(transferId)

        // Write known content: "hello world"
        val content = "hello world".toByteArray(Charsets.UTF_8)
        FileOutputStream(stagingFile).use { it.write(content) }

        val expectedSha256 = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"
        val published = stagingManager.verifyAndPublish(transferId, "hello.txt", expectedSha256)

        assertTrue(published.exists())
        assertEquals(destinationDir.absolutePath, published.parentFile?.absolutePath)
        assertEquals("hello.txt", published.name)
        assertEquals(content.size.toLong(), published.length())
        assertFalse(stagingFile.exists()) // Staging file cleaned up / moved
    }

    @Test
    fun testVerifyAndPublishFailsAndDeletesStagingOnCorruptedDigest() {
        val transferId = "tx-corrupt-1"
        val stagingFile = stagingManager.getStagingFile(transferId)

        FileOutputStream(stagingFile).use { it.write("tampered content".toByteArray()) }

        val bogusSha256 = "0000000000000000000000000000000000000000000000000000000000000000"

        try {
            stagingManager.verifyAndPublish(transferId, "tampered.txt", bogusSha256)
            fail("Expected SecurityException on mismatched digest")
        } catch (e: SecurityException) {
            // Staging file must be wiped immediately
            assertFalse(stagingFile.exists())
            // No file in destination
            assertEquals(0, destinationDir.listFiles()?.size ?: 0)
        }
    }
}
