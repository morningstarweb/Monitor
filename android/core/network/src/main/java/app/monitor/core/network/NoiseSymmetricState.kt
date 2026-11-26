package app.monitor.core.network

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class NoiseSymmetricState(protocolName: String, prologue: ByteArray, remoteStatic: ByteArray) {
    private var chainingKey = protocolName.toByteArray(Charsets.US_ASCII).let { name ->
        if (name.size <= HASH_SIZE) name.copyOf(HASH_SIZE) else sha256(name)
    }
    private var hash = chainingKey.copyOf()
    private var cipherKey: ByteArray? = null
    private var nonce = 0L

    init {
        mixHash(prologue)
        mixHash(remoteStatic)
    }

    fun mixHash(data: ByteArray) {
        hash = sha256(hash + data)
    }

    fun mixKey(input: ByteArray) {
        val output = hkdf(chainingKey, input)
        chainingKey = output[0]
        cipherKey = output[1]
        nonce = 0L
    }

    fun encryptAndHash(plaintext: ByteArray): ByteArray {
        val key = cipherKey ?: return plaintext.also(::mixHash)
        return crypt(Cipher.ENCRYPT_MODE, key, nonce++, plaintext).also(::mixHash)
    }

    fun decryptAndHash(ciphertext: ByteArray): ByteArray {
        val key = cipherKey ?: return ciphertext.also(::mixHash)
        val plaintext = crypt(Cipher.DECRYPT_MODE, key, nonce++, ciphertext)
        mixHash(ciphertext)
        return plaintext
    }

    fun split(): Pair<ByteArray, ByteArray> {
        val output = hkdf(chainingKey, ByteArray(0))
        return output[0] to output[1]
    }

    fun wipe() {
        chainingKey.fill(0)
        hash.fill(0)
        cipherKey?.fill(0)
        cipherKey = null
    }

    private fun crypt(mode: Int, key: ByteArray, counter: Long, data: ByteArray): ByteArray {
        val iv = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0)
            putLong(counter)
        }.array()
        val cipher = Cipher.getInstance("ChaCha20-Poly1305")
        cipher.init(mode, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(iv))
        cipher.updateAAD(hash)
        return cipher.doFinal(data)
    }

    private fun hkdf(salt: ByteArray, input: ByteArray): List<ByteArray> {
        val tempKey = hmac(salt, input)
        val first = hmac(tempKey, byteArrayOf(1))
        val second = hmac(tempKey, first + byteArrayOf(2))
        return listOf(first, second)
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    companion object {
        private const val HASH_SIZE = 32
    }
}
