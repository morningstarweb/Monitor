package app.monitor.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoSpikeTest {

    @Test
    fun testJdk21X25519AndChaCha20Poly1305Availability() {
        // 1. Verify X25519 (XDH) KeyPair generation and Diffie-Hellman agreement
        val kpg = KeyPairGenerator.getInstance("XDH")
        val alice = kpg.generateKeyPair()
        val bob = kpg.generateKeyPair()

        val aliceAgreement = KeyAgreement.getInstance("XDH")
        aliceAgreement.init(alice.private)
        aliceAgreement.doPhase(bob.public, true)
        val aliceShared = aliceAgreement.generateSecret()

        val bobAgreement = KeyAgreement.getInstance("XDH")
        bobAgreement.init(bob.private)
        bobAgreement.doPhase(alice.public, true)
        val bobShared = bobAgreement.generateSecret()

        assertEquals(32, aliceShared.size)
        assertTrue(aliceShared.contentEquals(bobShared))

        // 2. Verify SHA-256 Digest
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest("MONITOR_V3_2".toByteArray())
        assertEquals(32, digest.size)

        // 3. Verify ChaCha20-Poly1305 AEAD Cipher
        val key = SecretKeySpec(aliceShared, "ChaCha20")
        val nonce = ByteArray(12) { 0 } // 96-bit nonce
        val ivSpec = IvParameterSpec(nonce)

        val cipher = Cipher.getInstance("ChaCha20-Poly1305")
        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec)
        val plaintext = "{\"test\":\"telemetry\"}".toByteArray()
        val ciphertext = cipher.doFinal(plaintext)

        assertNotNull(ciphertext)
        assertEquals(plaintext.size + 16, ciphertext.size) // 16-byte Poly1305 MAC tag

        val decryptCipher = Cipher.getInstance("ChaCha20-Poly1305")
        decryptCipher.init(Cipher.DECRYPT_MODE, key, ivSpec)
        val decrypted = decryptCipher.doFinal(ciphertext)

        assertTrue(plaintext.contentEquals(decrypted))
    }
}
