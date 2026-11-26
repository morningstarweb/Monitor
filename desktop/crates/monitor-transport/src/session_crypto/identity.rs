// Constant-time identity verification helpers (SEC-03).
//
// These helpers deliberately introduce no new cryptographic primitives. They
// only provide a uniform-time byte comparison and a pinned-key check built on
// the existing `subtle` crate (already used for pairing-code verification).

use subtle::ConstantTimeEq;

/// Compares two byte sequences without branching on secret-dependent content.
///
/// The lengths are treated as public metadata: a length mismatch returns
/// immediately, while equal-length inputs are compared in constant time.
pub fn constant_time_eq(a: &[u8], b: &[u8]) -> bool {
    if a.len() != b.len() {
        return false;
    }
    bool::from(a.ct_eq(b))
}

/// Verifies a 32-byte Noise static public key against a hex-encoded pin.
///
/// A malformed or non-32-byte pin never matches (fail closed).
pub fn verify_static_key_pin(actual: &[u8], expected_hex: &str) -> bool {
    if actual.len() != 32 || expected_hex.len() != 64 {
        return false;
    }
    let Ok(expected) = hex_decode(expected_hex) else {
        return false;
    };
    constant_time_eq(actual, &expected)
}

/// Hex-encoded variant used when the presented identity is itself a hex string
/// (the Noise static key is the device identity on both LAN v2 and HostRoom).
pub fn verify_static_key_pin_hex(actual_hex: &str, expected_hex: &str) -> bool {
    if actual_hex.len() != 64 || expected_hex.len() != 64 {
        return false;
    }
    constant_time_eq(actual_hex.as_bytes(), expected_hex.as_bytes())
}

impl HostHandshakeResponder {
    /// Returns true only when the authenticated remote static key equals the
    /// pinned key. A failed handshake has no static key and never matches.
    pub fn verify_remote_static_pin(&self, pinned: &[u8; 32]) -> bool {
        self.remote_static
            .map(|actual| constant_time_eq(&actual, pinned))
            .unwrap_or(false)
    }
}
