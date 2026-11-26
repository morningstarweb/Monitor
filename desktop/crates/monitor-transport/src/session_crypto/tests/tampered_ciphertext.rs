#[tokio::test]
async fn test_tampered_ciphertext_fails_auth() {
    let host_keys = NoiseKeypair::generate().expect("host keys");
    let viewer_keys = NoiseKeypair::generate().expect("viewer keys");
    let prologue = b"PROLOGUE";

    let mut initiator =
        ViewerHandshakeInitiator::new(&viewer_keys, &host_keys.public_key, prologue).unwrap();
    let msg1 = initiator.write_handshake_msg1(b"").unwrap();

    let mut responder = HostHandshakeResponder::new(&host_keys, prologue).unwrap();
    let _ = responder.read_handshake_msg1(&msg1).unwrap();
    let (msg2, host_session) = responder
        .write_handshake_msg2(b"", "s1".to_string(), 1)
        .unwrap();
    let (_, viewer_session) = initiator
        .read_handshake_msg2(&msg2, "s1".to_string(), 1)
        .unwrap();

    let mut ciphertext = host_session.encrypt(b"secret telemetry").await.unwrap();
    // Tamper with one byte in the ciphertext payload
    ciphertext[0] ^= 0xFF;

    let res = viewer_session.decrypt(&ciphertext).await;
    assert!(res.is_err(), "Tampered ciphertext must fail authentication");
}

/// Noise spec §5.1: a failed AEAD authentication must not consume the receive
/// nonce. Otherwise one injected frame permanently desyncs the session.
#[tokio::test]
async fn test_invalid_data_channel_frame_does_not_advance_recv_nonce() {
    let host_keys = NoiseKeypair::generate().expect("host keys");
    let viewer_keys = NoiseKeypair::generate().expect("viewer keys");
    let prologue = b"PROLOGUE";

    let mut initiator =
        ViewerHandshakeInitiator::new(&viewer_keys, &host_keys.public_key, prologue).unwrap();
    let msg1 = initiator.write_handshake_msg1(b"").unwrap();
    let mut responder = HostHandshakeResponder::new(&host_keys, prologue).unwrap();
    let _ = responder.read_handshake_msg1(&msg1).unwrap();
    let (msg2, host_session) = responder
        .write_handshake_msg2(b"", "s1".to_string(), 1)
        .unwrap();
    let (_, viewer_session) = initiator
        .read_handshake_msg2(&msg2, "s1".to_string(), 1)
        .unwrap();

    let valid = host_session.encrypt_data_channel(b"legitimate frame").unwrap();
    let mut tampered = valid.clone();
    tampered[0] ^= 0xFF;
    assert!(
        viewer_session.decrypt_data_channel(&tampered).is_err(),
        "tampered data channel frame must be rejected"
    );
    let plaintext = viewer_session
        .decrypt_data_channel(&valid)
        .expect("the original frame must still decrypt after a rejected frame");
    assert_eq!(plaintext, b"legitimate frame");
}

/// Duplicate delivery must not be accepted twice: the receive nonce advanced
/// on the first successful decryption, so the replay fails authentication.
#[tokio::test]
async fn test_replayed_data_channel_frame_is_rejected() {
    let host_keys = NoiseKeypair::generate().expect("host keys");
    let viewer_keys = NoiseKeypair::generate().expect("viewer keys");
    let prologue = b"PROLOGUE";
    let mut initiator =
        ViewerHandshakeInitiator::new(&viewer_keys, &host_keys.public_key, prologue).unwrap();
    let msg1 = initiator.write_handshake_msg1(b"").unwrap();
    let mut responder = HostHandshakeResponder::new(&host_keys, prologue).unwrap();
    let _ = responder.read_handshake_msg1(&msg1).unwrap();
    let (msg2, host_session) = responder
        .write_handshake_msg2(b"", "s1".to_string(), 1)
        .unwrap();
    let (_, viewer_session) = initiator
        .read_handshake_msg2(&msg2, "s1".to_string(), 1)
        .unwrap();

    let frame = host_session.encrypt_data_channel(b"only once").unwrap();
    assert_eq!(
        viewer_session.decrypt_data_channel(&frame).unwrap(),
        b"only once"
    );
    assert!(
        viewer_session.decrypt_data_channel(&frame).is_err(),
        "replayed frame must fail authentication"
    );
}
