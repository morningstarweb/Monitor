//! Blocking local I/O stays on one worker thread, never on the tray message loop.
use anyhow::{Context, Result};
use monitor_core::{RepoSummary, SystemSnapshot};
use reqwest::blocking::Client;
use serde::Deserialize;
use std::{
    sync::mpsc::{self, Receiver, Sender},
    time::Duration,
};

#[derive(Debug, Deserialize)]
pub struct PairingCode {
    pub code: String,
}
#[derive(Debug, Deserialize, Clone)]
pub struct Device {
    pub device_id: String,
    pub display_name: String,
    pub role: String,
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
}

#[derive(Debug, Deserialize, Clone)]
pub struct AlertItem {
    pub severity: String,
    pub title: String,
    pub body: String,
}

#[derive(Debug, Deserialize, Clone)]
pub struct AgentSessionItem {
    pub profile_name: String,
    pub pid: u32,
    pub state: String,
}

fn default_true() -> bool {
    true
}

fn default_port() -> u16 {
    monitor_core::brand_config::DEFAULT_PORT
}

#[derive(Debug, Deserialize)]
pub struct Status {
    pub snapshot: SystemSnapshot,
    pub projects: Vec<RepoSummary>,
    pub devices: Vec<Device>,
    pub addresses: Vec<String>,
    pub pairing_code: Option<PairingCode>,
    #[serde(default)]
    pub connected_clients: usize,
    pub cloud_account: Option<monitor_core::CloudAccountSummary>,
    #[serde(default = "default_true")]
    pub git_access_allowed: bool,
    #[serde(default)]
    pub delete_access_allowed: bool,
    #[serde(default = "default_true")]
    pub cloud_access_allowed: bool,
    #[serde(default = "default_true")]
    pub local_access_allowed: bool,
    #[serde(default)]
    pub terminal_access_allowed: bool,
    #[serde(default = "default_true")]
    pub checkpoint_access_allowed: bool,
    #[serde(default)]
    pub active_agent_sessions: Vec<AgentSessionItem>,
    #[serde(default)]
    pub alerts: Vec<AlertItem>,
    #[serde(default = "default_port")]
    pub listen_port: u16,
    #[serde(default)]
    pub host_cloud_device_id: Option<String>,
}

#[derive(Debug, Clone)]
pub enum Command {
    Poll,
    Refresh,
    Pair,
    RegisterPath(std::path::PathBuf),
    RemoveProject(String),
    RevokeDevice(String),
    GenerateDeviceOtp(String, String),
    SetDeviceTier(String, String),
    CloudLogin,
    CloudLogout,
    ToggleCloudAccess,
    ToggleLocalAccess,
    ToggleGitAccess,
    ToggleDeleteAccess,
    ToggleTerminalAccess,
    ToggleCheckpointAccess,
    ClearAlerts,
}
#[derive(Debug)]
pub struct Update {
    pub status: Result<Status>,
}

fn action(client: &Client, base: &str, command: Command) -> Result<Option<String>> {
    let (request, success_msg) = match command {
        // FEAT-05: Refresh does not perform work of its own; the status GET
        // immediately after this command is the only truthful result. Never
        // report a success message that can survive a failed status fetch.
        Command::Poll | Command::Refresh => return Ok(None),
        Command::Pair => (
            client.post(format!("{base}/local/pairing-code")),
            "Pairing code generated",
        ),
        Command::RegisterPath(path) => (
            client
                .post(format!("{base}/local/projects"))
                .json(&serde_json::json!({ "path": path.to_string_lossy() })),
            "Project added successfully",
        ),
        Command::RemoveProject(id) => (
            client.delete(format!("{base}/local/projects/{id}")),
            "Project removed",
        ),
        Command::RevokeDevice(id) => (
            client.delete(format!("{base}/local/devices/{id}")),
            "Device disconnected",
        ),
        Command::GenerateDeviceOtp(id, name) => (
            client
                .post(format!("{base}/local/devices/{id}/otp"))
                .json(&serde_json::json!({ "device_name": name })),
            "Device OTP generated",
        ),
        Command::SetDeviceTier(id, tier) => (
            client
                .post(format!("{base}/local/devices/{id}/tier"))
                .json(&serde_json::json!({ "tier": tier })),
            "Device permission tier updated",
        ),
        Command::CloudLogin => (
            client.post(format!("{base}/local/cloud/login")),
            "Opening browser for Google Login…",
        ),
        Command::CloudLogout => (
            client.post(format!("{base}/local/cloud/disconnect")),
            "Cloud disconnected",
        ),
        Command::ToggleCloudAccess => (
            client.post(format!("{base}/local/permissions/toggle-cloud")),
            "Cloud sync permission updated",
        ),
        Command::ToggleLocalAccess => (
            client.post(format!("{base}/local/permissions/toggle-local")),
            "Local Wi-Fi permission updated",
        ),
        Command::ToggleGitAccess => (
            client.post(format!("{base}/local/permissions/toggle-git")),
            "Git permission updated",
        ),
        Command::ToggleDeleteAccess => (
            client.post(format!("{base}/local/permissions/toggle-delete")),
            "Delete permission updated",
        ),
        Command::ToggleTerminalAccess => (
            client.post(format!("{base}/local/permissions/toggle-terminal")),
            "Terminal execution permission updated",
        ),
        Command::ToggleCheckpointAccess => (
            client.post(format!("{base}/local/permissions/toggle-checkpoints")),
            "Safety checkpoints permission updated",
        ),
        Command::ClearAlerts => (
            client.post(format!("{base}/local/alerts/clear")),
            "Alert logs cleared",
        ),
    };
    let response = request
        .header(reqwest::header::CONNECTION, "close")
        .send()
        .context("contacting Monitor")?;
    if !response.status().is_success() {
        let message = response
            .json::<serde_json::Value>()
            .ok()
            .and_then(|value| {
                value
                    .get("message")
                    .and_then(|value| value.as_str())
                    .map(str::to_owned)
            })
            .unwrap_or_else(|| "Action could not be completed".to_string());
        anyhow::bail!("{message}");
    }
    let _ = response.bytes();
    Ok(Some(success_msg.to_string()))
}

pub fn start(port: u16) -> Result<(Sender<Command>, Receiver<Update>)> {
    let client = Client::builder()
        .no_proxy()
        .connect_timeout(Duration::from_millis(800))
        .timeout(Duration::from_secs(4))
        .tcp_keepalive(Duration::from_secs(15))
        .pool_idle_timeout(Duration::from_secs(10))
        .pool_max_idle_per_host(2)
        .build()?;
    let base = format!("http://127.0.0.1:{port}/api/v1");
    let (command_tx, command_rx) = mpsc::channel();
    let (update_tx, update_rx) = mpsc::channel();
    std::thread::Builder::new()
        .name("monitor-tray-io".to_string())
        .spawn(move || {
            loop {
                let command = match command_rx.recv_timeout(Duration::from_secs(2)) {
                    Ok(command) => command,
                    Err(mpsc::RecvTimeoutError::Timeout) => Command::Poll,
                    Err(mpsc::RecvTimeoutError::Disconnected) => break,
                };
                let action = match action(&client, &base, command) {
                    Ok(message) => message,
                    Err(error) => {
                        tracing::warn!("Tray action failed: {error:#}");
                        Some(format!("{error:#}"))
                    }
                };
                let status = client
                    .get(format!("{base}/local/status"))
                    .header(reqwest::header::CONNECTION, "close")
                    .send()
                    .and_then(reqwest::blocking::Response::error_for_status)
                    .and_then(reqwest::blocking::Response::json::<Status>)
                    .context("Monitor is unavailable; use Retry Background Service");
                let _ = action;
                if update_tx.send(Update { status }).is_err() {
                    break;
                }
            }
        })?;
    Ok((command_tx, update_rx))
}
