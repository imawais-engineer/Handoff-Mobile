package com.handoff.files

import com.handoff.files.security.CryptoEngine
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import javax.crypto.AEADBadTagException

class CryptoEngineTest {

    @Test
    fun testEcdhKeyAgreementAndSessionDerivation() {
        // Peer A (Sender)
        val keyPairA = CryptoEngine.generateEphemeralKeyPair()
        val nonceA = CryptoEngine.generateNonce()

        // Peer B (Receiver)
        val keyPairB = CryptoEngine.generateEphemeralKeyPair()
        val nonceB = CryptoEngine.generateNonce()

        // Key Agreement
        val secretA = CryptoEngine.computeSharedSecret(keyPairA.private, keyPairB.public.encoded)
        val secretB = CryptoEngine.computeSharedSecret(keyPairB.private, keyPairA.public.encoded)

        // Must derive exact same shared secret
        assertArrayEquals(secretA, secretB)

        // Session derivation with HKDF-SHA256
        val salt = nonceA + nonceB
        val sessionKeysA = CryptoEngine.deriveSessionKeys(secretA, nonceA, nonceB, salt)
        val sessionKeysB = CryptoEngine.deriveSessionKeys(secretB, nonceA, nonceB, salt)

        assertArrayEquals(sessionKeysA.aesKey, sessionKeysB.aesKey)
        assertArrayEquals(sessionKeysA.authKey, sessionKeysB.authKey)
        assertEquals(sessionKeysA.sasPinCode, sessionKeysB.sasPinCode)

        // 6-digit PIN validation
        assertEquals(6, sessionKeysA.sasPinCode.length)
        assertTrue(sessionKeysA.sasPinCode.all { it.isDigit() })
    }

    @Test
    fun testAesGcmEncryptionAndDecryptionRoundtrip() {
        val key = CryptoEngine.generateRandomBytes(32) // 256-bit key
        val iv = CryptoEngine.generateNonce()          // 12-byte IV
        val plaintext = "Handoff Files cross-platform wire payload verification".toByteArray(Charsets.UTF_8)

        val enc = CryptoEngine.encryptChunk(plaintext, key, iv)
        assertEquals(12, enc.iv.size)
        assertEquals(16, enc.tag.size)

        val decrypted = CryptoEngine.decryptChunk(enc.iv, enc.ciphertext, enc.tag, key)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun testTamperedCiphertextFailsAuthenticationTag() {
        val key = CryptoEngine.generateRandomBytes(32)
        val iv = CryptoEngine.generateNonce()
        val plaintext = "Sensitive file bytes".toByteArray(Charsets.UTF_8)

        val enc = CryptoEngine.encryptChunk(plaintext, key, iv)

        // Tamper with one byte in ciphertext
        val tamperedCiphertext = enc.ciphertext.copyOf()
        tamperedCiphertext[0] = (tamperedCiphertext[0] xor 0x01.toByte())

        try {
            CryptoEngine.decryptChunk(enc.iv, tamperedCiphertext, enc.tag, key)
            fail("Expected AEADBadTagException for tampered ciphertext")
        } catch (e: Exception) {
            // Success: AEAD detected tampering
            assertTrue(e is AEADBadTagException || e.cause is AEADBadTagException)
        }
    }

    @Test
    fun testSha256KnownTestVector() {
        // Test vector: SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        val emptyDigest = CryptoEngine.computeSha256(ByteArrayInputStream(ByteArray(0)))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", emptyDigest)

        // Test vector: SHA-256("hello world") = b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9
        val helloDigest = CryptoEngine.computeSha256(ByteArrayInputStream("hello world".toByteArray(Charsets.UTF_8)))
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", helloDigest)
    }
}
