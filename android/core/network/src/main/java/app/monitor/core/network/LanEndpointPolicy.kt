package app.monitor.core.network

import java.io.IOException

/**
 * Application-level guard for the only transport that legitimately uses
 * cleartext on Android: direct LAN discovery/pairing/streaming (AUD-03).
 *
 * Android Network Security Configuration cannot express RFC1918 CIDR ranges,
 * so a global `cleartextTrafficPermitted="true"` would also allow plaintext
 * traffic to arbitrary public hosts. This policy closes that gap by requiring
 * every cleartext URL to target a loopback, emulator, link-local, RFC1918 or
 * mDNS host on one of the two application ports. All other endpoints must use
 * TLS/WSS.
 */
object LanEndpointPolicy {
    const val LAN_PORT = 9480
    const val OAUTH_CALLBACK_PORT = 9481
    private val ALLOWED_PORTS = LAN_PORT..(LAN_PORT + 20)

    fun isAllowedCleartextEndpoint(host: String, port: Int): Boolean {
        if (port !in ALLOWED_PORTS) return false

        val normalized = host.trim().trimEnd('.').lowercase()
        if (normalized.isEmpty()) return false
        if (normalized in setOf(
                "localhost",
                "127.0.0.1",
                "::1",
                "[::1]",
                "10.0.2.2" // Android emulator host alias
            )
        ) return true
        if (normalized.endsWith(".local")) return true // mDNS discovery names

        val octets = parseIpv4(normalized) ?: return false
        return when {
            octets[0] == 10 -> true // 10.0.0.0/8
            octets[0] == 172 && octets[1] in 16..31 -> true // 172.16.0.0/12
            octets[0] == 192 && octets[1] == 168 -> true // 192.168.0.0/16
            octets[0] == 169 && octets[1] == 254 -> true // 169.254.0.0/16 link-local
            octets[0] == 127 -> true // loopback
            else -> false
        }
    }

    /** Fail fast before any cleartext request is constructed. */
    fun requireAllowedCleartextEndpoint(host: String, port: Int) {
        if (!isAllowedCleartextEndpoint(host, port)) {
            throw IOException("Blocked cleartext connection to non-LAN endpoint: $host:$port")
        }
    }

    private fun parseIpv4(value: String): IntArray? {
        val parts = value.split('.')
        if (parts.size != 4) return null
        val result = IntArray(4)
        for (index in 0..3) {
            val part = parts[index]
            if (part.isEmpty() || part.length > 3 || !part.all { it.isDigit() }) return null
            val octet = part.toIntOrNull() ?: return null
            if (octet !in 0..255) return null
            result[index] = octet
        }
        return result
    }
}
