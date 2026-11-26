package app.monitor.core.network

data class NoiseStaticKeyPair(val privateKey: ByteArray, val publicKey: ByteArray)

object NoiseKeyMaterial {
    fun generate(): NoiseStaticKeyPair {
        val privRaw = X25519.generatePrivateKey()
        val pubRaw = X25519.publicFromPrivate(privRaw)
        return NoiseStaticKeyPair(privRaw, pubRaw)
    }

    fun validatePrivateKey(raw: ByteArray): ByteArray {
        require(raw.size == 32) { "X25519 private key must be exactly 32 bytes" }
        return raw
    }

    fun validatePublicKey(raw: ByteArray): ByteArray {
        require(raw.size == 32) { "X25519 public key must be exactly 32 bytes" }
        return raw
    }
}
