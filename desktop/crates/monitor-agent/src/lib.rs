//! The agent runs inside Monitor's tray process. No companion executable is needed.
use anyhow::{Context, Result};
use monitor_core::brand_config::DEFAULT_PORT;
use monitor_storage::Storage;
use monitor_telemetry::TelemetrySampler;
use monitor_transport::{AppState, run_server};
use std::{net::Ipv4Addr, path::PathBuf, time::Duration};
use tokio::{runtime::Runtime, task::JoinHandle};

pub mod watchdog;

pub fn data_directory() -> PathBuf {
    std::env::var_os("MONITOR_DATA_DIR")
        .map(PathBuf::from)
        .unwrap_or_else(|| {
            std::env::var_os("LOCALAPPDATA")
                .map(PathBuf::from)
                .unwrap_or_else(std::env::temp_dir)
                .join("Monitor")
        })
}

pub fn configured_port() -> Result<u16> {
    let port =
        std::env::var("MONITOR_PORT").map_or(Ok(DEFAULT_PORT), |value| value.parse::<u16>())?;
    anyhow::ensure!(port != 0, "MONITOR_PORT must be between 1 and 65535");
    Ok(port)
}

/// Owns every background task; dropping the runtime stops the whole application.
#[derive(Debug)]
pub struct EmbeddedAgent {
    runtime: Runtime,
    task: JoinHandle<Result<()>>,
}

impl EmbeddedAgent {
    pub fn start() -> Result<Self> {
        configured_port()?;
        let runtime = tokio::runtime::Builder::new_multi_thread()
            .enable_all()
            .build()
            .context("starting Monitor background runtime")?;
        let task = runtime.spawn(run_logged());
        Ok(Self { runtime, task })
    }

    pub fn stop(self) {
        self.task.abort();
        // AUD-07: release owned terminal reader/monitor tasks explicitly before
        // the runtime is torn down, so no task state outlives the agent.
        monitor_transport::terminal_handlers::abort_all_terminal_tasks();
        self.runtime.shutdown_timeout(Duration::from_secs(2));
    }
}

async fn refresh_addresses(state: &AppState, port: u16) {
    let lan = monitor_transport::network::discover_local_ipv4();
    let mut addresses = Vec::new();
    if let Some(ip) = lan {
        addresses.push(format!("http://{ip}:{port}"));
    }
    addresses.push(format!("http://127.0.0.1:{port}"));
    *state
        .addresses
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner) = addresses;

    // Check tailscale without blocking local binding
    if let Some(ts_ip) = monitor_transport::network::discover_tailscale_ipv4().await {
        let ts_addr = format!("http://{ts_ip}:{port}");
        let mut addrs = state
            .addresses
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        if !addrs.contains(&ts_addr) {
            addrs.insert(0, ts_addr);
        }
    }
}

pub async fn start_daemon_async() -> Result<()> {
    let port = configured_port()?;
    let db_dir = data_directory();
    std::fs::create_dir_all(&db_dir)?;
    let storage = Storage::open(db_dir.join("monitor.db"))?;
    let state = AppState::new(
        storage,
        TelemetrySampler::new(),
        monitor_git::GitExecutor::new()?,
    );
    refresh_addresses(&state, port).await;
    let telemetry_task = monitor_transport::server::spawn_telemetry_broadcast_task(state.clone());
    let cloud_task = monitor_transport::spawn_cloud_heartbeat_task(state.clone());
    let host_control_task = monitor_transport::spawn_host_control_task(state.clone());
    let watchdog_task = watchdog::spawn_watchdog_pipeline_task(state.clone());
    let address_state = state.clone();
    let address_task = tokio::spawn(async move {
        loop {
            tokio::time::sleep(Duration::from_secs(10)).await;
            refresh_addresses(&address_state, port).await;
        }
    });
    // Discovery must never suspend polling the server: Wi-Fi/Tailscale can be slow.
    let result = run_server(Ipv4Addr::UNSPECIFIED, port, state).await;
    telemetry_task.abort();
    cloud_task.abort();
    host_control_task.abort();
    watchdog_task.abort();
    address_task.abort();
    // AUD-07 / NEW-03: the daemon path must release owned terminal tasks too.
    // Their `Child` handles use kill_on_drop, so this also terminates children.
    monitor_transport::terminal_handlers::abort_all_terminal_tasks();
    result.map_err(|error| anyhow::anyhow!(error.to_string()))
}

async fn run_logged() -> Result<()> {
    let result = start_daemon_async().await;
    if let Err(error) = &result {
        tracing::error!("Monitor background service stopped: {error:#}");
    }
    result
}
