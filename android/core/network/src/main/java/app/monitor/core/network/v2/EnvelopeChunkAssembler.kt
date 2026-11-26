package app.monitor.core.network.v2

import android.util.Base64
import app.monitor.core.model.EnvelopeV2
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Reassembles bounded EnvelopeV2 payload fragments shared by WSS and DataChannel paths. */
internal class EnvelopeChunkAssembler(private val json: Json) {
    private data class ChunkSet(
        val count: Int,
        val chunks: Array<ByteArray?>,
        val createdAtMs: Long = System.currentTimeMillis(),
        var receivedBytes: Int = 0
    )
    private val pending = ConcurrentHashMap<String, ChunkSet>()

    fun accept(envelope: EnvelopeV2): EnvelopeV2? {
        val payload = envelope.payload?.jsonObject ?: return envelope
        if (payload["_frame_chunk"]?.jsonPrimitive?.booleanOrNull != true) return envelope
        val index = payload["index"]?.jsonPrimitive?.intOrNull ?: throw IOException("Invalid chunk index")
        val count = payload["count"]?.jsonPrimitive?.intOrNull ?: throw IOException("Invalid chunk count")
        val chunkId = payload["chunk_id"]?.jsonPrimitive?.content ?: throw IOException("Missing chunk ID")
        val encoded = payload["data"]?.jsonPrimitive?.content ?: throw IOException("Missing chunk data")
        if (count !in 1..MAX_CHUNKS || index !in 0 until count) throw IOException("Invalid V2 chunk range")
        val chunk = try { Base64.decode(encoded, Base64.DEFAULT) } catch (_: IllegalArgumentException) {
            throw IOException("Invalid V2 chunk encoding")
        }
        val now = System.currentTimeMillis()
        pending.entries.removeIf { now - it.value.createdAtMs > ASSEMBLY_TTL_MS }
        val key = "${envelope.sessionId}:$chunkId"
        if (!pending.containsKey(key) && pending.size >= MAX_PENDING_SETS) {
            throw IOException("Too many incomplete V2 chunk payloads")
        }
        val set = pending.compute(key) { _, existing ->
            existing ?: ChunkSet(count, arrayOfNulls(count))
        } ?: throw IOException("Could not allocate V2 chunk set")
        synchronized(set) {
            if (set.count != count) {
                pending.remove(key)
                throw IOException("Conflicting V2 chunk metadata")
            }
            if (set.chunks[index] == null) {
                set.receivedBytes += chunk.size
                if (set.receivedBytes > MAX_ASSEMBLED_BYTES) {
                    pending.remove(key)
                    throw IOException("V2 chunk payload exceeds the 4 MiB limit")
                }
                set.chunks[index] = chunk
            }
            if (set.chunks.any { it == null }) return null
            val output = ByteArrayOutputStream(set.receivedBytes)
            set.chunks.forEach { output.write(it!!) }
            pending.remove(key)
            val restoredPayload = json.parseToJsonElement(output.toString(Charsets.UTF_8.name()))
            return envelope.copy(payload = restoredPayload)
        }
    }

    private companion object {
        const val MAX_CHUNKS = 512
        const val MAX_ASSEMBLED_BYTES = 4 * 1024 * 1024
        const val MAX_PENDING_SETS = 16
        const val ASSEMBLY_TTL_MS = 30_000L
    }
}
