use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum CommandLedgerState {
    New,
    Accepted,
    Executing,
    Completed,
    Failed,
    Cancelled,
    UnknownAfterCrash,
    Rejected,
}

impl CommandLedgerState {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::New => "NEW",
            Self::Accepted => "ACCEPTED",
            Self::Executing => "EXECUTING",
            Self::Completed => "COMPLETED",
            Self::Failed => "FAILED",
            Self::Cancelled => "CANCELLED",
            Self::UnknownAfterCrash => "UNKNOWN_AFTER_CRASH",
            Self::Rejected => "REJECTED",
        }
    }

    pub fn from_db_str(s: &str) -> Self {
        match s {
            "NEW" => Self::New,
            "ACCEPTED" => Self::Accepted,
            "EXECUTING" => Self::Executing,
            "COMPLETED" => Self::Completed,
            "FAILED" => Self::Failed,
            "CANCELLED" => Self::Cancelled,
            "REJECTED" => Self::Rejected,
            _ => Self::UnknownAfterCrash,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct CommandLedgerEntry {
    pub command_id: String,
    pub viewer_device_id: String,
    pub operation: String,
    pub canonical_params_hash: String,
    pub session_id: String,
    pub accepted_epoch: u64,
    pub tier: String,
    pub state: CommandLedgerState,
    pub received_at_utc_ms: i64,
    pub started_at_utc_ms: Option<i64>,
    pub finished_at_utc_ms: Option<i64>,
    pub deadline_ms: u64,
    pub result_code: Option<String>,
    pub result_digest: Option<String>,
    pub result_summary: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum LedgerAdmission {
    /// Newly admitted for execution
    Admitted,
    /// Duplicate command ID with matching params; returns previous execution state
    Duplicate(Box<CommandLedgerEntry>),
    /// Reused command ID with conflicting/different parameters
    Conflict,
}
