package app.monitor.core.network

import app.monitor.core.model.EnvelopeV2
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

const val MAX_PLAINTEXT_SIZE = 16 * 1024
const val AEAD_TAG_SIZE = 16
const val MAX_CIPHERTEXT_SIZE = MAX_PLAINTEXT_SIZE + AEAD_TAG_SIZE

/**
 * Android client-side Noise AEAD secure session engine.
 * Encrypts and decrypts frames using ChaCha20-Poly1305 with 16 KiB frame limits
 * and monotonic sequence counters.
 */
class SecureSessionV2(
    private val sendKey: ByteArray,
    private val recvKey: ByteArray,
    val sessionId: String,
    val epoch: Long = 1L,
    private val dataChannelSendKey: ByteArray = ByteArray(32),
    private val dataChannelRecvKey: ByteArray = ByteArray(32)
) {
    val dataChannelReady: Boolean
        get() = dataChannelSendKey.size == 32 && dataChannelRecvKey.size == 32 &&
            dataChannelSendKey.any { it != 0.toByte() } && dataChannelRecvKey.any { it != 0.toByte() }
    private val sendNonce = AtomicLong(0L)
    private val recvNonce = AtomicLong(0L)
    private val dataChannelSendNonce = AtomicLong(0L)
    private val dataChannelRecvNonce = AtomicLong(0L)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun encrypt(plaintext: ByteArray): ByteArray {
        require(plaintext.size <= MAX_PLAINTEXT_SIZE) {
            "Plaintext payload size (${plaintext.size}) exceeds 16 KiB limit"
        }
        val nonce = sendNonce.getAndIncrement()
        val iv = buildIv(nonce)
        val cipher = Cipher.getInstance("ChaCha20-Poly1305")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sendKey, "ChaCha20"), IvParameterSpec(iv))
        return cipher.doFinal(plaintext)
    }

    fun decrypt(ciphertext: ByteArray): ByteArray {
        require(ciphertext.size <= MAX_CIPHERTEXT_SIZE) {
            "Ciphertext frame size (${ciphertext.size}) exceeds maximum allowed limit"
        }
        require(ciphertext.size >= AEAD_TAG_SIZE) {
            "Ciphertext frame too short to contain AEAD auth tag"
        }
        val nonce = recvNonce.get()
        val iv = buildIv(nonce)
        val cipher = Cipher.getInstance("ChaCha20-Poly1305")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(recvKey, "ChaCha20"), IvParameterSpec(iv))
        val plaintext = cipher.doFinal(ciphertext)
        recvNonce.incrementAndGet()
        return plaintext
    }

    fun encryptEnvelope(envelope: EnvelopeV2): ByteArray {
        val jsonStr = json.encodeToString(EnvelopeV2.serializer(), envelope)
        return encrypt(jsonStr.toByteArray(Charsets.UTF_8))
    }

    fun decryptEnvelope(ciphertext: ByteArray): EnvelopeV2 {
        val plaintext = decrypt(ciphertext)
        val jsonStr = String(plaintext, Charsets.UTF_8)
        return json.decodeFromString(EnvelopeV2.serializer(), jsonStr)
    }

    fun encryptDataChannel(plaintext: ByteArray): ByteArray =
        encryptWithKey(plaintext, dataChannelSendKey, dataChannelSendNonce.getAndIncrement())

    fun decryptDataChannel(ciphertext: ByteArray): ByteArray {
        require(ciphertext.size <= MAX_CIPHERTEXT_SIZE && ciphertext.size >= AEAD_TAG_SIZE) {
            "Invalid encrypted DataChannel frame size"
        }
        return decryptWithKey(ciphertext, dataChannelRecvKey, dataChannelRecvNonce.getAndIncrement())
    }

    fun currentSendNonce(): Long = sendNonce.get()
    fun currentRecvNonce(): Long = recvNonce.get()

    private fun buildIv(nonce: Long): ByteArray {
        val buf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0) // 4 bytes padding
        buf.putLong(nonce) // 8 bytes sequence
        return buf.array()
    }

    private fun encryptWithKey(plaintext: ByteArray, key: ByteArray, nonce: Long): ByteArray {
        require(plaintext.size <= MAX_PLAINTEXT_SIZE) { "DataChannel frame exceeds 16 KiB" }
        val cipher = Cipher.getInstance("ChaCha20-Poly1305")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(buildIv(nonce)))
        return cipher.doFinal(plaintext)
    }

    private fun decryptWithKey(ciphertext: ByteArray, key: ByteArray, nonce: Long): ByteArray {
        val cipher = Cipher.getInstance("ChaCha20-Poly1305")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(buildIv(nonce)))
        return cipher.doFinal(ciphertext)
    }

    companion object {
        fun generateX25519KeyPair(): NoiseStaticKeyPair = NoiseKeyMaterial.generate()

        fun computeSharedSecret(localPrivateKey: ByteArray, remotePublicKey: ByteArray): ByteArray =
            X25519.computeSharedSecret(localPrivateKey, remotePublicKey)
    }
}
