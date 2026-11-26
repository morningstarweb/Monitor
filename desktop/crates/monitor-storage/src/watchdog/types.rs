use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct StoredAlert {
    pub alert_id: String,
    pub rule_kind: String,
    pub severity: String,
    pub title: String,
    pub body: String,
    pub dedup_key: String,
    pub session_id: Option<String>,
    pub value: Option<f64>,
    pub created_at_utc_ms: i64,
    pub delivered_at_utc_ms: Option<i64>,
    pub acked_at_utc_ms: Option<i64>,
    pub snoozed_until_utc_ms: Option<i64>,
    pub recovered_at_utc_ms: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct StoredAlertRule {
    pub kind: String,
    pub enabled: bool,
    pub severity: String,
    pub threshold: f64,
    pub for_duration_s: u32,
    pub cooldown_s: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct StoredAgentProfile {
    pub name: String,
    pub matchers: Vec<String>,
    pub stall_threshold_s: u32,
    pub restart_command: Option<String>,
    pub keep_running: bool,
    pub max_retries: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct StoredAgentSession {
    pub session_id: String,
    pub profile_name: String,
    pub pid: u32,
    pub repo_path: Option<String>,
    pub state: String,
    pub started_at_utc_ms: i64,
    pub last_activity_utc_ms: i64,
    pub ended_at_utc_ms: Option<i64>,
    pub exit_code: Option<i32>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct StoredCheckpoint {
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
