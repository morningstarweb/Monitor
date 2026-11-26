package app.monitor.core.network.webrtc

import app.monitor.core.network.MAX_PLAINTEXT_SIZE
import app.monitor.core.network.v2.PendingV2Request
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.io.IOException

internal suspend fun WebRtcDataChannelClient.sendRequest(request: PendingV2Request) {
    val session = activeSession ?: throw IOException("WebRTC session is not initialized")
    val plaintext = rpc.encode(request.envelope.copy(sessionId = session.sessionId, epoch = session.epoch))
    require(plaintext.size <= MAX_PLAINTEXT_SIZE) { "V2 request exceeds the 16 KiB DataChannel limit" }
    val frame = session.encryptDataChannel(plaintext)
    withTimeout(10_000) {
        while (bufferedAmount() > MAX_BUFFERED_BYTES) delay(BACKPRESSURE_POLL_MS)
    }
    if (!sendPayload(frame)) {
        close()
        throw IOException("WebRTC DataChannel is not open")
    }
}

private const val MAX_BUFFERED_BYTES = 256L * 1024L
private const val BACKPRESSURE_POLL_MS = 25L
