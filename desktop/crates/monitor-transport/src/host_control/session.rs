use super::{handle_viewer_frame, queue_relay_frame, split_v2_envelope};
use crate::session_crypto::SecureSession;
use crate::state::AppState;
use base64::Engine;
use base64::engine::general_purpose::STANDARD as BASE64;
use futures_util::{SinkExt, StreamExt};
use monitor_core::{EnvelopeV2, MessageType, StreamKind};
use serde::Deserialize;
use std::collections::HashMap;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Duration;
use tokio::sync::{Mutex, mpsc};
use tokio_tungstenite::tungstenite::Message;
use tracing::{debug, info, warn};
use uuid::Uuid;

#[derive(Debug, Deserialize)]
struct RoomEvent {
    event: String,
    #[serde(default)]
    viewer_device_id: Option<String>,
    #[serde(default)]
    viewer_session_id: Option<String>,
}

#[derive(Debug, Deserialize)]
struct RoutedRelayFrame {
    event: String,
    viewer_session_id: String,
    payload: String,
}

pub(super) async fn handle_host_room_session<S>(ws_stream: S, state: &AppState)
where
    S: futures_util::Stream<Item = Result<Message, tokio_tungstenite::tungstenite::Error>>
        + futures_util::Sink<Message, Error = tokio_tungstenite::tungstenite::Error>
        + Send
        + 'static,
{
    let (mut ws_sink, mut ws_stream) = ws_stream.split();
    let (out_tx, mut out_rx) = mpsc::channel::<Message>(64);

    let writer_handle = tokio::spawn(async move {
        while let Some(msg) = out_rx.recv().await {
            if let Err(e) = ws_sink.send(msg).await {
                warn!("HostRoom WebSocket sink write error: {e}");
                break;
            }
        }
    });

    let is_idle = Arc::new(AtomicBool::new(true));
    let active_sessions: Arc<Mutex<HashMap<String, Arc<SecureSession>>>> =
        Arc::new(Mutex::new(HashMap::new()));
    let viewer_trust_ids: Arc<Mutex<HashMap<String, String>>> =
        Arc::new(Mutex::new(HashMap::new()));
    let webrtc_ready: Arc<Mutex<std::collections::HashSet<String>>> =
        Arc::new(Mutex::new(std::collections::HashSet::new()));
    let webrtc_manager = Arc::new(crate::webrtc_peer::WebRtcPeerManager::new());
    let mut telemetry_ticker = tokio::time::interval(Duration::from_millis(1000));
    let mut telemetry_ticks: u64 = 0;
    let mut event_rx = state.event_tx.subscribe();
    let mut revocation_rx = state.revocation_tx.subscribe();
    let mut revocation_ticker = tokio::time::interval(Duration::from_secs(5));

    let initial_lan_ip = crate::network::discover_local_ipv4().map(|ip| ip.to_string());
    let mut last_detected_lan_ip = initial_lan_ip.clone();
    let meta_msg = serde_json::json!({
        "event": "host_metadata",
        "lan_ip": initial_lan_ip,
        "lan_port": state.listen_port(),
    });
    let _ = out_tx
        .send(Message::Text(meta_msg.to_string().into()))
        .await;

    let mut ping_ticker = tokio::time::interval(Duration::from_secs(15));

    loop {
        tokio::select! {
            _ = ping_ticker.tick() => {
                if state.cloud_account.lock().unwrap_or_else(std::sync::PoisonError::into_inner).is_none() {
                    info!("Cloud account logged out; gracefully disconnecting HostRoom DO.");
                    let _ = out_tx.send(Message::Close(None)).await;
                    break;
                }
                let cloud_allowed = state
                    .storage
                    .get_setting("allow_cloud_operations")
                    .unwrap_or(None)
                    .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
                    .unwrap_or(false);
                if !cloud_allowed {
                    info!("Cloud Relay Sync disabled by desktop policy; gracefully disconnecting HostRoom DO.");
                    let _ = out_tx.send(Message::Close(None)).await;
                    break;
                }
                if out_tx.send(Message::Ping(vec![].into())).await.is_err() {
                    break;
                }
                let current_lan_ip = crate::network::discover_local_ipv4().map(|ip| ip.to_string());
                if current_lan_ip != last_detected_lan_ip {
                    last_detected_lan_ip = current_lan_ip.clone();
                    let meta_update = serde_json::json!({
                        "event": "host_metadata",
                        "lan_ip": current_lan_ip,
                        "lan_port": state.listen_port(),
                    });
                    let _ = out_tx.send(Message::Text(meta_update.to_string().into())).await;
                }
            }

            event_res = event_rx.recv() => {
                if let Ok(event) = event_res
                    && !is_idle.load(Ordering::Relaxed) {
                        let sessions = active_sessions.lock().await;
                        if !sessions.is_empty() {
                            let (stream_kind, msg_type) = match &event {
                                monitor_core::ServerEvent::TelemetryTick(_) => (StreamKind::Telemetry, MessageType::Snapshot),
                                monitor_core::ServerEvent::AlertTriggered { .. } => (StreamKind::Telemetry, MessageType::WatchdogAlert),
                                _ => (StreamKind::Control, MessageType::Snapshot),
                            };
                            for (viewer_session_id, session) in &*sessions {
                                let mut env = EnvelopeV2::new(
                                    Uuid::parse_str(&session.session_id).unwrap_or_else(|_| Uuid::new_v4()),
                                    session.epoch,
                                    stream_kind,
                                    msg_type,
                                    session.send_sequence(),
                                    session.recv_sequence(),
                                    30_000,
                                );
                                env.payload = serde_json::to_value(&event).ok();

                                for part in split_v2_envelope(&env, crate::session_crypto::MAX_PLAINTEXT_FRAME_SIZE) {
                                    if let Ok(part_bytes) = serde_json::to_vec(&part)
                                        && let Ok(ct) = session.encrypt(&part_bytes).await
                                        && queue_relay_frame(&out_tx, viewer_session_id, &ct).await.is_err()
                                    {
                                        debug!("Failed to queue event frame for {viewer_session_id}");
                                    }
                                }
                            }
                        }
                    }
            }

            _ = telemetry_ticker.tick() => {
                telemetry_ticks = telemetry_ticks.wrapping_add(1);
                if !is_idle.load(Ordering::Relaxed) {
                    let sessions = active_sessions.lock().await;
                    let ready = webrtc_ready.lock().await.clone();
                    if !sessions.is_empty() {
                        let snapshot = state
                            .telemetry_bus
                            .latest()
                            .map(|snapshot| (*snapshot).clone())
                            .unwrap_or_else(|| state.telemetry.sample_system_snapshot());
                        for (viewer_session_id, session) in &*sessions {
                            let is_webrtc = ready.contains(viewer_session_id);
                            if is_webrtc && !telemetry_ticks.is_multiple_of(5) {
                                continue;
                            }
                            let mut env = EnvelopeV2::new(
                                Uuid::parse_str(&session.session_id).unwrap_or_else(|_| Uuid::new_v4()),
                                session.epoch,
                                StreamKind::Telemetry,
                                MessageType::Snapshot,
                                session.send_sequence(),
                                session.recv_sequence(),
                                30_000,
                            );
                            env.payload = serde_json::to_value(&snapshot).ok();

                            for part in split_v2_envelope(&env, crate::session_crypto::MAX_PLAINTEXT_FRAME_SIZE) {
                                if let Ok(part_bytes) = serde_json::to_vec(&part)
                                    && let Ok(ct) = session.encrypt(&part_bytes).await
                                    && queue_relay_frame(&out_tx, viewer_session_id, &ct).await.is_err()
                                {
                                    debug!("Failed to queue telemetry frame for {viewer_session_id}");
                                }
                            }
                        }
                    }
                }
            }

            revoked = revocation_rx.recv() => {
                if let Ok(identity) = revoked {
                    terminate_sessions_for_identity(
                        &identity,
                        &active_sessions,
                        &viewer_trust_ids,
                        &webrtc_ready,
                        &webrtc_manager,
                        &is_idle,
                    ).await;
                }
            }

            _ = revocation_ticker.tick() => {
                let trust_ids: Vec<(String, String)> = viewer_trust_ids
                    .lock()
                    .await
                    .iter()
                    .map(|(session_id, trust_id)| (session_id.clone(), trust_id.clone()))
                    .collect();
                for (_session_id, trust_id) in trust_ids {
                    let revoked = state.is_revoked_locally(&trust_id)
                        || state
                            .storage
                            .get_viewer_trust(&trust_id)
                            .ok()
                            .flatten()
                            .map(|trust| trust.revoked_at_utc_ms.is_some())
                            .unwrap_or(true);
                    if revoked {
                        terminate_sessions_for_identity(
                            &trust_id,
                            &active_sessions,
                            &viewer_trust_ids,
                            &webrtc_ready,
                            &webrtc_manager,
                            &is_idle,
                        ).await;
                    }
                }
            }

            msg = ws_stream.next() => {
                match msg {
                    Some(Ok(Message::Text(text))) => {
                        if let Ok(frame) = serde_json::from_str::<RoutedRelayFrame>(&text)
                            && frame.event == "relay_frame"
                        {
                            match BASE64.decode(frame.payload.as_bytes()) {
                                Ok(bytes) if bytes.len() <= crate::session_crypto::MAX_CIPHERTEXT_FRAME_SIZE => {
                                    handle_viewer_frame(
                                        &bytes,
                                        &frame.viewer_session_id,
                                        state,
                                        &active_sessions,
                                        &viewer_trust_ids,
                                        &is_idle,
                                        &out_tx,
                                    ).await;
                                }
                                _ => warn!("Rejected invalid or oversized routed relay frame"),
                            }
                        } else if let Ok(sig) = serde_json::from_str::<crate::webrtc_peer::WebRtcSignalMessage>(&text) {
                            if let Err(e) = webrtc_manager.handle_signal(
                                state,
                                sig,
                                &out_tx,
                                Arc::clone(&active_sessions),
                                Arc::clone(&viewer_trust_ids),
                                Arc::clone(&webrtc_ready),
                            ).await {
                                warn!("WebRTC signaling error: {e}");
                            }
                        } else if let Ok(room_event) = serde_json::from_str::<RoomEvent>(&text) {
                            match room_event.event.as_str() {
                                "all_viewers_disconnected" => {
                                    is_idle.store(true, Ordering::Relaxed);
                                    info!("All viewers disconnected from HostRoom; entered zero-sampling idle mode");
                                }
                                "viewer_connected" => {
                                    is_idle.store(false, Ordering::Relaxed);
                                    info!("Viewer {:?} connected to HostRoom session {:?}; resumed telemetry streaming", room_event.viewer_device_id, room_event.viewer_session_id);
                                }
                                "viewer_disconnected" => {
                                    if let Some(ref session_id) = room_event.viewer_session_id {
                                        webrtc_manager.remove_peer(session_id).await;
                                        let mut sessions = active_sessions.lock().await;
                                        sessions.remove(session_id);
                                        viewer_trust_ids.lock().await.remove(session_id);
                                        webrtc_ready.lock().await.remove(session_id);
                                        if sessions.is_empty() {
                                            is_idle.store(true, Ordering::Relaxed);
                                        }
                                        info!("Viewer session {session_id} disconnected from HostRoom");
                                    }
                                }
                                _ => {}
                            }
                        }
                    }
                    Some(Ok(Message::Binary(_))) => warn!("Ignoring un-routed binary HostRoom frame"),
                    Some(Ok(Message::Ping(p))) => {
                        let _ = out_tx.send(Message::Pong(p)).await;
                    }
                    Some(Ok(Message::Close(_))) | None => {
                        warn!("HostRoom WebSocket closed by remote broker");
                        break;
                    }
                    Some(Err(e)) => {
                        warn!("HostRoom WebSocket stream error: {e}");
                        break;
                    }
                    _ => {}
                }
            }
        }
    }

    writer_handle.abort();
}

/// AUD-01 / NEW-06: tear down every active cloud session belonging to a revoked identity.
pub(super) async fn terminate_sessions_for_identity(
    identity: &str,
    active_sessions: &Arc<Mutex<HashMap<String, Arc<SecureSession>>>>,
    viewer_trust_ids: &Arc<Mutex<HashMap<String, String>>>,
    webrtc_ready: &Arc<Mutex<std::collections::HashSet<String>>>,
    webrtc_manager: &crate::webrtc_peer::WebRtcPeerManager,
    is_idle: &AtomicBool,
) {
    let doomed: Vec<String> = viewer_trust_ids
        .lock()
        .await
        .iter()
        .filter(|(_, trust_id)| trust_id.as_str() == identity)
        .map(|(session_id, _)| session_id.clone())
        .collect();

    for session_id in doomed {
        webrtc_manager.remove_peer(&session_id).await;
        active_sessions.lock().await.remove(&session_id);
        viewer_trust_ids.lock().await.remove(&session_id);
        webrtc_ready.lock().await.remove(&session_id);
        info!("Terminated revoked HostRoom viewer session {session_id}");
    }

    if active_sessions.lock().await.is_empty() {
        is_idle.store(true, Ordering::Relaxed);
    }
}
