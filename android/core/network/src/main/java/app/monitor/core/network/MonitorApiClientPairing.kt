package app.monitor.core.network

import app.monitor.core.model.HealthResponse
import app.monitor.core.model.PairingRequest
import app.monitor.core.model.PairingResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

suspend fun MonitorApiClient.getHealthApi(host: String, port: Int): Result<HealthResponse> = apiResult {
    LanEndpointPolicy.requireAllowedCleartextEndpoint(host, port)
    client.get("http://$host:$port/api/v1/health") {
        timeout { connectTimeoutMillis = 1_500 }
    }.body()
}


suspend fun MonitorApiClient.pairApi(host: String, port: Int, request: PairingRequest): Result<PairingResponse> = apiResult {
    if (port != 443) LanEndpointPolicy.requireAllowedCleartextEndpoint(host, port)
    val scheme = if (port == 443) "https" else "http"
    client.post("$scheme://$host:$port/api/v1/auth/pair") {
        timeout {
            connectTimeoutMillis = 2_500
            requestTimeoutMillis = 5_000
        }
        contentType(ContentType.Application.Json)
        header("X-Origin-Host", host)
        setBody(request)
    }.body()
}

suspend fun MonitorApiClient.verifyDeviceOtpRemoteApi(
    pairingCode: String,
    deviceName: String,
    publicKeyHex: String,
    cloudDeviceId: String?
): Result<PairingResponse> = apiResult {
    val payload = buildJsonObject {
        put("pairing_code", pairingCode)
        put("device_name", deviceName)
        put("public_key", publicKeyHex)
        if (!cloudDeviceId.isNullOrBlank()) {
            put("cloud_device_id", cloudDeviceId)
        }
    }
    val resJson = v2Transport.verifyOtpRemote(payload)
    json.decodeFromJsonElement(PairingResponse.serializer(), resJson)
}

fun MonitorApiClient.announceUnverifiedDeviceApi(hostId: String? = null) {
    v2Transport.announceIfSignedIn(hostId)
}


suspend fun MonitorApiClient.killProcessApi(pid: Long): Result<Boolean> = apiResult {
    gitRequest { base, token ->
        val res = client.post("$base/system/processes/$pid/kill") {
            timeout { connectTimeoutMillis = 3_000 }
            header("Authorization", "Bearer $token")
        }
        res.status.value in 200..299
    }
}
