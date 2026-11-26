use crate::state::{AppState, AuthenticatedDevice};
use axum::{
    Extension, Json,
    extract::{Path as AxumPath, Query, State},
    http::StatusCode,
};
use monitor_core::{
    AgentProfileConfig, AgentSessionSummary, AlertItem, AlertRuleConfig, CheckpointSummary,
    CreateCheckpointRequest, DeviceRole, RollbackResponse, ServerEvent, SnoozeAlertRequest,
};
use monitor_git::{CheckpointEngine, GitExecutor};
use monitor_storage::{StoredAlertRule, StoredCheckpoint};
use serde::Deserialize;
use serde_json::{Value, json};
use std::path::Path;
include!("watchdog_handlers/alerts.rs");
include!("watchdog_handlers/checkpoints.rs");
include!("watchdog_handlers/session_kill.rs");
include!("watchdog_handlers/session_kill_endpoint.rs");
