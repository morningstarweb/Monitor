#![allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)]

use monitor_storage::Storage;
use monitor_telemetry::TelemetrySampler;
use monitor_transport::{
    AppState, build_local_router, build_router, session_crypto::get_or_create_host_noise_keypair,
};
use std::net::SocketAddr;
use tokio::net::TcpListener;

/// Runs an ephemeral compiled Windows `monitor-transport` server for live
/// emulator/device integration tests (`WindowsBridgeE2EAndroidTest`) on
/// `127.0.0.1:9485` (public LAN v2 router) and `127.0.0.1:9486` (local control router).
#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let api_port: u16 = std::env::var("MONITOR_BRIDGE_PORT")
        .ok()
        .and_then(|v| v.parse().ok())
        .unwrap_or(9485);
    let local_port: u16 = std::env::var("MONITOR_BRIDGE_LOCAL_PORT")
        .ok()
        .and_then(|v| v.parse().ok())
        .unwrap_or(9486);
    let ready_file = std::env::var("MONITOR_BRIDGE_READY_FILE")
        .unwrap_or_else(|_| "bridge-ready.json".to_string());
    let max_secs: u64 = std::env::var("MONITOR_BRIDGE_TIMEOUT_SECS")
        .ok()
        .and_then(|v| v.parse().ok())
        .unwrap_or(90);

    let storage = Storage::in_memory()?;
    storage.set_setting("allow_local_network", "1")?;
    storage.set_setting("allow_git_operations", "1")?;
    let telemetry = TelemetrySampler::new();
    let git_executor = monitor_git::GitExecutor::new()?;
    let state = AppState::new(storage, telemetry, git_executor);
    state.set_listen_port(api_port);
    state
        .telemetry_bus
        .publish(state.telemetry.sample_system_snapshot());

    let host_keys = get_or_create_host_noise_keypair(&state.storage)?;
    let pairing_code = state.generate_pairing_code();

    let api_listener = TcpListener::bind(("127.0.0.1", api_port)).await?;
    let local_listener = TcpListener::bind(("127.0.0.1", local_port)).await?;

    let ready_json = serde_json::json!({
        "api_port": api_listener.local_addr()?.port(),
        "local_port": local_listener.local_addr()?.port(),
        "pairing_code": pairing_code,
        "host_public_key": monitor_transport::session_crypto::hex_encode(&host_keys.public_key),
    });
    std::fs::write(&ready_file, serde_json::to_string_pretty(&ready_json)?)?;

    let bus_state = state.clone();
    tokio::spawn(async move {
        let mut ticker = tokio::time::interval(std::time::Duration::from_millis(500));
        loop {
            ticker.tick().await;
            let snap = bus_state.telemetry.sample_system_snapshot();
            bus_state.telemetry_bus.publish(snap);
        }
    });

    let api_router = build_router(state.clone());
    let local_router = build_local_router(state);

    tokio::select! {
        res = axum::serve(api_listener, api_router.into_make_service_with_connect_info::<SocketAddr>()) => {
            res?;
        }
        res = axum::serve(local_listener, local_router.into_make_service_with_connect_info::<SocketAddr>()) => {
            res?;
        }
        () = tokio::time::sleep(std::time::Duration::from_secs(max_secs)) => {}
    }

    Ok(())
}
