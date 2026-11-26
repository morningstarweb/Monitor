use super::handshake_tests::create_test_state;
use super::webrtc_setup::{ViewerPeerHandler, spawn_dc_event_loop, spawn_host_signal_pump};
use monitor_core::MessageType;
use monitor_transport::connection_controller::{ConnectionState, TransportPath};
use monitor_transport::host_control::CLOUD_V2_PROLOGUE;
use monitor_transport::session_crypto::{
    NoiseKeypair, ViewerHandshakeInitiator, get_or_create_host_noise_keypair, hex_encode,
};
use monitor_transport::webrtc_peer::{WebRtcPeerManager, WebRtcSignalMessage};
use std::collections::{HashMap, HashSet};
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::{Mutex, mpsc};
use tokio_tungstenite::tungstenite::Message;
use uuid::Uuid;
use webrtc::peer_connection::{
    PeerConnection, PeerConnectionBuilder, PeerConnectionEventHandler, RTCConfigurationBuilder,
};

#[tokio::test]
async fn webrtc_peer_real_data_channel_e2ee_and_ice_restart() {
    let state = create_test_state();
    {
        let mut controller = state.connection_controller.lock().unwrap();
        assert!(controller.begin_discovery());
        let generation = controller.generation();
        let _ = controller.begin_authentication(generation);
        let _ = controller.begin_race(
            generation,
            &[TransportPath::CloudRelay, TransportPath::WebRtc],
        );
    }
    state
        .telemetry_bus
        .publish(state.telemetry.sample_system_snapshot());

    let host_keys = get_or_create_host_noise_keypair(&state.storage).unwrap();
    let viewer_keys = NoiseKeypair::generate().unwrap();
    let viewer_id = hex_encode(&viewer_keys.public_key);
    state
        .storage
        .enroll_viewer(&viewer_id, "WebRTC Viewer", &viewer_id)
        .unwrap();
    state.storage.approve_viewer_tier(&viewer_id, "T2").unwrap();

    let host_send_dc = [0x42u8; 32];
    let host_recv_dc = [0x99u8; 32];
    let mut initiator =
        ViewerHandshakeInitiator::new(&viewer_keys, &host_keys.public_key, CLOUD_V2_PROLOGUE)
            .unwrap();
    let msg1 = initiator.write_handshake_msg1(b"{}").unwrap();
    let mut responder =
        monitor_transport::HostHandshakeResponder::new(&host_keys, CLOUD_V2_PROLOGUE).unwrap();
    responder.read_handshake_msg1(&msg1).unwrap();
    let sid = Uuid::new_v4().to_string();
    let (msg2, host_session) = responder
        .write_handshake_msg2_with_data_channel_keys(
            b"{\"status\":\"authenticated\"}",
            sid.clone(),
            1,
            host_send_dc,
            host_recv_dc,
        )
        .unwrap();
    let (_, viewer_session) = initiator
        .read_handshake_msg2(&msg2, sid.clone(), 1)
        .unwrap();
    let host_session = Arc::new(host_session);
    let viewer_session =
        Arc::new(viewer_session.with_data_channel_keys(host_recv_dc, host_send_dc));

    let active_sessions = Arc::new(Mutex::new(HashMap::from([(sid.clone(), host_session)])));
    let viewer_trust_ids = Arc::new(Mutex::new(HashMap::from([(sid.clone(), viewer_id)])));
    let webrtc_ready = Arc::new(Mutex::new(HashSet::new()));
    let manager = Arc::new(WebRtcPeerManager::new());

    let (host_out_tx, host_out_rx) = mpsc::channel::<Message>(64);
    let (viewer_sig_tx, mut viewer_sig_rx) = mpsc::unbounded_channel::<WebRtcSignalMessage>();
    let (pong_tx, pong_rx) = tokio::sync::oneshot::channel();
    let (tele_tx, tele_rx) = tokio::sync::oneshot::channel();

    let viewer_handler = Arc::new(ViewerPeerHandler {
        viewer_session_id: sid.clone(),
        signal_tx: viewer_sig_tx,
        session: Arc::clone(&viewer_session),
        pong_tx: Arc::new(Mutex::new(Some(pong_tx))),
        telemetry_tx: Arc::new(Mutex::new(Some(tele_tx))),
    });
    let viewer_pc = PeerConnectionBuilder::new()
        .with_configuration(RTCConfigurationBuilder::default().build())
        .with_handler(Arc::clone(&viewer_handler) as Arc<dyn PeerConnectionEventHandler>)
        .with_udp_addrs(vec!["0.0.0.0:0"])
        .build()
        .await
        .unwrap();
    let dc = viewer_pc
        .create_data_channel("monitor-telemetry", None)
        .await
        .unwrap();
    let offer = viewer_pc.create_offer(None).await.unwrap();
    viewer_pc
        .set_local_description(offer.clone())
        .await
        .unwrap();
    let offer_sdp = serde_json::to_value(&offer).unwrap()["sdp"]
        .as_str()
        .unwrap()
        .to_string();

    manager
        .handle_signal(
            &state,
            WebRtcSignalMessage {
                signal: "offer".to_string(),
                viewer_session_id: Some(sid.clone()),
                sdp: Some(offer_sdp),
                sdp_type: Some("offer".to_string()),
                candidate: None,
            },
            &host_out_tx,
            Arc::clone(&active_sessions),
            Arc::clone(&viewer_trust_ids),
            Arc::clone(&webrtc_ready),
        )
        .await
        .unwrap();

    let manager_bg = Arc::clone(&manager);
    let state_bg = state.clone();
    let out_tx_bg = host_out_tx.clone();
    let act_bg = Arc::clone(&active_sessions);
    let trust_bg = Arc::clone(&viewer_trust_ids);
    let ready_bg = Arc::clone(&webrtc_ready);
    let viewer_pump = tokio::spawn(async move {
        while let Some(sig) = viewer_sig_rx.recv().await {
            let _ = manager_bg
                .handle_signal(
                    &state_bg,
                    sig,
                    &out_tx_bg,
                    Arc::clone(&act_bg),
                    Arc::clone(&trust_bg),
                    Arc::clone(&ready_bg),
                )
                .await;
        }
    });

    let (restarted_tx, restarted_rx) = tokio::sync::oneshot::channel::<()>();
    let restarted_slot = Arc::new(Mutex::new(Some(restarted_tx)));
    let viewer_pc_bg = Arc::new(viewer_pc);
    let host_pump = spawn_host_signal_pump(host_out_rx, Arc::clone(&viewer_pc_bg), restarted_slot);
    let dc_task = spawn_dc_event_loop(dc, sid.clone(), viewer_handler);

    let tele_env = tokio::time::timeout(Duration::from_secs(10), tele_rx)
        .await
        .expect("WebRTC DataChannel snapshot timeout")
        .unwrap();
    assert_eq!(tele_env.message_type, MessageType::Snapshot);
    let pong_env = tokio::time::timeout(Duration::from_secs(10), pong_rx)
        .await
        .expect("WebRTC DataChannel Pong timeout")
        .unwrap();
    assert_eq!(pong_env.message_type, MessageType::Pong);
    assert!(matches!(
        state.connection_controller.lock().unwrap().state(),
        ConnectionState::Connected {
            path: TransportPath::WebRtc,
            ..
        }
    ));

    // Exercise live ICE restart signal on the active peer
    manager
        .handle_signal(
            &state,
            WebRtcSignalMessage {
                signal: "restart".to_string(),
                viewer_session_id: Some(sid.clone()),
                sdp: None,
                sdp_type: None,
                candidate: None,
            },
            &host_out_tx,
            Arc::clone(&active_sessions),
            Arc::clone(&viewer_trust_ids),
            Arc::clone(&webrtc_ready),
        )
        .await
        .unwrap();
    tokio::time::timeout(Duration::from_secs(5), restarted_rx)
        .await
        .expect("ICE restart acknowledgement timeout")
        .unwrap();

    manager.remove_peer(&sid).await;
    let _ = viewer_pc_bg.close().await;
    dc_task.abort();
    viewer_pump.abort();
    host_pump.abort();
}
