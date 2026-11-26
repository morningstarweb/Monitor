#[test]
fn constant_time_comparison_matches_only_equal_byte_sequences() {
    assert!(constant_time_eq(b"same-value", b"same-value"));
    assert!(!constant_time_eq(b"same-value", b"same-valuF"));
    assert!(!constant_time_eq(b"short", b"longer-value"));
    assert!(constant_time_eq(&[], &[]));
}

#[test]
fn static_key_pin_accepts_the_authenticated_key_only() {
    let keypair = NoiseKeypair::generate().expect("keypair");
    let foreign = NoiseKeypair::generate().expect("foreign keypair");
    let pin = hex_encode(&keypair.public_key);

    assert!(verify_static_key_pin(&keypair.public_key, &pin));
    assert!(verify_static_key_pin_hex(&hex_encode(&keypair.public_key), &pin));
    assert!(!verify_static_key_pin(&foreign.public_key, &pin));
    assert!(!verify_static_key_pin_hex(
        &hex_encode(&foreign.public_key),
        &pin
    ));

    // Malformed pins fail closed: wrong length or non-hex. Hex parsing itself
    // is case-insensitive, so an uppercase pin still denotes the same key.
    assert!(!verify_static_key_pin(&keypair.public_key, "deadbeef"));
    assert!(!verify_static_key_pin(&keypair.public_key, &"zz".repeat(32)));
    assert!(verify_static_key_pin(
        &keypair.public_key,
        &pin.to_ascii_uppercase()
    ));
    assert!(!verify_static_key_pin_hex("not-a-key", &pin));
}

#[test]
fn handshake_static_identity_can_be_pinned() {
    let host_keys = NoiseKeypair::generate().expect("host keys");
    let viewer_keys = NoiseKeypair::generate().expect("viewer keys");
    let other_keys = NoiseKeypair::generate().expect("other keys");

    let mut initiator = ViewerHandshakeInitiator::new(
        &viewer_keys,
        &host_keys.public_key,
        b"MONITOR_PIN_TEST",
    )
    .expect("initiator");
    let msg1 = initiator
        .write_handshake_msg1(b"{\"device\":\"phone\"}")
        .expect("msg1");
    let mut responder =
        HostHandshakeResponder::new(&host_keys, b"MONITOR_PIN_TEST").expect("responder");
    responder.read_handshake_msg1(&msg1).expect("read msg1");

    assert!(responder.verify_remote_static_pin(&viewer_keys.public_key));
    assert!(!responder.verify_remote_static_pin(&other_keys.public_key));

    // A responder that has not authenticated a static key cannot be pinned.
    let fresh = HostHandshakeResponder::new(&host_keys, b"MONITOR_PIN_TEST").expect("responder");
    assert!(!fresh.verify_remote_static_pin(&viewer_keys.public_key));
}
