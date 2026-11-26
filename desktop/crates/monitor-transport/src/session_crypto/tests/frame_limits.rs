#[tokio::test]
async fn test_max_frame_size_enforced() {
    let host_keys = NoiseKeypair::generate().unwrap();
    let viewer_keys = NoiseKeypair::generate().unwrap();
    let mut initiator =
        ViewerHandshakeInitiator::new(&viewer_keys, &host_keys.public_key, b"").unwrap();
    let msg1 = initiator.write_handshake_msg1(b"").unwrap();
    let mut responder = HostHandshakeResponder::new(&host_keys, b"").unwrap();
    let _ = responder.read_handshake_msg1(&msg1).unwrap();
    let (msg2, host_session) = responder
        .write_handshake_msg2(b"", "s1".to_string(), 1)
        .unwrap();
    let _ = initiator
        .read_handshake_msg2(&msg2, "s1".to_string(), 1)
        .unwrap();

    // 17 KiB exceeds 16 KiB limit
    let oversized = vec![0u8; 17 * 1024];
    let res = host_session.encrypt(&oversized).await;
    assert!(matches!(res, Err(CoreError::InvalidArgument(_))));
}
