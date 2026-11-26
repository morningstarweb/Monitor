use super::helpers::{create_test_state, spawn_v2_ws_server};
use futures_util::{SinkExt, StreamExt};
use monitor_core::{EnvelopeV2, MessageType, StreamKind};
use monitor_transport::connection_controller::{ConnectionState, Transition, TransportPath};
use monitor_transport::lan_v2::LAN_V2_PROLOGUE;
use monitor_transport::session_crypto::{
    NoiseKeypair, ViewerHandshakeInitiator, get_or_create_host_noise_keypair, hex_encode,
};
use tokio_tungstenite::tungstenite::Message;
use uuid::Uuid;

/// NET-01/NET-02 integration: a real LAN v2 session must update the shared
/// connection state machine, and a revoked identity must drive it to a terminal
/// state so no recovery timer can resume it.
#[tokio::test]
async fn test_lan_session_drives_connection_controller() {
    let state = create_test_state();
    let generation = {
        let mut controller = state
            .connection_controller
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        assert!(controller.begin_discovery());
        let generation_id = controller.generation();
        assert_eq!(
            controller.begin_authentication(generation_id),
            Transition::Accepted
        );
        assert_eq!(
            controller.begin_race(generation_id, &[TransportPath::Lan]),
            Transition::Accepted
        );
        generation_id
    };

    let host_keys = get_or_create_host_noise_keypair(&state.storage).expect("host keys");
    let ws_url = spawn_v2_ws_server(state.clone()).await;

    let (mut ws_stream, _) = tokio_tungstenite::connect_async(&ws_url)
        .await
        .expect("connect to v2 stream");
    let viewer_keys = NoiseKeypair::generate().expect("viewer keys");
    let viewer_device_id = hex_encode(&viewer_keys.public_key);
    state
        .storage
        .enroll_viewer(&viewer_device_id, "controller_viewer", &viewer_device_id)
        .expect("enroll viewer");
    state
        .storage
        .approve_viewer_tier(&viewer_device_id, "T2")
        .expect("approve tier");

    let mut initiator =
        ViewerHandshakeInitiator::new(&viewer_keys, &host_keys.public_key, LAN_V2_PROLOGUE)
            .expect("initiator setup");
    let msg1 = initiator
        .write_handshake_msg1(b"{\"platform\":\"android\"}")
        .expect("write msg1");
    ws_stream
        .send(Message::Binary(msg1.into()))
        .await
        .expect("send msg1");
    let msg2 = ws_stream.next().await.expect("msg2").expect("msg2 ok");
    assert!(
        matches!(msg2, Message::Binary(_)),
        "authenticated handshake must produce binary msg2"
    );

    let connected = {
        let controller = state
            .connection_controller
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        controller.state().clone()
    };
    assert_eq!(
        connected,
        ConnectionState::Connected {
            generation,
            path: TransportPath::Lan,
        }
    );

    let mut rev_rx = state.revocation_tx.subscribe();
    state.notify_revoked(&viewer_device_id);
    assert_eq!(rev_rx.try_recv().unwrap(), viewer_device_id);
    // AUD-007: revoking ONE viewer must NOT kill the host controller for other viewers.
    let still_connected = {
        let controller = state
            .connection_controller
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        controller.state().clone()
    };
    assert_eq!(
        still_connected,
        ConnectionState::Connected {
            generation,
            path: TransportPath::Lan,
        }
    );

    // Explicit host shutdown (e.g. account sign-out) transitions controller to terminal Revoked.
    state.shutdown_controller();
    let revoked = {
        let controller = state
            .connection_controller
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        controller.state().clone()
    };
    assert_eq!(revoked, ConnectionState::Revoked);
}

/// AUD-004 regression: an unknown Noise public key arriving over LAN MUST NOT
/// be auto-enrolled at T2 Operator. It is capped at T0 Viewer and rejected from
/// mutations.
#[tokio::test]
async fn test_unknown_lan_v2_peer_capped_at_t0_and_blocked_from_mutations() {
    let state = create_test_state();
    state
        .storage
        .set_setting("allow_git_operations", "1")
        .unwrap();
    state
        .storage
        .set_setting("allow_remote_terminal", "1")
        .unwrap();
    let host_keys = get_or_create_host_noise_keypair(&state.storage).expect("host keys");
    let ws_url = spawn_v2_ws_server(state.clone()).await;

    let (mut ws_stream, _) = tokio_tungstenite::connect_async(&ws_url)
        .await
        .expect("connect to v2 stream");

    let rogue_keys = NoiseKeypair::generate().expect("rogue keys");
    let rogue_id = hex_encode(&rogue_keys.public_key);
    let mut initiator =
        ViewerHandshakeInitiator::new(&rogue_keys, &host_keys.public_key, LAN_V2_PROLOGUE)
            .expect("initiator setup");
    let msg1 = initiator
        .write_handshake_msg1(b"{\"device_name\":\"Rogue LAN Peer\"}")
        .expect("msg1");
    ws_stream
        .send(Message::Binary(msg1.into()))
        .await
        .expect("send msg1");

    let msg2_bytes = match ws_stream.next().await.expect("msg2").expect("msg2 ok") {
        Message::Binary(b) => b.to_vec(),
        other => panic!("unexpected frame: {other:?}"),
    };
    let (server_hello, viewer_session) = initiator
        .read_handshake_msg2(&msg2_bytes, Uuid::new_v4().to_string(), 1)
        .expect("msg2 parse");
    let hello: serde_json::Value = serde_json::from_slice(&server_hello).expect("json");
    assert_eq!(hello["trust_tier"].as_str(), Some("T0"));
    let trust = state
        .storage
        .get_viewer_trust(&rogue_id)
        .expect("trust lookup")
        .expect("row created");
    assert_eq!(trust.trust_tier, "T0");

    // Attempt a T2 git mutation — must be rejected at LAN preflight.
    let session_uuid = Uuid::parse_str(hello["session_id"].as_str().unwrap()).unwrap();
    let mut env = EnvelopeV2::new(
        session_uuid,
        1,
        StreamKind::Control,
        MessageType::CommandRequest,
        viewer_session.send_sequence(),
        viewer_session.recv_sequence(),
        10_000,
    );
    env.command_id = Some(Uuid::new_v4());
    env.payload = Some(serde_json::json!({ "command_type": "git.stage", "payload": {} }));
    let ct = viewer_session
        .encrypt(&serde_json::to_vec(&env).unwrap())
        .await
        .unwrap();
    ws_stream.send(Message::Binary(ct.into())).await.unwrap();

    // Drain initial telemetry/projects frames until CommandRejected arrives.
    let mut rejected = false;
    for _ in 0..10 {
        let frame = tokio::time::timeout(std::time::Duration::from_secs(2), ws_stream.next())
            .await
            .expect("frame timeout");
        if let Some(Ok(Message::Binary(b))) = frame {
            let pt = viewer_session.decrypt(&b).await.unwrap();
            let ev: EnvelopeV2 = serde_json::from_slice(&pt).unwrap();
            if ev.message_type == MessageType::CommandRejected {
                rejected = true;
                break;
            }
        }
    }
    assert!(
        rejected,
        "unapproved T0 LAN peer must receive CommandRejected for git.stage"
    );
}
