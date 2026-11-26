#[cfg(windows)]
#[allow(unsafe_code)]
mod dpapi;
#[cfg(test)]
mod tests;

use super::{NoiseKeypair, hex_decode, hex_encode};
use monitor_core::CoreError;
use monitor_storage::Storage;

pub(super) const PRIVATE_KEY_SETTING: &str = "host_noise_private_key";
pub(super) const PUBLIC_KEY_SETTING: &str = "host_noise_public_key";
pub(super) const DPAPI_PREFIX: &str = "dpapi:";
pub(super) const DPAPI_PERSISTENT_PREFIX: &str = "dpapi2:";
pub(super) const HEX_PREFIX: &str = "hex:";

#[cfg(not(windows))]
mod fallback {
    use super::*;

    pub(super) fn protect(plaintext: &[u8]) -> Result<Vec<u8>, CoreError> {
        Ok(plaintext.to_vec())
    }

    pub(super) fn unprotect(blob: &[u8]) -> Result<Vec<u8>, CoreError> {
        if blob.len() > 4096 {
            return Err(CoreError::InvalidArgument(
                "Oversized test secret".to_string(),
            ));
        }
        Ok(blob.to_vec())
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ProtectedSecret {
    #[cfg(windows)]
    DpapiPersistent(Vec<u8>),
    #[cfg(windows)]
    DpapiLegacyMemory(Vec<u8>),
    #[cfg(not(windows))]
    Hex(Vec<u8>),
}

impl ProtectedSecret {
    pub fn protect(plaintext: &[u8]) -> Result<Self, CoreError> {
        #[cfg(windows)]
        {
            Ok(Self::DpapiPersistent(dpapi::protect(plaintext)?))
        }
        #[cfg(not(windows))]
        {
            Ok(Self::Hex(fallback::protect(plaintext)?))
        }
    }

    pub fn unprotect(&self) -> Result<Vec<u8>, CoreError> {
        match self {
            #[cfg(windows)]
            Self::DpapiPersistent(blob) => dpapi::unprotect_persistent(blob),
            #[cfg(windows)]
            Self::DpapiLegacyMemory(blob) => dpapi::unprotect_legacy_memory(blob),
            #[cfg(not(windows))]
            Self::Hex(blob) => fallback::unprotect(blob),
        }
    }

    pub fn encode(&self) -> String {
        match self {
            #[cfg(windows)]
            Self::DpapiPersistent(blob) => {
                format!("{DPAPI_PERSISTENT_PREFIX}{}", hex_encode(blob))
            }
            #[cfg(windows)]
            Self::DpapiLegacyMemory(blob) => format!("{DPAPI_PREFIX}{}", hex_encode(blob)),
            #[cfg(not(windows))]
            Self::Hex(blob) => format!("{HEX_PREFIX}{}", hex_encode(blob)),
        }
    }

    /// Decodes a protected value. `Ok(None)` means the raw value is a legacy
    /// plaintext hex secret that still needs migration.
    pub fn decode(raw: &str) -> Result<Option<Self>, CoreError> {
        if let Some(encoded) = raw.strip_prefix(DPAPI_PERSISTENT_PREFIX) {
            #[cfg(windows)]
            {
                return Ok(Some(Self::DpapiPersistent(hex_decode(encoded)?)));
            }
            #[cfg(not(windows))]
            {
                let _ = encoded;
                return Err(CoreError::InvalidArgument(
                    "DPAPI-protected secret cannot be opened on this platform".to_string(),
                ));
            }
        }
        if let Some(encoded) = raw.strip_prefix(DPAPI_PREFIX) {
            #[cfg(windows)]
            {
                return Ok(Some(Self::DpapiLegacyMemory(hex_decode(encoded)?)));
            }
            #[cfg(not(windows))]
            {
                let _ = encoded;
                return Err(CoreError::InvalidArgument(
                    "DPAPI-protected secret cannot be opened on this platform".to_string(),
                ));
            }
        }
        if let Some(encoded) = raw.strip_prefix(HEX_PREFIX) {
            #[cfg(windows)]
            {
                let _ = encoded;
                return Err(CoreError::InvalidArgument(
                    "Test-only secret format is not accepted on Windows".to_string(),
                ));
            }
            #[cfg(not(windows))]
            {
                return Ok(Some(Self::Hex(hex_decode(encoded)?)));
            }
        }
        Ok(None)
    }
}

fn read_setting(storage: &Storage, key: &str) -> Result<Option<String>, CoreError> {
    storage
        .get_setting(key)
        .map_err(|e| CoreError::Internal(format!("Failed to read {key}: {e}")))
}

fn write_protected(storage: &Storage, key: &str, secret: &[u8]) -> Result<(), CoreError> {
    let encoded = ProtectedSecret::protect(secret)?.encode();
    storage
        .set_setting(key, &encoded)
        .map_err(|e| CoreError::Internal(format!("Failed to save {key}: {e}")))
}

/// Reads one secret, returning `(bytes, needs_migration_to_dpapi2)`.
pub(super) fn read_secret(
    storage: &Storage,
    key: &str,
) -> Result<(Option<Vec<u8>>, bool), CoreError> {
    let Some(raw) = read_setting(storage, key)? else {
        return Ok((None, false));
    };
    match ProtectedSecret::decode(&raw)? {
        #[cfg(windows)]
        Some(ProtectedSecret::DpapiLegacyMemory(blob)) => {
            Ok((Some(dpapi::unprotect_legacy_memory(&blob)?), true))
        }
        Some(protected) => Ok((Some(protected.unprotect()?), false)),
        None => Ok((Some(hex_decode(&raw)?), true)),
    }
}

/// Migrates legacy plaintext Noise keys to protected storage exactly once.
pub fn migrate_legacy_secrets(storage: &Storage) -> Result<usize, CoreError> {
    let mut migrated = 0usize;
    for key in [PRIVATE_KEY_SETTING, PUBLIC_KEY_SETTING] {
        let (secret, was_legacy) = read_secret(storage, key)?;
        if was_legacy && let Some(secret) = secret {
            write_protected(storage, key, &secret)?;
            migrated = migrated.saturating_add(1);
        }
    }
    Ok(migrated)
}

/// Retrieves the host's persistent Noise static keypair (K2) from SQLite,
/// or generates and securely stores a new one if not yet provisioned.
pub fn get_or_create_host_noise_keypair(storage: &Storage) -> Result<NoiseKeypair, CoreError> {
    let (priv_bytes, priv_was_legacy) = read_secret(storage, PRIVATE_KEY_SETTING)?;
    let (pub_bytes, pub_was_legacy) = read_secret(storage, PUBLIC_KEY_SETTING)?;

    if let (Some(priv_bytes), Some(pub_bytes)) = (priv_bytes, pub_bytes)
        && priv_bytes.len() == 32
        && pub_bytes.len() == 32
    {
        if priv_was_legacy || pub_was_legacy {
            let _ = migrate_legacy_secrets(storage)?;
        }
        let mut priv_arr = [0u8; 32];
        let mut pub_arr = [0u8; 32];
        priv_arr.copy_from_slice(&priv_bytes);
        pub_arr.copy_from_slice(&pub_bytes);
        return Ok(NoiseKeypair {
            private_key: priv_arr,
            public_key: pub_arr,
        });
    }

    let keypair = NoiseKeypair::generate()?;
    write_protected(storage, PRIVATE_KEY_SETTING, &keypair.private_key)?;
    write_protected(storage, PUBLIC_KEY_SETTING, &keypair.public_key)?;

    Ok(keypair)
}
