package app.monitor.core.network.v2

import app.monitor.core.model.EnvelopeV2
import app.monitor.core.model.MessageTypeV2
import app.monitor.core.model.StreamKind
import app.monitor.core.network.MAX_PLAINTEXT_SIZE
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal data class PendingV2Request(
    val envelope: EnvelopeV2,
    val result: CompletableDeferred<JsonElement>
)

/** Shares command IDs and response correlation across WebRTC and WSS failover. */
class V2RequestCorrelator(private val json: Json) {
    private val nextSequence = AtomicLong(0)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonElement>>()
    private val chunkAssembler = EnvelopeChunkAssembler(json)

    internal fun begin(
        sessionId: String,
        epoch: Long,
        commandType: String,
        payload: JsonElement
    ): PendingV2Request {
        if (pending.size >= MAX_PENDING_REQUESTS) throw IOException("Too many pending desktop requests")
        val commandId = UUID.randomUUID().toString()
        val completion = CompletableDeferred<JsonElement>()
        pending[commandId] = completion
        val envelope = EnvelopeV2(
            sessionId = sessionId,
            messageId = UUID.randomUUID().toString(),
            epoch = epoch,
            stream = StreamKind.CONTROL,
            type = MessageTypeV2.COMMAND_REQUEST,
            seq = nextSequence.getAndIncrement(),
            ack = 0L,
            deadlineMs = REQUEST_TIMEOUT_MS,
            commandId = commandId,
            payload = buildJsonObject {
                put("command_type", commandType)
                put("payload", payload)
            }
        )
        return PendingV2Request(envelope, completion)
    }

    internal suspend fun await(
        request: PendingV2Request,
        timeoutMs: Long = REQUEST_TIMEOUT_MS
    ): JsonElement = withTimeout(timeoutMs) { request.result.await() }

    internal fun abandon(request: PendingV2Request) {
        request.envelope.commandId?.let(pending::remove)
    }

    internal fun accept(envelope: EnvelopeV2) {
        if (envelope.type != MessageTypeV2.COMMAND_RESULT &&
            envelope.type != MessageTypeV2.COMMAND_REJECTED
        ) return
        val commandId = envelope.commandId ?: return
        val completion = pending[commandId] ?: return
        val response = envelope.payload?.jsonObject ?: emptyMap()
        val status = response["status"]?.jsonPrimitive?.content.orEmpty()
        if (envelope.type == MessageTypeV2.COMMAND_REJECTED || status == "failed" || status == "rejected") {
            val message = response["error"]?.jsonPrimitive?.content
                ?: response["result"]?.jsonPrimitive?.content
                ?: "Desktop rejected the operation"
            completion.completeExceptionally(IOException(message))
            return
        }
        val rawResult = response["result"] ?: JsonNull
        val normalized = if (rawResult is JsonPrimitive && rawResult.isString) {
            runCatching { json.parseToJsonElement(rawResult.content) }.getOrElse { rawResult }
        } else rawResult
        completion.complete(normalized)
    }

    internal fun acceptFrame(envelope: EnvelopeV2): EnvelopeV2? {
        val complete = try { chunkAssembler.accept(envelope) } catch (_: IOException) { null }
        complete?.let(::accept)
        return complete
    }

    internal fun encode(envelope: EnvelopeV2): ByteArray {
        val bytes = json.encodeToString(EnvelopeV2.serializer(), envelope).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_PLAINTEXT_SIZE) { "V2 request exceeds the 16 KiB frame limit" }
        return bytes
    }

    companion object {
        private const val MAX_PENDING_REQUESTS = 32
        private const val REQUEST_TIMEOUT_MS = 60_000L
    }
}
