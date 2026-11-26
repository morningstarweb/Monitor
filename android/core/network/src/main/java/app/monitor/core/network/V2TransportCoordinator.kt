package app.monitor.core.network

import app.monitor.core.model.ServerEvent
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.IOException

/** Owns one remote HostRoom/WebRTC session for every dashboard and workspace operation. */
class V2TransportCoordinator(
    private val client: HttpClient,
    private val json: Json,
    private val tokenStorage: TokenStorage
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val relay = CloudRelayTransportV2(client, json, tokenStorage)
    private val manager = DualPathTransportManager(client, json, tokenStorage, relay, scope)
    private val connectLock = Mutex()
    @Volatile private var connectedHostId: String? = null
    @Volatile private var cachedHostId: String? = null

    val connectionPath: kotlinx.coroutines.flow.StateFlow<ConnectionPath>
        get() = manager.connectionPath

    /** NET-03: in-place ICE restart when a WebRTC data channel is live. */
    fun requestIceRestart(): Boolean = manager.requestIceRestart()

    /** Migrates active dual-path transports seamlessly on OS route/interface changes. */
    fun onNetworkChanged() {
        manager.onNetworkChanged()
    }

    fun forceReconnect() {
        val target = connectedHostId ?: cachedHostId
        connectedHostId = null
        manager.forceReconnect(target)
    }

    fun disconnect() {
        connectedHostId = null
        cachedHostId = null
        manager.disconnect()
    }

    suspend fun request(commandType: String, payload: JsonElement): JsonElement {
        val hostId = resolveHostId()
        ensureConnected(hostId)
        return manager.requestOperation(commandType, payload)
    }

    suspend fun hostDeviceId(): String = resolveHostId()

    fun streamEvents(hostDeviceId: String): Flow<ServerEvent> = flow {
        ensureConnected(hostDeviceId)
        manager.events.collect { emit(it) }
    }

    fun announceIfSignedIn(explicitHostId: String? = null) {
        val token = tokenStorage.getCloudSessionToken()
        if (token.isNullOrBlank()) return
        scope.launch {
            val hostId = explicitHostId?.takeIf { it.isNotBlank() } ?: runCatching { resolveHostId() }.getOrNull()
            if (!hostId.isNullOrBlank()) {
                ensureConnected(hostId)
            }
        }
    }

    suspend fun verifyOtpRemote(payload: JsonElement): JsonElement {
        val hostId = resolveHostId()
        ensureConnected(hostId)
        val deadlineMs = System.currentTimeMillis() + 12_000L
        while (System.currentTimeMillis() < deadlineMs) {
            if (manager.lanTransport.isConnected ||
                manager.webrtcClient?.isOpen == true ||
                relay.isConnected
            ) {
                break
            }
            kotlinx.coroutines.delay(150)
        }
        return manager.requestOperation("auth.verify_otp", payload)
    }

    private suspend fun ensureConnected(hostDeviceId: String) = connectLock.withLock {
        if (connectedHostId != hostDeviceId) {
            manager.connect(hostDeviceId)
            connectedHostId = hostDeviceId
        } else {
            manager.connect(hostDeviceId)
        }
    }

    private suspend fun resolveHostId(): String {
        connectedHostId?.let { return it }
        cachedHostId?.let { cached ->
            if (!tokenStorage.getCloudSessionToken().isNullOrBlank()) {
                return cached
            }
        }
        val serverHost = tokenStorage.getServerHost().trim()
        if (manager.lanTransport.isConnected && serverHost.isNotEmpty()) {
            return "lan_local_host"
        }
        val token = tokenStorage.getCloudSessionToken()
        if (token.isNullOrBlank()) {
            if (serverHost.isNotEmpty()) {
                return "lan_local_host"
            }
            throw IOException("Sign in with Google or pair a local desktop to connect")
        }
        val cachedHost = tokenStorage.getCloudHostDeviceId()?.takeIf { it.isNotBlank() }
        if (cachedHost != null && !tokenStorage.getCloudSessionToken().isNullOrBlank()) {
            cachedHostId = cachedHost
            return cachedHost
        }
        val desktop = runCatching {
            CloudTelemetryClient.fetch(client, json, tokenStorage, token).desktopDeviceId
        }.getOrNull()
        if (desktop != null) {
            cachedHostId = desktop
            tokenStorage.saveCloudHostDeviceId(desktop)
            return desktop
        }
        if (serverHost.isNotEmpty()) {
            return "lan_local_host"
        }
        throw IOException("No desktop is registered to this cloud account")
    }
}
