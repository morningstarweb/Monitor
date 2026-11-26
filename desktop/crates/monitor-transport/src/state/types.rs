use monitor_core::{DeviceId, DeviceRole, GitWorkspaceSnapshot, RepoId, RepoSummary};
use std::collections::HashMap;
use std::sync::Arc;

#[derive(Debug, Clone, serde::Serialize)]
pub struct ActivePairingCode {
    pub code: String,
    pub expires_at_ms: i64,
    pub attempts: u8,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct DeviceOtpEntry {
    pub device_id: String,
    pub device_name: String,
    pub code: String,
    pub expires_at_ms: i64,
    pub attempts: u8,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct CloudDeviceInfo {
    pub id: String,
    pub name: String,
    #[serde(default)]
    pub platform: String,
    #[serde(default)]
    pub role: String,
    #[serde(default)]
    pub is_online: i64,
    #[serde(default)]
    pub last_heartbeat: i64,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum OtpVerifyError {
    NoActiveSession,
    Expired,
    TooManyAttempts,
    InvalidCode,
}

#[derive(Debug, Clone)]
pub struct WsTicketEntry {
    pub device_id: DeviceId,
    pub role: DeviceRole,
    pub expires_at_ms: i64,
}

#[derive(Debug, Clone)]
pub struct AuthenticatedDevice {
    pub device_id: DeviceId,
    pub role: DeviceRole,
}

pub type ProjectCache = Arc<tokio::sync::Mutex<Option<(std::time::Instant, Vec<RepoSummary>)>>>;
pub type WorkspaceCache =
    Arc<tokio::sync::Mutex<HashMap<RepoId, (std::time::Instant, GitWorkspaceSnapshot)>>>;
