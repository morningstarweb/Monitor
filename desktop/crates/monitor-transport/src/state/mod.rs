mod otp;
mod revocation;
mod types;

use crate::connection_controller::{ConnectionController, ReconnectPolicy};
use crate::telemetry_bus::{DEFAULT_MAX_TELEMETRY_BYTES, TelemetryBus};
use monitor_core::ServerEvent;
use monitor_git::{GitExecutor, RepoCoordinator};
use monitor_storage::Storage;
use monitor_telemetry::TelemetrySampler;
use std::collections::{HashMap, HashSet};
use std::sync::{Arc, Mutex};
use tokio::sync::broadcast;

pub use types::{
    ActivePairingCode, AuthenticatedDevice, CloudDeviceInfo, DeviceOtpEntry, OtpVerifyError,
    ProjectCache, WorkspaceCache, WsTicketEntry,
};

#[derive(Clone, Debug)]
pub struct AppState {
    pub storage: Storage,
    pub telemetry: TelemetrySampler,
    /// Coalesced, byte-bounded latest-value channel for telemetry ticks.
    pub telemetry_bus: TelemetryBus,
    /// Live transport state machine shared by LAN, WebRTC and HostRoom paths.
    /// Generation IDs make events from superseded attempts harmless.
    pub connection_controller: Arc<Mutex<ConnectionController>>,
    pub git_executor: GitExecutor,
    pub repo_coordinator: RepoCoordinator,
    pub pairing_code: Arc<Mutex<Option<ActivePairingCode>>>,
    pub ws_tickets: Arc<Mutex<HashMap<String, WsTicketEntry>>>,
    pub event_tx: broadcast::Sender<ServerEvent>,
    pub addresses: Arc<Mutex<Vec<String>>>,
    pub project_cache: ProjectCache,
    pub workspace_cache: WorkspaceCache,
    pub cloud_account: Arc<Mutex<Option<monitor_core::CloudAccount>>>,
    pub active_client_count: Arc<std::sync::atomic::AtomicUsize>,
    /// In-memory revocation set for immediate notification of live transports.
    /// The database remains authoritative across restarts; this set closes the
    /// gap between a revocation and the next database poll (AUD-01 / NEW-06).
    pub revoked_devices: Arc<Mutex<HashSet<String>>>,
    /// Broadcasts a revoked identity to live session owners (cloud/LAN).
    pub revocation_tx: broadcast::Sender<String>,
    /// Active devices connected on the local network (Wi-Fi/LAN).
    pub active_lan_devices: Arc<Mutex<HashSet<String>>>,
    /// Actual bound TCP port of the local HTTP/LAN server (`AUD-023`).
    pub listen_port: Arc<std::sync::atomic::AtomicU16>,
    /// Per-device 6-digit verification OTPs keyed by device_id / Noise identity.
    pub device_otps: Arc<Mutex<HashMap<String, DeviceOtpEntry>>>,
    /// Cached client devices synced from Cloudflare `/api/v1/devices` under same Gmail.
    pub cloud_devices_cache: Arc<Mutex<(Option<std::time::Instant>, Vec<CloudDeviceInfo>)>>,
    /// Prevents overlapping background cloud device list refreshes.
    pub cloud_devices_refreshing: Arc<std::sync::atomic::AtomicBool>,
}

impl AppState {
    pub fn new(storage: Storage, telemetry: TelemetrySampler, git_executor: GitExecutor) -> Self {
        let (event_tx, _) = broadcast::channel(256);
        let (revocation_tx, _) = broadcast::channel(256);
        let repo_coordinator = RepoCoordinator::new(git_executor.clone());
        let initial_cloud = storage
            .get_setting("cloud_account")
            .ok()
            .flatten()
            .and_then(|raw| serde_json::from_str::<monitor_core::CloudAccount>(&raw).ok());

        Self {
            storage,
            telemetry,
            telemetry_bus: TelemetryBus::new(DEFAULT_MAX_TELEMETRY_BYTES),
            connection_controller: Arc::new(Mutex::new(ConnectionController::new(
                ReconnectPolicy::default(),
            ))),
            git_executor,
            repo_coordinator,
            pairing_code: Arc::new(Mutex::new(None)),
            ws_tickets: Arc::new(Mutex::new(HashMap::new())),
            event_tx,
            addresses: Arc::new(Mutex::new(Vec::new())),
            project_cache: Arc::new(tokio::sync::Mutex::new(None)),
            workspace_cache: Arc::new(tokio::sync::Mutex::new(HashMap::new())),
            cloud_account: Arc::new(Mutex::new(initial_cloud)),
            active_client_count: Arc::new(std::sync::atomic::AtomicUsize::new(0)),
            revoked_devices: Arc::new(Mutex::new(HashSet::new())),
            revocation_tx,
            active_lan_devices: Arc::new(Mutex::new(HashSet::new())),
            listen_port: Arc::new(std::sync::atomic::AtomicU16::new(
                monitor_core::brand_config::DEFAULT_PORT,
            )),
            device_otps: Arc::new(Mutex::new(HashMap::new())),
            cloud_devices_cache: Arc::new(Mutex::new((None, Vec::new()))),
            cloud_devices_refreshing: Arc::new(std::sync::atomic::AtomicBool::new(false)),
        }
    }

    pub fn set_listen_port(&self, port: u16) {
        self.listen_port
            .store(port, std::sync::atomic::Ordering::Release);
    }

    pub fn listen_port(&self) -> u16 {
        self.listen_port.load(std::sync::atomic::Ordering::Acquire)
    }
}
