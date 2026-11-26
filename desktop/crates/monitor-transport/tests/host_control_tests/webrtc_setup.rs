use monitor_core::{EnvelopeV2, MessageType, StreamKind};
use monitor_transport::session_crypto::SecureSession;
use monitor_transport::webrtc_peer::WebRtcSignalMessage;
use std::sync::Arc;
use tokio::sync::{Mutex, mpsc};
use tokio_tungstenite::tungstenite::Message;
use uuid::Uuid;
use webrtc::data_channel::{DataChannel, DataChannelEvent};
use webrtc::peer_connection::{
    PeerConnection, PeerConnectionEventHandler, RTCPeerConnectionIceEvent, RTCPeerConnectionState,
    RTCSessionDescription,
};

#[derive(Clone)]
pub(super) struct ViewerPeerHandler {
    pub viewer_session_id: String,
    pub signal_tx: mpsc::UnboundedSender<WebRtcSignalMessage>,
    pub session: Arc<SecureSession>,
    pub pong_tx: Arc<Mutex<Option<tokio::sync::oneshot::Sender<EnvelopeV2>>>>,
    pub telemetry_tx: Arc<Mutex<Option<tokio::sync::oneshot::Sender<EnvelopeV2>>>>,
}

#[async_trait::async_trait]
impl PeerConnectionEventHandler for ViewerPeerHandler {
    async fn on_ice_candidate(&self, event: RTCPeerConnectionIceEvent) {
        if let Ok(cand) = event.candidate.to_json()
            && !cand.candidate.is_empty()
        {
            let _ = self.signal_tx.send(WebRtcSignalMessage {
                signal: "candidate".to_string(),
                viewer_session_id: Some(self.viewer_session_id.clone()),
                sdp: None,
                sdp_type: None,
                candidate: Some(cand),
            });
        }
    }

    async fn on_connection_state_change(&self, _state: RTCPeerConnectionState) {}

    async fn on_data_channel(&self, _dc: Arc<dyn DataChannel>) {}
}

pub(super) fn spawn_host_signal_pump<P: PeerConnection + 'static>(
    mut host_out_rx: mpsc::Receiver<Message>,
    viewer_pc_sig: Arc<P>,
    restarted_slot: Arc<Mutex<Option<tokio::sync::oneshot::Sender<()>>>>,
) -> tokio::task::JoinHandle<()> {
    tokio::spawn(async move {
        while let Some(msg) = host_out_rx.recv().await {
            if let Message::Text(txt) = msg
                && let Ok(sig) = serde_json::from_str::<WebRtcSignalMessage>(&txt)
            {
                match sig.signal.as_str() {
                    "answer" => {
                        if let Some(sdp) = sig.sdp {
                            let val = serde_json::json!({ "type": "answer", "sdp": sdp });
                            let ans: RTCSessionDescription = serde_json::from_value(val).unwrap();
                            let _ = viewer_pc_sig.set_remote_description(ans).await;
                        }
                    }
                    "candidate" => {
                        if let Some(cand) = sig.candidate {
                            let _: Result<(), _> = viewer_pc_sig.add_ice_candidate(cand).await;
                        }
                    }
                    "restarted" => {
                        if let Some(tx) = restarted_slot.lock().await.take() {
                            let _ = tx.send(());
                        }
                    }
                    _ => {}
                }
            }
        }
    })
}

pub(super) fn spawn_dc_event_loop(
    dc: Arc<dyn DataChannel>,
    dc_sid: String,
    viewer_handler: Arc<ViewerPeerHandler>,
) -> tokio::task::JoinHandle<()> {
    tokio::spawn(async move {
        while let Some(ev) = dc.poll().await {
            match ev {
                DataChannelEvent::OnOpen => {
                    let ping = EnvelopeV2::new(
                        Uuid::parse_str(&dc_sid).unwrap_or_else(|_| Uuid::new_v4()),
                        1,
                        StreamKind::Control,
                        MessageType::Ping,
                        0,
                        0,
                        30_000,
                    );
                    let pt = serde_json::to_vec(&ping).unwrap();
                    let ct = viewer_handler.session.encrypt_data_channel(&pt).unwrap();
                    let _ = dc.send(bytes::BytesMut::from(ct.as_slice())).await;
                }
                DataChannelEvent::OnMessage(msg) => {
                    if let Ok(pt) = viewer_handler.session.decrypt_data_channel(&msg.data)
                        && let Ok(env) = serde_json::from_slice::<EnvelopeV2>(&pt)
                    {
                        if env.message_type == MessageType::Pong {
                            if let Some(tx) = viewer_handler.pong_tx.lock().await.take() {
                                let _ = tx.send(env);
                            }
                        } else if env.message_type == MessageType::Snapshot
                            && let Some(tx) = viewer_handler.telemetry_tx.lock().await.take()
                        {
                            let _ = tx.send(env);
                        }
                    }
                }
                DataChannelEvent::OnClose => break,
                _ => {}
            }
        }
    })
}
