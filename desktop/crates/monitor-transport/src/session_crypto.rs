use monitor_core::CoreError;
use snow::params::NoiseParams;
use snow::{Builder, TransportState};
use std::sync::atomic::{AtomicU64, Ordering};
use tokio::sync::Mutex;

/// Primary Noise protocol pattern specified by MONITOR v3.2 Master Plan.
pub const NOISE_PATTERN: &str = "Noise_IK_25519_ChaChaPoly_SHA256";

/// Maximum application plaintext payload allowed per frame (16 KiB).
pub const MAX_PLAINTEXT_FRAME_SIZE: usize = 16 * 1024;

/// Poly1305 MAC tag size in bytes.
pub const AEAD_TAG_SIZE: usize = 16;

/// Maximum ciphertext frame size accepted on wire.
pub const MAX_CIPHERTEXT_FRAME_SIZE: usize = MAX_PLAINTEXT_FRAME_SIZE + AEAD_TAG_SIZE;

include!("session_crypto/keypair.rs");
include!("session_crypto/host_handshake.rs");
include!("session_crypto/viewer_handshake.rs");
include!("session_crypto/secure_session.rs");
include!("session_crypto/encoding.rs");
include!("session_crypto/identity.rs");
pub mod key_storage;
pub use key_storage::{ProtectedSecret, get_or_create_host_noise_keypair, migrate_legacy_secrets};

#[cfg(test)]
#[allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)]
mod tests {
    use super::*;

    include!("session_crypto/tests/handshake_roundtrip.rs");
    include!("session_crypto/tests/tampered_ciphertext.rs");
    include!("session_crypto/tests/frame_limits.rs");
    include!("session_crypto/tests/identity_pinning.rs");
}
