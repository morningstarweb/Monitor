package app.monitor.core.network

import app.monitor.core.model.MessageTypeV2
import app.monitor.core.model.RepoSummary
import app.monitor.core.model.ServerEvent
import app.monitor.core.model.SystemSnapshot
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Matches Rust's adjacently tagged serde envelope: {"event": ..., "data": ...}. */
internal fun Json.decodeServerEvent(text: String): ServerEvent? {
    val envelope = parseToJsonElement(text).jsonObject
    val event = envelope["event"]?.jsonPrimitive?.content
        ?: throw SerializationException("Desktop event is missing its event name")
    val data = envelope["data"] ?: JsonNull
    return when (event) {
        "TelemetryTick" -> ServerEvent.TelemetryTick(decodeFromJsonElement<SystemSnapshot>(data))
        "ProjectUpdated" -> ServerEvent.ProjectUpdated(decodeFromJsonElement<RepoSummary>(data))
        "WorkspaceUpdated" -> decodeFromJsonElement<ServerEvent.WorkspaceUpdated>(data)
        "OperationProgress" -> decodeFromJsonElement<ServerEvent.OperationProgress>(data)
        "AlertTriggered" -> decodeFromJsonElement<ServerEvent.AlertTriggered>(data)
        "TerminalOutput" -> decodeFromJsonElement<ServerEvent.TerminalOutput>(data)
        "ResyncRequired" -> ServerEvent.ResyncRequired
        // A newer desktop can add an event without interrupting live telemetry.
        else -> null
    }
}

/** Decodes V2 envelopes into ServerEvent objects, supporting raw snapshots, Rust ServerEvent envelopes, and V2 message types. */
internal fun Json.decodeV2PayloadEvent(type: MessageTypeV2, payload: JsonElement?): ServerEvent? {
    if (payload != null && payload is JsonObject) {
        val asServerEvent = runCatching {
            if (payload.containsKey("event")) {
                decodeServerEvent(payload.toString())
            } else null
        }.getOrNull()
        if (asServerEvent != null) return asServerEvent
    }

    return when (type) {
        MessageTypeV2.SNAPSHOT -> {
            payload?.let { pl ->
                runCatching {
                    val snapshot = decodeFromJsonElement<SystemSnapshot>(pl)
                    ServerEvent.TelemetryTick(snapshot, isOnline = true)
                }.getOrNull()
            }
        }
        MessageTypeV2.WATCHDOG_ALERT -> {
            payload?.let { pl ->
                runCatching { decodeFromJsonElement<ServerEvent.AlertTriggered>(pl) }.getOrNull()
            }
        }
        MessageTypeV2.COMMAND_PROGRESS -> {
            payload?.let { pl ->
                runCatching { decodeFromJsonElement<ServerEvent.OperationProgress>(pl) }.getOrNull()
            }
        }
        MessageTypeV2.PROJECTS_RESULT -> {
            payload?.let { pl ->
                runCatching {
                    val summary = decodeFromJsonElement<RepoSummary>(pl)
                    ServerEvent.ProjectUpdated(summary)
                }.getOrNull()
            }
        }
        MessageTypeV2.RESYNC_REQUEST -> ServerEvent.ResyncRequired
        else -> null
    }
}
