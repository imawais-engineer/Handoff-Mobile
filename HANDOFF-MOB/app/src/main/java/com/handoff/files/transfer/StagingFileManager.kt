package com.handoff.files.transfer

import com.handoff.files.security.CryptoEngine
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class StagingFileManager(private val stagingDirectory: File, private val destinationDirectory: File) {

    init {
        if (!stagingDirectory.exists()) {
            stagingDirectory.mkdirs()
        }
        if (!destinationDirectory.exists()) {
            destinationDirectory.mkdirs()
        }
    }

    /**
     * Sanitizes untrusted filenames received over the network:
     * - Strips path traversal sequences (../, ..\)
     * - Strips illegal characters: / \ : * ? " < > | and null bytes
     * - Clamps to 255 characters
     */
    fun sanitizeFilename(rawName: String): String {
        var clean = rawName.replace("\\", "/").substringAfterLast("/")
        clean = clean.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_").trim()
        if (clean.isEmpty() || clean == "." || clean == "..") {
            clean = "handoff_file_${System.currentTimeMillis()}"
        }
        if (clean.length > 255) {
            val ext = clean.substringAfterLast(".", "")
            val base = clean.substringBeforeLast(".")
            clean = if (ext.isNotEmpty()) {
                val allowedBase = 254 - ext.length
                base.take(allowedBase) + "." + ext
            } else {
                clean.take(255)
            }
        }
        return clean
    }

    /**
     * Returns a dedicated temporary staging file path for an active transfer.
     */
    fun getStagingFile(transferId: String): File {
        val safeId = transferId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(stagingDirectory, ".handoff_$safeId.tmp")
    }

    /**
     * Verifies the staging file against the expected SHA-256 digest, then
     * atomically promotes/moves the file to the final destination directory.
     * Handles filename conflicts by appending (1), (2), etc.
     */
    fun verifyAndPublish(transferId: String, rawFilename: String, expectedSha256: String): File {
        val stagingFile = getStagingFile(transferId)
        if (!stagingFile.exists()) {
            throw IllegalStateException("Staging file not found for transfer $transferId")
        }

        // 1. Verify SHA-256 digest
        val computedDigest = FileInputStream(stagingFile).use { CryptoEngine.computeSha256(it) }
        if (!computedDigest.equals(expectedSha256, ignoreCase = true)) {
            // Tampered or corrupted: wipe staging file immediately
            stagingFile.delete()
            throw SecurityException("Integrity check failed: expected $expectedSha256 but got $computedDigest")
        }

        // 2. Resolve safe unique destination file
        val cleanName = sanitizeFilename(rawFilename)
        val finalDestination = resolveUniqueDestinationFile(cleanName)

        // 3. Atomically rename/move file
        val moved = stagingFile.renameTo(finalDestination)
        if (!moved) {
            // Fallback copy & delete if across filesystem boundaries
            FileInputStream(stagingFile).use { input ->
                FileOutputStream(finalDestination).use { output ->
                    input.copyTo(output)
                }
            }
            stagingFile.delete()
        }

        return finalDestination
    }

    /**
     * Cleans up staging file on cancellation or failure.
     */
    fun cleanupStagingFile(transferId: String) {
        try {
            val stagingFile = getStagingFile(transferId)
            if (stagingFile.exists()) {
                stagingFile.delete()
            }
        } catch (_: Exception) {}
    }

    private fun resolveUniqueDestinationFile(sanitizedName: String): File {
        var target = File(destinationDirectory, sanitizedName)
        if (!target.exists()) return target

        val nameWithoutExt = sanitizedName.substringBeforeLast(".")
        val ext = sanitizedName.substringAfterLast(".", "")
        val extSuffix = if (ext.isNotEmpty()) ".$ext" else ""

        var counter = 1
        while (target.exists()) {
            target = File(destinationDirectory, "$nameWithoutExt ($counter)$extSuffix")
            counter++
        }
        return target
    }
}
