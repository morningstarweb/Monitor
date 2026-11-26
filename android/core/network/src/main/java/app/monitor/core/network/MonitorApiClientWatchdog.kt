package app.monitor.core.network

import app.monitor.core.model.AgentSessionSummary
import app.monitor.core.model.AlertItem
import app.monitor.core.model.AlertRuleConfig
import app.monitor.core.model.CheckpointItem
import app.monitor.core.model.RollbackResponse
import app.monitor.core.model.SnoozeAlertRequest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

suspend fun MonitorApiClient.getAlertsApi(
    limit: Int = 50,
    activeOnly: Boolean = false
): Result<List<AlertItem>> = apiResult {
    v2RequestIfCloud("watchdog.alerts", buildJsonObject {
        put("limit", limit); put("active_only", activeOnly)
    }) {
        localJsonRequest { base, token -> client.get("$base/alerts") {
            timeout { connectTimeoutMillis = 3_000 }; header("Authorization", "Bearer $token")
            parameter("limit", limit); parameter("active_only", activeOnly)
        }.body<kotlinx.serialization.json.JsonElement>() }
    }?.let { return@apiResult decodeV2Result(it) }
    gitRequest { base, token ->
        client.get("$base/alerts") {
            timeout { connectTimeoutMillis = 3_000 }
            header("Authorization", "Bearer $token")
            parameter("limit", limit)
            parameter("active_only", activeOnly)
        }.body()
    }
}

suspend fun MonitorApiClient.ackAlertApi(alertId: String): Result<Boolean> = apiResult {
    v2RequestIfCloud("watchdog.ack", buildJsonObject { put("alert_id", alertId) }) {
        localJsonRequest { base, token ->
            client.post("$base/alerts/$alertId/ack") {
                timeout { connectTimeoutMillis = 3_000 }; header("Authorization", "Bearer $token")
            }
            kotlinx.serialization.json.JsonPrimitive(true)
        }
    }?.let {
        return@apiResult decodeV2Result(it)
    }
    gitRequest { base, token ->
        client.post("$base/alerts/$alertId/ack") {
            timeout { connectTimeoutMillis = 3_000 }
            header("Authorization", "Bearer $token")
        }
        true
    }
}

suspend fun MonitorApiClient.snoozeAlertApi(
    alertId: String,
    durationS: Long = 3600
): Result<Boolean> = apiResult {
    v2RequestIfCloud("watchdog.snooze", buildJsonObject {
        put("alert_id", alertId); put("duration_s", durationS)
    }) {
        localJsonRequest { base, token -> client.post("$base/alerts/$alertId/snooze") {
            timeout { connectTimeoutMillis = 3_000 }; header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json); setBody(SnoozeAlertRequest(durationS))
        }; kotlinx.serialization.json.JsonPrimitive(true) }
    }?.let { return@apiResult decodeV2Result(it) }
    gitRequest { base, token ->
        client.post("$base/alerts/$alertId/snooze") {
            timeout { connectTimeoutMillis = 3_000 }
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(SnoozeAlertRequest(durationS))
        }
        true
    }
}

suspend fun MonitorApiClient.getAgentSessionsApi(): Result<List<AgentSessionSummary>> = apiResult {
    v2RequestIfCloud("watchdog.sessions", buildJsonObject {}) {
        localJsonRequest { base, token -> client.get("$base/watchdog/sessions") {
            timeout { connectTimeoutMillis = 3_000 }; header("Authorization", "Bearer $token")
        }.body<kotlinx.serialization.json.JsonElement>() }
    }?.let {
        return@apiResult decodeV2Result(it)
    }
    gitRequest { base, token ->
        client.get("$base/watchdog/sessions") {
            timeout { connectTimeoutMillis = 3_000 }
            header("Authorization", "Bearer $token")
        }.body()
    }
}

suspend fun MonitorApiClient.getAlertRulesApi(): Result<List<AlertRuleConfig>> = apiResult {
    localJsonRequest { base, token ->
        client.get("$base/watchdog/rules") {
            timeout { connectTimeoutMillis = 3_000 }
            header("Authorization", "Bearer $token")
        }.body()
    }.let { decodeV2Result(it) }
}

suspend fun MonitorApiClient.getCheckpointsApi(
    repoPath: String,
    limit: Int = 50
): Result<List<CheckpointItem>> = apiResult {
    v2RequestIfCloud("watchdog.checkpoints", buildJsonObject {
        put("repo_path", repoPath); put("limit", limit)
    }) {
        localJsonRequest { base, token -> client.get("$base/git/checkpoints") {
            timeout { connectTimeoutMillis = 4_000 }; header("Authorization", "Bearer $token")
            parameter("repo_path", repoPath); parameter("limit", limit)
        }.body<kotlinx.serialization.json.JsonElement>() }
    }?.let { return@apiResult decodeV2Result(it) }
    gitRequest { base, token ->
        client.get("$base/git/checkpoints") {
            timeout { connectTimeoutMillis = 4_000 }
            header("Authorization", "Bearer $token")
            parameter("repo_path", repoPath)
            parameter("limit", limit)
        }.body()
    }
}

suspend fun MonitorApiClient.createCheckpointApi(
    repoPath: String,
    description: String? = null
): Result<CheckpointItem> = apiResult {
    v2RequestIfCloud("watchdog.checkpoint_create", buildJsonObject {
        put("repo_path", repoPath); put("description", description ?: "Manual Checkpoint")
    }) {
        localJsonRequest { base, token -> client.post("$base/git/checkpoints") {
            timeout { connectTimeoutMillis = 5_000 }; header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(mapOf("repo_path" to repoPath, "description" to (description ?: "Manual Checkpoint")))
        }.body<kotlinx.serialization.json.JsonElement>() }
    }?.let { return@apiResult decodeV2Result(it) }
    gitRequest { base, token ->
        client.post("$base/git/checkpoints") {
            timeout { connectTimeoutMillis = 5_000 }
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(mapOf("repo_path" to repoPath, "description" to (description ?: "Manual Checkpoint")))
        }.body()
    }
}

suspend fun MonitorApiClient.rollbackCheckpointApi(checkpointId: String): Result<RollbackResponse> = apiResult {
    v2RequestIfCloud("watchdog.checkpoint_rollback", buildJsonObject {
        put("checkpoint_id", checkpointId)
    }) {
        localJsonRequest { base, token -> client.post("$base/git/checkpoints/$checkpointId/rollback") {
            timeout { connectTimeoutMillis = 8_000 }; header("Authorization", "Bearer $token")
        }.body<kotlinx.serialization.json.JsonElement>() }
    }?.let { return@apiResult decodeV2Result(it) }
    gitRequest { base, token ->
        client.post("$base/git/checkpoints/$checkpointId/rollback") {
            timeout { connectTimeoutMillis = 8_000 }
            header("Authorization", "Bearer $token")
        }.body()
    }
}

suspend fun MonitorApiClient.killAgentSessionApi(sessionId: String): Result<Boolean> = apiResult {
    v2RequestIfCloud("watchdog.kill", buildJsonObject { put("session_id", sessionId) }) {
        localJsonRequest {
            base, token -> client.post("$base/watchdog/sessions/$sessionId/kill") {
                timeout { connectTimeoutMillis = 3_000 }; header("Authorization", "Bearer $token")
            }
            kotlinx.serialization.json.JsonPrimitive(true)
        }
    }?.let {
        return@apiResult decodeV2Result(it)
    }
    gitRequest { base, token ->
        client.post("$base/watchdog/sessions/$sessionId/kill") {
            timeout { connectTimeoutMillis = 3_000 }
            header("Authorization", "Bearer $token")
        }
        true
    }
}
