use crate::git::{GitWorkspaceSnapshot, RepoSummary};
use crate::telemetry::SystemSnapshot;
use crate::types::{DeviceId, DeviceRole, OperationId, RepoId};
use schemars::JsonSchema;
use serde::{Deserialize, Serialize};

/// Events pushed over the real-time WebSocket stream (`WS /api/v1/stream`).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, JsonSchema)]
#[serde(tag = "event", content = "data")]
pub enum ServerEvent {
    TelemetryTick(SystemSnapshot),
    ProjectUpdated(RepoSummary),
    WorkspaceUpdated {
        repo_id: RepoId,
        snapshot: GitWorkspaceSnapshot,
    },
    OperationProgress {
        operation_id: OperationId,
        repo_id: RepoId,
        stage: String,
        message: String,
    },
    AlertTriggered {
        alert_id: String,
        rule_name: String,
        message: String,
        severity: String,
    },
    TerminalOutput {
        repo_id: RepoId,
        session_id: String,
        text: String,
    },
    ResyncRequired,
}

/// Request payload for exchanging a 6-digit pairing code.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub struct PairingRequest {
    pub pairing_code: String,
    pub device_name: String,
    #[serde(default)]
    pub public_key: Option<String>,
}

/// Response payload containing the scoped device token.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub struct PairingResponse {
    pub device_token: String,
    pub device_id: DeviceId,
    pub role: DeviceRole,
}

/// Short-lived single-use ticket for WebSocket authentication.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub struct WsTicketResponse {
    pub ticket: String,
    pub expires_in_seconds: u32,
}

/// Health check response.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub struct HealthResponse {
    pub app_name: String,
    pub version: String,
    pub auth_required: bool,
}

/// Response payload for process termination endpoint.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub struct ProcessKillResponse {
    pub success: bool,
    pub pid: u32,
}
