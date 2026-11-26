package app.monitor.core.network

import app.monitor.core.model.ServerEvent
import app.monitor.core.network.v2.PendingV2Request
import app.monitor.core.network.v2.V2RequestCorrelator
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readBytes
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
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** NEW-08: the LAN host's Noise static key changed after it was pinned. */
class HostKeyMismatchException : IOException("LAN host identity changed; re-pair with the desktop host required")

internal fun noiseKeyToHex(key: ByteArray): String = key.joinToString("") { "%02x".format(it) }

/** Pure pinning decision, separated from I/O so it can be tested directly. */
object HostKeyPinning {
    /** Returns the key to use and an optional hex value that should be pinned. */
    fun resolve(supplied: ByteArray, pinnedHex: String?, override: ByteArray?): Pair<ByteArray, String?> {
        if (override != null) return override to null
        if (pinnedHex == null) return supplied to noiseKeyToHex(supplied)
        val pinned = decodeNoiseHex(pinnedHex)
        if (!pinned.contentEquals(supplied)) throw HostKeyMismatchException()
        return pinned to null
    }
}

class LanTransportV2(
    private val client: HttpClient,
    private val json: Json,
    private val tokenStorage: TokenStorage,
    internal val rpc: V2RequestCorrelator = V2RequestCorrelator(json)
) {
    @Volatile private var activeWsBinarySend: (suspend (ByteArray) -> Unit)? = null
    @Volatile private var activeWebSocket: WebSocketSession? = null
    private val activeSession = AtomicReference<SecureSessionV2?>(null)
    private val wakeChannel = Channel<Unit>(Channel.CONFLATED)
    private val routeResetRequested = AtomicBoolean(false)
    private val networkEpoch = AtomicLong(0L)
    var onDisconnected: (() -> Unit)? = null

    val isConnected: Boolean
        get() = activeSession.get() != null && activeWebSocket?.isActive == true

    val session: SecureSessionV2?
        get() = activeSession.get()

    internal fun onNetworkChanged() {
        val ws = activeWebSocket
        networkEpoch.incrementAndGet()
        routeResetRequested.set(true)
        resetSession()
        wakeChannel.trySend(Unit)
        runCatching { ws?.cancel(CancellationException("Network route changed")) }
    }

    fun resetSession() {
        val wasConnected = activeSession.getAndSet(null) != null
        activeWsBinarySend = null
        activeWebSocket = null
        if (wasConnected) {
            onDisconnected?.invoke()
        }
    }

    internal suspend fun sendRequest(request: PendingV2Request) {
        val session = activeSession.get() ?: throw IOException("LAN v2 session is not active")
        val send = activeWsBinarySend ?: throw IOException("LAN v2 WebSocket is not ready")
        val currentEnvelope = request.envelope.copy(sessionId = session.sessionId, epoch = session.epoch)
        send(session.encryptEnvelope(currentEnvelope))
    }

    suspend fun fetchLocalNoisePublicKey(host: String, port: Int): ByteArray {
        LanEndpointPolicy.requireAllowedCleartextEndpoint(host, port)
        val url = "http://$host:$port/api/v2/noise-key"
        val respText = client.get(url) {
            timeout {
                connectTimeoutMillis = 2000
                requestTimeoutMillis = 3000
            }
        }.bodyAsText()
        val obj = JSONObject(respText)
        val hex = obj.getString("public_key")
        return decodeNoiseHex(hex)
    }

    /**
     * NEW-08: key resolution with pinning lives in [streamEvents]. The first
     * connection learns (TOFU) and stores the key; every later reconnect must
     * present exactly the same key or the connection is refused.
     */
    fun streamEvents(
        host: String,
        port: Int,
        hostNoiseKeyOverride: ByteArray? = null
    ): Flow<ServerEvent> = flow {
        LanEndpointPolicy.requireAllowedCleartextEndpoint(host, port)
        var backoffMs = 500L
        while (true) {
            currentCoroutineContext().ensureActive()
            val attemptEpoch = networkEpoch.get()
            try {
                val pinnedHex = tokenStorage.getHostNoiseKey(host, port)
                val supplied = hostNoiseKeyOverride ?: fetchLocalNoisePublicKey(host, port)
                val (hostNoiseKey, newPin) = HostKeyPinning.resolve(supplied, pinnedHex, hostNoiseKeyOverride)
                if (newPin != null) {
                    // First trusted contact: learn and pin the host identity.
                    tokenStorage.saveHostNoiseKey(host, port, newPin)
                }
                val handshake = NoiseIkViewerHandshake(
                    tokenStorage.getOrCreateNoiseStaticKeyPair(),
                    hostNoiseKey,
                    NoiseIkViewerHandshake.PROLOGUE_LAN
                )

                val wsUrl = "ws://$host:$port/api/v2/stream"

                client.webSocket(urlString = wsUrl) {
                    activeWebSocket = this
                    activeWsBinarySend = { bytes -> send(Frame.Binary(true, bytes)) }
                    backoffMs = 500L

                    // 1. Send Handshake Message 1
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

                    // 2. Await Handshake Message 2
                    val msg2Frame = withTimeoutOrNull(5000) {
                        while (true) {
                            val frame = incoming.receiveCatching().getOrNull()
                                ?: throw IOException("Host closed during LAN Noise handshake")
                            if (frame is Frame.Binary) return@withTimeoutOrNull frame
                            if (frame is Frame.Ping) send(Frame.Pong(frame.data))
                        }
                    } ?: throw IOException("LAN Noise handshake msg2 timed out")

                    val result = handshake.readMessage2((msg2Frame as Frame.Binary).readBytes())
                    activeSession.set(result.session)

                    // 3. Transport Loop
                    while (true) {
                        val received = withTimeoutOrNull(8_000) { incoming.receiveCatching() }
                            ?: throw IOException("LAN v2 stream timed out")
                        val frame = received.getOrNull() ?: break

                        when (frame) {
                            is Frame.Binary -> {
                                val ciphertext = frame.readBytes()
                                val envelope = result.session.decryptEnvelope(ciphertext)
                                val complete = rpc.acceptFrame(envelope) ?: continue
                                val event = json.decodeV2PayloadEvent(complete.type, complete.payload)
                                if (event != null) {
                                    emit(event)
                                }
                            }
                            is Frame.Ping -> send(Frame.Pong(frame.data))
                            is Frame.Close -> break
                            else -> Unit
                        }
                    }
                }
            } catch (e: HostKeyMismatchException) {
                // A changed host identity is a hard security failure: never
                // retry with a substituted key (NEW-08).
                resetSession()
                throw e
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
                    backoffMs = if (woken != null) 500L else (backoffMs * 2).coerceAtMost(10_000L)
                }
            }
        }
    }
}
