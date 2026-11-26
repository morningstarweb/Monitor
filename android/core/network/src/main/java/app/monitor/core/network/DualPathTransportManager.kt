package app.monitor.core.network

import app.monitor.core.model.ServerEvent
import app.monitor.core.network.webrtc.WebRtcDataChannelClient
import app.monitor.core.network.webrtc.WebRtcSignalMessage
import app.monitor.core.network.webrtc.createAndSendIceRestartOffer
import app.monitor.core.network.webrtc.handleRemoteAnswer
import app.monitor.core.network.webrtc.handleRemoteCandidate
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

enum class ConnectionPath(val isRelay: Boolean) {
    LAN_DIRECT(false),
    WEBRTC_P2P(false),
    WEBRTC_TURN(true),
    CLOUD_RELAY_WSS(true),
    DISCONNECTED(false);

    val isWebRtcDataChannel: Boolean
        get() = this == WEBRTC_P2P || this == WEBRTC_TURN
}

/**
 * Android Dual-Path & Failover Transport Manager (MONITOR v3.2 Protocol).
 * Coordinates transparent switching between LAN Direct, WebRTC P2P DataChannel,
 * and Cloudflare HostRoom WSS Relay without losing UI state or duplicate command execution.
 */
class DualPathTransportManager(
    private val client: HttpClient,
    private val json: Json,
    internal val tokenStorage: TokenStorage,
    internal val cloudRelay: CloudRelayTransportV2,
    private val scope: CoroutineScope
) {
    private val _connectionPath = MutableStateFlow(ConnectionPath.DISCONNECTED)
    val connectionPath: StateFlow<ConnectionPath> = _connectionPath.asStateFlow()

    private val _events = MutableSharedFlow<ServerEvent>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<ServerEvent> = _events.asSharedFlow()

    private var activeJob: Job? = null
    internal var webrtcClient: WebRtcDataChannelClient? = null
    internal val lanTransport = LanTransportV2(client, json, tokenStorage, cloudRelay.rpc)
    internal var connectedHostDeviceId: String? = null
    @Volatile internal var activeViewerSessionId: String? = null
    private var lastCloudSession: Triple<String, SecureSessionV2, String>? = null
    @Volatile private var lastLanEventAtMs: Long = 0L
    @Volatile private var lastWebRtcEventAtMs: Long = 0L

    companion object {
        internal const val HIGH_PRIORITY_STALL_MS = 2_500L

        internal fun shouldPromoteCloudFallback(
            currentPath: ConnectionPath,
            lanConnected: Boolean,
            webrtcOpen: Boolean,
            nowMs: Long,
            lastLanEventAtMs: Long,
            lastWebRtcEventAtMs: Long
        ): Boolean = when {
            currentPath == ConnectionPath.LAN_DIRECT ->
                !lanConnected && (nowMs - lastLanEventAtMs >= HIGH_PRIORITY_STALL_MS)
            currentPath.isWebRtcDataChannel ->
                !webrtcOpen || (nowMs - lastWebRtcEventAtMs >= HIGH_PRIORITY_STALL_MS)
            else -> true
        }
    }

    fun connect(hostDeviceId: String) {
        if (connectedHostDeviceId == hostDeviceId && activeJob?.isActive == true) return
        activeJob?.cancel()
        connectedHostDeviceId = hostDeviceId
        activeJob = scope.launch {
            raceTransportsAndStream(hostDeviceId)
        }
    }

    fun onNetworkChanged() {
        lastLanEventAtMs = 0L
        lastWebRtcEventAtMs = 0L
        activeViewerSessionId = null
        evictMonitorHttpPool(client)
        // Old ICE UDP sockets bound to the departed interface linger in OPEN state
        // for 10+ seconds; tear down WebRTC immediately so RPCs never stall on a dead DataChannel.
        webrtcClient?.let { deadPeer ->
            runCatching { deadPeer.close() }
        }
        webrtcClient = null
        lanTransport.onNetworkChanged()
        cloudRelay.onNetworkChanged()
        promoteFallbackPath()
        val targetHostId = connectedHostDeviceId
        if (targetHostId != null && activeJob?.isActive != true) {
            connect(targetHostId)
        }
    }

    private fun promoteFallbackPath() {
        if (lanTransport.isConnected) {
            _connectionPath.value = ConnectionPath.LAN_DIRECT
            MonitorApiClient.currentRoute = "Direct LAN Stream (v2)"
            return
        }
        val rtc = webrtcClient
        val rtcState = rtc?.connectionState?.value
        if (rtc?.isOpen == true && rtcState != null && rtcState.isWebRtcDataChannel) {
            _connectionPath.value = rtcState
            MonitorApiClient.currentRoute = if (rtcState == ConnectionPath.WEBRTC_TURN) {
                "WebRTC TURN Relay"
            } else {
                "WebRTC P2P Stream"
            }
            return
        }
        if (cloudRelay.isConnected) {
            _connectionPath.value = ConnectionPath.CLOUD_RELAY_WSS
            MonitorApiClient.currentRoute = "Cloudflare Edge Relay"
        } else {
            _connectionPath.value = ConnectionPath.DISCONNECTED
        }
    }

    fun forceReconnect(hostDeviceId: String? = null) {
        val targetHostId = hostDeviceId ?: connectedHostDeviceId ?: return
        activeJob?.cancel()
        activeJob = null
        connectedHostDeviceId = null
        activeViewerSessionId = null
        lastCloudSession = null
        lastLanEventAtMs = 0L
        lastWebRtcEventAtMs = 0L
        cloudRelay.onWebRtcSignal = null
        cloudRelay.onWsOpen = null
        lanTransport.onDisconnected = null
        lanTransport.resetSession()
        cloudRelay.resetSession()
        webrtcClient?.close()
        webrtcClient = null
        _connectionPath.value = ConnectionPath.DISCONNECTED
        connect(targetHostId)
    }

    /**
     * NET-03: on a network change, ask the host to restart ICE in place. The
     * encrypted application session, Noise keys and viewer identity survive;
     * only the underlying candidate set is refreshed. Returns false when no
     * live signaling/data channel is ready, so callers fall back to reconnect.
     */
    fun requestIceRestart(): Boolean {
        val client = webrtcClient ?: return false
        if (client.connectionState.value != ConnectionPath.WEBRTC_P2P &&
            client.connectionState.value != ConnectionPath.WEBRTC_TURN
        ) return false
        if (!cloudRelay.isConnected) return false
        val sessionId = activeViewerSessionId ?: return false
        val signal = WebRtcSignalMessage(signal = "restart", viewerSessionId = sessionId)
        scope.launch {
            val sent = cloudRelay.sendSignalText(json.encodeToString(WebRtcSignalMessage.serializer(), signal))
            if (!sent) {
                cloudRelay.onNetworkChanged()
            }
        }
        return true
    }

    fun disconnect() {
        activeJob?.cancel()
        activeJob = null
        connectedHostDeviceId = null
        activeViewerSessionId = null
        lastCloudSession = null
        lastLanEventAtMs = 0L
        lastWebRtcEventAtMs = 0L
        cloudRelay.onWebRtcSignal = null
        cloudRelay.onWsOpen = null
        lanTransport.onDisconnected = null
        lanTransport.resetSession()
        cloudRelay.resetSession()
        webrtcClient?.close()
        webrtcClient = null
        _connectionPath.value = ConnectionPath.DISCONNECTED
    }

    private suspend fun raceTransportsAndStream(
        hostDeviceId: String
    ) {
        if (_connectionPath.value != ConnectionPath.LAN_DIRECT) {
            _connectionPath.value = ConnectionPath.DISCONNECTED
            MonitorApiClient.currentRoute = "Connecting..."
        }

        val webrtc = WebRtcDataChannelClient(
            context = tokenStorage.appContext,
            client = client,
            json = json,
            tokenStorage = tokenStorage,
            scope = scope,
            sendSignal = { sig ->
                val text = json.encodeToString(WebRtcSignalMessage.serializer(), sig)
                scope.launch { cloudRelay.sendSignalText(text) }
            },
            rpc = cloudRelay.rpc
        )
        webrtcClient = webrtc

        cloudRelay.onWebRtcSignal = { sig ->
            when (sig.signal) {
                "answer" -> sig.sdp?.let { webrtc.handleRemoteAnswer(it) }
                "candidate" -> sig.candidate?.let { webrtc.handleRemoteCandidate(it) }
                // NET-03: host confirmed ICE restart; renegotiate without
                // dropping the application session or its Noise keys.
                "restarted" -> webrtc.createAndSendIceRestartOffer(
                    viewerSessionId = sig.viewerSessionId ?: activeViewerSessionId.orEmpty()
                )
            }
        }

        cloudRelay.onWsOpen = { brokerUrl, session, viewerSessionId ->
            activeViewerSessionId = viewerSessionId
            lastCloudSession = Triple(brokerUrl, session, viewerSessionId)
            if (_connectionPath.value != ConnectionPath.LAN_DIRECT && !_connectionPath.value.isWebRtcDataChannel) {
                _connectionPath.value = ConnectionPath.CLOUD_RELAY_WSS
                MonitorApiClient.currentRoute = "Cloudflare Edge Relay"
            }
            if (session.dataChannelReady) {
                webrtc.startPeerConnection(brokerUrl, hostDeviceId, session, viewerSessionId)
            } else {
                webrtc.close()
            }
        }

        try {
            // Structured concurrency guarantees disconnect() / forceReconnect() cancels
            // every transport collector without leaking orphaned background loops (AUD-009).
            coroutineScope {
                lanTransport.onDisconnected = {
                    if (_connectionPath.value == ConnectionPath.LAN_DIRECT) {
                        promoteFallbackPath()
                    }
                }
                launch {
                    var currentLanHost = ""
                    while (true) {
                        val rawHost = tokenStorage.getServerHost().trim()
                        val port = tokenStorage.getServerPort()
                        val lanHost = if (rawHost == "127.0.0.1" || rawHost == "localhost") "10.0.2.2" else rawHost
                        if (lanHost.isNotBlank()) {
                            currentLanHost = lanHost
                            try {
                                lanTransport.streamEvents(currentLanHost, port).collect { event ->
                                    lastLanEventAtMs = System.currentTimeMillis()
                                    _connectionPath.value = ConnectionPath.LAN_DIRECT
                                    MonitorApiClient.currentRoute = "Direct LAN Stream (v2)"
                                    _events.emit(event)
                                }
                            } catch (c: kotlinx.coroutines.CancellationException) {
                                throw c
                            } catch (_: Exception) {}
                            if (_connectionPath.value == ConnectionPath.LAN_DIRECT) {
                                promoteFallbackPath()
                            }
                            kotlinx.coroutines.delay(1_000)
                        } else {
                            kotlinx.coroutines.delay(2_000)
                        }
                    }
                }

                launch {
                    webrtc.connectionState.collectLatest { path ->
                        if (_connectionPath.value != ConnectionPath.LAN_DIRECT || !lanTransport.isConnected) {
                            if (path.isWebRtcDataChannel) {
                                _connectionPath.value = path
                                MonitorApiClient.currentRoute = if (path == ConnectionPath.WEBRTC_TURN) {
                                    "WebRTC TURN Relay"
                                } else "WebRTC P2P Stream"
                            } else if (_connectionPath.value.isWebRtcDataChannel) {
                                promoteFallbackPath()
                            }
                        }
                    }
                }

                launch {
                    webrtc.events.collect { ev ->
                        val now = System.currentTimeMillis()
                        if (_connectionPath.value != ConnectionPath.LAN_DIRECT ||
                            (!lanTransport.isConnected && now - lastLanEventAtMs >= HIGH_PRIORITY_STALL_MS)
                        ) {
                            lastWebRtcEventAtMs = now
                            val rtcState = webrtc.connectionState.value
                            if (rtcState.isWebRtcDataChannel) {
                                _connectionPath.value = rtcState
                                MonitorApiClient.currentRoute = if (rtcState == ConnectionPath.WEBRTC_TURN) {
                                    "WebRTC TURN Relay"
                                } else "WebRTC P2P Stream"
                            } else if (!_connectionPath.value.isWebRtcDataChannel) {
                                _connectionPath.value = ConnectionPath.WEBRTC_P2P
                                MonitorApiClient.currentRoute = "WebRTC P2P Stream"
                            }
                            _events.emit(ev)
                        }
                    }
                }

                try {
                    cloudRelay.streamEvents(hostDeviceId).collectLatest { event ->
                        val now = System.currentTimeMillis()
                        val current = _connectionPath.value
                        if (shouldPromoteCloudFallback(
                                currentPath = current,
                                lanConnected = lanTransport.isConnected,
                                webrtcOpen = webrtc.isOpen,
                                nowMs = now,
                                lastLanEventAtMs = lastLanEventAtMs,
                                lastWebRtcEventAtMs = lastWebRtcEventAtMs
                            )
                        ) {
                            _connectionPath.value = ConnectionPath.CLOUD_RELAY_WSS
                            MonitorApiClient.currentRoute = "Cloudflare Edge Relay"
                            _events.emit(event)
                        }
                    }
                } catch (_: Exception) {
                    if (_connectionPath.value != ConnectionPath.LAN_DIRECT && !_connectionPath.value.isWebRtcDataChannel) {
                        _connectionPath.value = ConnectionPath.DISCONNECTED
                    }
                }
            }
        } finally {
            lanTransport.onDisconnected = null
            cloudRelay.onWebRtcSignal = null
            cloudRelay.onWsOpen = null
            activeViewerSessionId = null
            lastCloudSession = null
            webrtc.close()
            if (webrtcClient === webrtc) {
                webrtcClient = null
            }
        }
    }
}
