package app.monitor.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AlertItem(
    @SerialName("alert_id") val alertId: String,
    @SerialName("rule_kind") val ruleKind: String,
    val severity: String,
    val title: String,
    val body: String,
    @SerialName("dedup_key") val dedupKey: String,
    @SerialName("session_id") val sessionId: String? = null,
    val value: Double? = null,
    @SerialName("created_at_utc_ms") val createdAtUtcMs: Long,
    @SerialName("delivered_at_utc_ms") val deliveredAtUtcMs: Long? = null,
    @SerialName("acked_at_utc_ms") val ackedAtUtcMs: Long? = null,
    @SerialName("snoozed_until_utc_ms") val snoozedUntilUtcMs: Long? = null,
    @SerialName("recovered_at_utc_ms") val recoveredAtUtcMs: Long? = null
)

@Serializable
data class AgentSessionSummary(
    @SerialName("session_id") val sessionId: String,
    @SerialName("profile_name") val profileName: String,
    val pid: Long,
    @SerialName("repo_path") val repoPath: String? = null,
    val state: String,
    @SerialName("started_at_utc_ms") val startedAtUtcMs: Long,
    @SerialName("last_activity_utc_ms") val lastActivityUtcMs: Long,
    @SerialName("ended_at_utc_ms") val endedAtUtcMs: Long? = null,
    @SerialName("exit_code") val exitCode: Int? = null
)

@Serializable
data class AlertRuleConfig(
    val kind: String,
    val enabled: Boolean,
    val severity: String,
    val threshold: Double,
    @SerialName("for_duration_s") val forDurationS: Long,
    @SerialName("cooldown_s") val cooldownS: Long
)

@Serializable
data class SnoozeAlertRequest(
    @SerialName("duration_s") val durationS: Long = 3600
)

@Serializable
data class CheckpointItem(
    @SerialName("checkpoint_id") val checkpointId: String,
    @SerialName("repo_path") val repoPath: String,
    val branch: String,
    @SerialName("commit_sha") val commitSha: String,
    @SerialName("created_at_utc_ms") val createdAtUtcMs: Long,
    @SerialName("files_changed_count") val filesChangedCount: Long,
    @SerialName("lines_added") val linesAdded: Long,
    @SerialName("lines_deleted") val linesDeleted: Long,
    @SerialName("is_safety") val isSafety: Boolean = false,
    val description: String
)

@Serializable
data class RollbackResponse(
    val success: Boolean,
    @SerialName("checkpoint_id") val checkpointId: String,
    @SerialName("safety_checkpoint_id") val safetyCheckpointId: String,
    val message: String
)
