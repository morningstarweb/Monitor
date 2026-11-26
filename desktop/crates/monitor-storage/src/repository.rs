use crate::migrations::run_migrations;
use monitor_core::{DeviceId, DeviceRole, DeviceTokenHash, RepoId, Revision, TrustProfile};
use rusqlite::{Connection, Result, params};
use serde::{Deserialize, Serialize};
use std::path::Path;
use std::sync::{Arc, Mutex};

#[derive(Debug, Clone)]
pub struct StoredRepository {
    pub repo_id: RepoId,
    pub canonical_path: String,
    pub display_name: String,
    pub trust_profile: TrustProfile,
    pub registered_at_utc_ms: i64,
    pub watch_enabled: bool,
    pub revision: Revision,
}

#[derive(Debug, Clone)]
pub struct Storage {
    pub(crate) conn: Arc<Mutex<Connection>>,
    pub(crate) read_conn: Arc<Mutex<Connection>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StoredDevice {
    pub device_id: DeviceId,
    pub display_name: String,
    pub role: String,
    pub last_seen_utc_ms: Option<i64>,
    #[serde(default)]
    pub is_revoked: bool,
    #[serde(default)]
    pub is_local: bool,
    #[serde(default)]
    pub is_pending: bool,
    #[serde(default)]
    pub trust_tier: String,
    #[serde(default)]
    pub active_otp: Option<String>,
    #[serde(default)]
    pub otp_expires_at_ms: Option<i64>,
    #[serde(default)]
    pub platform: String,
}

include!("repository/repositories.rs");
include!("repository/devices.rs");
include!("repository/settings.rs");
include!("repository/command_ledger.rs");

#[cfg(test)]
mod tests {
    use super::*;
    include!("repository/tests/lifecycle.rs");
}
