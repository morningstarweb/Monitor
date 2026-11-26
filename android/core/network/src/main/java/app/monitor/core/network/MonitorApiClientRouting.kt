package app.monitor.core.network

import kotlinx.coroutines.CancellationException
import java.io.IOException

internal suspend fun <T> MonitorApiClient.requestWithFallback(
    block: suspend (baseUrl: String) -> T
): T {
    val host = tokenStorage.getServerHost().trim()
    val port = tokenStorage.getServerPort()
    // AUD-03: never construct a cleartext URL for a non-LAN destination.
    LanEndpointPolicy.requireAllowedCleartextEndpoint(
        if (host.isBlank()) "127.0.0.1" else host,
        port
    )
    val candidates = if (host.isNotBlank()) {
        mutableListOf("http://$host:$port/api/v1").apply {
            if (host == "127.0.0.1" || host == "localhost") add("http://10.0.2.2:$port/api/v1")
        }
    } else mutableListOf("http://10.0.2.2:$port/api/v1", "http://127.0.0.1:$port/api/v1")

    var lastError: Throwable? = null
    for (candidate in candidates) {
        try {
            val result = block(candidate)
            MonitorApiClient.recordSuccess()
            return result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            MonitorApiClient.recordFailure()
            lastError = error
        }
    }
    throw lastError ?: IOException("Cannot reach desktop at $host:$port")
}

internal suspend fun <T> MonitorApiClient.gitRequest(
    block: suspend (baseUrl: String, token: String) -> T
): T {
    val deviceToken = tokenStorage.getDeviceToken()
    if (deviceToken != null) {
        return requestWithFallback { base -> block(base, deviceToken) }
    }
    // Workstation REST paths (/projects, /alerts, /git, /watchdog) only run on the desktop host or
    // via encrypted E2EE V2 tunnel — never against monitor-cloud-broker/api/v1 (AUD-015).
    throw IOException("Workspace request requires an active LAN pairing or E2EE V2 tunnel to your desktop.")
}
