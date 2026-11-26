use super::helpers::{create_test_state, spawn_v2_ws_server};
use futures_util::{SinkExt, StreamExt};
use monitor_core::{EnvelopeV2, MessageType, StreamKind};
use monitor_transport::lan_v2::LAN_V2_PROLOGUE;
use monitor_transport::session_crypto::{
    NoiseKeypair, ViewerHandshakeInitiator, get_or_create_host_noise_keypair, hex_encode,
};
use tokio_tungstenite::tungstenite::Message;
use uuid::Uuid;

#[tokio::test]
async fn test_secure_lan_v2_e2ee_handshake_and_stream() {
    let state = create_test_state();
    let host_keys = get_or_create_host_noise_keypair(&state.storage).expect("host keys");
    let ws_url = spawn_v2_ws_server(state.clone()).await;

    // 1. Initiator (Android Viewer) connects via WebSocket
    let (mut ws_stream, _) = tokio_tungstenite::connect_async(&ws_url)
        .await
        .expect("connect to v2 stream");

    let viewer_keys = NoiseKeypair::generate().expect("viewer keys");
    let viewer_device_id = hex_encode(&viewer_keys.public_key);

    // Enroll viewer as T2 Operator in host storage
    state
        .storage
        .enroll_viewer(&viewer_device_id, "test_owner", &viewer_device_id)
        .expect("enroll viewer");
    state
        .storage
        .approve_viewer_tier(&viewer_device_id, "T2")
        .expect("approve tier");

    // 2. Perform Noise IK Handshake
    let mut initiator =
        ViewerHandshakeInitiator::new(&viewer_keys, &host_keys.public_key, LAN_V2_PROLOGUE)
            .expect("initiator setup");

    let client_hello = b"{\"platform\":\"android\",\"version\":\"3.2\"}";
    let msg1 = initiator
        .write_handshake_msg1(client_hello)
        .expect("write msg1");

    ws_stream
        .send(Message::Binary(msg1.into()))
        .await
        .expect("send msg1");

    // Await Handshake Msg 2 from Host
    let resp = ws_stream
        .next()
        .await
        .expect("receive msg2")
        .expect("msg2 ok");
    let msg2_bytes = match resp {
        Message::Binary(bytes) => bytes.to_vec(),
        other => panic!("Expected Binary frame for msg2, got: {other:?}"),
    };

    let session_uuid = Uuid::new_v4();
    let epoch = 1u64;
    let (server_hello, mut viewer_session) = initiator
        .read_handshake_msg2(&msg2_bytes, session_uuid.to_string(), epoch)
        .expect("complete handshake");

    assert!(String::from_utf8_lossy(&server_hello).contains("\"status\":\"authenticated\""));
    let hello_json: serde_json::Value = serde_json::from_slice(&server_hello).expect("parse hello");
    let server_session_id = hello_json["session_id"].as_str().expect("session_id");
    viewer_session.session_id = server_session_id.to_string();
    let host_session_uuid = Uuid::parse_str(server_session_id).expect("parse session uuid");

    // 3. Receive initial encrypted telemetry snapshot from Host
    let initial_frame = ws_stream
        .next()
        .await
        .expect("receive telemetry")
        .expect("frame ok");
    let ct_bytes = match initial_frame {
        Message::Binary(bytes) => bytes.to_vec(),
        other => panic!("Expected binary ciphertext frame, got: {other:?}"),
    };

    let plaintext = viewer_session
        .decrypt(&ct_bytes)
        .await
        .expect("decrypt initial snapshot");
    let envelope: EnvelopeV2 =
        serde_json::from_slice(&plaintext).expect("parse telemetry envelope");

    assert_eq!(envelope.stream, StreamKind::Telemetry);
    assert_eq!(envelope.message_type, MessageType::Snapshot);
    assert!(envelope.payload.is_some());

    // 4. Test Bidirectional Ping / Pong over E2EE
    let mut ping_envelope = EnvelopeV2::new(
        host_session_uuid,
        epoch,
        StreamKind::Control,
        MessageType::Ping,
        viewer_session.send_sequence(),
        viewer_session.recv_sequence(),
        10_000,
    );
    ping_envelope.payload = Some(serde_json::json!({ "ts": 123456789 }));

    let ping_bytes = serde_json::to_vec(&ping_envelope).expect("serialize ping");
    let ping_ct = viewer_session
        .encrypt(&ping_bytes)
        .await
        .expect("encrypt ping");
    ws_stream
        .send(Message::Binary(ping_ct.into()))
        .await
        .expect("send ping");

    let pong_frame = ws_stream
        .next()
        .await
        .expect("receive pong")
        .expect("pong ok");
    let pong_ct = match pong_frame {
        Message::Binary(bytes) => bytes.to_vec(),
        other => panic!("Expected binary pong frame, got: {other:?}"),
    };
    let pong_pt = viewer_session
        .decrypt(&pong_ct)
        .await
        .expect("decrypt pong");
    let pong_env: EnvelopeV2 = serde_json::from_slice(&pong_pt).expect("parse pong env");
    assert_eq!(pong_env.message_type, MessageType::Pong);
}

/// SEC-01 regression: a revoked viewer must never regain access by simply
/// reconnecting on the same LAN. The connection path performs no state-changing
/// authorization write, so the durable revocation must survive the attempt.
#[tokio::test]
async fn test_revoked_lan_v2_viewer_cannot_reconnect() {
    let state = create_test_state();
    let host_keys = get_or_create_host_noise_keypair(&state.storage).expect("host keys");
    let ws_url = spawn_v2_ws_server(state.clone()).await;

    let (mut ws_stream, _) = tokio_tungstenite::connect_async(&ws_url)
        .await
        .expect("connect to v2 stream");

    let viewer_keys = NoiseKeypair::generate().expect("viewer keys");
    let viewer_device_id = hex_encode(&viewer_keys.public_key);
    state
        .storage
        .enroll_viewer(&viewer_device_id, "revoked_phone", &viewer_device_id)
        .expect("enroll viewer");
    state
        .storage
        .approve_viewer_tier(&viewer_device_id, "T2")
        .expect("approve tier");
    assert!(
        state
            .storage
            .revoke_viewer(&viewer_device_id)
            .expect("revoke viewer"),
        "revocation must succeed"
    );

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

    // No authenticated msg2 may be produced for a revoked identity.
    let outcome = tokio::time::timeout(std::time::Duration::from_secs(2), ws_stream.next()).await;
    if let Ok(Some(Ok(Message::Binary(bytes)))) = outcome {
        let text = String::from_utf8_lossy(&bytes);
        assert!(
            !text.contains("authenticated"),
            "revoked viewer received an authenticated handshake: {text}"
        );
    }

    let trust = state
        .storage
        .get_viewer_trust(&viewer_device_id)
        .expect("trust lookup")
        .expect("trust row must remain");
    assert!(
        trust.revoked_at_utc_ms.is_some(),
        "revocation must not be cleared by a reconnect attempt"
    );
}
