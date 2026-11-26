#![allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)]

use super::session::terminate_sessions_for_identity;
use super::{CLOUD_V2_PROLOGUE, process_v2_envelope};
use crate::session_crypto::{
    HostHandshakeResponder, NoiseKeypair, SecureSession, ViewerHandshakeInitiator,
    get_or_create_host_noise_keypair, hex_encode,
};
use crate::state::AppState;
use monitor_core::{EnvelopeV2, MessageType, StreamKind};
use monitor_storage::Storage;
use monitor_telemetry::TelemetrySampler;
use std::collections::HashMap;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use tokio::sync::Mutex;
use uuid::Uuid;

#[tokio::test]
async fn terminating_revoked_identity_removes_only_its_sessions() {
    let active_sessions: Arc<Mutex<HashMap<String, Arc<SecureSession>>>> =
        Arc::new(Mutex::new(HashMap::new()));
    let viewer_trust_ids = Arc::new(Mutex::new(HashMap::from([
        ("session-revoked".to_string(), "revoked-key".to_string()),
        ("session-allowed".to_string(), "allowed-key".to_string()),
    ])));
    let webrtc_ready = Arc::new(Mutex::new(std::collections::HashSet::new()));
    let manager = crate::webrtc_peer::WebRtcPeerManager::new();
    let is_idle = AtomicBool::new(false);

    terminate_sessions_for_identity(
        "revoked-key",
        &active_sessions,
        &viewer_trust_ids,
        &webrtc_ready,
        &manager,
        &is_idle,
    )
    .await;

    let remaining = viewer_trust_ids.lock().await;
    assert_eq!(remaining.len(), 1);
    assert!(remaining.contains_key("session-allowed"));
    assert!(!remaining.contains_key("session-revoked"));
    assert!(is_idle.load(Ordering::Relaxed));
}

#[tokio::test]
async fn revoked_identity_loses_its_session_and_commands_are_denied() {
    let storage = Storage::in_memory().expect("in-memory storage");
    for setting in [
        "allow_local_network",
        "allow_cloud_operations",
        "allow_git_operations",
        "allow_delete_operations",
    ] {
        storage.set_setting(setting, "1").expect("grant policy");
    }
    let host_keys = get_or_create_host_noise_keypair(&storage).expect("host keys");
    let viewer_keys = NoiseKeypair::generate().expect("viewer keys");
    let viewer_id = hex_encode(&viewer_keys.public_key);
    storage
        .enroll_viewer_with_tier(&viewer_id, "user-1", &viewer_id, "T3")
        .expect("enroll viewer");
    let state = AppState::new(
        storage,
        TelemetrySampler::new(),
        monitor_git::GitExecutor::new().expect("git executor"),
    );

    let mut initiator =
        ViewerHandshakeInitiator::new(&viewer_keys, &host_keys.public_key, CLOUD_V2_PROLOGUE)
            .expect("initiator");
    let msg1 = initiator
        .write_handshake_msg1(b"{\"device\":\"phone\"}")
        .expect("msg1");
    let mut responder =
        HostHandshakeResponder::new(&host_keys, CLOUD_V2_PROLOGUE).expect("responder");
    responder.read_handshake_msg1(&msg1).expect("read msg1");
    let session_id = Uuid::new_v4().to_string();
    let (_, session) = responder
        .write_handshake_msg2(b"{}", session_id.clone(), 1)
        .expect("msg2");
    let session = Arc::new(session);

    let active_sessions: Arc<Mutex<HashMap<String, Arc<SecureSession>>>> = Arc::new(Mutex::new(
        HashMap::from([(session_id.clone(), Arc::clone(&session))]),
    ));
    let viewer_trust_ids = Arc::new(Mutex::new(HashMap::from([(
        session_id.clone(),
        viewer_id.clone(),
    )])));
    let webrtc_ready = Arc::new(Mutex::new(std::collections::HashSet::new()));
    let manager = crate::webrtc_peer::WebRtcPeerManager::new();
    let is_idle = AtomicBool::new(false);

    assert!(state.revoke_identity(&viewer_id).expect("revoke"));
    terminate_sessions_for_identity(
        &viewer_id,
        &active_sessions,
        &viewer_trust_ids,
        &webrtc_ready,
        &manager,
        &is_idle,
    )
    .await;

    assert!(active_sessions.lock().await.is_empty());
    assert!(viewer_trust_ids.lock().await.is_empty());
    assert!(is_idle.load(Ordering::Relaxed));

    let mut envelope = EnvelopeV2::new(
        Uuid::parse_str(&session_id).unwrap_or_else(|_| Uuid::new_v4()),
        1,
        StreamKind::Control,
        MessageType::CommandRequest,
        1,
        0,
        30_000,
    );
    envelope.command_id = Some(Uuid::new_v4());
    envelope.payload = Some(serde_json::json!({
        "command_type": "git.stage",
        "payload": {}
    }));
    let response = process_v2_envelope(&envelope, &session, &state, &viewer_id, false)
        .await
        .expect("rejection response");
    assert_eq!(response.message_type, MessageType::CommandRejected);
    assert!(
        response
            .payload
            .and_then(|value| value
                .get("error")
                .and_then(|v| v.as_str())
                .map(str::to_string))
            .unwrap_or_default()
            .contains("revoked")
    );
}
