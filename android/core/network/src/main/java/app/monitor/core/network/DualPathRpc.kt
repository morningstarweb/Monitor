package app.monitor.core.network

import app.monitor.core.network.webrtc.sendRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import java.io.IOException

internal suspend fun DualPathTransportManager.requestOperation(
    commandType: String,
    payload: JsonElement
): JsonElement {
    val hostId = connectedHostDeviceId
    if (hostId != null) {
        connect(hostId)
    }

    if (!lanTransport.isConnected && webrtcClient?.isOpen != true && connectionPath.value == ConnectionPath.LAN_DIRECT && tokenStorage.getServerHost().isNotBlank()) {
        withTimeoutOrNull(400) {
            while (!lanTransport.isConnected) {
                delay(40)
            }
        }
    }

    val isLongOp = commandType.startsWith("terminal.") || commandType.startsWith("git.")
    val lanTimeout = if (isLongOp) 25_000L else 4_000L
    val webrtcTimeout = if (isLongOp) 40_000L else 8_000L
    val cloudTimeout = if (isLongOp) 60_000L else 15_000L

    // 1. Priority 1: Direct LAN Stream (v2) - Ultra-low latency (< 5ms) when on the same Wi-Fi
    val lanSession = lanTransport.session
    if (lanTransport.isConnected && lanSession != null) {
        val request = cloudRelay.rpc.begin(lanSession.sessionId, lanSession.epoch, commandType, payload)
        try {
            lanTransport.sendRequest(request)
            val quickResult = withTimeoutOrNull(lanTimeout) {
                cloudRelay.rpc.await(request, lanTimeout)
            }
            if (quickResult != null) return quickResult
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // LAN request failed or timed out, fall through to WebRTC / Cloud WSS
        } finally {
            cloudRelay.rpc.abandon(request)
        }
    }

    // 2. Priority 2: WebRTC DataChannel (Direct P2P or Cloudflare TURN Relay)
    val peer = webrtcClient
    val webrtcSession = peer?.activeSession
    if (peer != null && peer.isOpen && webrtcSession != null) {
        val request = cloudRelay.rpc.begin(webrtcSession.sessionId, webrtcSession.epoch, commandType, payload)
        try {
            peer.sendRequest(request)
            val quickResult = withTimeoutOrNull(webrtcTimeout) {
                cloudRelay.rpc.await(request, webrtcTimeout)
            }
            if (quickResult != null) return quickResult
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // WebRTC request failed or timed out, fall through to Cloud WSS
        } finally {
            cloudRelay.rpc.abandon(request)
        }
    }

    // 3. Priority 3: Cloudflare HostRoom WSS Relay (Always-on fallback pipe)
    val cloudSession = cloudRelay.awaitActiveSession()
    val request = cloudRelay.rpc.begin(cloudSession.sessionId, cloudSession.epoch, commandType, payload)
    try {
        cloudRelay.sendRequest(request)
        return cloudRelay.rpc.await(request, cloudTimeout)
    } finally {
        cloudRelay.rpc.abandon(request)
    }
}
