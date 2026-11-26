package app.monitor.core.network.webrtc

import android.content.Context
import app.monitor.core.model.ServerEvent
import app.monitor.core.network.ConnectionPath
import app.monitor.core.network.TokenStorage
import app.monitor.core.network.SecureSessionV2
import app.monitor.core.network.v2.V2RequestCorrelator
import app.monitor.core.network.decodeServerEvent
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import java.nio.ByteBuffer

/**
 * Android WebRTC DataChannel Client (MONITOR v3.2 Protocol).
 * Provides ultra-low-latency direct P2P (< 20ms) telemetry streaming over WebRTC DataChannel.
 */
class WebRtcDataChannelClient(
    private val context: Context,
    private val client: HttpClient,
    private val json: Json,
    private val tokenStorage: TokenStorage,
    private val scope: CoroutineScope,
    internal val sendSignal: (WebRtcSignalMessage) -> Unit,
    internal val rpc: V2RequestCorrelator = V2RequestCorrelator(json)
) {
    private var peerConnectionFactory: PeerConnectionFactory? = null
    internal var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    internal var activeSession: SecureSessionV2? = null
    private var connectJob: Job? = null

    val isOpen: Boolean
        get() = dataChannel?.state() == DataChannel.State.OPEN

    private val _connectionState = MutableStateFlow(ConnectionPath.DISCONNECTED)
    val connectionState: StateFlow<ConnectionPath> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ServerEvent> = _events.asSharedFlow()

    companion object {
        @Volatile private var isInitialized = false
        @Volatile private var sharedFactory: PeerConnectionFactory? = null

        private fun getOrCreateFactory(ctx: Context): PeerConnectionFactory? {
            sharedFactory?.let { return it }
            return synchronized(WebRtcDataChannelClient::class.java) {
                if (!isInitialized) {
                    val options = PeerConnectionFactory.InitializationOptions
                        .builder(ctx.applicationContext)
                        .setFieldTrials(
                            "WebRTC-IceFieldTrials/" +
                            "disabled_mdns_obfuscation_for_ice_1:true/"
                        )
                        .createInitializationOptions()
                    PeerConnectionFactory.initialize(options)
                    isInitialized = true
                }
                sharedFactory ?: PeerConnectionFactory.builder().createPeerConnectionFactory().also {
                    sharedFactory = it
                }
            }
        }
    }

    init {
        peerConnectionFactory = getOrCreateFactory(context)
    }

    fun startPeerConnection(
        brokerUrl: String,
        hostDeviceId: String,
        secureSession: SecureSessionV2,
        viewerSessionId: String
    ) {
        close()
        activeSession = secureSession
        connectJob = scope.launch {
            val iceServers = WebRtcIceFetcher.fetch(client, json, tokenStorage, brokerUrl)
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            }

            val observer = object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    when (state) {
                        PeerConnection.IceConnectionState.DISCONNECTED,
                        PeerConnection.IceConnectionState.FAILED -> _connectionState.value = ConnectionPath.DISCONNECTED
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> peerConnection?.let { peer ->
                            inspectWebRtcPath(peer) { path ->
                                if (peerConnection === peer && dataChannel?.state() == DataChannel.State.OPEN) {
                                    _connectionState.value = path
                                }
                            }
                        }
                        else -> Unit
                    }
                }
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let {
                        val payload = IceCandidatePayload(it.sdp, it.sdpMid, it.sdpMLineIndex)
                        sendSignal(WebRtcSignalMessage(
                            signal = "candidate", viewerSessionId = viewerSessionId, candidate = payload
                        ))
                    }
                }
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onAddStream(stream: org.webrtc.MediaStream?) {}
                override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}
                override fun onDataChannel(dc: DataChannel?) { setupDataChannel(dc) }
                override fun onRenegotiationNeeded() {}
            }

            peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)
            val dcInit = DataChannel.Init().apply {
                ordered = true
                maxRetransmits = -1
            }
            val localDc = peerConnection?.createDataChannel("monitor-telemetry", dcInit)
            setupDataChannel(localDc)

            createAndSendOffer(viewerSessionId)
        }
    }

    private fun setupDataChannel(dc: DataChannel?) {
        val channel = dc ?: return
        dataChannel = channel
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {
                if (channel.state() == DataChannel.State.OPEN) {
                    _connectionState.value = ConnectionPath.WEBRTC_P2P
                    peerConnection?.let { peer ->
                        inspectWebRtcPath(peer) { path ->
                            if (peerConnection === peer && channel.state() == DataChannel.State.OPEN) {
                                _connectionState.value = path
                            }
                        }
                    }
                } else if (channel.state() == DataChannel.State.CLOSED) {
                    _connectionState.value = ConnectionPath.DISCONNECTED
                }
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                val plaintext = runCatching { activeSession?.decryptDataChannel(bytes) }.getOrNull() ?: return
                decodeWebRtcV2Frame(plaintext, json, rpc) { _events.tryEmit(it) }
            }
        })
    }

    fun close() {
        connectJob?.cancel()
        connectJob = null
        try { dataChannel?.close() } catch (_: Exception) {}
        try { peerConnection?.close() } catch (_: Exception) {}
        peerConnection = null
        dataChannel = null
        activeSession = null
        _connectionState.value = ConnectionPath.DISCONNECTED
    }

    internal fun sendPayload(bytes: ByteArray): Boolean {
        val channel = dataChannel ?: return false
        if (channel.state() != DataChannel.State.OPEN) return false
        return channel.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false))
    }

    internal fun bufferedAmount(): Long = dataChannel?.bufferedAmount() ?: 0L
}
