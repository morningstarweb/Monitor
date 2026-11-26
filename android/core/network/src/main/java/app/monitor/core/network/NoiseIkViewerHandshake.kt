package app.monitor.core.network

import org.json.JSONObject

data class NoiseHandshakeResult(
    val session: SecureSessionV2,
    val trustTier: String
)

class NoiseIkViewerHandshake(
    private val viewerKeys: NoiseStaticKeyPair,
    hostPublicKey: ByteArray,
    prologue: ByteArray = PROLOGUE_CLOUD
) {
    private val hostStaticPublic = NoiseKeyMaterial.validatePublicKey(hostPublicKey)
    private val viewerPrivate = NoiseKeyMaterial.validatePrivateKey(viewerKeys.privateKey)
    private val symmetric = NoiseSymmetricState(PROTOCOL_NAME, prologue, hostPublicKey)
    private val ephemeral: NoiseStaticKeyPair = NoiseKeyMaterial.generate()
    private val ephemeralPrivate = ephemeral.privateKey
    private val ephemeralPublic = ephemeral.publicKey
    private var messageWritten = false

    fun writeMessage1(payload: ByteArray = defaultDevicePayload()): ByteArray {
        check(!messageWritten) { "Noise message 1 can only be written once" }
        messageWritten = true
        symmetric.mixHash(ephemeralPublic)
        symmetric.mixKey(X25519.computeSharedSecret(ephemeralPrivate, hostStaticPublic))
        val encryptedStatic = symmetric.encryptAndHash(viewerKeys.publicKey)
        symmetric.mixKey(X25519.computeSharedSecret(viewerPrivate, hostStaticPublic))
        val encryptedPayload = symmetric.encryptAndHash(payload)
        return ephemeralPublic + encryptedStatic + encryptedPayload
    }

    fun readMessage2(frame: ByteArray): NoiseHandshakeResult {
        check(messageWritten) { "Noise message 1 must be sent before message 2" }
        require(frame.size >= 32 + 16) { "Noise message 2 is too short" }
        val remoteEphemeral = frame.copyOfRange(0, 32)
        symmetric.mixHash(remoteEphemeral)
        val remoteEphemeralKey = NoiseKeyMaterial.validatePublicKey(remoteEphemeral)
        try {
            symmetric.mixKey(X25519.computeSharedSecret(ephemeralPrivate, remoteEphemeralKey))
            symmetric.mixKey(X25519.computeSharedSecret(viewerPrivate, remoteEphemeralKey))
            val payload = symmetric.decryptAndHash(frame.copyOfRange(32, frame.size))
            val hello = JSONObject(String(payload, Charsets.UTF_8))
            require(hello.optString("status") == "authenticated") { "Host rejected Noise session" }
            val (sendKey, recvKey) = symmetric.split()
            val sessionId = hello.getString("session_id")
            val epoch = hello.optLong("epoch", 1L)
            val hostSendKey = hello.optString("data_channel_send_key").takeIf(String::isNotBlank)
                ?.let(::decodeKey) ?: ByteArray(32)
            val hostReceiveKey = hello.optString("data_channel_recv_key").takeIf(String::isNotBlank)
                ?.let(::decodeKey) ?: ByteArray(32)
            return NoiseHandshakeResult(
                SecureSessionV2(sendKey, recvKey, sessionId, epoch, hostReceiveKey, hostSendKey),
                hello.optString("trust_tier", "T0")
            )
        } finally {
            ephemeralPrivate.fill(0)
            symmetric.wipe()
        }
    }

    private fun decodeKey(value: String): ByteArray {
        require(value.length == 64 && value.all { it.digitToIntOrNull(16) != null }) {
            "Host DataChannel key is malformed"
        }
        return ByteArray(32) { index -> value.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }

    companion object {
        private const val PROTOCOL_NAME = "Noise_IK_25519_ChaChaPoly_SHA256"
        val PROLOGUE_CLOUD = "MONITOR_V3_2_CLOUD_V2".toByteArray(Charsets.US_ASCII)
        val PROLOGUE_LAN = "MONITOR_V3_2_LAN_V2".toByteArray(Charsets.US_ASCII)
        @Deprecated("Use PROLOGUE_CLOUD", ReplaceWith("PROLOGUE_CLOUD"))
        val PROLOGUE = PROLOGUE_CLOUD

        fun defaultDevicePayload(cloudDeviceId: String? = null): ByteArray {
            val manufacturer = try { android.os.Build.MANUFACTURER?.trim() ?: "" } catch (_: Throwable) { "" }
            val model = try { android.os.Build.MODEL?.trim() ?: "" } catch (_: Throwable) { "" }
            val deviceName = if (model.startsWith(manufacturer, ignoreCase = true)) {
                model
            } else if (manufacturer.isNotBlank() && model.isNotBlank()) {
                "$manufacturer $model"
            } else if (model.isNotBlank()) {
                model
            } else {
                "Android Phone"
            }
            val json = JSONObject().apply {
                put("device_name", deviceName)
                put("model", model)
                put("manufacturer", manufacturer)
                if (!cloudDeviceId.isNullOrBlank()) {
                    put("cloud_device_id", cloudDeviceId)
                }
            }
            return json.toString().toByteArray(Charsets.UTF_8)
        }
    }
}
