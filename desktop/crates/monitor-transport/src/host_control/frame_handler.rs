use super::handshake::handle_viewer_handshake;
use super::{process_v2_envelope, split_v2_envelope};
use crate::session_crypto::SecureSession;
use crate::state::AppState;
use base64::Engine;
use base64::engine::general_purpose::STANDARD as BASE64;
use monitor_core::EnvelopeV2;
use std::collections::HashMap;
use std::sync::Arc;
use std::sync::atomic::AtomicBool;
use tokio::sync::{Mutex, mpsc};
use tokio_tungstenite::tungstenite::Message;
use tracing::warn;

pub(super) async fn handle_viewer_frame(
    data: &[u8],
    viewer_session_id: &str,
    state: &AppState,
    active_sessions: &Arc<Mutex<HashMap<String, Arc<SecureSession>>>>,
    viewer_trust_ids: &Arc<Mutex<HashMap<String, String>>>,
    is_idle: &AtomicBool,
    out_tx: &mpsc::Sender<Message>,
) {
    let existing_session = active_sessions.lock().await.get(viewer_session_id).cloned();

    if let Some(session) = existing_session
        && let Ok(plaintext) = session.decrypt(data).await
    {
        let Ok(envelope) = serde_json::from_slice::<EnvelopeV2>(&plaintext) else {
            warn!("Rejected malformed EnvelopeV2 from {viewer_session_id}");
            return;
        };
        let trust_id = viewer_trust_ids
            .lock()
            .await
            .get(viewer_session_id)
            .cloned()
            .unwrap_or_default();
        handle_relay_command(
            &envelope,
            &session,
            state,
            &trust_id,
            viewer_session_id,
            out_tx,
        )
        .await;
        return;
    }

    handle_viewer_handshake(
        data,
        viewer_session_id,
        state,
        active_sessions,
        viewer_trust_ids,
        is_idle,
        out_tx,
    )
    .await;
}

pub(super) async fn queue_relay_frame(
    out_tx: &mpsc::Sender<Message>,
    viewer_session_id: &str,
    data: &[u8],
) -> Result<(), tokio::sync::mpsc::error::SendError<Message>> {
    let frame = serde_json::json!({
        "event": "relay_frame",
        "viewer_session_id": viewer_session_id,
        "payload": BASE64.encode(data),
    });
    out_tx.send(Message::Text(frame.to_string().into())).await
}

async fn handle_relay_command(
    envelope: &EnvelopeV2,
    session: &SecureSession,
    state: &AppState,
    viewer_id: &str,
    viewer_session_id: &str,
    out_tx: &mpsc::Sender<Message>,
) {
    if let Some(response) = process_v2_envelope(envelope, session, state, viewer_id, false).await {
        for part in split_v2_envelope(&response, crate::session_crypto::MAX_PLAINTEXT_FRAME_SIZE) {
            if let Ok(bytes) = serde_json::to_vec(&part)
                && let Ok(ciphertext) = session.encrypt(&bytes).await
            {
                let _ = queue_relay_frame(out_tx, viewer_session_id, &ciphertext).await;
            }
        }
    }
}
