use schemars::JsonSchema;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, JsonSchema)]
pub struct AlertItem {
    pub alert_id: String,
    pub rule_kind: String,
    pub severity: String,
    pub title: String,
    pub body: String,
    pub dedup_key: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub session_id: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub value: Option<f64>,
    pub created_at_utc_ms: i64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub delivered_at_utc_ms: Option<i64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub acked_at_utc_ms: Option<i64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub snoozed_until_utc_ms: Option<i64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub recovered_at_utc_ms: Option<i64>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, JsonSchema)]
pub struct AlertRuleConfig {
    pub kind: String,
    pub enabled: bool,
    pub severity: String,
    pub threshold: f64,
    pub for_duration_s: u32,
    pub cooldown_s: u32,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, JsonSchema)]
pub struct AgentProfileConfig {
    pub name: String,
    pub matchers: Vec<String>,
    pub stall_threshold_s: u32,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub restart_command: Option<String>,
    pub keep_running: bool,
    pub max_retries: u32,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, JsonSchema)]
pub struct AgentSessionSummary {
    pub session_id: String,
    pub profile_name: String,
    pub pid: u32,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub repo_path: Option<String>,
    pub state: String, // "Active", "Quiet", "Stalled", "Ended"
    pub started_at_utc_ms: i64,
    pub last_activity_utc_ms: i64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub ended_at_utc_ms: Option<i64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub exit_code: Option<i32>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, JsonSchema)]
pub struct SnoozeAlertRequest {
    pub duration_s: u32,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, JsonSchema)]
pub struct CheckpointSummary {
    pub checkpoint_id: String,
    pub repo_path: String,
    pub branch: String,
    pub commit_sha: String,
    pub created_at_utc_ms: i64,
    pub files_changed_count: u32,
    pub lines_added: u32,
    pub lines_deleted: u32,
    pub is_safety: bool,
    pub description: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, JsonSchema)]
pub struct CreateCheckpointRequest {
    pub repo_path: String,
    pub description: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, JsonSchema)]
pub struct RollbackResponse {
    pub success: bool,
    pub checkpoint_id: String,
    pub safety_checkpoint_id: String,
    pub message: String,
}
