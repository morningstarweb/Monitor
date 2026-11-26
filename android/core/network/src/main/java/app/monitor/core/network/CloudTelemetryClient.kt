package app.monitor.core.network

import app.monitor.core.model.RepoSummary
import app.monitor.core.model.SystemSnapshot
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import org.json.JSONObject

data class CloudData(
    val snapshot: SystemSnapshot?,
    val projects: List<RepoSummary>,
    val isOnline: Boolean = true,
    val desktopDeviceId: String? = null
)

object CloudTelemetryClient {
    private const val CLOUD_BROKER_URL = "https://your-monitor-broker.workers.dev/api/v1/telemetry"

    suspend fun fetch(
        client: HttpClient,
        json: Json,
        tokenStorage: TokenStorage,
        cloudToken: String
    ): CloudData {
        val respText = client.get(CLOUD_BROKER_URL) {
            header("Authorization", "Bearer $cloudToken")
            timeout { requestTimeoutMillis = 10_000 }
        }.bodyAsText()

        val obj = JSONObject(respText)
        val desktopObj = obj.optJSONObject("desktop")
        if (desktopObj != null) {
            val lanIp = desktopObj.optString("lan_ip")
            val lanPort = desktopObj.optInt("lan_port", 9480)
            if (lanIp.isNotBlank() && lanIp != "null") {
                tokenStorage.saveServerEndpoint(lanIp, lanPort)
            }
        }

        val snapshot = obj.optJSONObject("snapshot")?.let {
            runCatching { json.decodeFromString<SystemSnapshot>(it.toString()) }.getOrNull()
        }
        val projects = obj.optJSONArray("projects")?.let {
            runCatching { json.decodeFromString<List<RepoSummary>>(it.toString()) }.getOrNull()
        }.orEmpty()

        val isOnline = desktopObj?.optBoolean("is_online", false) ?: false

        val desktopDeviceId = desktopObj?.optString("id")?.takeIf { it.isNotBlank() && it != "null" }
        return CloudData(snapshot, projects, isOnline, desktopDeviceId)
    }
}
