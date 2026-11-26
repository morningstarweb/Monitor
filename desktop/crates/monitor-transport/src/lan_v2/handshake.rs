use super::LAN_V2_PROLOGUE;
use super::session_loop::run_lan_v2_loop;
use crate::session_crypto::{HostHandshakeResponder, get_or_create_host_noise_keypair, hex_encode};
use crate::state::AppState;
use axum::extract::ws::{Message, WebSocket};
use monitor_core::{DeviceId, DeviceRole};
use std::sync::Arc;
use std::time::Duration;
use tracing::{debug, error, info, warn};
use uuid::Uuid;

struct ClientGuard {
    counter: Arc<std::sync::atomic::AtomicUsize>,
    state: AppState,
    device_id: String,
}

impl ClientGuard {
    fn new(
        counter: Arc<std::sync::atomic::AtomicUsize>,
        state: AppState,
        device_id: String,
    ) -> Self {
        counter.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
        state.mark_lan_device_connected(&device_id);
        Self {
            counter,
            state,
            device_id,
        }
    }
}

impl Drop for ClientGuard {
    fn drop(&mut self) {
        self.counter
            .fetch_sub(1, std::sync::atomic::Ordering::Relaxed);
        self.state.mark_lan_device_disconnected(&self.device_id);
    }
}

pub(super) async fn handle_socket_v2(mut socket: WebSocket, state: AppState) {
    let host_keys = match get_or_create_host_noise_keypair(&state.storage) {
        Ok(k) => k,
        Err(e) => {
            error!("Failed to obtain host Noise static keypair: {e}");
            return;
        }
    };

    let mut responder = match HostHandshakeResponder::new(&host_keys, LAN_V2_PROLOGUE) {
        Ok(r) => r,
        Err(e) => {
            error!("Failed to initialize Noise handshake responder: {e}");
            return;
        }
    };

    let msg1_frame = match tokio::time::timeout(Duration::from_secs(10), socket.recv()).await {
        Ok(Some(Ok(Message::Binary(bytes)))) => bytes.to_vec(),
        Ok(Some(Ok(other))) => {
            warn!("Expected binary Noise handshake msg1, received non-binary frame: {other:?}");
            return;
        }
        Ok(Some(Err(e))) => {
            warn!("WebSocket read error during Noise handshake: {e}");
            return;
        }
        Ok(None) => {
            debug!("WebSocket client disconnected during Noise handshake");
            return;
        }
        Err(_) => {
            warn!("Timeout awaiting Noise handshake msg1 from client");
            return;
        }
    };

    let payload_bytes = match responder.read_handshake_msg1(&msg1_frame) {
        Ok(bytes) => bytes,
        Err(e) => {
            warn!("Noise handshake msg1 verification failed: {e}");
            return;
        }
    };

    let Some(remote_static_pk) = responder.remote_static_public_key() else {
        warn!("Initiator did not present a valid static public key");
        return;
    };

    let viewer_device_id = hex_encode(&remote_static_pk);
    let session_uuid = Uuid::new_v4();
    let epoch = 1u64;

    let parsed_hello = serde_json::from_slice::<serde_json::Value>(&payload_bytes).ok();
    let display_name = parsed_hello
        .as_ref()
        .and_then(|val| {
            val.get("device_name")
                .or_else(|| val.get("display_name"))
                .or_else(|| val.get("name"))
                .or_else(|| val.get("model"))
                .and_then(|v| v.as_str())
                .filter(|s| !s.trim().is_empty())
                .map(str::to_string)
        })
        .unwrap_or_else(|| "Android Phone".to_string());
    let cloud_device_id_hint = parsed_hello
        .as_ref()
        .and_then(|val| val.get("cloud_device_id").and_then(|v| v.as_str()))
        .map(str::trim)
        .filter(|s| !s.is_empty() && s.len() <= 128)
        .map(str::to_string);

    let existing_trust = state
        .storage
        .get_viewer_trust(&viewer_device_id)
        .ok()
        .flatten();

    if state.is_revoked_locally(&viewer_device_id)
        || existing_trust
            .as_ref()
            .is_some_and(|trust| trust.revoked_at_utc_ms.is_some())
    {
        warn!(
            "Revoked viewer {viewer_device_id} attempted a LAN v2 reconnect; rejecting without changing authorization state"
        );
        return;
    }

    if let Some(ref cid) = cloud_device_id_hint {
        if state.is_revoked_locally(cid)
            || state
                .storage
                .get_viewer_trust(cid)
                .ok()
                .flatten()
                .is_some_and(|t| t.revoked_at_utc_ms.is_some())
        {
            warn!("Revoked cloud device {cid} attempted a LAN v2 reconnect; rejecting");
            return;
        }
        let _ = state
            .storage
            .set_setting(&format!("cloud_device_link:{cid}"), &viewer_device_id);
        let _ = state
            .storage
            .set_setting(&format!("device_cloud_id:{viewer_device_id}"), cid);
    }

    let is_new_viewer = state
        .storage
        .get_viewer_trust(&viewer_device_id)
        .ok()
        .flatten()
        .is_none();
    if is_new_viewer {
        let _ = state.storage.enroll_viewer_with_tier(
            &viewer_device_id,
            &display_name,
            &viewer_device_id,
            "T0",
        );
    }

    let current_trust = state
        .storage
        .get_viewer_trust(&viewer_device_id)
        .ok()
        .flatten()
        .filter(|trust| trust.revoked_at_utc_ms.is_none());
    let Some(current_trust) = current_trust else {
        warn!("Viewer device {viewer_device_id} could not obtain valid trust tier; rejecting");
        return;
    };

    let dev_id = DeviceId::new(&viewer_device_id);
    let token_hash =
        crate::auth::TokenAuthenticator::hash_token(&format!("v2_lan_peer:{viewer_device_id}"));
    let role = match current_trust.trust_tier.as_str() {
        "T2" | "T3" => DeviceRole::Operator,
        _ => DeviceRole::Viewer,
    };
    let _ = state
        .storage
        .upsert_device(&dev_id, &display_name, &token_hash, role);

    let host_hello = serde_json::json!({
        "status": "authenticated",
        "session_id": session_uuid.to_string(),
        "trust_tier": current_trust.trust_tier,
    })
    .to_string();

    let (msg2_bytes, session) = match responder.write_handshake_msg2(
        host_hello.as_bytes(),
        session_uuid.to_string(),
        epoch,
    ) {
        Ok(res) => res,
        Err(e) => {
            error!("Failed to generate Noise handshake msg2: {e}");
            return;
        }
    };

    if let Err(e) = socket.send(Message::Binary(msg2_bytes.into())).await {
        warn!("Failed to send Noise handshake msg2: {e}");
        return;
    }

    info!(
        "Secure v2 LAN session established: viewer={} session={} tier={}",
        viewer_device_id, session_uuid, current_trust.trust_tier
    );
    {
        let mut controller = state
            .connection_controller
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        let generation = controller.generation();
        if generation > 0 {
            let _ =
                controller.path_ready(generation, crate::connection_controller::TransportPath::Lan);
        }
    }

    let _client_guard = ClientGuard::new(
        Arc::clone(&state.active_client_count),
        state.clone(),
        viewer_device_id.clone(),
    );

    run_lan_v2_loop(
        socket,
        state,
        Arc::new(session),
        session_uuid,
        epoch,
        viewer_device_id,
    )
    .await;
}
