use super::handle_host_room_session;
use crate::cloud::broker_base_url;
use crate::connection_controller::TransportPath;
use crate::session_crypto::{get_or_create_host_noise_keypair, hex_encode};
use crate::state::AppState;
use serde::Deserialize;
use std::time::Duration;
use tracing::{debug, error, info, warn};

#[derive(Debug, Deserialize)]
struct HostTicketResponse {
    success: bool,
    ticket: String,
    host_room_id: String,
}

/// Sleeps with exponential backoff and randomized 1-5 second jitter to prevent thundering herd reconnection storms on D1.
async fn reconnect_sleep_with_jitter(backoff: &mut Duration) {
    let jitter_ms = (std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.subsec_millis() as u64)
        .unwrap_or(500)
        % 4000)
        + 1000;
    tokio::time::sleep(*backoff + Duration::from_millis(jitter_ms)).await;
    *backoff = (*backoff * 2).min(Duration::from_secs(30));
}

/// NET-02: drive the shared connection state machine on failure. The controller
/// owns the bounded backoff; the legacy jitter is only a fallback for states the
/// controller considers stale (e.g. a late error from a superseded generation).
async fn controller_reconnect_sleep(state: &AppState, generation: u64, fallback: &mut Duration) {
    let scheduled = {
        let mut controller = state
            .connection_controller
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        let _ = controller.degrade(generation);
        controller
            .begin_recovery(generation)
            .ok()
            .map(Duration::from_millis)
    };
    match scheduled {
        Some(delay) => tokio::time::sleep(delay).await,
        None => reconnect_sleep_with_jitter(fallback).await,
    }
}

pub(super) async fn run_host_control_loop(state: AppState) {
    let client = match reqwest::Client::builder()
        .timeout(Duration::from_secs(10))
        .build()
    {
        Ok(c) => c,
        Err(e) => {
            error!("Failed to initialize host control HTTP client: {e}");
            return;
        }
    };

    let mut backoff = Duration::from_secs(2);

    loop {
        let generation = {
            let mut controller = state
                .connection_controller
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner);
            if !controller.begin_discovery() {
                warn!("Connection controller is terminal; stopping host control loop");
                return;
            }
            controller.generation()
        };

        let maybe_account = {
            let guard = state
                .cloud_account
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner);
            guard.clone()
        };

        let Some(account) = maybe_account else {
            tokio::time::sleep(Duration::from_secs(3)).await;
            continue;
        };

        let cloud_allowed = state
            .storage
            .get_setting("allow_cloud_operations")
            .unwrap_or(None)
            .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
            .unwrap_or(false);

        if !cloud_allowed {
            tokio::time::sleep(Duration::from_secs(2)).await;
            continue;
        }

        let host_keys = match get_or_create_host_noise_keypair(&state.storage) {
            Ok(keys) => keys,
            Err(error) => {
                error!("Failed to load host Noise keys: {error}");
                controller_reconnect_sleep(&state, generation, &mut backoff).await;
                continue;
            }
        };
        let key_url = format!("{}/api/v2/hosts/noise-key", broker_base_url());
        match client
            .post(&key_url)
            .bearer_auth(&account.token)
            .json(&serde_json::json!({ "public_key": hex_encode(&host_keys.public_key) }))
            .send()
            .await
        {
            Ok(response) if response.status().is_success() => {
                let mut controller = state
                    .connection_controller
                    .lock()
                    .unwrap_or_else(std::sync::PoisonError::into_inner);
                let _ = controller.begin_authentication(generation);
            }
            Ok(response) => {
                warn!(
                    "Host Noise key registration rejected with HTTP status: {}",
                    response.status()
                );
                controller_reconnect_sleep(&state, generation, &mut backoff).await;
                continue;
            }
            Err(error) => {
                warn!("Host Noise key registration failed: {error}");
                controller_reconnect_sleep(&state, generation, &mut backoff).await;
                continue;
            }
        }

        let ticket_url = format!("{}/api/v2/hosts/tickets", broker_base_url());
        let ticket_res = client
            .post(&ticket_url)
            .bearer_auth(&account.token)
            .send()
            .await;

        let ticket_data = match ticket_res {
            Ok(res) if res.status().is_success() => match res.json::<HostTicketResponse>().await {
                Ok(body) if !body.success => {
                    warn!("Host ticket response marked success as false");
                    controller_reconnect_sleep(&state, generation, &mut backoff).await;
                    continue;
                }
                Ok(body) => body,
                Err(e) => {
                    warn!("Failed to parse host ticket response: {e}");
                    controller_reconnect_sleep(&state, generation, &mut backoff).await;
                    continue;
                }
            },
            Ok(res) => {
                warn!(
                    "Host ticket request rejected with HTTP status: {}",
                    res.status()
                );
                controller_reconnect_sleep(&state, generation, &mut backoff).await;
                continue;
            }
            Err(e) => {
                debug!("Host ticket network failure: {e}");
                controller_reconnect_sleep(&state, generation, &mut backoff).await;
                continue;
            }
        };

        {
            let mut controller = state
                .connection_controller
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner);
            let _ = controller.begin_race(generation, &[TransportPath::CloudRelay]);
        }

        let base_url = broker_base_url();
        let ws_base = if base_url.starts_with("https://") {
            base_url.replacen("https://", "wss://", 1)
        } else {
            base_url.replacen("http://", "ws://", 1)
        };
        let ws_url = format!(
            "{ws_base}/api/v2/rendezvous/host?ticket={}",
            ticket_data.ticket
        );

        info!(
            "Connecting outbound to Cloudflare HostRoom DO ({})",
            ticket_data.host_room_id
        );

        match tokio_tungstenite::connect_async(&ws_url).await {
            Ok((ws_stream, _response)) => {
                info!(
                    "Successfully established outbound relay connection to Cloudflare HostRoom DO"
                );
                backoff = Duration::from_secs(2);
                {
                    let mut controller = state
                        .connection_controller
                        .lock()
                        .unwrap_or_else(std::sync::PoisonError::into_inner);
                    let _ = controller.path_ready(generation, TransportPath::CloudRelay);
                }
                handle_host_room_session(ws_stream, &state).await;
            }
            Err(e) => {
                warn!("Outbound connection to Cloudflare HostRoom failed: {e}");
                controller_reconnect_sleep(&state, generation, &mut backoff).await;
            }
        }
    }
}
