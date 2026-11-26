package app.monitor.core.network

import app.monitor.core.model.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

class MonitorApiClient(
    internal val tokenStorage: TokenStorage
) : DashboardConnection {
    internal val json = Json {
        ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true; coerceInputValues = true
    }

    internal val client: HttpClient = createMonitorHttpClient(json)
    internal val v2Transport by lazy { V2TransportCoordinator(client, json, tokenStorage) }

    fun close() {
        runCatching { v2Transport.disconnect() }
        runCatching { client.close() }
    }

    companion object {
        @Volatile private var lastLocalFailureMs: Long = 0L
        @Volatile var currentRoute: String = "Direct LAN Stream"
        internal fun isCoolingDown(): Boolean = (System.currentTimeMillis() - lastLocalFailureMs) < 8_000L
        internal fun recordFailure() { lastLocalFailureMs = System.currentTimeMillis(); currentRoute = "Cloudflare Edge Relay" }
        internal fun recordSuccess() { lastLocalFailureMs = 0L; currentRoute = "Direct LAN Stream" }
        internal fun clearCooldown() { lastLocalFailureMs = 0L }
    }

    override val activeRoute: String get() = currentRoute

    override fun notifyNetworkChanged() {
        clearCooldown()
        v2Transport.onNetworkChanged()
    }

    suspend fun getHealth(host: String, port: Int): Result<HealthResponse> = getHealthApi(host, port)
    suspend fun pair(host: String, port: Int, req: PairingRequest): Result<PairingResponse> = pairApi(host, port, req)
    suspend fun verifyDeviceOtpRemote(
        pairingCode: String,
        deviceName: String,
        publicKeyHex: String,
        cloudDeviceId: String?
    ): Result<PairingResponse> = verifyDeviceOtpRemoteApi(pairingCode, deviceName, publicKeyHex, cloudDeviceId)
    fun announceUnverifiedDevice(hostId: String? = null) = announceUnverifiedDeviceApi(hostId)
    fun disconnectTransport() = v2Transport.disconnect()

    override suspend fun killProcess(pid: Long): Result<Boolean> = apiResult {
        v2RequestIfCloud("process.kill", buildJsonObject { put("pid", pid) }) {
            localJsonRequest { base, token ->
                client.post("$base/system/processes/$pid/kill") {
                    timeout { connectTimeoutMillis = 3_000 }
                    header("Authorization", "Bearer $token")
                }
                JsonPrimitive(true)
            }
        }?.let {
            decodeV2Result<Boolean>(it)
        } ?: killProcessApi(pid).getOrThrow()
    }

    override suspend fun acquireWsTicket(): Result<String> = apiResult {
        val token = tokenStorage.getDeviceToken()
        val cloudToken = tokenStorage.getCloudSessionToken()
        if (!cloudToken.isNullOrBlank()) {
            val hostId = v2Transport.hostDeviceId()
            currentRoute = when (v2Transport.connectionPath.value) {
                ConnectionPath.LAN_DIRECT -> "Direct LAN Stream (v2)"
                ConnectionPath.WEBRTC_P2P -> "WebRTC P2P Stream"
                ConnectionPath.WEBRTC_TURN -> "WebRTC TURN Relay"
                else -> "Cloudflare Edge Relay"
            }
            return@apiResult "cloud_v2:$hostId"
        }
        if (token != null && !isCoolingDown()) {
            val localResult = runCatching {
                requestWithFallback { base ->
                    val response: WsTicketResponse = client.post("$base/auth/ws-ticket") {
                        timeout { connectTimeoutMillis = 1_500 }
                        header("Authorization", "Bearer $token")
                    }.body()
                    response.ticket
                }
            }
            if (localResult.isSuccess) {
                currentRoute = "Direct LAN Stream"
                return@apiResult localResult.getOrThrow()
            }
        }
        error("Cannot reach desktop: connect to same Wi-Fi or sign in with Google Cloud")
    }

    override suspend fun getSystemSnapshot(): Result<SystemSnapshot> = apiResult {
        v2RequestIfCloud("telemetry.poll", buildJsonObject {}) {
            localJsonRequest { base, token ->
                client.get("$base/device/snapshot") {
                    timeout { connectTimeoutMillis = 1_500 }
                    header("Authorization", "Bearer $token")
                }.body<JsonElement>()
            }
        }?.let {
            return@apiResult decodeV2Result(it)
        }
        val token = tokenStorage.getDeviceToken()
        val cloudToken = tokenStorage.getCloudSessionToken()
        if (!cloudToken.isNullOrBlank() && isCoolingDown()) {
            val cloudData = CloudTelemetryClient.fetch(client, json, tokenStorage, cloudToken)
            if (cloudData.snapshot != null) { currentRoute = "Cloudflare Edge Relay"; return@apiResult cloudData.snapshot }
        }
        if (token != null && !isCoolingDown()) {
            val localResult = runCatching {
                requestWithFallback { base ->
                    client.get("$base/device/snapshot") {
                        timeout { connectTimeoutMillis = 1_500 }
                        header("Authorization", "Bearer $token")
                    }.body<SystemSnapshot>()
                }
            }
            if (localResult.isSuccess) { currentRoute = "Direct LAN Stream"; return@apiResult localResult.getOrThrow() }
        }
        if (!cloudToken.isNullOrBlank()) {
            val cloudData = CloudTelemetryClient.fetch(client, json, tokenStorage, cloudToken)
            if (cloudData.snapshot != null) { currentRoute = "Cloudflare Edge Relay"; return@apiResult cloudData.snapshot }
        }
        error("Cannot reach desktop")
    }

    override suspend fun getProjects(): Result<List<RepoSummary>> = apiResult {
        v2RequestIfCloud("projects.list", buildJsonObject {}) {
            localJsonRequest { base, token ->
                client.get("$base/projects") {
                    timeout { connectTimeoutMillis = 1_500 }
                    header("Authorization", "Bearer $token")
                }.body<JsonElement>()
            }
        }?.let {
            return@apiResult decodeV2Result(it)
        }
        val token = tokenStorage.getDeviceToken()
        val cloudToken = tokenStorage.getCloudSessionToken()
        if (!cloudToken.isNullOrBlank() && isCoolingDown()) {
            val cloudData = CloudTelemetryClient.fetch(client, json, tokenStorage, cloudToken)
            currentRoute = "Cloudflare Edge Relay"
            return@apiResult cloudData.projects
        }
        if (token != null && !isCoolingDown()) {
            val localResult = runCatching {
                requestWithFallback { base ->
                    client.get("$base/projects") {
                        timeout { connectTimeoutMillis = 1_500 }
                        header("Authorization", "Bearer $token")
                    }.body<List<RepoSummary>>()
                }
            }
            if (localResult.isSuccess) { currentRoute = "Direct LAN Stream"; return@apiResult localResult.getOrThrow() }
        }
        if (!cloudToken.isNullOrBlank()) {
            val cloudData = CloudTelemetryClient.fetch(client, json, tokenStorage, cloudToken)
            currentRoute = "Cloudflare Edge Relay"
            return@apiResult cloudData.projects
        }
        error("Cannot reach desktop")
    }

    override suspend fun getPermissions(): Result<PermissionsResponse> = apiResult {
        v2RequestIfCloud("permissions.get", buildJsonObject {}) {
            localJsonRequest { base, token ->
                client.get("$base/permissions") {
                    timeout { connectTimeoutMillis = 2_500 }
                    header("Authorization", "Bearer $token")
                }.body<JsonElement>()
            }
        }?.let {
            return@apiResult decodeV2Result(it)
        }
        gitRequest { base, token ->
            client.get("$base/permissions") {
                timeout { connectTimeoutMillis = 2_500 }
                header("Authorization", "Bearer $token")
            }.body()
        }
    }

    override suspend fun getAlerts(limit: Int, activeOnly: Boolean): Result<List<AlertItem>> = getAlertsApi(limit, activeOnly)
    override suspend fun ackAlert(alertId: String): Result<Boolean> = ackAlertApi(alertId)
    override suspend fun snoozeAlert(alertId: String, durationS: Long): Result<Boolean> = snoozeAlertApi(alertId, durationS)
    override suspend fun getAgentSessions(): Result<List<AgentSessionSummary>> = getAgentSessionsApi()
    override suspend fun getCheckpoints(repoPath: String, limit: Int): Result<List<CheckpointItem>> = getCheckpointsApi(repoPath, limit)
    override suspend fun createCheckpoint(repoPath: String, description: String?): Result<CheckpointItem?> = createCheckpointApi(repoPath, description).map { it }
    override suspend fun rollbackCheckpoint(checkpointId: String): Result<RollbackResponse?> = rollbackCheckpointApi(checkpointId).map { it }
    override suspend fun killAgentSession(sessionId: String): Result<Boolean> = killAgentSessionApi(sessionId)

    override fun streamServerEvents(ticket: String): Flow<ServerEvent> =
        if (ticket.startsWith("cloud_v2:")) {
            v2Transport.streamEvents(ticket.removePrefix("cloud_v2:"))
        } else {
            ServerEventStreamer.stream(client, json, tokenStorage, ticket)
        }
}
