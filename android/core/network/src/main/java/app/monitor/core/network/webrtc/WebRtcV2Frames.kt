package app.monitor.core.network.webrtc

import app.monitor.core.model.EnvelopeV2
import app.monitor.core.model.MessageTypeV2
import app.monitor.core.model.ServerEvent
import app.monitor.core.model.SystemSnapshot
import app.monitor.core.network.decodeServerEvent
import app.monitor.core.network.v2.V2RequestCorrelator
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

import app.monitor.core.network.decodeV2PayloadEvent

internal fun decodeWebRtcV2Frame(
    bytes: ByteArray,
    json: Json,
    rpc: V2RequestCorrelator,
    emit: (ServerEvent) -> Unit
) {
    val text = String(bytes, Charsets.UTF_8)
    val envelope = runCatching { json.decodeFromString<EnvelopeV2>(text) }.getOrNull()
    if (envelope == null) {
        json.decodeServerEvent(text)?.let(emit)
        return
    }
    val complete = rpc.acceptFrame(envelope) ?: return
    json.decodeV2PayloadEvent(complete.type, complete.payload)?.let(emit)
}
