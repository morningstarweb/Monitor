package app.monitor.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ServerEvent {
    @Serializable
    @SerialName("TelemetryTick")
    data class TelemetryTick(
        val snapshot: SystemSnapshot,
        val isOnline: Boolean = true
    ) : ServerEvent

    @Serializable
    @SerialName("ProjectUpdated")
    data class ProjectUpdated(val summary: RepoSummary) : ServerEvent

    @Serializable
    @SerialName("WorkspaceUpdated")
    data class WorkspaceUpdated(
        @SerialName("repo_id") val repoId: String,
        val snapshot: GitWorkspaceSnapshot
    ) : ServerEvent

    @Serializable
    @SerialName("OperationProgress")
    data class OperationProgress(
        @SerialName("operation_id") val operationId: String,
        @SerialName("repo_id") val repoId: String,
        val stage: String,
        val message: String
    ) : ServerEvent

    @Serializable
    @SerialName("AlertTriggered")
    data class AlertTriggered(
        @SerialName("alert_id") val alertId: String,
        @SerialName("rule_name") val ruleName: String,
        val message: String,
        val severity: String
    ) : ServerEvent

    @Serializable
    @SerialName("TerminalOutput")
    data class TerminalOutput(
        @SerialName("repo_id") val repoId: String,
        @SerialName("session_id") val sessionId: String,
        val text: String
    ) : ServerEvent

    @Serializable
    @SerialName("ResyncRequired")
    data object ResyncRequired : ServerEvent
}

