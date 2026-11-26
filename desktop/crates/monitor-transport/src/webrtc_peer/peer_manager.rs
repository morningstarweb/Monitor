use crate::session_crypto::SecureSession;
use crate::state::AppState;
use anyhow::Result;
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet};
use std::sync::Arc;
use std::sync::atomic::AtomicU64;
use std::time::Duration;
use tokio::sync::{Mutex, mpsc};
use tokio_tungstenite::tungstenite::Message;
use tracing::{debug, error, info, warn};
use webrtc::peer_connection::{
    PeerConnection, PeerConnectionBuilder, RTCConfigurationBuilder, RTCIceCandidateInit,
    RTCIceServer, RTCSessionDescription,
};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WebRtcSignalMessage {
    pub signal: String,
    #[serde(default)]
    #[serde(alias = "viewer_id")]
    pub viewer_session_id: Option<String>,
    #[serde(default)]
    pub sdp: Option<String>,
    #[serde(default, rename = "type")]
    pub sdp_type: Option<String>,
    #[serde(default)]
    pub candidate: Option<RTCIceCandidateInit>,
}

#[derive(Deserialize)]
struct TurnCredentialsResponse {
    #[serde(default)]
    success: bool,
    #[serde(default)]
    ice_servers: Vec<TurnIceServer>,
}

#[derive(Deserialize)]
struct TurnIceServer {
    urls: Vec<String>,
    #[serde(default)]
    username: String,
    #[serde(default)]
    credential: String,
}

#[derive(Clone, Debug)]
pub struct HostPeerHandler {
    pub outbound_signal_tx: mpsc::Sender<Message>,
    pub viewer_session_id: String,
    pub state: AppState,
    pub active_sessions: Arc<Mutex<HashMap<String, Arc<SecureSession>>>>,
    pub viewer_trust_ids: Arc<Mutex<HashMap<String, String>>>,
    pub webrtc_ready: Arc<Mutex<HashSet<String>>>,
    pub data_channel_sequence: Arc<AtomicU64>,
}

#[derive(Clone, Default)]
pub struct WebRtcPeerManager {
    peers: Arc<Mutex<HashMap<String, Arc<dyn PeerConnection>>>>,
    pending_candidates: Arc<Mutex<HashMap<String, Vec<RTCIceCandidateInit>>>>,
}

impl std::fmt::Debug for WebRtcPeerManager {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("WebRtcPeerManager").finish()
    }
}

impl WebRtcPeerManager {
    pub fn new() -> Self {
        Self {
            peers: Arc::new(Mutex::new(HashMap::new())),
            pending_candidates: Arc::new(Mutex::new(HashMap::new())),
        }
    }

    pub async fn handle_signal(
        &self,
        state: &AppState,
        signal: WebRtcSignalMessage,
        out_tx: &mpsc::Sender<Message>,
        active_sessions: Arc<Mutex<HashMap<String, Arc<SecureSession>>>>,
        viewer_trust_ids: Arc<Mutex<HashMap<String, String>>>,
        webrtc_ready: Arc<Mutex<HashSet<String>>>,
    ) -> Result<()> {
        let Some(viewer_session_id) = signal.viewer_session_id.clone() else {
            warn!("Dropping WebRTC signal without a viewer_session_id");
            return Ok(());
        };
        if !active_sessions
            .lock()
            .await
            .contains_key(&viewer_session_id)
            || !viewer_trust_ids
                .lock()
                .await
                .contains_key(&viewer_session_id)
        {
            warn!("Ignoring WebRTC signal before the viewer completed Noise authentication");
            return Ok(());
        }

        match signal.signal.as_str() {
            "offer" => {
                let Some(sdp_str) = signal.sdp else {
                    warn!("Received WebRTC offer without SDP from {viewer_session_id}");
                    return Ok(());
                };

                info!(
                    "Processing WebRTC offer from viewer {viewer_session_id} (P2P negotiation initiated)"
                );

                let config = RTCConfigurationBuilder::default()
                    .with_ice_servers(fetch_ice_servers(state).await)
                    .build();

                let handler = Arc::new(HostPeerHandler {
                    outbound_signal_tx: out_tx.clone(),
                    viewer_session_id: viewer_session_id.clone(),
                    state: state.clone(),
                    active_sessions: Arc::clone(&active_sessions),
                    viewer_trust_ids: Arc::clone(&viewer_trust_ids),
                    webrtc_ready: Arc::clone(&webrtc_ready),
                    data_channel_sequence: Arc::new(AtomicU64::new(0)),
                });

                let pc = match PeerConnectionBuilder::new()
                    .with_configuration(config)
                    .with_handler(handler)
                    .with_data_channel_send_buffer_limit(512 * 1024)
                    .with_udp_addrs(vec!["0.0.0.0:0"])
                    .build()
                    .await
                {
                    Ok(p) => p,
                    Err(e) => {
                        error!("Failed to build PeerConnection for {viewer_session_id}: {e}");
                        return Err(e.into());
                    }
                };

                let offer_json = serde_json::json!({
                    "type": "offer",
                    "sdp": sdp_str
                });
                let offer_sdp: RTCSessionDescription = serde_json::from_value(offer_json)?;

                pc.set_remote_description(offer_sdp).await?;
                let pending = self
                    .pending_candidates
                    .lock()
                    .await
                    .remove(&viewer_session_id)
                    .unwrap_or_default();
                for candidate in pending {
                    if let Err(error) = pc.add_ice_candidate(candidate).await {
                        warn!(
                            "Failed to apply an early ICE candidate for {viewer_session_id}: {error}"
                        );
                    }
                }
                let answer = pc.create_answer(None).await?;
                pc.set_local_description(answer.clone()).await?;

                let answer_val = serde_json::to_value(&answer)?;
                let answer_sdp_str = answer_val
                    .get("sdp")
                    .and_then(|v| v.as_str())
                    .unwrap_or_default()
                    .to_string();

                let answer_sig = WebRtcSignalMessage {
                    signal: "answer".to_string(),
                    viewer_session_id: Some(viewer_session_id.clone()),
                    sdp: Some(answer_sdp_str),
                    sdp_type: Some("answer".to_string()),
                    candidate: None,
                };

                if let Ok(sig_text) = serde_json::to_string(&answer_sig) {
                    let _ = out_tx.send(Message::Text(sig_text.into())).await;
                    info!(
                        "Dispatched WebRTC answer to viewer {viewer_session_id} via Cloudflare rendezvous"
                    );
                }

                let mut peers = self.peers.lock().await;
                if let Some(old) = peers.insert(viewer_session_id.clone(), Arc::new(pc)) {
                    let _ = old.close().await;
                }
            }

            "restart" => {
                let peers = self.peers.lock().await;
                match peers.get(&viewer_session_id) {
                    Some(pc) => match pc.restart_ice().await {
                        Ok(()) => {
                            info!("ICE restart requested by viewer {viewer_session_id}");
                            let restarted = WebRtcSignalMessage {
                                signal: "restarted".to_string(),
                                viewer_session_id: Some(viewer_session_id.clone()),
                                sdp: None,
                                sdp_type: None,
                                candidate: None,
                            };
                            if let Ok(text) = serde_json::to_string(&restarted) {
                                let _ = out_tx.send(Message::Text(text.into())).await;
                            }
                        }
                        Err(e) => warn!("ICE restart failed for {viewer_session_id}: {e}"),
                    },
                    None => warn!("ICE restart for unknown viewer {viewer_session_id}"),
                }
            }

            "candidate" => {
                if let Some(candidate) = signal.candidate {
                    let peers = self.peers.lock().await;
                    if let Some(pc) = peers.get(&viewer_session_id) {
                        debug!("Applying remote ICE candidate from viewer {viewer_session_id}");
                        if let Err(e) = pc.add_ice_candidate(candidate).await {
                            warn!("Failed to add ICE candidate for {viewer_session_id}: {e}");
                        }
                    } else {
                        drop(peers);
                        let mut pending = self.pending_candidates.lock().await;
                        let candidates = pending.entry(viewer_session_id.clone()).or_default();
                        if candidates.len() < 128 {
                            candidates.push(candidate);
                        } else {
                            warn!("Dropping excess early ICE candidates for {viewer_session_id}");
                        }
                    }
                }
            }

            _ => {
                debug!("Unhandled WebRTC signal: {}", signal.signal);
            }
        }

        Ok(())
    }

    pub async fn remove_peer(&self, viewer_id: &str) {
        let mut peers = self.peers.lock().await;
        self.pending_candidates.lock().await.remove(viewer_id);
        if let Some(pc) = peers.remove(viewer_id) {
            let _ = pc.close().await;
            info!("Cleaned up WebRTC PeerConnection for viewer {viewer_id}");
        }
    }
}

async fn fetch_ice_servers(state: &AppState) -> Vec<RTCIceServer> {
    let mut servers = vec![RTCIceServer {
        urls: vec![
            "stun:stun.cloudflare.com:3478".to_owned(),
            "stun:stun.l.google.com:19302".to_owned(),
        ],
        ..Default::default()
    }];
    let token = state
        .cloud_account
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .as_ref()
        .map(|account| account.token.clone());
    let Some(token) = token else { return servers };
    let Ok(client) = reqwest::Client::builder()
        .timeout(Duration::from_secs(8))
        .build()
    else {
        return servers;
    };
    let endpoint = format!(
        "{}/api/v2/webrtc/turn-credentials",
        crate::cloud::broker_base_url()
    );
    let response = match client.post(endpoint).bearer_auth(token).send().await {
        Ok(response) if response.status().is_success() => response,
        Ok(res) => {
            warn!(
                "Broker TURN credential request returned HTTP status: {}; falling back to STUN-only mode",
                res.status()
            );
            return servers;
        }
        Err(e) => {
            warn!(
                "Failed to query TURN credentials from broker ({e}); falling back to STUN-only mode"
            );
            return servers;
        }
    };
    let Ok(credentials) = response.json::<TurnCredentialsResponse>().await else {
        warn!("Failed to parse TURN credentials response; using STUN-only mode");
        return servers;
    };
    if !credentials.success {
        warn!("Broker indicated TURN credentials unavailable; using STUN-only mode");
        return servers;
    }
    let turn_count = credentials.ice_servers.len();
    servers.extend(credentials.ice_servers.into_iter().filter_map(|server| {
        if server.urls.is_empty() || server.username.is_empty() || server.credential.is_empty() {
            return None;
        }
        Some(RTCIceServer {
            urls: server.urls,
            username: server.username,
            credential: server.credential,
        })
    }));
    info!(
        "Configured WebRTC ICE with {} STUN/TURN servers ({turn_count} TURN relayed)",
        servers.len()
    );
    servers
}
