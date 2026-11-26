package app.monitor.core.network

import app.monitor.core.model.TerminalCommandRequest
import app.monitor.core.model.TerminalCommandResult
import app.monitor.core.model.TerminalSettingPayload
import app.monitor.core.model.TerminalStopRequest
import app.monitor.core.model.TerminalStopResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

suspend fun MonitorApiClient.runTerminalCommandApi(
    repoId: String,
    command: String,
    shell: String? = null,
    sessionId: String = "1"
): Result<TerminalCommandResult> = apiResult {
    v2RequestIfCloud("terminal.start", buildJsonObject {
        put("repo_id", repoId); put("command", command); put("session_id", sessionId)
        if (!shell.isNullOrBlank()) put("shell", shell)
    }) {
        localJsonRequest { base, token -> client.post("$base/projects/$repoId/terminal/run") {
            timeout { connectTimeoutMillis = 5_000; socketTimeoutMillis = 65_000 }
            header("Authorization", "Bearer $token"); contentType(ContentType.Application.Json)
            setBody(TerminalCommandRequest(command = command, shell = shell, sessionId = sessionId))
        }.body<kotlinx.serialization.json.JsonElement>() }
    }?.let { return@apiResult decodeV2Result(it) }
    gitRequest { base, token ->
        client.post("$base/projects/$repoId/terminal/run") {
            timeout {
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 65_000
            }
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(TerminalCommandRequest(command = command, shell = shell, sessionId = sessionId))
        }.body()
    }
}

suspend fun MonitorApiClient.stopTerminalCommandApi(
    repoId: String,
    sessionId: String = "1"
): Result<TerminalStopResult> = apiResult {
    v2RequestIfCloud("terminal.stop", buildJsonObject {
        put("repo_id", repoId); put("session_id", sessionId)
    }) {
        localJsonRequest { base, token -> client.post("$base/projects/$repoId/terminal/stop") {
            timeout { connectTimeoutMillis = 3_000; socketTimeoutMillis = 5_000 }
            header("Authorization", "Bearer $token"); contentType(ContentType.Application.Json)
            setBody(TerminalStopRequest(sessionId = sessionId))
        }.body<kotlinx.serialization.json.JsonElement>() }
    }?.let { return@apiResult decodeV2Result(it) }
    gitRequest { base, token ->
        client.post("$base/projects/$repoId/terminal/stop") {
            timeout { connectTimeoutMillis = 3_000; socketTimeoutMillis = 5_000 }
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(TerminalStopRequest(sessionId = sessionId))
        }.body()
    }
}

suspend fun MonitorApiClient.getTerminalSettingApi(): Result<TerminalSettingPayload> = apiResult {
    v2RequestIfCloud("terminal.setting.get", buildJsonObject {}) {
        localJsonRequest { base, token -> client.get("$base/settings/terminal") {
            header("Authorization", "Bearer $token")
        }.body<kotlinx.serialization.json.JsonElement>() }
    }?.let {
        return@apiResult decodeV2Result(it)
    }
    gitRequest { base, token ->
        client.get("$base/settings/terminal") {
            header("Authorization", "Bearer $token")
        }.body()
    }
}

suspend fun MonitorApiClient.setTerminalSettingApi(enabled: Boolean): Result<TerminalSettingPayload> = apiResult {
    v2RequestIfCloud("terminal.setting.set", buildJsonObject { put("enabled", enabled) }) {
        localJsonRequest { base, token -> client.post("$base/settings/terminal") {
            header("Authorization", "Bearer $token"); contentType(ContentType.Application.Json)
            setBody(TerminalSettingPayload(enabled = enabled))
        }.body<kotlinx.serialization.json.JsonElement>() }
    }?.let {
        return@apiResult decodeV2Result(it)
    }
    gitRequest { base, token ->
        client.post("$base/settings/terminal") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(TerminalSettingPayload(enabled = enabled))
        }.body()
    }
}
