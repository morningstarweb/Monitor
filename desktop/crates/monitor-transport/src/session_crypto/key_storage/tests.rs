#![allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)]

use super::super::{NoiseKeypair, hex_encode};
#[cfg(not(windows))]
use super::HEX_PREFIX;
use super::{
    DPAPI_PERSISTENT_PREFIX, DPAPI_PREFIX, PRIVATE_KEY_SETTING, PUBLIC_KEY_SETTING,
    ProtectedSecret, get_or_create_host_noise_keypair, migrate_legacy_secrets, read_secret,
};
use monitor_storage::Storage;

#[test]
fn protected_secret_roundtrip() {
    let secret = [0xA5u8; 32];
    let protected = ProtectedSecret::protect(&secret).expect("protect");
    let encoded = protected.encode();
    #[cfg(windows)]
    assert!(
        encoded.starts_with(DPAPI_PERSISTENT_PREFIX),
        "expected persistent DPAPI prefix on Windows: {encoded}"
    );
    #[cfg(not(windows))]
    assert!(
        encoded.starts_with(HEX_PREFIX),
        "unexpected secret prefix: {encoded}"
    );
    let decoded = ProtectedSecret::decode(&encoded)
        .expect("decode")
        .expect("protected value");
    assert_eq!(decoded.unprotect().expect("unprotect"), secret);
}

#[cfg(windows)]
#[test]
fn dpapi2_and_legacy_dpapi_memory_migration_roundtrip() {
    let secret = b"persistent-host-noise-key-32-byt".to_vec();
    let legacy_blob = {
        use windows_sys::Win32::Security::Cryptography::{
            CRYPTPROTECTMEMORY_SAME_LOGON, CryptProtectMemory,
        };
        let total_len = 4 + secret.len();
        let padded_len = (total_len + 15) & !15;
        let mut buf = vec![0u8; padded_len];
        buf[..4].copy_from_slice(&u32::try_from(secret.len()).expect("len").to_le_bytes());
        buf[4..total_len].copy_from_slice(&secret);
        #[allow(unsafe_code)]
        let ok = unsafe {
            CryptProtectMemory(
                buf.as_mut_ptr().cast(),
                u32::try_from(buf.len()).expect("len"),
                CRYPTPROTECTMEMORY_SAME_LOGON,
            )
        };
        assert_ne!(ok, 0, "CryptProtectMemory must succeed");
        format!("{DPAPI_PREFIX}{}", hex_encode(&buf))
    };

    let storage = Storage::in_memory().expect("in-memory storage");
    storage
        .set_setting(PRIVATE_KEY_SETTING, &legacy_blob)
        .expect("legacy dpapi:");
    let keypair = NoiseKeypair::generate().expect("kp");
    storage
        .set_setting(PUBLIC_KEY_SETTING, &hex_encode(&keypair.public_key))
        .expect("pub");

    let (read_priv, needs_migration) = read_secret(&storage, PRIVATE_KEY_SETTING).expect("read");
    assert!(needs_migration);
    assert_eq!(read_priv.as_deref(), Some(secret.as_slice()));

    assert_eq!(migrate_legacy_secrets(&storage).expect("migrate"), 2);
    let stored_priv = storage
        .get_setting(PRIVATE_KEY_SETTING)
        .expect("get")
        .expect("val");
    assert!(stored_priv.starts_with(DPAPI_PERSISTENT_PREFIX));
    let (reloaded, still_needs) = read_secret(&storage, PRIVATE_KEY_SETTING).expect("reload");
    assert!(!still_needs);
    assert_eq!(reloaded.as_deref(), Some(secret.as_slice()));
}

#[test]
fn legacy_plaintext_is_migrated_once() {
    let storage = Storage::in_memory().expect("in-memory storage");
    let keypair = NoiseKeypair::generate().expect("keypair");
    storage
        .set_setting(PRIVATE_KEY_SETTING, &hex_encode(&keypair.private_key))
        .expect("legacy private key");
    storage
        .set_setting(PUBLIC_KEY_SETTING, &hex_encode(&keypair.public_key))
        .expect("legacy public key");

    let loaded = get_or_create_host_noise_keypair(&storage).expect("load/migrate");
    assert_eq!(loaded, keypair);

    for key in [PRIVATE_KEY_SETTING, PUBLIC_KEY_SETTING] {
        let raw = storage.get_setting(key).expect("read").expect("value");
        assert!(
            ProtectedSecret::decode(&raw).expect("decode").is_some(),
            "secret {key} was not rewritten in protected form"
        );
    }
    assert_eq!(
        migrate_legacy_secrets(&storage).expect("no-op migration"),
        0
    );
    assert_eq!(
        get_or_create_host_noise_keypair(&storage).expect("reload"),
        keypair
    );
}

#[test]
fn malformed_protected_value_fails_closed() {
    let storage = Storage::in_memory().expect("in-memory storage");
    storage
        .set_setting(PRIVATE_KEY_SETTING, &format!("{DPAPI_PREFIX}zz"))
        .expect("corrupt private key");
    let outcome = get_or_create_host_noise_keypair(&storage);
    assert!(
        outcome.is_err(),
        "corrupt secret must not be silently accepted"
    );
}
