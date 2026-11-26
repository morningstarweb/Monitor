use futures_util::{SinkExt, StreamExt};
use monitor_core::{EnvelopeV2, MessageType, StreamKind};
use monitor_storage::Storage;
use monitor_telemetry::TelemetrySampler;
use monitor_transport::AppState;
use monitor_transport::host_control::CLOUD_V2_PROLOGUE;
use monitor_transport::session_crypto::{
    NoiseKeypair, ViewerHandshakeInitiator, get_or_create_host_noise_keypair,
};
use tokio::net::TcpListener;
use tokio_tungstenite::tungstenite::Message;
use uuid::Uuid;

pub(super) fn create_test_state() -> AppState {
    let storage = Storage::in_memory().expect("in-memory storage");
    // AUD-04: test operator is explicitly authorized; defaults fail closed.
    storage
        .set_setting("allow_git_operations", "1")
        .expect("grant git");
    storage
        .set_setting("allow_cloud_operations", "1")
        .expect("grant cloud");
    storage
        .set_setting("allow_local_network", "1")
        .expect("grant LAN");
    let telemetry = TelemetrySampler::new();
    let git_executor = monitor_git::GitExecutor::new().expect("git executor");
    AppState::new(storage, telemetry, git_executor)
}

#[tokio::test]
async fn test_host_control_relay_e2ee_handshake_and_idle_transitions() {
    let state = create_test_state();

    // Setup host Noise keys
    let host_keys = get_or_create_host_noise_keypair(&state.storage).unwrap();
    let host_pk = host_keys.public_key;

    // Start a local mock HostRoom WebSocket server
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let port = listener.local_addr().unwrap().port();

    // Spawn mock HostRoom server
    tokio::spawn(async move {
        let (stream, _) = listener.accept().await.unwrap();
        let mut ws = tokio_tungstenite::accept_async(stream).await.unwrap();

        // 1. Notify host of viewer connection
        ws.send(Message::Text(
            r#"{"event":"viewer_connected","viewer_device_id":"test_viewer"}"#.into(),
        ))
        .await
        .unwrap();
    });

    // Connect outbound from simulated host worker
    let ws_url = format!("ws://127.0.0.1:{port}");
    let (ws_stream, _) = tokio_tungstenite::connect_async(&ws_url).await.unwrap();
    let (_host_sink, mut host_stream) = ws_stream.split();

    // Verify reception of viewer_connected event
    let event_msg = host_stream.next().await.unwrap().unwrap();
    assert!(matches!(event_msg, Message::Text(ref t) if t.contains("viewer_connected")));

    // Generate viewer client keys and handshake msg1
    let viewer_keys = NoiseKeypair::generate().unwrap();
    let mut initiator =
        ViewerHandshakeInitiator::new(&viewer_keys, &host_pk, CLOUD_V2_PROLOGUE).unwrap();
    let client_hello = serde_json::json!({ "viewer_version": "v3.2" }).to_string();
    let msg1 = initiator
        .write_handshake_msg1(client_hello.as_bytes())
        .unwrap();

    // Simulate host handling msg1
    let mut responder =
        monitor_transport::HostHandshakeResponder::new(&host_keys, CLOUD_V2_PROLOGUE).unwrap();
    responder.read_handshake_msg1(&msg1).unwrap();
    let (msg2, host_session) = responder
        .write_handshake_msg2(b"{\"status\":\"ok\"}", "session-123".to_string(), 1)
        .unwrap();

    // Initiator completes handshake with msg2
    let (_, viewer_session) = initiator
        .read_handshake_msg2(&msg2, "session-123".to_string(), 1)
        .unwrap();

    // Viewer encrypts EnvelopeV2 ping
    let ping_env = EnvelopeV2::new(
        Uuid::new_v4(),
        1,
        StreamKind::Control,
        MessageType::Ping,
        viewer_session.send_sequence(),
        viewer_session.recv_sequence(),
        30_000,
    );
    let ping_bytes = serde_json::to_vec(&ping_env).unwrap();
    let encrypted_ping = viewer_session.encrypt(&ping_bytes).await.unwrap();

    // Host decrypts ping
    let decrypted_ping = host_session.decrypt(&encrypted_ping).await.unwrap();
    let parsed_ping: EnvelopeV2 = serde_json::from_slice(&decrypted_ping).unwrap();
    assert_eq!(parsed_ping.message_type, MessageType::Ping);

    // Host responds with encrypted pong
    let pong_env = EnvelopeV2::new(
        parsed_ping.session_id,
        parsed_ping.epoch,
        StreamKind::Control,
        MessageType::Pong,
        host_session.send_sequence(),
        host_session.recv_sequence(),
        30_000,
    );
    let pong_bytes = serde_json::to_vec(&pong_env).unwrap();
    let encrypted_pong = host_session.encrypt(&pong_bytes).await.unwrap();

    // Viewer decrypts pong
    let decrypted_pong = viewer_session.decrypt(&encrypted_pong).await.unwrap();
    let parsed_pong: EnvelopeV2 = serde_json::from_slice(&decrypted_pong).unwrap();
    assert_eq!(parsed_pong.message_type, MessageType::Pong);
}
