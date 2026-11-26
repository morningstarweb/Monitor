use super::peer_manager::{HostPeerHandler, WebRtcSignalMessage};
use async_trait::async_trait;
use bytes::BytesMut;
use monitor_core::{EnvelopeV2, MessageType, StreamKind};
use std::sync::Arc;
use std::sync::atomic::Ordering;
use std::time::Duration;
use tokio_tungstenite::tungstenite::Message;
use tracing::{debug, info, warn};
use uuid::Uuid;
use webrtc::data_channel::{DataChannel, DataChannelEvent};
use webrtc::peer_connection::{
    PeerConnectionEventHandler, RTCPeerConnectionIceEvent, RTCPeerConnectionState,
};

#[async_trait]
impl PeerConnectionEventHandler for HostPeerHandler {
    async fn on_ice_candidate(&self, event: RTCPeerConnectionIceEvent) {
        if let Ok(cand_init) = event.candidate.to_json()
            && !cand_init.candidate.is_empty()
        {
            let sig = WebRtcSignalMessage {
                signal: "candidate".to_string(),
                viewer_session_id: Some(self.viewer_session_id.clone()),
                sdp: None,
                sdp_type: None,
                candidate: Some(cand_init),
            };
            if let Ok(json_str) = serde_json::to_string(&sig) {
                let _ = self
                    .outbound_signal_tx
                    .send(Message::Text(json_str.into()))
                    .await;
            }
        }
    }

    async fn on_connection_state_change(&self, state: RTCPeerConnectionState) {
        info!(
            "WebRTC P2P peer connection state change for {}: {}",
            self.viewer_session_id, state
        );
        if matches!(
            state,
            RTCPeerConnectionState::Failed
                | RTCPeerConnectionState::Closed
                | RTCPeerConnectionState::Disconnected
        ) {
            self.webrtc_ready
                .lock()
                .await
                .remove(&self.viewer_session_id);
        }
    }

    async fn on_data_channel(&self, dc: Arc<dyn DataChannel>) {
        let label = dc.label().await.unwrap_or_default();
        info!(
            "WebRTC DataChannel opened: '{label}' for viewer {}",
            self.viewer_session_id
        );

        let state = self.state.clone();
        let viewer_session_id = self.viewer_session_id.clone();
        let active_sessions = Arc::clone(&self.active_sessions);
        let viewer_trust_ids = Arc::clone(&self.viewer_trust_ids);
        let webrtc_ready = Arc::clone(&self.webrtc_ready);
        let data_channel_sequence = Arc::clone(&self.data_channel_sequence);
        let dc_clone = Arc::clone(&dc);

        tokio::spawn(async move {
            let mut ticker = tokio::time::interval(Duration::from_millis(1000));
            let mut event_rx = state.event_tx.subscribe();
            let mut is_open = false;

            loop {
                tokio::select! {
                    _ = ticker.tick() => {
                        if is_open
                            && let Some(session) = active_sessions.lock().await.get(&viewer_session_id).cloned()
                        {
                            let mut envelope = EnvelopeV2::new(
                                Uuid::parse_str(&session.session_id).unwrap_or_else(|_| Uuid::new_v4()),
                                session.epoch,
                                StreamKind::Telemetry,
                                MessageType::Snapshot,
                                data_channel_sequence.load(Ordering::Relaxed),
                                session.recv_sequence(),
                                30_000,
                            );
                            envelope.payload = serde_json::to_value(
                                state
                                    .telemetry_bus
                                    .latest()
                                    .map(|s| (*s).clone())
                                    .unwrap_or_else(|| state.telemetry.sample_system_snapshot()),
                            )
                            .ok();
                            let parts = crate::host_control::split_v2_envelope(
                                &envelope,
                                crate::session_crypto::MAX_PLAINTEXT_FRAME_SIZE,
                            );
                            for mut part in parts {
                                part.seq = data_channel_sequence.fetch_add(1, Ordering::SeqCst);
                                if let Ok(plaintext) = serde_json::to_vec(&part)
                                    && let Ok(ciphertext) = session.encrypt_data_channel(&plaintext)
                                    && let Err(error) = dc_clone.send(BytesMut::from(ciphertext.as_slice())).await
                                {
                                    debug!("DataChannel telemetry send failed for {viewer_session_id}: {error}");
                                    break;
                                }
                            }
                        }
                    }
                    event_res = event_rx.recv() => {
                        if is_open
                            && let Ok(event) = event_res
                            && let Some(session) = active_sessions.lock().await.get(&viewer_session_id).cloned()
                        {
                            let (stream_kind, msg_type) = match &event {
                                monitor_core::ServerEvent::TelemetryTick(_) => (StreamKind::Telemetry, MessageType::Snapshot),
                                monitor_core::ServerEvent::AlertTriggered { .. } => (StreamKind::Telemetry, MessageType::WatchdogAlert),
                                _ => (StreamKind::Control, MessageType::Snapshot),
                            };
                            let mut envelope = EnvelopeV2::new(
                                Uuid::parse_str(&session.session_id).unwrap_or_else(|_| Uuid::new_v4()),
                                session.epoch,
                                stream_kind,
                                msg_type,
                                data_channel_sequence.load(Ordering::Relaxed),
                                session.recv_sequence(),
                                30_000,
                            );
                            envelope.payload = serde_json::to_value(&event).ok();
                            let parts = crate::host_control::split_v2_envelope(
                                &envelope,
                                crate::session_crypto::MAX_PLAINTEXT_FRAME_SIZE,
                            );
                            for mut part in parts {
                                part.seq = data_channel_sequence.fetch_add(1, Ordering::SeqCst);
                                if let Ok(plaintext) = serde_json::to_vec(&part)
                                    && let Ok(ciphertext) = session.encrypt_data_channel(&plaintext)
                                    && let Err(error) = dc_clone.send(BytesMut::from(ciphertext.as_slice())).await
                                {
                                    debug!("DataChannel event send failed for {viewer_session_id}: {error}");
                                    break;
                                }
                            }
                        }
                    }
                    event_opt = dc_clone.poll() => {
                        match event_opt {
                            Some(DataChannelEvent::OnOpen) => {
                                is_open = true;
                                webrtc_ready.lock().await.insert(viewer_session_id.clone());
                                if let Ok(mut controller) = state.connection_controller.lock() {
                                    let generation = controller.generation();
                                    if generation > 0 {
                                        let _ = controller.path_ready(
                                            generation,
                                            crate::connection_controller::TransportPath::WebRtc,
                                        );
                                    }
                                }
                                info!("DataChannel '{label}' is OPEN for viewer {viewer_session_id} (Ultra-low latency P2P active)");
                            }
                            Some(DataChannelEvent::OnMessage(msg)) => {
                                let session = active_sessions.lock().await.get(&viewer_session_id).cloned();
                                let trust_id = viewer_trust_ids.lock().await.get(&viewer_session_id).cloned();
                                let (Some(session), Some(trust_id)) = (session, trust_id) else {
                                    warn!("Rejected DataChannel frame without an authenticated Noise session");
                                    continue;
                                };
                                let Ok(plaintext) = session.decrypt_data_channel(&msg.data) else {
                                    warn!("Rejected DataChannel frame with an invalid application authentication tag");
                                    break;
                                };
                                let Ok(envelope) = serde_json::from_slice::<EnvelopeV2>(&plaintext) else {
                                    warn!("Rejected malformed EnvelopeV2 on DataChannel");
                                    continue;
                                };
                                if let Some(response) = crate::host_control::process_v2_envelope(
                                    &envelope,
                                    &session,
                                    &state,
                                    &trust_id,
                                    false,
                                ).await {
                                    let parts = crate::host_control::split_v2_envelope(
                                        &response,
                                        crate::session_crypto::MAX_PLAINTEXT_FRAME_SIZE,
                                    );
                                    for mut part in parts {
                                        part.seq = data_channel_sequence.fetch_add(1, Ordering::SeqCst);
                                        let Ok(plaintext) = serde_json::to_vec(&part) else { continue };
                                        let Ok(ciphertext) = session.encrypt_data_channel(&plaintext) else { continue };
                                        if let Err(error) = dc_clone.send(BytesMut::from(ciphertext.as_slice())).await {
                                            warn!("DataChannel command reply failed for {viewer_session_id}: {error}");
                                            break;
                                        }
                                    }
                                }
                            }
                            Some(DataChannelEvent::OnClose) | None => {
                                webrtc_ready.lock().await.remove(&viewer_session_id);
                                info!("DataChannel '{label}' closed for viewer {viewer_session_id}");
                                break;
                            }
                            _ => {}
                        }
                    }
                }
            }
            webrtc_ready.lock().await.remove(&viewer_session_id);
        });
    }
}
