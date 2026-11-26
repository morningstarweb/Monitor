package app.monitor.core.network

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import io.ktor.client.call.body
import java.io.IOException
import kotlinx.coroutines.CancellationException

internal suspend fun MonitorApiClient.v2RequestIfCloud(
    commandType: String,
    payload: JsonElement,
    localRequest: (suspend () -> JsonElement)? = null
): JsonElement? {
    if (!tokenStorage.isAuthenticated()) return null
    val currentPath = v2Transport.connectionPath.value
    val isRtcOrCloud = currentPath.isWebRtcDataChannel || currentPath == ConnectionPath.CLOUD_RELAY_WSS
    val hasDeviceToken = tokenStorage.getDeviceToken() != null
    val hasCloudToken = !tokenStorage.getCloudSessionToken().isNullOrBlank()
    if (!isRtcOrCloud && localRequest != null && hasDeviceToken && !MonitorApiClient.isCoolingDown()) {
        try {
            val local = localRequest()
            MonitorApiClient.currentRoute = "Direct LAN Stream"
            return local
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            MonitorApiClient.currentRoute = "Cloudflare Edge Relay"
        }
    }
    return try {
        val remote = v2Transport.request(commandType, payload)
        if (!MonitorApiClient.currentRoute.startsWith("WebRTC ") && !MonitorApiClient.currentRoute.startsWith("Direct LAN")) {
            MonitorApiClient.currentRoute = "Cloudflare Edge Relay"
        }
        remote
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        if (!isRtcOrCloud && hasDeviceToken && localRequest != null) {
            runCatching {
                val fallback = localRequest()
                MonitorApiClient.currentRoute = "Direct LAN Stream"
                fallback
            }.getOrNull()?.let { return it }
        }
        // Never silently fall through to Worker /api/v1/projects/... HTTP endpoints that do not exist (AUD-015).
        if (hasCloudToken && !hasDeviceToken) throw e
        null
    }
}

internal suspend fun MonitorApiClient.localJsonRequest(
    request: suspend (String, String) -> JsonElement
): JsonElement {
    val token = tokenStorage.getDeviceToken() ?: throw IOException("No local desktop token is paired")
    return requestWithFallback { base -> request(base, token) }
}

internal inline fun <reified T> MonitorApiClient.decodeV2Result(value: JsonElement): T =
    json.decodeFromJsonElement(value)
