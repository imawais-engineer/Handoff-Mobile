package com.handoff.files.security

import java.io.InputStream
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class SessionKeys(
    val aesKey: ByteArray,
    val authKey: ByteArray,
    val sasPinCode: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SessionKeys
        return aesKey.contentEquals(other.aesKey) &&
                authKey.contentEquals(other.authKey) &&
                sasPinCode == other.sasPinCode
    }

    override fun hashCode(): Int {
        var result = aesKey.contentHashCode()
        result = 31 * result + authKey.contentHashCode()
        result = 31 * result + sasPinCode.hashCode()
        return result
    }
}

data class EncryptedChunkResult(
    val iv: ByteArray,
    val ciphertext: ByteArray,
    val tag: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EncryptedChunkResult
        return iv.contentEquals(other.iv) &&
                ciphertext.contentEquals(other.ciphertext) &&
                tag.contentEquals(other.tag)
    }

    override fun hashCode(): Int {
        var result = iv.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + tag.contentHashCode()
        return result
    }
}

object CryptoEngine {
    private val secureRandom = SecureRandom()

    fun generateRandomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return bytes
    }

    fun generateNonce(): ByteArray = generateRandomBytes(12)

    fun generateEphemeralKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(256, secureRandom)
        return kpg.generateKeyPair()
    }

    fun computeSharedSecret(myPrivateKey: java.security.PrivateKey, peerPublicKeyBytes: ByteArray): ByteArray {
        val kf = KeyFactory.getInstance("EC")
        val peerKey = kf.generatePublic(X509EncodedKeySpec(peerPublicKeyBytes))
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(myPrivateKey)
        ka.doPhase(peerKey, true)
        return ka.generateSecret()
    }

    /**
     * Derives session keys using HKDF-SHA256 over sharedSecret, clientNonce, serverNonce, and salt.
     * Computes AES-256 encryption key, HMAC auth key, and 6-digit Short Authentication String (SAS).
     */
    fun deriveSessionKeys(
        sharedSecret: ByteArray,
        clientNonce: ByteArray,
        serverNonce: ByteArray,
        salt: ByteArray
    ): SessionKeys {
        // HKDF-Extract: PRK = HMAC-SHA256(salt, sharedSecret)
        val prk = hmacSha256(salt, sharedSecret)

        // HKDF-Expand for AES-256 Key (32 bytes)
        val infoAes = "HandoffFiles-v1-AES256GCM".toByteArray(Charsets.UTF_8) + clientNonce + serverNonce
        val aesKey = hkdfExpand(prk, infoAes, 32)

        // HKDF-Expand for Auth Key (32 bytes)
        val infoAuth = "HandoffFiles-v1-AuthHMAC".toByteArray(Charsets.UTF_8) + clientNonce + serverNonce
        val authKey = hkdfExpand(prk, infoAuth, 32)

        // SAS (Short Authentication String) derivation for 6-digit verification code
        val infoSas = "HandoffFiles-v1-SAS".toByteArray(Charsets.UTF_8) + clientNonce + serverNonce
        val sasBytes = hkdfExpand(prk, infoSas, 4)
        val sasInt = ByteBuffer.wrap(sasBytes).int and 0x7FFFFFFF
        val sasCode = String.format("%06d", sasInt % 1_000_000)

        return SessionKeys(aesKey = aesKey, authKey = authKey, sasPinCode = sasCode)
    }

    /**
     * Encrypts chunk data with AES-256-GCM.
     * Output has separate IV (12B), ciphertext, and GCM tag (16B).
     */
    fun encryptChunk(plaintext: ByteArray, aesKey: ByteArray, iv: ByteArray): EncryptedChunkResult {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(aesKey, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        val encryptedWithTag = cipher.doFinal(plaintext)
        val tagLength = 16
        val cipherLength = encryptedWithTag.size - tagLength

        val ciphertext = ByteArray(cipherLength)
        val tag = ByteArray(tagLength)

        System.arraycopy(encryptedWithTag, 0, ciphertext, 0, cipherLength)
        System.arraycopy(encryptedWithTag, cipherLength, tag, 0, tagLength)

        return EncryptedChunkResult(iv = iv, ciphertext = ciphertext, tag = tag)
    }

    /**
     * Decrypts AES-256-GCM chunk data, verifying the 16-byte authentication tag.
     */
    fun decryptChunk(iv: ByteArray, ciphertext: ByteArray, tag: ByteArray, aesKey: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(aesKey, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        val combined = ByteArray(ciphertext.size + tag.size)
        System.arraycopy(ciphertext, 0, combined, 0, ciphertext.size)
        System.arraycopy(tag, 0, combined, ciphertext.size, tag.size)

        return cipher.doFinal(combined)
    }

    /**
     * Computes SHA-256 stream digest as lowercase hex string.
     */
    fun computeSha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        return bytesToHex(digest.digest())
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))

        val result = ByteArray(length)
        var previousBlock = ByteArray(0)
        var generatedBytes = 0
        var counter: Byte = 1

        while (generatedBytes < length) {
            mac.reset()
            mac.update(previousBlock)
            mac.update(info)
            mac.update(counter)
            previousBlock = mac.doFinal()

            val toCopy = minOf(previousBlock.size, length - generatedBytes)
            System.arraycopy(previousBlock, 0, result, generatedBytes, toCopy)
            generatedBytes += toCopy
            counter++
        }
        return result
    }

    fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            val index = i * 2
            result[i] = clean.substring(index, index + 2).toInt(16).toByte()
        }
        return result
    }
}
