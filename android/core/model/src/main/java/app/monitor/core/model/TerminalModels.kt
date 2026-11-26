package app.monitor.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TerminalCommandRequest(
    @SerialName("command") val command: String,
    @SerialName("shell") val shell: String? = null,
    @SerialName("session_id") val sessionId: String? = null
)

@Serializable
data class TerminalCommandResult(
    @SerialName("exit_code") val exitCode: Int,
    @SerialName("stdout") val stdout: String = "",
    @SerialName("stderr") val stderr: String = "",
    @SerialName("duration_ms") val durationMs: Long = 0
)

@Serializable
data class TerminalStopRequest(
    @SerialName("session_id") val sessionId: String? = null
)

@Serializable
data class TerminalStopResult(
    @SerialName("success") val success: Boolean,
    @SerialName("pid") val pid: Long? = null
)

@Serializable
data class TerminalSettingPayload(
    @SerialName("enabled") val enabled: Boolean
)

data class TerminalLogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val command: String,
    val result: TerminalCommandResult?,
    val isRunning: Boolean = false,
    val streamingText: String = "",
    val timestampMs: Long = System.currentTimeMillis()
)

data class TerminalSession(
    val id: String,
    val title: String,
    val logs: List<TerminalLogEntry> = emptyList(),
    val isRunning: Boolean = false,
    val activeCommand: String = "",
    val commandHistory: List<String> = emptyList()
)
