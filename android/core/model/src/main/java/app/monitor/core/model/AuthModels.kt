package app.monitor.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DeviceRole {
    @SerialName("Admin") Admin,
    @SerialName("Operator") Operator,
    @SerialName("Viewer") Viewer,
    @SerialName("LocalUi") LocalUi
}

@Serializable
data class PairingRequest(
    @SerialName("pairing_code") val pairingCode: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("public_key") val publicKey: String? = null,
    @SerialName("cloud_device_id") val cloudDeviceId: String? = null
)

@Serializable
data class PairingResponse(
    @SerialName("device_token") val deviceToken: String,
    @SerialName("role") val role: DeviceRole,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("expires_at_utc_ms") val expiresAtUtcMs: Long? = null
)

@Serializable
data class WsTicketResponse(
    @SerialName("ticket") val ticket: String,
    @SerialName("expires_in_seconds") val expiresInSeconds: Int
)

@Serializable
data class HealthResponse(
    @SerialName("app_name") val appName: String,
    @SerialName("version") val version: String,
    @SerialName("auth_required") val authRequired: Boolean
)

