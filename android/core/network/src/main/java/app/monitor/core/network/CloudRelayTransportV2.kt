package app.monitor.core.network

import app.monitor.core.model.EnvelopeV2
import app.monitor.core.model.MessageTypeV2
import app.monitor.core.model.ServerEvent
import app.monitor.core.model.SystemSnapshot
import app.monitor.core.network.webrtc.WebRtcSignalMessage
import app.monitor.core.network.v2.PendingV2Request
import app.monitor.core.network.v2.V2RequestCorrelator
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class CloudRelayTransportV2(
    private val client: HttpClient,
    private val json: Json,
    private val tokenStorage: TokenStorage,
    val brokerBaseUrl: String = "https://your-monitor-broker.workers.dev",
    internal val rpc: V2RequestCorrelator = V2RequestCorrelator(json)
) {
    var onWebRtcSignal: ((WebRtcSignalMessage) -> Unit)? = null
    var onWsOpen: ((String, SecureSessionV2, String) -> Unit)? = null
    @Volatile private var activeWsSend: (suspend (String) -> Unit)? = null
    @Volatile private var activeWsBinarySend: (suspend (ByteArray) -> Unit)? = null
    @Volatile private var activeWebSocket: WebSocketSession? = null
    private val activeSession = AtomicReference<SecureSessionV2?>(null)
    private val wakeChannel = Channel<Unit>(Channel.CONFLATED)
    private val routeResetRequested = AtomicBoolean(false)
    private val networkEpoch = AtomicLong(0L)

    val isConnected: Boolean
        get() = activeSession.get() != null && activeWsSend != null && activeWebSocket?.isActive == true

    suspend fun sendSignalText(text: String): Boolean {
        val send = activeWsSend ?: return false
        return try {
            send(text)
            true
        } catch (_: Exception) {
            false
        }
    }

    internal fun onNetworkChanged() {
        val ws = activeWebSocket
        networkEpoch.incrementAndGet()
        routeResetRequested.set(true)
        resetSession()
        wakeChannel.trySend(Unit)
        runCatching { ws?.cancel(CancellationException("Network route changed")) }
    }

    internal fun resetSession() {
        activeSession.set(null)
        activeWsSend = null
        activeWsBinarySend = null
        activeWebSocket = null
    }

    internal suspend fun sendRequest(request: PendingV2Request) {
        val session = awaitActiveSession()
        val send = activeWsBinarySend ?: throw IOException("Cloud relay WebSocket is not ready")
        val currentEnvelope = request.envelope.copy(sessionId = session.sessionId, epoch = session.epoch)
        send(session.encryptEnvelope(currentEnvelope))
    }

    suspend fun obtainViewerTicket(hostDeviceId: String, cloudToken: String): ViewerTicketResult {
        val endpoint = "$brokerBaseUrl/api/v2/sessions"
        val respText = client.post(endpoint) {
            header("Authorization", "Bearer $cloudToken")
            contentType(ContentType.Application.Json)
            setBody(JSONObject(mapOf("host_device_id" to hostDeviceId)).toString())
            timeout {
                connectTimeoutMillis = 3_000
                requestTimeoutMillis = 6_000
            }
        }.bodyAsText()

        val obj = JSONObject(respText)
        if (!obj.optBoolean("success", false)) {
            val err = obj.optString("error", "Failed to obtain viewer ticket")
            throw IOException(err)
        }

        return ViewerTicketResult(
            ticket = obj.getString("ticket"),
            hostNoisePublicKey = obj.getString("host_noise_public_key")
        )
    }

    fun streamEvents(hostDeviceId: String): Flow<ServerEvent> = flow {
        var backoffMs = 500L
        while (true) {
            currentCoroutineContext().ensureActive()
            val attemptEpoch = networkEpoch.get()
            try {
                val cloudToken = tokenStorage.getCloudSessionToken()
                    ?: throw IOException("No cloud session token available")

                val ticketData = obtainViewerTicket(hostDeviceId, cloudToken)
                val hostNoiseKey = decodeNoiseHex(ticketData.hostNoisePublicKey)
                val handshake = NoiseIkViewerHandshake(
                    tokenStorage.getOrCreateNoiseStaticKeyPair(),
                    hostNoiseKey
                )
                val wsBase = brokerBaseUrl.replace("https://", "wss://").replace("http://", "ws://")
                val wsUrl = "$wsBase/api/v2/rendezvous/viewer?ticket=${ticketData.ticket}"

                client.webSocket(urlString = wsUrl) {
                    activeWebSocket = this
                    activeWsSend = { text -> send(Frame.Text(text)) }
                    activeWsBinarySend = { bytes -> send(Frame.Binary(true, bytes)) }
                    backoffMs = 500L
                    send(
                        Frame.Binary(
                            true,
                            handshake.writeMessage1(
                                NoiseIkViewerHandshake.defaultDevicePayload(
                                    tokenStorage.getOrCreateCloudDeviceId()
                                )
                            )
                        )
                    )

                    var established: Pair<SecureSessionV2, String>? = null
                    withTimeoutOrNull(10_000) {
                        var session: SecureSessionV2? = null
                        var viewerSessionId: String? = null
                        while (true) {
                            val incomingFrame = incoming.receiveCatching().getOrNull()
                                ?: throw IOException("Host closed during Noise handshake")
                            when (incomingFrame) {
                                is Frame.Binary -> session = handshake.readMessage2(incomingFrame.readBytes()).session
                                is Frame.Text -> {
                                    val signal = runCatching { JSONObject(incomingFrame.readText()) }.getOrNull()
                                    if (signal?.optString("event") == "viewer_session") {
                                        viewerSessionId = signal.optString("viewer_session_id").takeIf(String::isNotBlank)
                                    }
                                }
                                is Frame.Ping -> send(Frame.Pong(incomingFrame.data))
                                is Frame.Close -> throw IOException("Host closed during Noise handshake")
                                else -> Unit
                            }
                            val readySession = session
                            val readyViewer = viewerSessionId
                            if (readySession != null && readyViewer != null) {
                                established = readySession to readyViewer
                                return@withTimeoutOrNull
                            }
                        }
                    } ?: throw IOException("Noise handshake or viewer session ID timed out")
                    val (session, viewerSessionId) = established
                        ?: throw IOException("HostRoom did not assign a viewer session")
                    activeSession.set(session)
                    onWsOpen?.invoke(brokerBaseUrl, session, viewerSessionId)

                    while (true) {
                        val received = withTimeoutOrNull(20_000) { incoming.receiveCatching() }
                            ?: throw IOException("Cloud relay timed out")
                        val frame = received.getOrNull() ?: break

                        when (frame) {
                            is Frame.Binary -> {
                                val ciphertext = frame.readBytes()
                                val envelope = session.decryptEnvelope(ciphertext)
                                val complete = rpc.acceptFrame(envelope) ?: continue
                                val event = json.decodeV2PayloadEvent(complete.type, complete.payload)
                                if (event != null) {
                                    emit(event)
                                }
                            }
                            is Frame.Text -> {
                                val text = frame.readText()
                                if (text.contains("\"signal\"")) {
                                    try {
                                        val sig = json.decodeFromString<WebRtcSignalMessage>(text)
                                        onWebRtcSignal?.invoke(sig)
                                    } catch (_: Exception) {}
                                }
                            }
                            is Frame.Ping -> send(Frame.Pong(frame.data))
                            is Frame.Close -> break
                            else -> Unit
                        }
                    }
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                val epochChanged = networkEpoch.get() != attemptEpoch
                val wokeForRouteChange = epochChanged || (e is CancellationException && routeResetRequested.getAndSet(false))
                resetSession()
                if (e is CancellationException && !wokeForRouteChange) throw e
                if (wokeForRouteChange) {
                    routeResetRequested.set(false)
                    wakeChannel.tryReceive()
                    backoffMs = 500L
                    delay(150)
                } else {
                    val woken = withTimeoutOrNull(backoffMs) { wakeChannel.receive() }
                    backoffMs = if (woken != null) 500L else (backoffMs * 2).coerceAtMost(20_000L)
                }
            }
        }
    }

    internal suspend fun awaitActiveSession(): SecureSessionV2 {
        repeat(100) {
            activeSession.get()?.let { return it }
            delay(100)
        }
        throw IOException("No encrypted Cloud relay session is active")
    }
}
