use super::{CLOUD_V2_PROLOGUE, queue_relay_frame, split_v2_envelope};
use crate::session_crypto::{
    HostHandshakeResponder, SecureSession, get_or_create_host_noise_keypair, hex_encode,
};
use crate::state::AppState;
use monitor_core::{DeviceId, DeviceRole, EnvelopeV2, MessageType, StreamKind};
use std::collections::HashMap;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use tokio::sync::{Mutex, mpsc};
use tokio_tungstenite::tungstenite::Message;
use tracing::{error, info, warn};
use uuid::Uuid;

pub(super) async fn handle_viewer_handshake(
    data: &[u8],
    viewer_session_id: &str,
    state: &AppState,
    active_sessions: &Arc<Mutex<HashMap<String, Arc<SecureSession>>>>,
    viewer_trust_ids: &Arc<Mutex<HashMap<String, String>>>,
    is_idle: &AtomicBool,
    out_tx: &mpsc::Sender<Message>,
) {
    let host_keys = match get_or_create_host_noise_keypair(&state.storage) {
        Ok(keys) => keys,
        Err(error) => {
            error!("Failed to load host Noise keys: {error}");
            return;
        }
    };
    let mut responder = match HostHandshakeResponder::new(&host_keys, CLOUD_V2_PROLOGUE) {
        Ok(responder) => responder,
        Err(error) => {
            warn!("Failed to initialize Noise responder: {error}");
            return;
        }
    };

    if let Ok(payload_bytes) = responder.read_handshake_msg1(data) {
        let Some(remote_pk) = responder.remote_static_public_key() else {
            warn!("Noise handshake from {viewer_session_id} omitted a static key");
            return;
        };
        let trust_id = hex_encode(&remote_pk);

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

        let existing_trust = state.storage.get_viewer_trust(&trust_id).ok().flatten();
        if state.is_revoked_locally(&trust_id)
            || existing_trust
                .as_ref()
                .is_some_and(|trust| trust.revoked_at_utc_ms.is_some())
        {
            warn!("Revoked viewer {trust_id} attempted a HostRoom session");
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
                warn!("Revoked cloud device {cid} attempted a HostRoom session");
                return;
            }
            let _ = state
                .storage
                .set_setting(&format!("cloud_device_link:{cid}"), &trust_id);
            let _ = state
                .storage
                .set_setting(&format!("device_cloud_id:{trust_id}"), cid);
        }

        if existing_trust.is_none() {
            let _ =
                state
                    .storage
                    .enroll_viewer_with_tier(&trust_id, &display_name, &trust_id, "T0");
        }

        let trust = existing_trust
            .or_else(|| state.storage.get_viewer_trust(&trust_id).ok().flatten())
            .filter(|trust| trust.revoked_at_utc_ms.is_none());
        let Some(trust) = trust else {
            warn!("Viewer {trust_id} was revoked during HostRoom setup; rejecting");
            return;
        };

        let dev_id = DeviceId::new(&trust_id);
        let token_hash =
            crate::auth::TokenAuthenticator::hash_token(&format!("cloud_v2_peer:{trust_id}"));
        let role = match trust.trust_tier.as_str() {
            "T2" | "T3" => DeviceRole::Operator,
            _ => DeviceRole::Viewer,
        };
        let _ = state
            .storage
            .upsert_device(&dev_id, &display_name, &token_hash, role);

        let session_id = Uuid::new_v4();
        let data_channel_send_key: [u8; 32] = rand::random();
        let data_channel_recv_key: [u8; 32] = rand::random();
        let hello = serde_json::json!({
            "status": "authenticated",
            "session_id": session_id,
            "trust_tier": trust.trust_tier,
            "data_channel_send_key": hex_encode(&data_channel_send_key),
            "data_channel_recv_key": hex_encode(&data_channel_recv_key),
        })
        .to_string();
        if let Ok((message, session)) = responder.write_handshake_msg2_with_data_channel_keys(
            hello.as_bytes(),
            session_id.to_string(),
            1,
            data_channel_send_key,
            data_channel_recv_key,
        ) {
            let session_arc = Arc::new(session);
            active_sessions
                .lock()
                .await
                .insert(viewer_session_id.to_string(), Arc::clone(&session_arc));
            viewer_trust_ids
                .lock()
                .await
                .insert(viewer_session_id.to_string(), trust_id.clone());
            is_idle.store(false, Ordering::Relaxed);
            let _ = queue_relay_frame(out_tx, viewer_session_id, &message).await;
            info!("Noise session established for viewer session {viewer_session_id}");

            push_initial_state(state, &session_arc, viewer_session_id, out_tx).await;
        }
        return;
    }

    warn!("Received data before or outside Noise handshake for {viewer_session_id}");
}

async fn push_initial_state(
    state: &AppState,
    session_arc: &Arc<SecureSession>,
    viewer_session_id: &str,
    out_tx: &mpsc::Sender<Message>,
) {
    let snapshot = state.telemetry.sample_system_snapshot();
    let mut snap_env = EnvelopeV2::new(
        Uuid::parse_str(&session_arc.session_id).unwrap_or_else(|_| Uuid::new_v4()),
        session_arc.epoch,
        StreamKind::Telemetry,
        MessageType::Snapshot,
        session_arc.send_sequence(),
        session_arc.recv_sequence(),
        30_000,
    );
    snap_env.payload = serde_json::to_value(&snapshot).ok();
    for part in split_v2_envelope(&snap_env, crate::session_crypto::MAX_PLAINTEXT_FRAME_SIZE) {
        if let Ok(part_bytes) = serde_json::to_vec(&part)
            && let Ok(ct) = session_arc.encrypt(&part_bytes).await
        {
            let _ = queue_relay_frame(out_tx, viewer_session_id, &ct).await;
        }
    }

    if let Ok(projects) = crate::local_handlers::list_project_summaries(state).await {
        for summary in &projects {
            let mut proj_env = EnvelopeV2::new(
                Uuid::parse_str(&session_arc.session_id).unwrap_or_else(|_| Uuid::new_v4()),
                session_arc.epoch,
                StreamKind::Control,
                MessageType::ProjectsResult,
                session_arc.send_sequence(),
                session_arc.recv_sequence(),
                30_000,
            );
            proj_env.payload = serde_json::to_value(summary).ok();
            for part in
                split_v2_envelope(&proj_env, crate::session_crypto::MAX_PLAINTEXT_FRAME_SIZE)
            {
                if let Ok(part_bytes) = serde_json::to_vec(&part)
                    && let Ok(ct) = session_arc.encrypt(&part_bytes).await
                {
                    let _ = queue_relay_frame(out_tx, viewer_session_id, &ct).await;
                }
            }
        }
    }
}
