package app.monitor.core.network

import java.io.IOException

data class ViewerTicketResult(
    val ticket: String,
    val hostNoisePublicKey: String
)

internal fun decodeNoiseHex(value: String): ByteArray {
    if (value.length != 64 || value.any { it.digitToIntOrNull(16) == null }) {
        throw IOException("Desktop Noise public key is malformed")
    }
    return ByteArray(32) { index -> value.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
}
