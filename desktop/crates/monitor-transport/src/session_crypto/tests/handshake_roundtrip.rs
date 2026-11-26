#[tokio::test]
async fn test_noise_handshake_and_transport_roundtrip() {
    let host_keys = NoiseKeypair::generate().expect("host keys");
    let viewer_keys = NoiseKeypair::generate().expect("viewer keys");

    let prologue = b"MONITOR_V3_2_PROLOGUE";
    let session_id = "test-session-uuid".to_string();

    // 1. Initiator prepares Handshake Msg 1
    let mut initiator =
        ViewerHandshakeInitiator::new(&viewer_keys, &host_keys.public_key, prologue)
            .expect("initiator setup");
    let client_hello = b"{\"client\":\"Android\"}";
    let msg1 = initiator
        .write_handshake_msg1(client_hello)
        .expect("msg1 generation");

    // 2. Responder reads Handshake Msg 1
    let mut responder = HostHandshakeResponder::new(&host_keys, prologue).expect("responder setup");
    let decoded_hello = responder.read_handshake_msg1(&msg1).expect("msg1 reading");
    assert_eq!(decoded_hello, client_hello);
    assert_eq!(
        responder.remote_static_public_key(),
        Some(viewer_keys.public_key)
    );

    // 3. Responder prepares Handshake Msg 2
    let server_hello = b"{\"server\":\"Windows\"}";
    let (msg2, host_session) = responder
        .write_handshake_msg2(server_hello, session_id.clone(), 1)
        .expect("msg2 generation");

    // 4. Initiator reads Handshake Msg 2 -> Transport mode
    let (decoded_server_hello, viewer_session) = initiator
        .read_handshake_msg2(&msg2, session_id.clone(), 1)
        .expect("msg2 reading");
    assert_eq!(decoded_server_hello, server_hello);

    // 5. Encrypted bidirectional transport communication
    let telemetry_sample = b"{\"cpu\":12.5,\"ram\":45.2}";
    let ciphertext = host_session
        .encrypt(telemetry_sample)
        .await
        .expect("host encrypt");
    assert_ne!(ciphertext, telemetry_sample);

    let decrypted = viewer_session
        .decrypt(&ciphertext)
        .await
        .expect("viewer decrypt");
    assert_eq!(decrypted, telemetry_sample);
    assert_eq!(host_session.send_sequence(), 1);
    assert_eq!(viewer_session.recv_sequence(), 1);

    // Viewer sends command to Host
    let command_payload = b"{\"command\":\"git_status\"}";
    let cmd_ciphertext = viewer_session
        .encrypt(command_payload)
        .await
        .expect("viewer encrypt");
    let host_decrypted = host_session
        .decrypt(&cmd_ciphertext)
        .await
        .expect("host decrypt");
    assert_eq!(host_decrypted, command_payload);
}
